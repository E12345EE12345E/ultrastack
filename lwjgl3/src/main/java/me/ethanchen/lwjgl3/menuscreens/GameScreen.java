package me.ethanchen.lwjgl3.menuscreens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
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
import me.ethanchen.network.packets.s2c.EndGameBroadcast;
import me.ethanchen.network.packets.s2c.LightGameStateBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleBroadcast;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.StartGameBroadcast;
import me.ethanchen.game.board.Piece;
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

    // Blocked-spawn / explode state (server-authoritative)
    private float latestExplodeProgress = -1f;
    private boolean ownPieceHoldGlow = false;

    // End-game explosion state
    private boolean exploded = false;
    private int fadeTimerMs = 0;
    private EndGameBroadcast endGamePacket = null;

    // Latest score-mode data received from the server (null until first packet arrives)
    private ScoreModeData latestScoreMode;

    // Absolute wall-clock ms when the 4-minute countdown expires
    private long gameEndTargetMs;

    public GameScreen(ClientApp app, StartGameBroadcast b) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        lastUpdateMs = System.currentTimeMillis();
        long startGameTimer = b.startTimeMS - System.currentTimeMillis();
        playerID = b.playerID;
        game = new GameHandler(b.totalPlayers);
        game.init(b.mode, startGameTimer);
        gameEndTargetMs = b.startTimeMS + 4L * 60 * 1000;
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

        tickAutoShift();
        tickSoftDrop();

        // Advance and prune dead particles
        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update(deltatime);
            if (p.isDead()) pit.remove();
        }

        // End-game fade-to-black
        if (exploded) {
            fadeTimerMs += deltatime;
            if (fadeTimerMs >= 1000 && endGamePacket != null) {
                EndGameBroadcast pkt = endGamePacket;
                endGamePacket = null;
                app.disconnect();
                app.switchMenu(new EndGameScreen(app, pkt));
                return;
            }
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

                // Override glow for blocked pieces: 0 glow for all blocked, except the
                // controlling player whose piece reached min interval (show 2f white glow).
                for (int i = 0; i < board.getActivePieces().size(); i++) {
                    if (board.getActivePieces().get(i).isBlockedFromSpawning) {
                        glowValues[i] = 0f;
                    }
                }
                if (ownPieceHoldGlow && playerID >= 0 && playerID < glowValues.length
                        && board.getActivePieces().size() > playerID
                        && board.getActivePieces().get(playerID).isBlockedFromSpawning) {
                    glowValues[playerID] = 2f;
                }

                // blockedWhiteAmt: ramp from 0->1 during the first second of the explode countdown
                float blockedWhiteAmt = (latestExplodeProgress >= 0f)
                        ? Math.min(1f, latestExplodeProgress / 1f) : 0f;

                Board.ShadowInfo[] shadows = new Board.ShadowInfo[board.getActivePieces().size()];
                if (!exploded) {
                    for (int i = 0; i < shadows.length; i++) {
                        shadows[i] = board.getShadow(i);
                    }
                }

                BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites,
                        glowValues, shadows, blockedWhiteAmt, !exploded);
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

                // Draw timer box at the bottom-left of the board (mirrors hold box at the top)
                float timerBoxSize = tileSize * 4f;
                float timerBoxX = originX - timerBoxSize - tileSize * 0.5f;
                float timerBoxY = originY;
                BoardRenderer.getInstance().drawTimerBox(
                        gameEndTargetMs, timerBoxX, timerBoxY, timerBoxSize, tileSize,
                        shapes, sprites, font);
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
            queueMove(isLeft ? MoveType.LEFT : MoveType.RIGHT);
            return true;
        }

        boolean isSoftDrop = keycode == keys.softDrop || (keys.softDrop2 != -1 && keycode == keys.softDrop2);
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
        if      (keycode == keys.hardDrop  || (keys.hardDrop2  != -1 && keycode == keys.hardDrop2))  type = MoveType.HARD_DROP;
        else if (keycode == keys.rotateCw  || (keys.rotateCw2  != -1 && keycode == keys.rotateCw2))  type = MoveType.ROTATE_CW;
        else if (keycode == keys.rotateCcw || (keys.rotateCcw2 != -1 && keycode == keys.rotateCcw2)) type = MoveType.ROTATE_CCW;
        else if (keycode == keys.rotate180 || (keys.rotate180_2 != -1 && keycode == keys.rotate180_2)) type = MoveType.ROTATE_180;
        else if (keycode == keys.hold      || (keys.hold2       != -1 && keycode == keys.hold2))      type = MoveType.HOLD;
        if (type == null) return super.keyDown(keycode);

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

    private void tickSoftDrop() {
        if (!softDropHeld || !game.isStarted()) return;
        if (game.getGravity() <= SOFT_DROP_INTERVAL_MS) return;
        Board board = game.getBoards().get(0);
        if (board.getActivePieces().size() <= playerID) return;

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

            // Cache blocked / explode state
            latestExplodeProgress = p.explodeProgress;
            ownPieceHoldGlow = p.ownPieceHoldGlow;

            // Cache score-mode data
            if (p.scoreMode != null) {
                latestScoreMode = p.scoreMode;
            }

            // Re-anchor gravity to the server's state to prevent prediction drift/jitter
            game.setGravity(p.gravity);
            game.setGravityTickCounter(p.gravityTickCounter);
        }

        if (w.packet instanceof EndGameBroadcast && !exploded) {
            EndGameBroadcast egp = (EndGameBroadcast) w.packet;
            endGamePacket = egp;
            exploded = true;
            fadeTimerMs = 0;
            // Spawn piece-explode particles for every tile of every active piece
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
                            shard.r = 1f;
                            shard.g = 1f;
                            shard.b = 1f;
                            shard.size = 0.18f + particleRng.nextFloat() * 0.14f;
                            shard.lifetime = 0.4f + particleRng.nextFloat() * 0.2f;
                            particles.add(shard);
                        }
                    }
                }
            }
        }

        if (w.packet instanceof ParticleBroadcast) {
            ParticleBroadcast p = (ParticleBroadcast) w.packet;
            if (p.spawners != null) {
                for (ParticleSpawner ps : p.spawners) {
                    expandParticleSpawner(ps);
                }
            }
            if (p.particles != null) {
                for (NetParticle np : p.particles) {
                    expandNetParticle(np);
                }
            }
        }
    }

    /**
     * Expands a compact {@link ParticleSpawner} into one or more local {@link Particle} objects.
     *
     * TYPE_HARD_DROP: reconstructs each mino position from the piece type, anchor, and rotation,
     *   then emits one FLASH particle per mino cell — identical to what the server previously
     *   sent as individual FLASH NetParticles.
     * TYPE_LINE_CLEAR: emits one TILE_BREAK burst per non-(-1) entry in {@code tileIds},
     *   reproducing the per-cell TILE_BREAK particles the server previously sent individually.
     */
    private void expandParticleSpawner(ParticleSpawner ps) {
        if (ps.spawnerType == ParticleSpawner.TYPE_HARD_DROP) {
            Piece.NetPiece netPiece = new Piece.NetPiece();
            netPiece.type = ps.pieceType;
            netPiece.doubledlocationx = ps.doubledX;
            netPiece.doubledlocationy = ps.doubledY;
            netPiece.rotation = ps.pieceRotation;
            Piece piece = Piece.createFromNetPiece(netPiece);
            for (Vector2 tile : piece.tiles) {
                int cx = (int) Math.floor(piece.location.x + tile.x);
                int cy = (int) Math.floor(piece.location.y + tile.y);
                NetParticle flash = new NetParticle();
                flash.boardIndex = ps.boardIndex;
                flash.kind = 0; // FLASH
                flash.tileType = ps.pieceType;
                flash.x = cx;
                flash.y = cy;
                expandNetParticle(flash);
            }
        } else if (ps.spawnerType == ParticleSpawner.TYPE_LINE_CLEAR) {
            if (ps.tileIds == null) return;
            for (int x = 0; x < ps.tileIds.length; x++) {
                if (ps.tileIds[x] == -1) continue;
                NetParticle tileBreak = new NetParticle();
                tileBreak.boardIndex = ps.boardIndex;
                tileBreak.kind = 1; // TILE_BREAK
                tileBreak.tileType = ps.tileIds[x];
                tileBreak.x = x;
                tileBreak.y = ps.lineY;
                expandNetParticle(tileBreak);
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
