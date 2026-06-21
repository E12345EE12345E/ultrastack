package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.settings.GameSettings;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.Particle;
import me.ethanchen.lwjgl3.render.PieceTints;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.packets.c2s.MoveListRequest;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleBroadcast;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

public class GameScreen extends MenuScreen {
    private GameHandler game;
    private GameDrawMode drawMode;
    private long lastUpdateMs;
    private int deltatime;
    private int playerID;

    private final ArrayList<PendingMove> pendingMoves = new ArrayList<>();
    private int nextMoveId = 0;

    // ARR/DAS state
    private boolean leftHeld = false;
    private boolean rightHeld = false;
    private int heldDirection = 0; // -1 = left, 0 = none, 1 = right
    private int dasTimer = 0;
    private int arrTimer = 0;
    private boolean dasCharged = false;

    // Soft-drop repeat state
    private static final int SOFT_DROP_INTERVAL_MS = 50;
    private boolean softDropHeld = false;
    private int softDropTimer = 0;

    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random particleRng = new Random();

    // Hold state (server-authoritative)
    private boolean holdAvailable = true;

    // Latest score-mode data received from the server (null until first packet arrives)
    private ScoreModeData latestScoreMode;

    // Blocked-spawn state (mirrored from latest server broadcast)
    /** Per-player cycle interval from server (seconds). */
    private float[] latestTimeBetweenNextPiece;
    /** Explode countdown from server (0 = not active). */
    private float latestPieceExplodeCountdown = 0f;
    /** True when the explode countdown is active (server-authoritative). */
    private boolean latestPieceExplodeActive = false;
    /** Whether the end-game fade has been triggered. */
    private boolean fadeToBlackStarted = false;
    /** Fade progress: 0 = normal, 1 = fully black. */
    private float fadeToBlackProgress = 0f;
    private static final float FADE_TO_BLACK_DURATION = 1.0f; // seconds

