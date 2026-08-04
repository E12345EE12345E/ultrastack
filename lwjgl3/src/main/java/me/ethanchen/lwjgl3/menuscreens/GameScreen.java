package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.input.LocalPlayerRoster;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.music.MusicTag;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.Particle;
import me.ethanchen.lwjgl3.render.shader.PlayerRipples;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.HardDropEffectsBroadcast;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.ParticleBroadcast;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PieceSwapBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

/**
 * Thin coordinator for the in-progress game screen. Supports zero or more local players
 * controlled by this client (keyboard and/or controllers).
 */
public class GameScreen extends MenuScreen {
    private GameHandler game;
    private GameDrawMode drawMode;
    private long lastUpdateMs;
    private int deltaTime;
    private final boolean isHost;
    private final List<LocalPlayer> localPlayers = new ArrayList<>();
    private final boolean[] isLocalSlot;
    private PlayerRipples ripples;

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random particleRng = new Random();

    // Server-authoritative shared state
    private float latestExplodeProgress = -1f;

    // End-game explosion state
    private boolean exploded = false;
    private int fadeTimerMs = 0;
    private EndGameBroadcast endGamePacket = null;

    private ScoreModeData latestScoreMode;
    private PuzzleModeData latestPuzzleMode;

    private long gameEndTargetMs;
    private long startTimeMS;
    private String[] playerNames;

    private final PacketDispatcher<ClientPacketWrapper> dispatcher = buildDispatcher();

    private PacketDispatcher<ClientPacketWrapper> buildDispatcher() {
        return new PacketDispatcher<ClientPacketWrapper>()
                .on(LightGameStateBroadcast.class, w -> handleLightGameState((LightGameStateBroadcast) w.packet))
                .on(EndGameBroadcast.class,         w -> handleEndGame((EndGameBroadcast) w.packet))
                .on(ParticleBroadcast.class,        w -> handleParticleBroadcast((ParticleBroadcast) w.packet))
                .on(HardDropEffectsBroadcast.class, w -> handleHardDropEffects((HardDropEffectsBroadcast) w.packet))
                .on(HoldSoundBroadcast.class,       w -> handleHoldSound((HoldSoundBroadcast) w.packet))
                .on(BumpSoundBroadcast.class,       w -> handleBumpSound((BumpSoundBroadcast) w.packet))
                .on(PieceSwapBroadcast.class,       w -> handlePieceSwap((PieceSwapBroadcast) w.packet));
    }

    public GameScreen(ClientApp app, StartGameBroadcast b, boolean isHost) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.isHost = isHost;
        lastUpdateMs = System.currentTimeMillis();
        long startGameTimer = b.msUntilStart;
        startTimeMS = lastUpdateMs + startGameTimer;
        playerNames = b.playerNames;
        game = new GameHandler(b.totalPlayers);
        game.init(b.mode, startGameTimer);
        gameEndTargetMs = startTimeMS + GameConstants.SCORE_MODE_DURATION_MS;
        if (b.boards != null) {
            int count = Math.min(b.boards.length, game.getBoards().size());
            for (int i = 0; i < count; i++) {
                Board board = new Board(b.boards[i]);
                game.getBoards().set(i, board);
            }
        }
        drawMode = (b.mode != GameMode.NONE) ? GameDrawMode.SINGLE_BOARD : GameDrawMode.NONE;

        isLocalSlot = new boolean[Math.max(1, b.totalPlayers & 0xFF)];
        LocalPlayerRoster roster = app.computeLocalPlayerRoster();
        byte[] ids = b.localPlayerIds != null ? b.localPlayerIds : new byte[0];
        int n = Math.min(ids.length, roster.size());
        for (int i = 0; i < n; i++) {
            int slot = ids[i] & 0xFF;
            LocalPlayerRoster.Entry entry = roster.getEntries().get(i);
            ClientMovePredictor predictor = new ClientMovePredictor(app, slot, i);
            GameInputHandler input = new GameInputHandler(app, slot, game, predictor);
            localPlayers.add(new LocalPlayer(slot, i, entry.source, entry.controllerSlot, input, predictor));
            if (slot >= 0 && slot < isLocalSlot.length) isLocalSlot[slot] = true;
        }

