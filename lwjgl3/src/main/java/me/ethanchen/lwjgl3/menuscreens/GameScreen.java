package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.music.MusicTag;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.Particle;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.ParticleBroadcast;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PlacementSoundBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

/**
 * Thin coordinator for the in-progress game screen. Delegates to:
 * <ul>
 *   <li>{@link GameInputHandler}  — DAS/ARR, keyboard + controller dispatch
 *   <li>{@link ClientMovePredictor} — pending-move queue and client-side prediction replay
 *   <li>{@link ParticleFactory}   — server-event → local {@link Particle} conversion
 * </ul>
 * The rendering body ({@link #renderSingleBoard}) remains here for now and is a candidate
 * for further extraction into a dedicated {@code SingleBoardView} class.
 */
public class GameScreen extends MenuScreen {
    private GameHandler game;
    private GameDrawMode drawMode;
    private long lastUpdateMs;
    private int deltaTime;
    private int playerId;

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random particleRng = new Random();

    // Server-authoritative state
    private boolean holdAvailable = true;
    private float latestExplodeProgress = -1f;
    private boolean ownPieceHoldGlow = false;

    // End-game explosion state
    private boolean exploded = false;
    private int fadeTimerMs = 0;
    private EndGameBroadcast endGamePacket = null;

    private ScoreModeData latestScoreMode;
    private PuzzleModeData latestPuzzleMode;

    private long gameEndTargetMs;
    private long startTimeMS;
    private String[] playerNames;

    // Collaborators
    private final ClientMovePredictor predictor;
    private final GameInputHandler inputHandler;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = buildDispatcher();

    private PacketDispatcher<ClientPacketWrapper> buildDispatcher() {
        return new PacketDispatcher<ClientPacketWrapper>()
                .on(LightGameStateBroadcast.class, w -> handleLightGameState((LightGameStateBroadcast) w.packet))
                .on(EndGameBroadcast.class,         w -> handleEndGame((EndGameBroadcast) w.packet))
                .on(ParticleBroadcast.class,        w -> handleParticleBroadcast((ParticleBroadcast) w.packet))
                .on(PlacementSoundBroadcast.class,  w -> handlePlacementSound((PlacementSoundBroadcast) w.packet))
                .on(HoldSoundBroadcast.class,       w -> handleHoldSound((HoldSoundBroadcast) w.packet))
                .on(BumpSoundBroadcast.class,       w -> handleBumpSound((BumpSoundBroadcast) w.packet));
    }

    public GameScreen(ClientApp app, StartGameBroadcast b) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        lastUpdateMs = System.currentTimeMillis();
        long startGameTimer = b.startTimeMS - System.currentTimeMillis();
        playerId = b.playerId;
        startTimeMS = b.startTimeMS;
        playerNames = b.playerNames;
        game = new GameHandler(b.totalPlayers);
        game.init(b.mode, startGameTimer);
        gameEndTargetMs = b.startTimeMS + GameConstants.SCORE_MODE_DURATION_MS;
        if (b.boards != null) {
            int count = Math.min(b.boards.length, game.getBoards().size());
            for (int i = 0; i < count; i++) {
                Board board = new Board(b.boards[i]);
                game.getBoards().set(i, board);
            }
        }
        drawMode = (b.mode != GameMode.NONE) ? GameDrawMode.SINGLE_BOARD : GameDrawMode.NONE;

        predictor = new ClientMovePredictor(app, playerId);
        inputHandler = new GameInputHandler(app, playerId, game, predictor);