    public GameScreen(ClientApp app, StartGameBroadcast b) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        lastUpdateMs = System.currentTimeMillis();
        long startGameTimer = b.startTimeMS - System.currentTimeMillis();
        playerID = b.playerID;
        game = new GameHandler(b.totalPlayers);
        game.init(b.mode, startGameTimer);
        for (int i = 0; i < b.boards.length; i++) {
            Board board = new Board(b.boards[i]);
            game.getBoards().set(i, board);
        }
        switch (b.mode) {
            case MULTIPLAYER_SCORE:
                drawMode = GameDrawMode.SINGLE_BOARD;
                break;
            default:
                drawMode = GameDrawMode.NONE;
                break;
        }
        System.out.println("playerID=" + playerID);
        Controllers.addListener(controllerAdapter);
    }

    @Override
    public void update() {
        deltatime = (int)(System.currentTimeMillis() - lastUpdateMs);
        game.update(deltatime);
        lastUpdateMs = System.currentTimeMillis();

        // Advance fade-to-black
        if (fadeToBlackStarted) {
            fadeToBlackProgress += deltatime / 1000f / FADE_TO_BLACK_DURATION;
            if (fadeToBlackProgress >= 1f) {
                fadeToBlackProgress = 1f;
            }
        }

        tickAutoShift();
        tickSoftDrop();

        // Advance and prune dead particles
        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update(deltatime);
            if (p.isDead()) pit.remove();
        }

        if (!pendingMoves.isEmpty()) {
            if (pendingMoves.size() > 10) {
                System.out.println("Too many unacknowledged moves (" + pendingMoves.size() + "); disconnecting.");
                app.disconnect();
                app.switchMenu(new MainMenu(app));
                return;
            }
            MoveListRequest req = new MoveListRequest();
            req.ids = new int[pendingMoves.size()];
            req.types = new byte[pendingMoves.size()];
            for (int i = 0; i < pendingMoves.size(); i++) {
                req.ids[i] = pendingMoves.get(i).id;
                req.types[i] = (byte) pendingMoves.get(i).type.ordinal();
            }
            app.sendUDP(req);
        }
    }

    @Override
    public void render() {
        switch (drawMode) {
            case SINGLE_BOARD:
                Board board = game.getBoards().get(0);
                float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
                float originX = BoardRenderer.centeredOriginX(board, tileSize);
                float originY = BoardRenderer.centeredOriginY(board, tileSize);

                // Build glow values from latest server data; default 0.5f until first packet
                float[] glowValues = new float[board.getActivePieces().size()];
                if (latestScoreMode != null && latestScoreMode.glowingValues != null
                        && latestScoreMode.glowingValues.length == glowValues.length) {
                    System.arraycopy(latestScoreMode.glowingValues, 0, glowValues, 0, glowValues.length);
                } else {
                    Arrays.fill(glowValues, 0.5f);
                }

                // Override glow for blocked players:
                // - All blocked players: 0 glow to others
                // - The local player if blocked and at minimum interval: 2f (max white glow)
                // - During the explode countdown, hold is forbidden, so no glow signal either
                for (int i = 0; i < board.getActivePieces().size(); i++) {
                    me.ethanchen.game.board.Piece p = board.getActivePiece(i);
                    if (p != null && p.isBlockedFromSpawning) {
                        if (i == playerID && !latestPieceExplodeActive
                                && latestTimeBetweenNextPiece != null
                                && i < latestTimeBetweenNextPiece.length
                                && latestTimeBetweenNextPiece[i] <= 0.25f + 0.001f) {
                            glowValues[i] = 2f; // signal: hold available
                        } else {
                            glowValues[i] = 0f;
                        }
                    }
                }

                Board.ShadowInfo[] shadows = new Board.ShadowInfo[board.getActivePieces().size()];
                for (int i = 0; i < shadows.length; i++) {
                    shadows[i] = board.getShadow(i);
                }

                BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites, glowValues, shadows, latestPieceExplodeCountdown);
                BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);

                // Draw repeat-column red highlights
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

                // Draw score below the board
                if (latestScoreMode != null) {
                    com.badlogic.gdx.graphics.g2d.GlyphLayout scoreLayout =
                            new com.badlogic.gdx.graphics.g2d.GlyphLayout();
                    float savedX = font.getScaleX(), savedY = font.getScaleY();
                    font.getData().setScale(1f);
                    float lh = font.getData().lineHeight;
                    float fs = 1.0f * (15f / lh) * (com.badlogic.gdx.Gdx.graphics.getHeight() / 640f);
                    font.getData().setScale(fs);
                    String scoreText = String.valueOf(latestScoreMode.totalScore);
                    scoreLayout.setText(font, scoreText);
                    sprites.begin();
                    font.setColor(Color.WHITE);
                    float scoreX = originX + board.bw() * tileSize * 0.5f - scoreLayout.width * 0.5f;
                    float scoreY = originY - scoreLayout.height * 0.3f;
                    font.draw(sprites, scoreText, scoreX, scoreY);
                    sprites.end();
                    font.getData().setScale(savedX, savedY);
                }

                // Draw floating text particles (score popups, bonus multiplier popups)
                BoardRenderer.getInstance().drawTextParticles(particles, originX, originY, tileSize, sprites, font);

                // Draw hold box to the left of the board
                float holdBoxSize = tileSize * 4f;
                float holdBoxX = originX - holdBoxSize - tileSize * 0.5f;
                float holdBoxY = originY + (board.bh() - 4) * tileSize;
                BoardRenderer.getInstance().drawHoldBox(
                        board.getHeldPieceType(), holdAvailable,
                        holdBoxX, holdBoxY, holdBoxSize, tileSize,
                        shapes, sprites, font);

                // Fade-to-black overlay
                if (fadeToBlackProgress > 0f) {
                    Gdx.gl.glEnable(GL20.GL_BLEND);
                    Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    shapes.begin(ShapeRenderer.ShapeType.Filled);
                    shapes.setColor(0f, 0f, 0f, fadeToBlackProgress);
                    shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                    shapes.end();
                    if (fadeToBlackProgress >= 1f && app.latestEndGame != null) {
                        app.switchMenu(new EndGameScreen(app, app.latestEndGame));
                        return;
                    }
                }

                long startDelay = game.getStartDelay();
                if (startDelay > 0) {
                    sprites.begin();
                    
                    if (startDelay <= 3000 && app.latestPlayerNames != null) {
                        float pNameAlpha = (float) Math.sin(((3000 - startDelay) / 3000f) * Math.PI);
                        if (pNameAlpha > 0) {
                            float savedX = font.getScaleX(), savedY = font.getScaleY();
                            font.getData().setScale(1f);
                            float lh = font.getData().lineHeight;
                            float fs = 1.5f * (15f / lh) * (com.badlogic.gdx.Gdx.graphics.getHeight() / 640f);
                            font.getData().setScale(fs);
                            font.setColor(1f, 1f, 1f, pNameAlpha);
                            
                            com.badlogic.gdx.graphics.g2d.GlyphLayout nameLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
                            for (int i = 0; i < board.getSpawnPositions().length; i++) {
                                if (i >= app.latestPlayerNames.length) break;
                                String name = app.latestPlayerNames[i];
                                nameLayout.setText(font, name);
                                float spX = originX + board.getSpawnPositions()[i].x * tileSize;
                                float spY = originY + board.getSpawnPositions()[i].y * tileSize;
                                
                                float cx = spX + 1.0f * tileSize;
                                float cy = spY + 0.5f * tileSize;
                                
                                font.draw(sprites, name, cx - nameLayout.width * 0.5f, cy + nameLayout.height * 0.5f);
                            }
                            font.getData().setScale(savedX, savedY);
                        }
                    }

                    String countdownText = "";
                    float p = 0;
                    if (startDelay <= 4000 && startDelay > 3000) {
                        countdownText = "3";
                        p = (4000 - startDelay) / 1000f;
                    } else if (startDelay <= 3000 && startDelay > 2000) {
                        countdownText = "2";
                        p = (3000 - startDelay) / 1000f;
                    } else if (startDelay <= 2000 && startDelay > 1000) {
                        countdownText = "1";
                        p = (2000 - startDelay) / 1000f;
                    } else if (startDelay <= 1000 && startDelay > 0) {
                        countdownText = "Start";
                        p = (1000 - startDelay) / 1000f;
                    }

                    if (!countdownText.isEmpty()) {
                        float alpha = (float) Math.sin(p * Math.PI);
                        if (alpha > 0) {
                            float savedX = font.getScaleX(), savedY = font.getScaleY();
                            font.getData().setScale(1f);
                            float lh = font.getData().lineHeight;
                            
                            float scaleMult = 1.0f + 1.5f * (float) Math.sin(p * Math.PI);
                            float fs = 2.0f * scaleMult * (15f / lh) * (com.badlogic.gdx.Gdx.graphics.getHeight() / 640f);
                            font.getData().setScale(fs);
                            font.setColor(1f, 1f, 1f, alpha);
                            
                            com.badlogic.gdx.graphics.g2d.GlyphLayout cdLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
                            cdLayout.setText(font, countdownText);
                            float cx = originX + board.bw() * tileSize * 0.5f - cdLayout.width * 0.5f;
                            float cy = originY + board.bh() * tileSize * 0.5f + cdLayout.height * 0.5f;
                            font.draw(sprites, countdownText, cx, cy);
                            
                            font.getData().setScale(savedX, savedY);
                        }
                    }
                    sprites.end();
                }
                break;
            default:
                break;
        }
        elements.forEach(element -> element.render(shapes, sprites, font));
    }

    @Override
    public boolean keyDown(int keycode) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        boolean isLeftKey  = keycode == keys.left  || (keys.left2  != -1 && keycode == keys.left2);
        boolean isRightKey = keycode == keys.right || (keys.right2 != -1 && keycode == keys.right2);

        if (isLeftKey || isRightKey) {
            boolean isLeft = isLeftKey;
            if (isLeft) leftHeld = true; else rightHeld = true;

            int newDir = isLeft ? -1 : 1;
            heldDirection = newDir;
            dasTimer = 0;
            arrTimer = 0;
            dasCharged = false;

            if (!game.isStarted()) return true;
            Board board = game.getBoards().get(0);
            if (board.getActivePieces().size() <= playerID) return true;
            // Suppress movement if blocked
            if (isLocalPlayerBlocked(board)) return true;
            queueMove(isLeft ? MoveType.LEFT : MoveType.RIGHT);
            return true;
        }

        boolean isSoftDrop = keycode == keys.softDrop || (keys.softDrop2 != -1 && keycode == keys.softDrop2);
        if (isSoftDrop) {
            softDropHeld = true;
            softDropTimer = 0;
            if (game.isStarted() && game.getGravity() > SOFT_DROP_INTERVAL_MS) {
                Board board = game.getBoards().get(0);
                if (board.getActivePieces().size() > playerID && !isLocalPlayerBlocked(board)) {
                    queueMove(MoveType.SOFT_DROP);
                    game.resetGravityTimer();
                }
            }
            return true;
        }

        if (!game.isStarted()) return false;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerID) return false;

        MoveType type = null;
        if      (keycode == keys.hardDrop  || (keys.hardDrop2  != -1 && keycode == keys.hardDrop2))  type = MoveType.HARD_DROP;
        else if (keycode == keys.rotateCw  || (keys.rotateCw2  != -1 && keycode == keys.rotateCw2))  type = MoveType.ROTATE_CW;
        else if (keycode == keys.rotateCcw || (keys.rotateCcw2 != -1 && keycode == keys.rotateCcw2)) type = MoveType.ROTATE_CCW;
        else if (keycode == keys.rotate180 || (keys.rotate180_2 != -1 && keycode == keys.rotate180_2)) type = MoveType.ROTATE_180;
        else if (keycode == keys.hold      || (keys.hold2       != -1 && keycode == keys.hold2))      type = MoveType.HOLD;
        if (type == null) return super.keyDown(keycode);

        // Suppress non-hold moves if blocked; also allow hold only when server says it's available
        boolean blocked = isLocalPlayerBlocked(board);
        if (blocked && type != MoveType.HOLD) return true;
        if (blocked && !holdAvailable) return true;

        queueMove(type);
        return true;
    }

    @Override
    public boolean keyUp(int keycode) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        boolean isLeftKey  = keycode == keys.left  || (keys.left2  != -1 && keycode == keys.left2);
        boolean isRightKey = keycode == keys.right || (keys.right2 != -1 && keycode == keys.right2);

        if (isLeftKey || isRightKey) {
            boolean isLeft = isLeftKey;
            if (isLeft) leftHeld = false; else rightHeld = false;

            boolean otherHeld = isLeft ? rightHeld : leftHeld;
            if (otherHeld) {
                heldDirection = isLeft ? 1 : -1;
                dasTimer = 0;
                arrTimer = 0;
                dasCharged = false;
            } else {
                heldDirection = 0;
            }
            return true;
        }

        boolean isSoftDrop = keycode == keys.softDrop || (keys.softDrop2 != -1 && keycode == keys.softDrop2);
        if (isSoftDrop) {
            softDropHeld = false;
            return true;
        }

        return super.keyUp(keycode);
    }

    private void queueMove(MoveType type) {
        PendingMove pm = new PendingMove(nextMoveId++, type);
        pendingMoves.add(pm);
        Board board = game.getBoards().get(0);
        // Hard drop and hold are server-authoritative: do NOT apply locally to avoid
        // desyncing the piece queue during prediction replay.
        if (type != MoveType.HARD_DROP && type != MoveType.HOLD) {
            board.applyMove(playerID, type);
        }
    }

    /** True if the local player's piece has isBlockedFromSpawning set in the local board. */
    private boolean isLocalPlayerBlocked(Board board) {
        if (playerID < 0 || playerID >= board.getActivePieces().size()) return false;
        me.ethanchen.game.board.Piece p = board.getActivePiece(playerID);
        return p != null && p.isBlockedFromSpawning;
    }

    private void tickSoftDrop() {
        if (!softDropHeld || !game.isStarted()) return;
        if (game.getGravity() <= SOFT_DROP_INTERVAL_MS) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerID) return;
        if (isLocalPlayerBlocked(board)) return; // blocked: no soft-drop

        softDropTimer += deltatime;
        while (softDropTimer >= SOFT_DROP_INTERVAL_MS) {
            softDropTimer -= SOFT_DROP_INTERVAL_MS;
            queueMove(MoveType.SOFT_DROP);
            game.resetGravityTimer();
        }
    }

    private void tickAutoShift() {
        if (heldDirection == 0 || !game.isStarted()) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerID) return;
        if (isLocalPlayerBlocked(board)) return; // blocked: no auto-shift

        GameSettings s = app.getSettings();

        if (!dasCharged) {
            dasTimer += deltatime;
            if (dasTimer >= s.das) {
                dasCharged = true;
                arrTimer = 0;
            }
        } else {
            arrTimer += deltatime;
            while (arrTimer >= s.arr) {
                arrTimer -= s.arr;
                queueMove(heldDirection < 0 ? MoveType.LEFT : MoveType.RIGHT);
            }
        }
    }

    @Override
    public void passClientPacket(ClientPacketWrapper w) {
        if (w.packet instanceof LightGameStateBroadcast) {
            LightGameStateBroadcast p = (LightGameStateBroadcast) w.packet;
            for (int i = 0; i < game.getBoards().size(); i++) {
                game.getBoards().get(i).updateFromNetBoardLight(p.boards[i]);
            }

            // Mirror blocked-spawn state from server broadcast
            if (p.boards != null && p.boards.length > 0) {
                Board.NetBoardLight nb = p.boards[0];
                latestTimeBetweenNextPiece = nb.timeBetweenNextPiece;
                latestPieceExplodeCountdown = nb.pieceExplodeCountdown;
                latestPieceExplodeActive = nb.pieceExplodeActive;
            }

            // Drop moves the server has already processed
            Iterator<PendingMove> it = pendingMoves.iterator();
            while (it.hasNext()) {
                if (it.next().id <= p.ackMoveId) it.remove();
            }

            // Replay remaining pending moves on top of server state (client-side prediction).
            // Hard drops and holds are excluded: the server result arrives in a subsequent broadcast.
            Board board = game.getBoards().get(0);
            for (PendingMove pm : pendingMoves) {
                if (pm.type != MoveType.HARD_DROP && pm.type != MoveType.HOLD) {
                    board.applyMove(playerID, pm.type);
                }
            }

            // Update hold availability
            holdAvailable = p.holdAvailable;

            // Cache score-mode data
            if (p.scoreMode != null) {
                latestScoreMode = p.scoreMode;
            }
        }

        if (w.packet instanceof ParticleBroadcast) {
            ParticleBroadcast p = (ParticleBroadcast) w.packet;
            if (p.particles != null) {
                for (NetParticle np : p.particles) {
                    expandNetParticle(np);
                }
            }
        }

        if (w.packet instanceof EndGameBroadcast) {
            EndGameBroadcast egb = (EndGameBroadcast) w.packet;
            app.latestEndGame = egb;
            // Spawn PIECE_EXPLODE particles for each blocked piece tile
            if (!game.getBoards().isEmpty()) {
                spawnPieceExplodeParticles(game.getBoards().get(0));
            }
            // Start fade to black
            fadeToBlackStarted = true;
            fadeToBlackProgress = 0f;
        }
    }

    /**
     * Spawns PIECE_EXPLODE particles for every tile of every blocked active piece.
     * 4 particles per tile, solid white, no gravity, outward velocity.
     */
    private void spawnPieceExplodeParticles(Board board) {
        float speed = 3f; // approx. same as TILE_BREAK
        for (me.ethanchen.game.board.Piece piece : board.getActivePieces()) {
            if (piece.tiles == null || piece.location == null) continue;
            if (!piece.isBlockedFromSpawning) continue;
            for (com.badlogic.gdx.math.Vector2 offset : piece.tiles) {
                float cx = piece.location.x + offset.x + 0.5f;
                float cy = piece.location.y + offset.y + 0.5f;
                for (int k = 0; k < 4; k++) {
                    Particle shard = new Particle();
                    shard.kind = Particle.Kind.PIECE_EXPLODE;
                    shard.x = cx;
                    shard.y = cy;
                    float angle = particleRng.nextFloat() * (float)(Math.PI * 2);
                    float s = speed + particleRng.nextFloat() * 2f;
                    shard.vx = (float) Math.cos(angle) * s;
                    shard.vy = (float) Math.sin(angle) * s;
                    shard.r = 1f; shard.g = 1f; shard.b = 1f;
                    shard.size = 0.2f + particleRng.nextFloat() * 0.12f;
                    shard.lifetime = 0.5f + particleRng.nextFloat() * 0.25f;
                    particles.add(shard);
                }
            }
        }
    }

    /**
     * Converts one {@link NetParticle} spawn event into one or more local {@link Particle}
     * objects and adds them to the particle list.
     *
     * FLASH (kind=0): a single white square centered on the cell, lifetime ~0.12 s.
     * TILE_BREAK (kind=1): 4–6 small colored shards with random outward velocities,
     *   affected by gravity, lifetime ~0.5 s.
     */
    private void expandNetParticle(NetParticle np) {
        if (np.kind == 0) {
            // FLASH — one white square covering roughly the whole cell
            Particle flash = new Particle();
            flash.kind = Particle.Kind.FLASH;
            flash.x = np.x + 0.5f;
            flash.y = np.y + 0.5f;
            flash.vx = 0;
            flash.vy = 0;
            flash.r = 1f;
            flash.g = 1f;
            flash.b = 1f;
            flash.size = 1.0f;
            flash.lifetime = 0.12f;
            particles.add(flash);
        } else if (np.kind == 1) {
            // TILE_BREAK — a small burst of colored shards
            Color tint = PieceTints.forType(np.tileType);
            int count = 4 + particleRng.nextInt(3); // 4–6 shards
            for (int i = 0; i < count; i++) {
                Particle shard = new Particle();
                shard.kind = Particle.Kind.TILE_BREAK;
                shard.x = np.x + particleRng.nextFloat();
                shard.y = np.y + particleRng.nextFloat();
                float angle = particleRng.nextFloat() * (float)(Math.PI * 2);
                float speed = 2f + particleRng.nextFloat() * 4f;
                shard.vx = (float) Math.cos(angle) * speed;
                shard.vy = (float) Math.sin(angle) * speed;
                shard.r = tint.r;
                shard.g = tint.g;
                shard.b = tint.b;
                shard.size = 0.18f + particleRng.nextFloat() * 0.14f;
                shard.lifetime = 0.35f + particleRng.nextFloat() * 0.25f;
                particles.add(shard);
            }
        } else if (np.kind == 2) {
            // POPUP_SCORE — floating "+N" text launched upward from the piece center
            Particle pop = new Particle();
            pop.kind = Particle.Kind.POPUP_SCORE;
            pop.x = np.x;
            pop.y = np.y + 1f;
            pop.vx = 0f;
            pop.vy = 3f;
            pop.r = 1f; pop.g = 1f; pop.b = 1f;
            pop.value = np.value;
            pop.lifetime = 1.1f;
            particles.add(pop);
        } else if (np.kind == 3) {
            // POPUP_SCORE_MULTIPLIER — floating bonus lines rising at constant speed
            Particle pop = new Particle();
            pop.kind = Particle.Kind.POPUP_SCORE_MULTIPLIER;
            pop.x = np.x;
            pop.y = np.y + 1.5f;
            pop.vx = 0f;
            pop.vy = 0f; // movement handled in update()
            pop.r = 1f; pop.g = 1f; pop.b = 1f;
            pop.value = np.value;
            // Decode the 4-bit bitfield into a boolean array
            pop.bonuses = new boolean[4];
            for (int bit = 0; bit < 4; bit++) {
                pop.bonuses[bit] = (np.value & (1 << bit)) != 0;
            }
            pop.lifetime = 1.2f;
            particles.add(pop);
        }
    }

    private final ControllerAdapter controllerAdapter = new ControllerAdapter() {
        @Override
        public boolean buttonDown(Controller controller, int buttonIndex) {
            return onControllerButtonDown(buttonIndex);
        }

        @Override
        public boolean buttonUp(Controller controller, int buttonIndex) {
            return onControllerButtonUp(buttonIndex);
        }
    };

    @Override
    public void dispose() {
        Controllers.removeListener(controllerAdapter);
    }

    private boolean isCtrlLeft(int b) {
        GameSettings.MovementKeys k = app.getSettings().movement;
        return b != -1 && (b == k.ctrlLeft || b == k.ctrlLeft2);
    }

    private boolean isCtrlRight(int b) {
        GameSettings.MovementKeys k = app.getSettings().movement;
        return b != -1 && (b == k.ctrlRight || b == k.ctrlRight2);
    }

    private boolean onControllerButtonDown(int b) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        if (isCtrlLeft(b) || isCtrlRight(b)) {
            boolean isLeft = isCtrlLeft(b);
            if (isLeft) leftHeld = true; else rightHeld = true;

            int newDir = isLeft ? -1 : 1;
            heldDirection = newDir;
            dasTimer = 0;
            arrTimer = 0;
            dasCharged = false;

            if (!game.isStarted()) return true;
            Board board = game.getBoards().get(0);
            if (board.getActivePieces().size() <= playerID) return true;
            queueMove(isLeft ? MoveType.LEFT : MoveType.RIGHT);
            return true;
        }

        boolean isSoftDrop = b != -1 && (b == keys.ctrlSoftDrop || b == keys.ctrlSoftDrop2);
        if (isSoftDrop) {
            softDropHeld = true;
            softDropTimer = 0;
            if (game.isStarted() && game.getGravity() > SOFT_DROP_INTERVAL_MS) {
                Board board = game.getBoards().get(0);
                if (board.getActivePieces().size() > playerID) {
                    queueMove(MoveType.SOFT_DROP);
                    game.resetGravityTimer();
                }
            }
            return true;
        }

        if (!game.isStarted()) return false;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerID) return false;

        MoveType type = null;
        if      (b != -1 && (b == keys.ctrlHardDrop  || b == keys.ctrlHardDrop2))  type = MoveType.HARD_DROP;
        else if (b != -1 && (b == keys.ctrlRotateCw   || b == keys.ctrlRotateCw2))  type = MoveType.ROTATE_CW;
        else if (b != -1 && (b == keys.ctrlRotateCcw  || b == keys.ctrlRotateCcw2)) type = MoveType.ROTATE_CCW;
        else if (b != -1 && (b == keys.ctrlRotate180  || b == keys.ctrlRotate180_2)) type = MoveType.ROTATE_180;
        else if (b != -1 && (b == keys.ctrlHold       || b == keys.ctrlHold2))      type = MoveType.HOLD;
        if (type == null) return false;

        queueMove(type);
        return true;
    }

    private boolean onControllerButtonUp(int b) {
        if (isCtrlLeft(b) || isCtrlRight(b)) {
            boolean isLeft = isCtrlLeft(b);
            if (isLeft) leftHeld = false; else rightHeld = false;

            boolean otherHeld = isLeft ? rightHeld : leftHeld;
            if (otherHeld) {
                heldDirection = isLeft ? 1 : -1;
                dasTimer = 0;
                arrTimer = 0;
                dasCharged = false;
            } else {
                heldDirection = 0;
            }
            return true;
        }

        GameSettings.MovementKeys keys = app.getSettings().movement;
        boolean isSoftDrop = b != -1 && (b == keys.ctrlSoftDrop || b == keys.ctrlSoftDrop2);
        if (isSoftDrop) {
            softDropHeld = false;
            return true;
        }

        return false;
    }

    private static class PendingMove {
        final int id;
        final MoveType type;

        PendingMove(int id, MoveType type) {
            this.id = id;
            this.type = type;
        }
    }

    private static enum GameDrawMode {
        NONE,
        SINGLE_BOARD
    }
}