        if (!game.getBoards().isEmpty()) {
            ripples = new PlayerRipples(game.getBoards().get(0), isLocalSlot);
        }

        Controllers.addListener(controllerAdapter);
        AudioManager.getInstance().playMusic(MusicTag.MULTIPLAYER_GAME);
    }

    private boolean isLocalSlot(int slot) {
        return slot >= 0 && slot < isLocalSlot.length && isLocalSlot[slot];
    }

    private LocalPlayer keyboardPlayer() {
        for (LocalPlayer lp : localPlayers) {
            if (lp.source == LocalPlayerRoster.InputSource.KEYBOARD
                    || lp.source == LocalPlayerRoster.InputSource.KEYBOARD_AND_ANY_CONTROLLER) {
                return lp;
            }
        }
        return null;
    }

    private LocalPlayer playerForControllerSlot(int controllerSlot) {
        for (LocalPlayer lp : localPlayers) {
            if (lp.source == LocalPlayerRoster.InputSource.KEYBOARD_AND_ANY_CONTROLLER) {
                return lp;
            }
            if (lp.source == LocalPlayerRoster.InputSource.CONTROLLER
                    && lp.controllerSlot == controllerSlot) {
                return lp;
            }
        }
        return null;
    }

    @Override
    public void update() {
        deltaTime = (int)(System.currentTimeMillis() - lastUpdateMs);
        game.update(deltaTime);
        lastUpdateMs = System.currentTimeMillis();

        Board board = game.getBoards().isEmpty() ? null : game.getBoards().get(0);
        if (board != null) {
            for (LocalPlayer lp : localPlayers) {
                lp.input.tick(deltaTime, board, lp.holdAvailable);
            }
            if (ripples != null) {
                ripples.update(board, deltaTime / 1000f, game.isStarted());
            }
        }

        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update(deltaTime);
            if (p.isDead()) pit.remove();
        }

        if (exploded) {
            fadeTimerMs += deltaTime;
            if (fadeTimerMs >= 1000 && endGamePacket != null) {
                EndGameBroadcast pkt = endGamePacket;
                endGamePacket = null;
                app.switchMenu(new EndGameScreen(app, pkt, app.isRoomHost()));
                return;
            }
        }

        for (LocalPlayer lp : localPlayers) {
            if (lp.predictor.hasTooManyPending()) {
                System.out.println("Too many unacknowledged moves (" + ClientMovePredictor.MAX_PENDING_MOVES
                        + "+); disconnecting.");
                app.disconnect();
                app.switchMenu(new MainMenu(app));
                return;
            }
            lp.predictor.sendIfNeeded();
        }
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

    private void renderSingleBoard() {
        Board board = game.getBoards().get(0);
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
        float originX  = BoardRenderer.centeredOriginX(board, tileSize);
        float originY  = BoardRenderer.centeredOriginY(board, tileSize);

        float[] glowValues = new float[board.getActivePieces().size()];
        if (latestScoreMode != null && latestScoreMode.glowingValues != null
                && latestScoreMode.glowingValues.length == glowValues.length) {
            System.arraycopy(latestScoreMode.glowingValues, 0, glowValues, 0, glowValues.length);
        } else {
            Arrays.fill(glowValues, 0.5f);
        }

        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (board.getActivePieces().get(i).isBlockedFromSpawning) glowValues[i] = 0f;
        }
        for (LocalPlayer lp : localPlayers) {
            if (lp.ownPieceHoldGlow && lp.slot >= 0 && lp.slot < glowValues.length
                    && board.getActivePieces().size() > lp.slot
                    && board.getActivePieces().get(lp.slot).isBlockedFromSpawning) {
                glowValues[lp.slot] = 2f;
            }
        }

        float blockedWhiteAmt = (latestExplodeProgress >= 0f)
                ? Math.min(1f, latestExplodeProgress / 1f) : 0f;

        // Spectators (no local players): keep full colour. Otherwise ramp grayscale for remotes.
        float otherPlayerGrayscaleAmt = 0f;
        if (!localPlayers.isEmpty() && game.isStarted()) {
            long elapsedSinceStart = System.currentTimeMillis() - startTimeMS;
            otherPlayerGrayscaleAmt = Math.min(1f, Math.max(0f, elapsedSinceStart / 4000f));
        }

        Board.ShadowInfo[] shadows = new Board.ShadowInfo[board.getActivePieces().size()];
        if (!exploded) {
            for (int i = 0; i < shadows.length; i++) shadows[i] = board.getShadow(i);
        }

        boolean[] localFlags = new boolean[board.getActivePieces().size()];
        for (int i = 0; i < localFlags.length; i++) {
            localFlags[i] = isLocalSlot(i);
        }

        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);
        if (ripples != null && !exploded) {
            ripples.draw(originX, originY, tileSize);
        }
        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites,
                glowValues, shadows, blockedWhiteAmt, !exploded,
                localPlayers.isEmpty() ? null : localFlags, otherPlayerGrayscaleAmt);

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

        float holdBoxSize = tileSize * 4f;
        float holdBoxX = originX - holdBoxSize - tileSize * 0.5f;
        float holdBoxY = originY + (board.bh() - 4) * tileSize;
        boolean holdAvail = localPlayers.isEmpty() || localPlayers.get(0).holdAvailable;
        BoardRenderer.getInstance().drawHoldBox(board.getHeldPieceType(), holdAvail,
                holdBoxX, holdBoxY, holdBoxSize, tileSize, shapes, sprites, font);

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

        renderCountdown(board, originX, originY, tileSize);
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
        if (playerNames == null) return;

        long msUntilStart = startTimeMS - System.currentTimeMillis();
        float preStartAlpha = 1f;
        if (!game.isStarted()) {
            preStartAlpha = msUntilStart > 500 ? 1f : Math.max(0f, msUntilStart / 500f);
            if (preStartAlpha <= 0f) return;
        }

        com.badlogic.gdx.graphics.g2d.GlyphLayout nameLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float nmFs = (game.isStarted() ? 0.55f : 0.9f) * (tileSize / font.getData().lineHeight);
        font.getData().setScale(nmFs);
        sprites.begin();

        for (int i = 0; i < playerNames.length; i++) {
            if (playerNames[i] == null || playerNames[i].isEmpty()) continue;
            boolean local = isLocalSlot(i);
            // After start: only keep labels for local players (piece identity).
            if (game.isStarted() && !local) continue;

            float alpha = game.isStarted() ? 0.55f : preStartAlpha;
            font.setColor(1f, 1f, 1f, alpha);

            float nameScreenX;
            float nameScreenY;
            if (game.isStarted() && board.getActivePieces().size() > i) {
                Piece piece = board.getActivePieces().get(i);
                if (piece == null || piece.location == null) continue;
                nameScreenX = originX + (piece.location.x + 1.5f) * tileSize;
                nameScreenY = originY + (piece.location.y + 4f) * tileSize;
            } else {
                Vector2 spawn = board.getSpawnPos(i);
                nameScreenX = originX + (spawn.x + 1.5f) * tileSize;
                nameScreenY = originY + spawn.y * tileSize;
            }
            nameLayout.setText(font, playerNames[i]);
            font.draw(sprites, playerNames[i], nameScreenX - nameLayout.width * 0.5f, nameScreenY);
        }
        sprites.end();
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.getData().setScale(savedX, savedY);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (game.getBoards().isEmpty()) return super.keyDown(keycode);
        LocalPlayer kb = keyboardPlayer();
        if (kb == null) return super.keyDown(keycode);
        Board board = game.getBoards().get(0);
        boolean handled = kb.input.keyDown(keycode, board, kb.holdAvailable);
        return handled ? true : super.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        LocalPlayer kb = keyboardPlayer();
        if (kb == null) return super.keyUp(keycode);
        boolean handled = kb.input.keyUp(keycode);
        return handled ? true : super.keyUp(keycode);
    }

    private final ControllerAdapter controllerAdapter = new ControllerAdapter() {
        @Override
        public boolean buttonDown(Controller controller, int buttonIndex) {
            if (game.getBoards().isEmpty()) return false;
            int slot = app.getControllerRoster().slotOf(controller);
            LocalPlayer lp = playerForControllerSlot(slot);
            if (lp == null) return false;
            return lp.input.controllerButtonDown(buttonIndex, game.getBoards().get(0), lp.holdAvailable);
        }
        @Override
        public boolean buttonUp(Controller controller, int buttonIndex) {
            int slot = app.getControllerRoster().slotOf(controller);
            LocalPlayer lp = playerForControllerSlot(slot);
            if (lp == null) return false;
            return lp.input.controllerButtonUp(buttonIndex);
        }
    };

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
        Board board = game.getBoards().isEmpty() ? null : game.getBoards().get(0);
        for (int i = 0; i < localPlayers.size(); i++) {
            LocalPlayer lp = localPlayers.get(i);
            if (p.ackMoveIds != null && i < p.ackMoveIds.length && board != null) {
                lp.predictor.ackMovesUpTo(p.ackMoveIds[i], board);
            }
            if (p.holdAvailable != null && i < p.holdAvailable.length) {
                lp.holdAvailable = p.holdAvailable[i];
            }
            if (p.ownPieceHoldGlow != null && i < p.ownPieceHoldGlow.length) {
                lp.ownPieceHoldGlow = p.ownPieceHoldGlow[i];
            }
        }

        float prevExplodeProgress = latestExplodeProgress;
        latestExplodeProgress = p.explodeProgress;
        if (prevExplodeProgress < 0f && latestExplodeProgress >= 0f) {
            AudioManager.getInstance().playDieSound();
        }
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

    private void handleHardDropEffects(HardDropEffectsBroadcast p) {
        if (p.effects == null) return;
        for (HardDropEffect e : p.effects) {
            AudioManager.getInstance().playPlaceSound(isLocalSlot(e.playerId));
            if (e.combo >= 0) {
                AudioManager.getInstance().playClearSound(e.combo);
                if (e.lines == 4) AudioManager.getInstance().playClearTetrisSound();
                if (e.spinType == HardDropEffect.SPIN_TSPIN
                        || e.spinType == HardDropEffect.SPIN_ALL_SPIN) {
                    AudioManager.getInstance().playSpinClearSound();
                }
            }
            ParticleFactory.expandHardDropFlash(e.pieceType, e.doubledX, e.doubledY, e.pieceRotation,
                    particles, particleRng);
            if (ripples != null) ripples.poof(e.playerId);
        }
    }

    private void handlePieceSwap(PieceSwapBroadcast p) {
        if (game.getBoards().isEmpty()) return;
        game.getBoards().get(0).swapActivePiece(p.playerId, p.pieceType);
        if (ripples != null) ripples.poof(p.playerId);
    }

    private void handleHoldSound(HoldSoundBroadcast p) {
        if (p.success) {
            AudioManager.getInstance().playHoldSound(isLocalSlot(p.playerId), true);
        } else if (isLocalSlot(p.playerId)) {
            AudioManager.getInstance().playHoldSound(true, false);
        }
    }

    private void handleBumpSound(BumpSoundBroadcast p) {
        boolean self = isLocalSlot(p.playerId) || isLocalSlot(p.otherPlayerId);
        AudioManager.getInstance().playBumpSound(self);
    }

    @Override
    public void dispose() {
        Controllers.removeListener(controllerAdapter);
        AudioManager.getInstance().stopMusic();
        if (ripples != null) ripples.dispose();
    }

    private enum GameDrawMode { NONE, SINGLE_BOARD }
}