        Controllers.addListener(controllerAdapter);
        AudioManager.getInstance().playMusic(MusicTag.MULTIPLAYER_GAME);
    }

    @Override
    public void update() {
        deltaTime = (int)(System.currentTimeMillis() - lastUpdateMs);
        game.update(deltaTime);
        lastUpdateMs = System.currentTimeMillis();

        Board board = game.getBoards().isEmpty() ? null : game.getBoards().get(0);
        if (board != null) {
            inputHandler.tick(deltaTime, board, holdAvailable);
        }

        // Advance and prune dead particles
        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update(deltaTime);
            if (p.isDead()) pit.remove();
        }

        // End-game fade-to-black
        if (exploded) {
            fadeTimerMs += deltaTime;
            if (fadeTimerMs >= 1000 && endGamePacket != null) {
                EndGameBroadcast pkt = endGamePacket;
                endGamePacket = null;
                app.disconnect();
                app.switchMenu(new EndGameScreen(app, pkt));
                return;
            }
        }

        if (predictor.hasTooManyPending()) {
            System.out.println("Too many unacknowledged moves (" + ClientMovePredictor.MAX_PENDING_MOVES
                    + "+); disconnecting.");
            app.disconnect();
            app.switchMenu(new MainMenu(app));
            return;
        }
        predictor.sendIfNeeded();
    }

    @Override
    public void render() {
        switch (drawMode) {
            case SINGLE_BOARD:
                renderSingleBoard();
                break;
            default:
                break;
        }

        // Fade-to-black overlay after explosion
        if (exploded) {
            float alpha = Math.min(1f, fadeTimerMs / 1000f);
            com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
            com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0f, 0f, 0f, alpha);
            shapes.rect(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(),
                    com.badlogic.gdx.Gdx.graphics.getHeight());
            shapes.end();
        }

        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    // -------------------------------------------------------------------------
    // Single-board rendering
    // -------------------------------------------------------------------------

    private void renderSingleBoard() {
        Board board = game.getBoards().get(0);
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
        float originX  = BoardRenderer.centeredOriginX(board, tileSize);
        float originY  = BoardRenderer.centeredOriginY(board, tileSize);

        // Build glow values from latest server data; default 0.5f until first packet
        float[] glowValues = new float[board.getActivePieces().size()];
        if (latestScoreMode != null && latestScoreMode.glowingValues != null
                && latestScoreMode.glowingValues.length == glowValues.length) {
            System.arraycopy(latestScoreMode.glowingValues, 0, glowValues, 0, glowValues.length);
        } else {
            Arrays.fill(glowValues, 0.5f);
        }

        // Override glow for blocked pieces
        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (board.getActivePieces().get(i).isBlockedFromSpawning) glowValues[i] = 0f;
        }
        if (ownPieceHoldGlow && playerId >= 0 && playerId < glowValues.length
                && board.getActivePieces().size() > playerId
                && board.getActivePieces().get(playerId).isBlockedFromSpawning) {
            glowValues[playerId] = 2f;
        }

        float blockedWhiteAmt = (latestExplodeProgress >= 0f)
                ? Math.min(1f, latestExplodeProgress / 1f) : 0f;

        float otherPlayerGrayscaleAmt = 0f;
        if (game.isStarted()) {
            long elapsedSinceStart = System.currentTimeMillis() - startTimeMS;
            otherPlayerGrayscaleAmt = Math.min(1f, Math.max(0f, elapsedSinceStart / 4000f));
        }

        Board.ShadowInfo[] shadows = new Board.ShadowInfo[board.getActivePieces().size()];
        if (!exploded) {
            for (int i = 0; i < shadows.length; i++) shadows[i] = board.getShadow(i);
        }

        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites,
                glowValues, shadows, blockedWhiteAmt, !exploded, playerId, otherPlayerGrayscaleAmt);
        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);

        // Repeat-column highlights
        if (latestScoreMode != null) {
            if (latestScoreMode.repeatColumn != -1) {
                BoardRenderer.getInstance().drawColumnHighlight(board, originX, originY, tileSize,
                        shapes, latestScoreMode.repeatColumn, 1f, 0f, 0f, 0.15f);
            }
            if (latestScoreMode.repeatColumn2 != -1) {
                BoardRenderer.getInstance().drawColumnHighlight(board, originX, originY, tileSize,
                        shapes, latestScoreMode.repeatColumn2, 1f, 0f, 0f, 0.15f);
            }
        }

        BoardRenderer.getInstance().drawParticles(particles, originX, originY, tileSize, shapes);
        BoardRenderer.getInstance().drawTextParticles(particles, originX, originY, tileSize, sprites, font);

        // Hold box
        float holdBoxSize = tileSize * 4f;
        float holdBoxX = originX - holdBoxSize - tileSize * 0.5f;
        float holdBoxY = originY + (board.bh() - 4) * tileSize;
        BoardRenderer.getInstance().drawHoldBox(board.getHeldPieceType(), holdAvailable,
                holdBoxX, holdBoxY, holdBoxSize, tileSize, shapes, sprites, font);

        // Timer / score box
        float timerBoxSize = tileSize * 5f;
        float timerBoxX = originX - timerBoxSize - tileSize * 0.5f;
        float timerBoxY = originY;
        if (game.getMode() == GameMode.MULTIPLAYER_PUZZLE) {
            long elapsedMs = latestPuzzleMode != null ? latestPuzzleMode.elapsedMs : 0;
            BoardRenderer.getInstance().drawCountUpTimerBox(
                    elapsedMs, timerBoxX, timerBoxY, timerBoxSize, tileSize, shapes, sprites, font);
        } else {
            long currentScore = latestScoreMode != null ? latestScoreMode.totalScore : 0;
            BoardRenderer.getInstance().drawTimerBox(
                    gameEndTargetMs, currentScore, timerBoxX, timerBoxY, timerBoxSize, tileSize,
                    shapes, sprites, font);
        }

        // Pre-start countdown
        renderCountdown(board, originX, originY, tileSize);

        // Spawn-position username labels before game start
        renderPlayerNames(board, originX, originY, tileSize);
    }

    private void renderCountdown(Board board, float originX, float originY, float tileSize) {
        long now = System.currentTimeMillis();
        long msUntilStart = startTimeMS - now;
        String countdownText = null;
        float beatProgress = -1f;

        if (!game.isStarted()) {
            if      (msUntilStart > 2000 && msUntilStart <= 3000) { countdownText = "3"; beatProgress = (3000f - msUntilStart) / 1000f; }
            else if (msUntilStart > 1000 && msUntilStart <= 2000) { countdownText = "2"; beatProgress = (2000f - msUntilStart) / 1000f; }
            else if (msUntilStart > 0    && msUntilStart <= 1000) { countdownText = "1"; beatProgress = (1000f - msUntilStart) / 1000f; }
        } else {
            long elapsed = now - startTimeMS;
            if (elapsed < 1000) { countdownText = "Start"; beatProgress = elapsed / 1000f; }
        }

        if (countdownText == null) return;

        float countdownAlpha, countdownScale;
        if (beatProgress < 0f) {
            countdownAlpha = 1f; countdownScale = 1f;
        } else {
            float fadeInT  = Math.min(1f, beatProgress / 0.45f);
            float fadeOutT = beatProgress > 0.75f ? (beatProgress - 0.75f) / 0.25f : 0f;
            countdownAlpha = fadeInT * (1f - fadeOutT);
            countdownScale = 2f - fadeInT;
        }

        com.badlogic.gdx.graphics.g2d.GlyphLayout cdLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float cdFs = 3.5f * (tileSize / font.getData().lineHeight) * countdownScale;
        font.getData().setScale(cdFs);
        cdLayout.setText(font, countdownText);
        float cdX = originX + board.bw() * tileSize * 0.5f - cdLayout.width * 0.5f;
        float cdY = originY + board.bh() * tileSize * 0.5f + cdLayout.height * 0.5f;
        sprites.begin();
        font.setColor(1f, 1f, 1f, countdownAlpha);
        font.draw(sprites, countdownText, cdX, cdY);
        sprites.end();
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.getData().setScale(savedX, savedY);
    }

    private void renderPlayerNames(Board board, float originX, float originY, float tileSize) {
        if (game.isStarted() || playerNames == null) return;
        long msUntilStart = startTimeMS - System.currentTimeMillis();
        float nameAlpha = msUntilStart > 500 ? 1f : Math.max(0f, msUntilStart / 500f);
        if (nameAlpha <= 0f) return;

        com.badlogic.gdx.graphics.g2d.GlyphLayout nameLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float nmFs = 0.9f * (tileSize / font.getData().lineHeight);
        font.getData().setScale(nmFs);
        sprites.begin();
        font.setColor(1f, 1f, 1f, nameAlpha);
        for (int i = 0; i < playerNames.length; i++) {
            if (playerNames[i] == null || playerNames[i].isEmpty()) continue;
            Vector2 spawn = board.getSpawnPos(i);
            float nameScreenX = originX + (spawn.x + 1.5f) * tileSize;
            float nameScreenY = originY + spawn.y * tileSize;
            nameLayout.setText(font, playerNames[i]);
            font.draw(sprites, playerNames[i], nameScreenX - nameLayout.width * 0.5f, nameScreenY);
        }
        sprites.end();
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.getData().setScale(savedX, savedY);
    }

    // -------------------------------------------------------------------------
    // Input processing (delegate to GameInputHandler)
    // -------------------------------------------------------------------------

    @Override
    public boolean keyDown(int keycode) {
        if (game.getBoards().isEmpty()) return super.keyDown(keycode);
        Board board = game.getBoards().get(0);
        boolean handled = inputHandler.keyDown(keycode, board, holdAvailable);
        return handled ? true : super.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        boolean handled = inputHandler.keyUp(keycode);
        return handled ? true : super.keyUp(keycode);
    }

    private final ControllerAdapter controllerAdapter = new ControllerAdapter() {
        @Override
        public boolean buttonDown(Controller controller, int buttonIndex) {
            if (game.getBoards().isEmpty()) return false;
            return inputHandler.controllerButtonDown(buttonIndex, game.getBoards().get(0), holdAvailable);
        }
        @Override
        public boolean buttonUp(Controller controller, int buttonIndex) {
            return inputHandler.controllerButtonUp(buttonIndex);
        }
    };

    // -------------------------------------------------------------------------
    // Packet handlers (delegate to appropriate collaborators)
    // -------------------------------------------------------------------------

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        dispatcher.dispatch(w);
    }

    private void handleLightGameState(LightGameStateBroadcast p) {
        if (p.boards != null) {
            int count = Math.min(p.boards.length, game.getBoards().size());
            for (int i = 0; i < count; i++) {
                game.getBoards().get(i).updateFromNetBoardLight(p.boards[i]);
            }
        }
        predictor.ackMovesUpTo(p.ackMoveId, game.getBoards().get(0));

        holdAvailable = p.holdAvailable;
        latestExplodeProgress = p.explodeProgress;
        ownPieceHoldGlow = p.ownPieceHoldGlow;
        if (p.scoreMode  != null) latestScoreMode  = p.scoreMode;
        if (p.puzzleMode != null) latestPuzzleMode = p.puzzleMode;

        game.setGravity(p.gravity);
        game.setGravityTickCounter(p.gravityTickCounter);
    }

    private void handleEndGame(EndGameBroadcast egp) {
        if (exploded) return;
        endGamePacket = egp;
        exploded      = true;
        fadeTimerMs   = 0;
        if (!game.getBoards().isEmpty()) {
            Board board = game.getBoards().get(0);
            for (me.ethanchen.game.board.Piece piece : board.getActivePieces()) {
                if (piece.tiles == null || piece.location == null) continue;
                for (com.badlogic.gdx.math.Vector2 offset : piece.tiles) {
                    float cx = piece.location.x + offset.x + 0.5f;
                    float cy = piece.location.y + offset.y + 0.5f;
                    for (int k = 0; k < 4; k++) {
                        Particle shard = new Particle();
                        shard.kind = Particle.Kind.PIECE_EXPLODE;
                        shard.x = cx;
                        shard.y = cy;
                        float angle = particleRng.nextFloat() * (float)(Math.PI * 2);
                        float speed = 2f + particleRng.nextFloat() * 4f;
                        shard.vx = (float) Math.cos(angle) * speed;
                        shard.vy = (float) Math.sin(angle) * speed;
                        shard.r = 1f; shard.g = 1f; shard.b = 1f;
                        shard.size = 0.18f + particleRng.nextFloat() * 0.14f;
                        shard.lifetime = 0.4f + particleRng.nextFloat() * 0.2f;
                        particles.add(shard);
                    }
                }
            }
        }
    }

    private void handleParticleBroadcast(ParticleBroadcast p) {
        if (p.spawners != null) {
            for (ParticleSpawner ps : p.spawners) {
                ParticleFactory.expandSpawner(ps, particles, particleRng);
            }
        }
        if (p.particles != null) {
            for (NetParticle np : p.particles) {
                ParticleFactory.expandNetParticle(np, particles, particleRng);
            }
        }
    }

    private void handlePlacementSound(PlacementSoundBroadcast p) {
        AudioManager.getInstance().playPlaceSound(p.playerId == playerId);
        if (p.combo >= 0) AudioManager.getInstance().playClearSound(p.combo);
    }

    private void handleHoldSound(HoldSoundBroadcast p) {
        if (p.success) {
            AudioManager.getInstance().playHoldSound(p.playerId == playerId, true);
        } else if (p.playerId == playerId) {
            AudioManager.getInstance().playHoldSound(true, false);
        }
    }

    private void handleBumpSound(BumpSoundBroadcast p) {
        boolean self = p.playerId == playerId || p.otherPlayerId == playerId;
        AudioManager.getInstance().playBumpSound(self);
    }

    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        Controllers.removeListener(controllerAdapter);
        AudioManager.getInstance().stopMusic();
    }

    private enum GameDrawMode { NONE, SINGLE_BOARD }
}
