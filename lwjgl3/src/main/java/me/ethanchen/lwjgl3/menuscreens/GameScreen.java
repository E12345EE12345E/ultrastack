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
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.input.LocalPlayerRoster;
import me.ethanchen.lwjgl3.menuscreens.ui.AspectLockedViewport;
import me.ethanchen.lwjgl3.menuscreens.ui.DesignUi;
import me.ethanchen.lwjgl3.music.AudioManager;
import me.ethanchen.lwjgl3.music.MusicTag;
import me.ethanchen.game.pve.PveBoardDisplay;
import me.ethanchen.game.pve.boss.BossDefeatAnim;
import me.ethanchen.game.pve.boss.BossDef;
import me.ethanchen.game.pve.boss.BossIntroAnim;
import me.ethanchen.game.pve.boss.BossPhaseDef;
import me.ethanchen.game.pve.boss.BossRegistry;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.BossParticle;
import me.ethanchen.lwjgl3.render.BossTextureShard;
import me.ethanchen.lwjgl3.render.CharacterAssets;
import me.ethanchen.lwjgl3.render.CharacterMeterRenderer;
import me.ethanchen.lwjgl3.render.Particle;
import me.ethanchen.lwjgl3.render.shader.PlayerRipples;
import me.ethanchen.lwjgl3.render.shader.RippleCircleRenderer;
import me.ethanchen.lwjgl3.render.shader.RippleShaderColor;
import me.ethanchen.lwjgl3.render.shader.ShockwaveRenderer;
import me.ethanchen.network.ClientPacketWrapper;
import me.ethanchen.network.PacketDispatcher;
import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.packets.s2c.AbilityActivateBroadcast;
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
import me.ethanchen.network.packets.s2c.gamemode.CharacterModeData;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.PveModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;
import me.ethanchen.server.BossController;

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
    private CharacterModeData latestCharacterMode;
    private PveModeData latestPveMode;
    private RippleCircleRenderer bossRipple;
    private float bossRippleTime;
    /** Displayed ripple radius (board units); springs toward {@link #bossRippleTargetRadius}. */
    private float bossRippleRadius = 3.5f;
    private float bossRippleRadiusVel;
    private float bossRippleTargetRadius = 3.5f;
    private static final float BOSS_RIPPLE_STIFFNESS = 22f;
    private static final float BOSS_RIPPLE_DAMPING = 2f * (float) Math.sqrt(BOSS_RIPPLE_STIFFNESS);
    /** Ring width in panel units; inner/outer radii are {@code radius ± thickness/2}. */
    private static final float BOSS_RIPPLE_THICKNESS = 1.0f;
    private static final float BOSS_RIPPLE_INTENSITY = 0.4f;
    /** Locked 16:9 design rect for all in-game board/HUD placement. */
    private final AspectLockedViewport gameViewport =
            new AspectLockedViewport(DesignUi.DESIGN_W, DesignUi.DESIGN_H);
    /** 0 = default dual/single layout, 1 = parted boards with boss in the middle. */
    private float bossLayoutBlend;
    private float bossLayoutTarget;
    /** Previous {@link PveModeData#bossPhase} for HP-bar appear edges. */
    private int prevBossPhase = -1;
    /** 0–1 fade for the linear HP bar after non-flash intros. */
    private float bossHpBarFade;
    private boolean bossHpBarFading;
    private static final float BOSS_HP_BAR_FADE_MS = 1000f;
    /** Client-only shatter shards from the boss portrait (not networked). */
    private final ArrayList<BossTextureShard> bossShards = new ArrayList<>();
    private boolean bossShardsSpawned;
    /** Client-only charge/explode orbs around the boss portrait (not networked). */
    private final ArrayList<BossParticle> bossParticles = new ArrayList<>();
    private float bossChargeSpawnAcc;
    private boolean attackBurstSpawned;
    private boolean flashInShockwaveSpawned;
    private ShockwaveRenderer shockwave;
    /** Per-slot previous "meter full / ability ready" state for rising-edge available SFX. */
    private boolean[] abilityWasReady;

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
                .on(AbilityActivateBroadcast.class, w -> handleAbilityActivate((AbilityActivateBroadcast) w.packet))
                .on(PieceSwapBroadcast.class,       w -> handlePieceSwap((PieceSwapBroadcast) w.packet));
    }

    public GameScreen(ClientApp app, StartGameBroadcast b, boolean isHost) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        this.isHost = isHost;
        lastUpdateMs = System.currentTimeMillis();
        long startGameTimer = b.msUntilStart;
        startTimeMS = lastUpdateMs + startGameTimer;
        playerNames = b.playerNames;
        game = new GameHandler(b.totalPlayers & 0xFF);
        game.init(b.mode, startGameTimer);
        gameEndTargetMs = startTimeMS + GameConstants.SCORE_MODE_DURATION_MS;
        // PvE's GameMode.rules() fallback is a single score-mode board; install the real
        // server boards and slot mapping so 3–4 player splits have the right seat counts.
        game.applyNetBoards(b.boards, b.slotBoardIndex, b.slotSeatIndex);
        if (b.mode == GameMode.NONE) {
            drawMode = GameDrawMode.NONE;
        } else {
            drawMode = game.getBoards().size() > 1 ? GameDrawMode.DUAL_BOARD : GameDrawMode.SINGLE_BOARD;
        }

        isLocalSlot = new boolean[Math.max(1, b.totalPlayers & 0xFF)];
        abilityWasReady = new boolean[isLocalSlot.length];
        LocalPlayerRoster roster = app.computeLocalPlayerRoster();
        byte[] ids = b.localPlayerIds != null ? b.localPlayerIds : new byte[0];
        int n = Math.min(ids.length, roster.size());
        for (int i = 0; i < n; i++) {
            int slot = ids[i] & 0xFF;
            LocalPlayerRoster.Entry entry = roster.getEntries().get(i);
            ClientMovePredictor predictor = new ClientMovePredictor(app, seatFor(slot), i);
            GameInputHandler input = new GameInputHandler(app, slot, game, predictor);
            localPlayers.add(new LocalPlayer(slot, i, entry.source, entry.controllerSlot, input, predictor));
            if (slot >= 0 && slot < isLocalSlot.length) isLocalSlot[slot] = true;
        }

        Board primaryBoardAtInit = primaryBoard();
        if (primaryBoardAtInit != null) {
            int seats = primaryBoardAtInit.getSpawnPositions().length;
            boolean[] localBySeat = new boolean[seats];
            for (int seat = 0; seat < seats; seat++) {
                localBySeat[seat] = isLocalSlot(primaryBoardAtInit.globalSlotForSeat(seat));
            }
            ripples = new PlayerRipples(primaryBoardAtInit, localBySeat);
        }

        Controllers.addListener(controllerAdapter);
        AudioManager.getInstance().playMusic(MusicTag.MULTIPLAYER_GAME);
    }

    private boolean isLocalSlot(int slot) {
        return slot >= 0 && slot < isLocalSlot.length && isLocalSlot[slot];
    }

    /** Resolves the board that global slot {@code slot} is seated on, or {@code null} if none exist. */
    private Board boardFor(int slot) {
        return game.boardFor(slot);
    }

    /** Resolves global slot {@code slot}'s board-local seat index. */
    private int seatFor(int slot) {
        return game.seatOf(slot);
    }

    /** The slot this client renders as "the" board: its first local player, or slot 0 when spectating. */
    private int primarySlot() {
        return localPlayers.isEmpty() ? 0 : localPlayers.get(0).slot;
    }

    /** The single board currently rendered/controlled by this screen (see {@link GameDrawMode#SINGLE_BOARD}). */
    private Board primaryBoard() {
        return boardFor(primarySlot());
    }

    private int primaryBoardIndex() {
        int bi = game.boardIndexOf(primarySlot());
        return bi >= 0 ? bi : 0;
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

        for (LocalPlayer lp : localPlayers) {
            Board lpBoard = boardFor(lp.slot);
            if (lpBoard != null) lp.input.tick(deltaTime, lpBoard, lp.holdAvailable);
        }
        Board rippleBoard = primaryBoard();
        if (ripples != null && rippleBoard != null) {
            ripples.update(rippleBoard, deltaTime / 1000f, game.isStarted());
        }

        // Ease boards apart / together when entering or leaving a bossfight section.
        if (bossLayoutBlend < bossLayoutTarget) {
            bossLayoutBlend = Math.min(bossLayoutTarget,
                    bossLayoutBlend + deltaTime / (float) BossIntroAnim.LANE_EXPAND_MS);
        } else if (bossLayoutBlend > bossLayoutTarget) {
            bossLayoutBlend = Math.max(bossLayoutTarget,
                    bossLayoutBlend - deltaTime / (float) BossIntroAnim.LANE_EXPAND_MS);
        }
        if (bossLayoutTarget <= 0f && bossLayoutBlend <= 0.001f
                && drawMode == GameDrawMode.BOSSFIGHT && game != null) {
            drawMode = game.getBoards().size() > 1 ? GameDrawMode.DUAL_BOARD : GameDrawMode.SINGLE_BOARD;
            bossLayoutBlend = 0f;
        }
        if (bossHpBarFading && bossHpBarFade < 1f) {
            bossHpBarFade = Math.min(1f, bossHpBarFade + deltaTime / BOSS_HP_BAR_FADE_MS);
            if (bossHpBarFade >= 1f) bossHpBarFading = false;
        }

        Iterator<Particle> pit = particles.iterator();
        while (pit.hasNext()) {
            Particle p = pit.next();
            p.update(deltaTime);
            if (p.isDead()) pit.remove();
        }
        Iterator<BossTextureShard> sit = bossShards.iterator();
        while (sit.hasNext()) {
            BossTextureShard s = sit.next();
            s.update(deltaTime);
            if (s.isDead()) sit.remove();
        }
        Iterator<BossParticle> bpit = bossParticles.iterator();
        while (bpit.hasNext()) {
            BossParticle p = bpit.next();
            p.update(deltaTime);
            if (p.isDead()) bpit.remove();
        }
        if (shockwave != null) shockwave.update(deltaTime / 1000f);

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
        gameViewport.update();
        boolean capturingShockwave = shouldCaptureShockwave();
        if (capturingShockwave) {
            if (shockwave == null) shockwave = new ShockwaveRenderer();
            shockwave.begin();
        }
        float bossT = smoothstep(bossLayoutBlend);
        boolean bossLayoutActive = bossT > 0.001f || bossLayoutTarget > 0f;
        List<Board> boards = game.getBoards();
        switch (drawMode) {
            case SINGLE_BOARD:
                if (bossLayoutActive) renderSingleBossLayout(bossT);
                else renderSingleBoard();
                break;
            case DUAL_BOARD:
                if (bossLayoutActive && boards.size() > 1) renderDualBossLayout(bossT);
                else if (bossLayoutActive) renderSingleBossLayout(bossT);
                else renderMultiBoard();
                break;
            case BOSSFIGHT:
                if (boards.size() > 1) renderDualBossLayout(bossT);
                else renderSingleBossLayout(bossT);
                break;
            default:
                break;
        }

        drawBossTextureShards();

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
        if (capturingShockwave) shockwave.end();
    }

    private void renderSingleBoard() {
        Board board = primaryBoard();
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f, gameViewport.viewW, gameViewport.viewH);
        float originX = BoardRenderer.centeredOriginX(board, tileSize, gameViewport.originX, gameViewport.viewW);
        float originY = BoardRenderer.centeredOriginY(board, tileSize, gameViewport.originY, gameViewport.viewH);

        renderBoardContents(board, primaryBoardIndex(), originX, originY, tileSize);
        renderPlayerNames(board, originX, originY, tileSize);
        renderBoardHud(board, originX, originY, tileSize);
    }

    /**
     * Renders every board in {@code game.getBoards()} side-by-side, each fit into its own
     * column of the locked 16:9 design rect via {@link BoardRenderer#originXForColumn}. The
     * primary board (the local player's own board, or board 0 for spectators) additionally
     * gets the HUD elements (hold box, timer, names, meters) that a single shared HUD makes
     * sense for.
     */
    private void renderMultiBoard() {
        List<Board> boards = game.getBoards();
        int totalColumns = boards.size();
        int primaryIndex = primaryBoardIndex();

        // Fit tile size against the narrowest column and shortest board height so every board
        // renders at the same scale within the 16:9 design rect.
        float tileSize = Float.MAX_VALUE;
        for (Board b : boards) {
            float maxW = gameViewport.viewW / (float) totalColumns * 0.85f / b.bw();
            float maxH = gameViewport.viewH * 0.85f / b.bh();
            tileSize = Math.min(tileSize, Math.min(maxW, maxH));
        }

        for (int i = 0; i < boards.size(); i++) {
            Board board = boards.get(i);
            float originX = BoardRenderer.originXForColumn(
                    board, tileSize, i, totalColumns, gameViewport.originX, gameViewport.viewW);
            float originY = BoardRenderer.centeredOriginY(
                    board, tileSize, gameViewport.originY, gameViewport.viewH);
            renderBoardContents(board, i, originX, originY, tileSize);
            renderPlayerNames(board, originX, originY, tileSize);
            if (i == primaryIndex) {
                renderBoardHud(board, originX, originY, tileSize);
            }
        }
    }

    /** Draws the grid, pieces, shadows and particles for one board (shared by single/dual layouts). */
    private void renderBoardContents(Board board, int boardIndex, float originX, float originY, float tileSize) {
        float[] glowValues = new float[board.getActivePieces().size()];
        float[] srcGlow = null;
        if (boardIndex == primaryBoardIndex()) {
            if (game.getMode() == GameMode.PVE && latestPveMode != null) {
                srcGlow = latestPveMode.glowingValues;
            } else if (latestScoreMode != null) {
                srcGlow = latestScoreMode.glowingValues;
            }
        }
        if (srcGlow != null && srcGlow.length == glowValues.length) {
            System.arraycopy(srcGlow, 0, glowValues, 0, glowValues.length);
        } else {
            Arrays.fill(glowValues, 0.5f);
        }

        for (int i = 0; i < board.getActivePieces().size(); i++) {
            if (board.getActivePieces().get(i).isBlockedFromSpawning) glowValues[i] = 0f;
        }
        for (LocalPlayer lp : localPlayers) {
            if (lp.ownPieceHoldGlow && boardFor(lp.slot) == board) {
                int seat = seatFor(lp.slot);
                if (seat >= 0 && seat < glowValues.length
                        && board.getActivePieces().size() > seat
                        && board.getActivePieces().get(seat).isBlockedFromSpawning) {
                    glowValues[seat] = 2f;
                }
            }
        }

        float blockedWhiteAmt = (latestExplodeProgress >= 0f)
                ? Math.min(1f, latestExplodeProgress / GameConstants.EXPLODE_DURATION) : 0f;

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
        for (int seat = 0; seat < localFlags.length; seat++) {
            localFlags[seat] = isLocalSlot(board.globalSlotForSeat(seat));
        }

        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);
        if (game.getMode() == GameMode.PVE && latestPveMode != null) {
            BoardRenderer.getInstance().drawPveScoreRequirement(
                    board, originX, originY, tileSize, sprites, font,
                    latestPveMode.sectionScore, latestPveMode.scoreHudTarget,
                    latestPveMode.scoreHudPassed);
        }
        if (ripples != null && !exploded && boardIndex == primaryBoardIndex()) {
            ripples.draw(originX, originY, tileSize);
        }
        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites,
                glowValues, shadows, blockedWhiteAmt, !exploded,
                localPlayers.isEmpty() ? null : localFlags, otherPlayerGrayscaleAmt);

        if (boardIndex == primaryBoardIndex()) {
            int repeatCol = -1;
            int repeatCol2 = -1;
            if (game.getMode() == GameMode.PVE && latestPveMode != null) {
                repeatCol = latestPveMode.repeatColumn;
                repeatCol2 = latestPveMode.repeatColumn2;
            } else if (latestScoreMode != null) {
                repeatCol = latestScoreMode.repeatColumn;
                repeatCol2 = latestScoreMode.repeatColumn2;
            }
            if (repeatCol != -1) {
                BoardRenderer.getInstance().drawColumnHighlight(board, originX, originY, tileSize,
                        shapes, repeatCol, 1f, 0f, 0f, 0.15f);
            }
            if (repeatCol2 != -1) {
                BoardRenderer.getInstance().drawColumnHighlight(board, originX, originY, tileSize,
                        shapes, repeatCol2, 1f, 0f, 0f, 0.15f);
            }
        }

        BoardRenderer.getInstance().drawParticles(particles, originX, originY, tileSize, shapes, boardIndex);
        BoardRenderer.getInstance().drawTextParticles(particles, originX, originY, tileSize, sprites, font, boardIndex);
    }

    /** Draws the shared HUD (hold box, timer, countdown, names, meters) attached to one board. */
    private void renderBoardHud(Board board, float originX, float originY, float tileSize) {
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
        } else if (game.getMode() == GameMode.PVE) {
            if (latestPveMode != null) {
                int objCount = latestPveMode.objectiveLines != null
                        ? latestPveMode.objectiveLines.length : 0;
                float sectionBoxH = timerBoxSize + tileSize * Math.max(0, objCount - 1) * 0.55f;
                BoardRenderer.getInstance().drawPveSectionBox(
                        latestPveMode.sectionIndex,
                        latestPveMode.sectionElapsedMs,
                        latestPveMode.sectionTimeoutMs,
                        latestPveMode.totalScore,
                        latestPveMode.objectiveLines,
                        timerBoxX, timerBoxY, timerBoxSize, sectionBoxH, tileSize,
                        shapes, sprites, font);
            }
        } else {
            long currentScore = latestScoreMode != null ? latestScoreMode.totalScore : 0;
            BoardRenderer.getInstance().drawTimerBox(
                    gameEndTargetMs, currentScore, timerBoxX, timerBoxY, timerBoxSize, tileSize,
                    shapes, sprites, font);
        }

        renderCountdown(board, originX, originY, tileSize);
        renderCharacterMeters(board, originX, originY, tileSize);
    }

    /**
     * Dual-board bossfight: both boards stay visible and float outward while the boss eases into
     * the center gap. {@code t} is an eased blend in [0, 1] (0 = default side-by-side, 1 = parted).
     */
    private void renderDualBossLayout(float t) {
        List<Board> boards = game.getBoards();
        if (boards.size() < 2) {
            renderSingleBossLayout(t);
            return;
        }
        int primaryIndex = primaryBoardIndex();

        float tileSize = Float.MAX_VALUE;
        for (Board b : boards) {
            float maxW = gameViewport.viewW / 2f * 0.85f / b.bw();
            float maxH = gameViewport.viewH * 0.85f / b.bh();
            tileSize = Math.min(tileSize, Math.min(maxW, maxH));
        }

        Board left = boards.get(0);
        Board right = boards.get(1);
        float leftW = left.bw() * tileSize;
        float rightW = right.bw() * tileSize;
        float originY = BoardRenderer.centeredOriginY(left, tileSize, gameViewport.originY, gameViewport.viewH);

        float defLeftX = BoardRenderer.originXForColumn(
                left, tileSize, 0, 2, gameViewport.originX, gameViewport.viewW);
        float defRightX = BoardRenderer.originXForColumn(
                right, tileSize, 1, 2, gameViewport.originX, gameViewport.viewW);

        // Part toward the edges of the 16:9 rect, leaving a center lane for the boss.
        float hudGutter = tileSize * 5.5f;
        float bossLeftX = gameViewport.toScreenX(0.02f) + hudGutter;
        float bossRightX = gameViewport.toScreenX(0.98f) - rightW;
        float leftX = defLeftX + (bossLeftX - defLeftX) * t;
        float rightX = defRightX + (bossRightX - defRightX) * t;

        renderBoardContents(left, 0, leftX, originY, tileSize);
        renderBoardContents(right, 1, rightX, originY, tileSize);
        renderPlayerNames(left, leftX, originY, tileSize);
        renderPlayerNames(right, rightX, originY, tileSize);
        if (primaryIndex == 0) {
            renderBoardHud(left, leftX, originY, tileSize);
        } else if (primaryIndex == 1) {
            renderBoardHud(right, rightX, originY, tileSize);
        } else {
            renderBoardHud(left, leftX, originY, tileSize);
        }

        if (t < 1f) return;

        float innerLeft = leftX + leftW;
        float innerRight = rightX;
        float laneWidth = Math.max(0f, innerRight - innerLeft);
        float boxSize = Math.min(gameViewport.toScreenH(0.42f), laneWidth * 0.92f) * 0.65f;
        float panelX = innerLeft + (laneWidth - boxSize) * 0.5f;
        float centerY = gameViewport.toScreenY(0.5f);
        renderBossPanel(panelX, centerY, boxSize, innerLeft, innerRight, originY);
    }

    /**
     * Single-board bossfight: board and boss form one centered group. At {@code t=0} the board
     * is screen-centered; as {@code t} rises the pair settles so the action reads in the middle
     * of the 16:9 design rect (board left of boss, not pinned to the left edge).
     */
    private void renderSingleBossLayout(float t) {
        Board board = primaryBoard();
        if (board == null) return;

        float tileSize = BoardRenderer.computeTileSize(board, 0.85f, gameViewport.viewW, gameViewport.viewH);
        // Keep room for the boss lane once parted; width guard only bites for wide boards.
        float partedTile = Math.min(tileSize, gameViewport.viewW * 0.42f / board.bw());
        tileSize = tileSize + (partedTile - tileSize) * t;

        float boardW = board.bw() * tileSize;
        float hudGutter = tileSize * 5.5f;
        float meterColumnW = hasSeatedCharacters() ? tileSize * 5.5f : 0f;
        float gap = tileSize * 1.5f;
        float boxSize = gameViewport.toScreenH(0.42f) * 0.65f;

        // Default: board alone, centered.
        float defX = BoardRenderer.centeredOriginX(board, tileSize, gameViewport.originX, gameViewport.viewW);
        // Parted: center the full [HUD][board][meters][gap][boss] group in the design rect.
        float groupW = hudGutter + boardW + meterColumnW + gap + boxSize;
        float groupLeft = gameViewport.toScreenX(0.5f) - groupW * 0.5f;
        float partedX = groupLeft + hudGutter;

        float originX = defX + (partedX - defX) * t;
        float originY = BoardRenderer.centeredOriginY(board, tileSize, gameViewport.originY, gameViewport.viewH);

        renderBoardContents(board, primaryBoardIndex(), originX, originY, tileSize);
        renderPlayerNames(board, originX, originY, tileSize);
        renderBoardHud(board, originX, originY, tileSize);

        if (t < 1f) return;

        float panelX = originX + boardW + meterColumnW + gap;
        float centerY = gameViewport.toScreenY(0.5f);
        float laneLeft = originX + boardW + meterColumnW;
        float laneRight = panelX + boxSize;
        renderBossPanel(panelX, centerY, boxSize, laneLeft, laneRight, originY);
    }

    private static float smoothstep(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    /** True when the live character payload has at least one seated character id. */
    private boolean hasSeatedCharacters() {
        if (latestCharacterMode == null || latestCharacterMode.characterIds == null) return false;
        for (int id : latestCharacterMode.characterIds) {
            if (id >= 0) return true;
        }
        return false;
    }

    private void renderBossPanel(float panelX, float restCenterY, float boxSize,
                                 float laneLeft, float laneRight, float boardOriginY) {
        if (latestPveMode == null || latestPveMode.bossId < 0 || boxSize <= 0f) return;

        BossDef def = BossRegistry.byId(latestPveMode.bossId);
        BossIntroAnim intro = def != null && def.intro != null ? def.intro : BossIntroAnim.FLASH_IN;
        boolean entering = latestPveMode.bossPhase == BossController.Phase.ENTERING.ordinal();
        long elapsed = latestPveMode.bossPhaseElapsedMs;

        if (entering && elapsed < BossIntroAnim.LANE_EXPAND_MS) return;

        float introT = 1f;
        if (entering) {
            introT = intro.durationMs <= 0 ? 1f
                    : Math.min(1f, (elapsed - BossIntroAnim.LANE_EXPAND_MS) / (float) intro.durationMs);
        }

        float alpha = 1f;
        float centerY = restCenterY;
        boolean flashWhite = false;
        if (entering) {
            switch (intro) {
                case FLASH_IN:
                    flashWhite = true;
                    break;
                case FLOAT_IN: {
                    float ease = 1f - (1f - introT) * (1f - introT);
                    float fromY = gameViewport.originY - boxSize;
                    centerY = fromY + (restCenterY - fromY) * ease;
                    break;
                }
                case FLOAT_IN_TOP: {
                    float ease = 1f - (1f - introT) * (1f - introT);
                    float fromY = gameViewport.originY + gameViewport.viewH + boxSize;
                    centerY = fromY + (restCenterY - fromY) * ease;
                    break;
                }
                case FADE_IN:
                    alpha = introT;
                    break;
            }
        }

        boolean defeated = latestPveMode.bossPhase == BossController.Phase.DEFEATED.ordinal();
        float panelY = centerY - boxSize * 0.5f;
        float cx = panelX + boxSize * 0.5f;
        float cy = panelY + boxSize * 0.55f;
        float panelUnit = boxSize / 6f;

        if (entering && intro == BossIntroAnim.FLASH_IN && !flashInShockwaveSpawned) {
            flashInShockwaveSpawned = true;
            spawnBossShockwave(cx, cy,
                    ShockwaveRenderer.AMPLITUDE_NORMAL, ShockwaveRenderer.SPEED_FAST);
        }

        if (!entering && !defeated) {
            if (bossRipple == null) bossRipple = new RippleCircleRenderer();
            bossRippleTime += deltaTime / 1000f;
            RippleShaderColor color;
            switch (latestPveMode.bossPhase) {
                case 1: // WINDUP — grow toward max + shift toward red
                    float progress = latestPveMode.bossPhaseDurationMs > 0
                            ? Math.min(1f, latestPveMode.bossPhaseElapsedMs / (float) latestPveMode.bossPhaseDurationMs)
                            : 0f;
                    bossRippleTargetRadius = 3.5f + 2.5f * progress;
                    color = new RippleShaderColor(
                            new com.badlogic.gdx.graphics.Color(1f, 0.85f - 0.55f * progress, 0.2f, 1f));
                    break;
                case 2: // ATTACK — shrink toward min + snap to hot color
                    bossRippleTargetRadius = 1.5f;
                    color = new RippleShaderColor(new com.badlogic.gdx.graphics.Color(1f, 0.15f, 0.1f, 1f));
                    break;
                case 3: // STUNNED — dim steady
                    bossRippleTargetRadius = 3f;
                    color = new RippleShaderColor(new com.badlogic.gdx.graphics.Color(0.5f, 0.7f, 1f, 1f));
                    break;
                default: // IDLE
                    bossRippleTargetRadius = 3.5f;
                    color = new RippleShaderColor(new com.badlogic.gdx.graphics.Color(0.9f, 0.9f, 1f, 1f));
                    break;
            }
            float dt = Math.min(0.05f, deltaTime / 1000f);
            bossRippleRadiusVel += (BOSS_RIPPLE_STIFFNESS * (bossRippleTargetRadius - bossRippleRadius)
                    - BOSS_RIPPLE_DAMPING * bossRippleRadiusVel) * dt;
            bossRippleRadius += bossRippleRadiusVel * dt;
            if (bossRippleRadius < 0.25f) {
                bossRippleRadius = 0.25f;
                if (bossRippleRadiusVel < 0f) bossRippleRadiusVel = 0f;
            }
            float synthOriginX = cx - 0.5f * panelUnit;
            float synthOriginY = cy - 0.5f * panelUnit;
            bossRipple.draw(synthOriginX, synthOriginY, panelUnit, 0f, 0f, bossRippleRadius,
                    1f, 1f, BOSS_RIPPLE_THICKNESS, BOSS_RIPPLE_INTENSITY, color, bossRippleTime, 1f);
        }

        com.badlogic.gdx.graphics.Texture portrait = CharacterAssets.portraitFor(0);
        float portraitSize = boxSize * 0.85f;
        float destX = cx - portraitSize * 0.5f;
        float destY = cy - portraitSize * 0.5f;
        boolean shattered = false;
        if (defeated) {
            if (elapsed < BossDefeatAnim.SHAKE_MS) {
                float t = elapsed / (float) BossDefeatAnim.SHAKE_MS;
                float amp = boxSize * (0.02f + 0.12f * t);
                destX += amp * (float) Math.sin(elapsed * 0.093f);
                destY += amp * (float) Math.cos(elapsed * 0.121f);
            } else {
                shattered = true;
                if (!bossShardsSpawned) {
                    spawnBossTextureShards(portrait, destX, destY, portraitSize, portraitSize);
                    bossShardsSpawned = true;
                    spawnBossShockwave(cx, cy,
                            ShockwaveRenderer.AMPLITUDE_LOW, ShockwaveRenderer.SPEED_NORMAL);
                }
            }
        }

        if (!shattered) {
            sprites.begin();
            sprites.setColor(1f, 1f, 1f, alpha);
            sprites.draw(portrait, destX, destY, portraitSize, portraitSize);
            if (flashWhite) {
                sprites.setBlendFunction(
                        com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                        com.badlogic.gdx.graphics.GL20.GL_ONE);
                sprites.setColor(1f, 1f, 1f, 1f);
                sprites.draw(portrait, destX, destY, portraitSize, portraitSize);
                sprites.draw(portrait, destX, destY, portraitSize, portraitSize);
                sprites.setBlendFunction(
                        com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                        com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            sprites.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            sprites.end();
        }

        spawnBossCombatParticles(cx, cy, portraitSize, def);
        drawBossParticles();

        boolean showBar;
        float barAlpha;
        boolean barFlash = false;
        if (entering) {
            if (intro == BossIntroAnim.FLASH_IN) {
                showBar = true;
                barAlpha = 1f;
                barFlash = true;
            } else {
                showBar = false;
                barAlpha = 0f;
            }
        } else {
            showBar = true;
            barAlpha = intro == BossIntroAnim.FLASH_IN ? 1f : bossHpBarFade;
        }
        if (showBar && barAlpha > 0.001f && laneRight > laneLeft) {
            drawBossHpBar(laneLeft, laneRight, boardOriginY, boxSize, barAlpha, barFlash);
        }
    }

    private void drawBossHpBar(float laneLeft, float laneRight, float boardOriginY,
                               float boxSize, float alpha, boolean flashWhite) {
        float barW = laneRight - laneLeft;
        float barH = Math.max(22f, boxSize * 0.18f);
        float barY = boardOriginY;
        float pct = latestPveMode.bossMaxHp > 0
                ? Math.max(0f, Math.min(1f, latestPveMode.bossHp / (float) latestPveMode.bossMaxHp))
                : 0f;

        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(
                com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        if (flashWhite) {
            shapes.setColor(1f, 1f, 1f, alpha);
            shapes.rect(laneLeft, barY, barW, barH);
        } else {
            shapes.setColor(0.25f, 0.25f, 0.25f, alpha);
            shapes.rect(laneLeft, barY, barW, barH);
            shapes.setColor(1f, 0.35f, 0.25f, alpha);
            shapes.rect(laneLeft, barY, barW * pct, barH);
        }
        shapes.end();

        String hpText = latestPveMode.bossHp + " / " + latestPveMode.bossMaxHp;
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
        float savedX = font.getScaleX(), savedY = font.getScaleY();
        font.getData().setScale(1f);
        float fs = 0.65f * (barH / font.getData().lineHeight);
        font.getData().setScale(fs);
        layout.setText(font, hpText);
        sprites.begin();
        font.setColor(1f, 1f, 1f, alpha);
        font.draw(sprites, hpText,
                laneLeft + (barW - layout.width) * 0.5f,
                barY + (barH + layout.height) * 0.5f);
        sprites.end();
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        font.getData().setScale(savedX, savedY);
    }

    private void spawnBossTextureShards(com.badlogic.gdx.graphics.Texture texture,
                                        float destX, float destY, float destW, float destH) {
        int tw = texture.getWidth();
        int th = texture.getHeight();
        if (tw <= 0 || th <= 0) return;
        int chunk = 2;
        com.badlogic.gdx.graphics.Pixmap pm = copyTexturePixmap(texture);
        float unit = Math.max(8f, destH / 8f);
        float gravity = BossTextureShard.gravityForUnit(unit);
        for (int sy = 0; sy < th; sy += chunk) {
            int ch = Math.min(chunk, th - sy);
            for (int sx = 0; sx < tw; sx += chunk) {
                int cw = Math.min(chunk, tw - sx);
                if (pm != null && bossChunkIsEmpty(pm, sx, sy, cw, ch)) continue;
                BossTextureShard s = new BossTextureShard();
                s.texture = texture;
                s.srcX = sx;
                s.srcY = sy;
                s.srcW = cw;
                s.srcH = ch;
                s.w = destW * cw / (float) tw;
                s.h = destH * ch / (float) th;
                s.x = destX + destW * sx / (float) tw;
                s.y = destY + destH * (th - sy - ch) / (float) th;
                float angle = particleRng.nextFloat() * (float) (Math.PI * 2);
                float speed = (2f + particleRng.nextFloat() * 4f) * unit;
                s.vx = (float) Math.cos(angle) * speed;
                s.vy = (float) Math.sin(angle) * speed;
                s.gravity = gravity;
                s.lifetime = 0.70f + particleRng.nextFloat() * 0.50f;
                bossShards.add(s);
            }
        }
        if (pm != null) pm.dispose();
    }

    private static com.badlogic.gdx.graphics.Pixmap copyTexturePixmap(
            com.badlogic.gdx.graphics.Texture texture) {
        try {
            com.badlogic.gdx.graphics.TextureData td = texture.getTextureData();
            if (!td.isPrepared()) td.prepare();
            com.badlogic.gdx.graphics.Pixmap src = td.consumePixmap();
            if (src == null) return null;
            com.badlogic.gdx.graphics.Pixmap copy = new com.badlogic.gdx.graphics.Pixmap(
                    src.getWidth(), src.getHeight(), src.getFormat());
            copy.drawPixmap(src, 0, 0);
            if (td.disposePixmap()) src.dispose();
            return copy;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean bossChunkIsEmpty(com.badlogic.gdx.graphics.Pixmap pm,
                                            int sx, int sy, int cw, int ch) {
        for (int y = sy; y < sy + ch; y++) {
            for (int x = sx; x < sx + cw; x++) {
                int rgba = pm.getPixel(x, y);
                int r = (rgba >>> 24) & 0xff;
                int g = (rgba >>> 16) & 0xff;
                int b = (rgba >>> 8) & 0xff;
                int a = rgba & 0xff;
                if (a > 8 && (r + g + b) > 12) return false;
            }
        }
        return true;
    }

    private void spawnBossCombatParticles(float cx, float cy, float portraitSize, BossDef def) {
        if (latestPveMode == null) return;
        int phase = latestPveMode.bossPhase;
        int attack = BossController.Phase.ATTACK.ordinal();
        int windup = BossController.Phase.WINDUP.ordinal();
        if (phase != attack) attackBurstSpawned = false;
        if (phase == BossController.Phase.ENTERING.ordinal()
                || phase == BossController.Phase.DEFEATED.ordinal()) {
            bossChargeSpawnAcc = 0f;
            return;
        }

        float hueMin = 0f;
        float hueMax = 360f;
        if (def != null && def.phases.length > 0) {
            int idx = latestPveMode.bossPhaseIndex;
            if (idx < 0) idx = 0;
            if (idx >= def.phases.length) idx = def.phases.length - 1;
            BossPhaseDef combat = def.phases[idx];
            hueMin = combat.particleHueMin;
            hueMax = combat.particleHueMax;
        }

        if (phase == windup) {
            long remaining = latestPveMode.bossPhaseDurationMs - latestPveMode.bossPhaseElapsedMs;
            if (remaining > BossParticle.CHARGE_SPAWN_CUTOFF_MS) {
                bossChargeSpawnAcc += deltaTime / 1000f * BossParticle.CHARGE_SPAWN_PER_SEC;
                while (bossChargeSpawnAcc >= 1f) {
                    bossChargeSpawnAcc -= 1f;
                    bossParticles.add(BossParticle.charge(particleRng, cx, cy, portraitSize, hueMin, hueMax));
                }
            }
        } else {
            bossChargeSpawnAcc = 0f;
        }

        if (phase == attack && !attackBurstSpawned) {
            attackBurstSpawned = true;
            for (int i = 0; i < BossParticle.EXPLODE_COUNT; i++) {
                bossParticles.add(BossParticle.explode(particleRng, cx, cy, portraitSize, hueMin, hueMax));
            }
            if (latestPveMode.bossAttackShockwave) {
                spawnBossShockwave(cx, cy,
                        ShockwaveRenderer.AMPLITUDE_VERY_LOW, ShockwaveRenderer.SPEED_LOW);
            }
        }
    }

    private void spawnBossShockwave(float cx, float cy, float amplitude, float speed) {
        if (shockwave == null) shockwave = new ShockwaveRenderer();
        shockwave.spawn(cx, cy, amplitude, speed);
    }

    /** Capture the scene when a wave is live, or this frame will spawn one. */
    private boolean shouldCaptureShockwave() {
        if (shockwave != null && shockwave.hasActive()) return true;
        if (latestPveMode == null) return false;
        int phase = latestPveMode.bossPhase;
        long elapsed = latestPveMode.bossPhaseElapsedMs;
        if (phase == BossController.Phase.ATTACK.ordinal()
                && !attackBurstSpawned
                && latestPveMode.bossAttackShockwave) {
            return true;
        }
        if (phase == BossController.Phase.DEFEATED.ordinal()
                && !bossShardsSpawned
                && elapsed >= BossDefeatAnim.SHAKE_MS) {
            return true;
        }
        if (phase == BossController.Phase.ENTERING.ordinal()
                && !flashInShockwaveSpawned
                && elapsed >= BossIntroAnim.LANE_EXPAND_MS) {
            BossDef def = BossRegistry.byId(latestPveMode.bossId);
            BossIntroAnim intro = def != null && def.intro != null ? def.intro : BossIntroAnim.FLASH_IN;
            return intro == BossIntroAnim.FLASH_IN;
        }
        return false;
    }

    private void drawBossParticles() {
        if (bossParticles.isEmpty()) return;
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(
                com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        for (BossParticle p : bossParticles) {
            if (p.isDead()) continue;
            float rad = p.radius();
            if (rad <= 0.5f) continue;
            shapes.setColor(p.r, p.g, p.b, p.alpha());
            shapes.circle(p.x, p.y, rad, BossParticle.CIRCLE_SEGMENTS);
        }
        shapes.end();
    }

    private void drawBossTextureShards() {
        if (bossShards.isEmpty()) return;
        sprites.begin();
        for (BossTextureShard s : bossShards) {
            if (s.isDead() || s.texture == null) continue;
            sprites.setColor(1f, 1f, 1f, s.alpha());
            sprites.draw(s.texture, s.x, s.y, s.w, s.h, s.srcX, s.srcY, s.srcW, s.srcH, false, false);
        }
        sprites.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        sprites.end();
    }

    /** Draws every seated player's character portrait + meter donut to the right of the board. */
    private void renderCharacterMeters(Board board, float originX, float originY, float tileSize) {
        if (latestCharacterMode == null) return;
        int[] ids = latestCharacterMode.characterIds;
        float[] fill = latestCharacterMode.meterFill;
        float[] max = latestCharacterMode.meterMax;
        if (ids == null || fill == null || max == null) return;

        float boxSize = tileSize * 4.5f;
        float boxX = originX + board.bw() * tileSize + tileSize * 0.5f;
        float boxY = originY + (board.bh() - 4) * tileSize;
        int drawn = 0;
        int n = Math.min(ids.length, Math.min(fill.length, max.length));
        for (int slot = 0; slot < n; slot++) {
            if (ids[slot] < 0) continue;
            if (boardFor(slot) != board) continue;
            String name = (playerNames != null && slot < playerNames.length) ? playerNames[slot] : null;
            float widgetY = boxY - drawn * (boxSize + tileSize * 0.3f);
            CharacterMeterRenderer.draw(shapes, sprites, font,
                    ids[slot], name, fill[slot], max[slot],
                    PlayerRipples.colorForSlot(slot),
                    boxX, widgetY, boxSize);
            drawn++;
        }
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

        for (int slot = 0; slot < playerNames.length; slot++) {
            if (playerNames[slot] == null || playerNames[slot].isEmpty()) continue;
            if (boardFor(slot) != board) continue;
            boolean local = isLocalSlot(slot);
            // After start: only keep labels for local players (piece identity).
            if (game.isStarted() && !local) continue;

            float alpha = game.isStarted() ? 0.55f : preStartAlpha;
            font.setColor(1f, 1f, 1f, alpha);

            int seat = seatFor(slot);
            float nameScreenX;
            float nameScreenY;
            if (game.isStarted() && board.getActivePieces().size() > seat && seat >= 0) {
                Piece piece = board.getActivePieces().get(seat);
                if (piece == null || piece.location == null) continue;
                nameScreenX = originX + (piece.location.x + 1.5f) * tileSize;
                nameScreenY = originY + (piece.location.y + 4f) * tileSize;
            } else {
                Vector2[] spawns = board.getSpawnPositions();
                if (seat < 0 || spawns == null || seat >= spawns.length) continue;
                Vector2 spawn = spawns[seat];
                nameScreenX = originX + (spawn.x + 1.5f) * tileSize;
                nameScreenY = originY + spawn.y * tileSize;
            }
            nameLayout.setText(font, playerNames[slot]);
            font.draw(sprites, playerNames[slot], nameScreenX - nameLayout.width * 0.5f, nameScreenY);
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
        Board board = boardFor(kb.slot);
        if (board == null) return super.keyDown(keycode);
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
            Board board = boardFor(lp.slot);
            if (board == null) return false;
            return lp.input.controllerButtonDown(buttonIndex, board, lp.holdAvailable);
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
        for (int i = 0; i < localPlayers.size(); i++) {
            LocalPlayer lp = localPlayers.get(i);
            Board lpBoard = boardFor(lp.slot);
            if (p.ackMoveIds != null && i < p.ackMoveIds.length && lpBoard != null) {
                lp.predictor.ackMovesUpTo(p.ackMoveIds[i], lpBoard);
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
        if (p.characterMode != null) {
            latestCharacterMode = p.characterMode;
            updateAbilityAvailableSounds(p.characterMode);
        }
        if (p.pveMode != null) {
            latestPveMode = p.pveMode;
            updateDrawModeFromPve(p.pveMode);
            noteBossPhase(p.pveMode);
        }

        game.setGravity(p.gravity);
        for (int b = 1; b < game.getBoards().size(); b++) {
            game.setGravity(b, p.gravity);
        }
        applyCharacterGravityPrediction(p);
        if (p.gravityTickCounters != null) {
            int n = Math.min(p.gravityTickCounters.length, game.getNumPlayers());
            for (int i = 0; i < n; i++) {
                game.setGravityTickCounter(i, p.gravityTickCounters[i]);
            }
        }
    }

    /**
     * Applies server character gravity modifiers for client-side prediction: per-slot passive
     * fall-speed multipliers and The Noob's global disable/ramp factor.
     */
    private void applyCharacterGravityPrediction(LightGameStateBroadcast p) {
        if (p.characterMode == null || p.characterMode.characterIds == null) {
            game.setGlobalGravitySpeedFactor(1f);
            for (int i = 0; i < game.getNumPlayers(); i++) {
                game.setPlayerGravitySpeedMult(i, 1f);
            }
            return;
        }
        float global = p.characterMode.globalGravitySpeedFactor;
        // Legacy packets / unset float default to 0; treat non-positive as "no override" only when
        // the effect is truly inactive — server always sends an explicit [0,1] factor.
        game.setGlobalGravitySpeedFactor(global);
        int[] ids = p.characterMode.characterIds;
        for (int i = 0; i < game.getNumPlayers(); i++) {
            float mult = 1f;
            if (i < ids.length && ids[i] >= 0) {
                CharacterDef def = CharacterRegistry.byId(ids[i]);
                if (def != null) mult = def.passiveGravitySpeedMultiplier;
            }
            game.setPlayerGravitySpeedMult(i, mult);
        }
    }

    private void handleEndGame(EndGameBroadcast egp) {
        if (exploded) return;
        endGamePacket = egp;
        exploded      = true;
        fadeTimerMs   = 0;
        Board explodeBoard = primaryBoard();
        if (explodeBoard != null) {
            Board board = explodeBoard;
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
        // Every board's particles are kept (tagged with their own boardIndex) and filtered only
        // at render time, so a client drawing more than one board (GameDrawMode.DUAL_BOARD) sees
        // particles on every board, not just its primary one.
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
        int primaryBoardIndex = primaryBoardIndex();
        for (HardDropEffect e : p.effects) {
            boolean onPrimaryBoard = (e.boardIndex & 0xFF) == primaryBoardIndex;
            // Sounds and the primary board's ripple shader stay scoped to the client's own board
            // so a client rendering multiple boards (DUAL_BOARD) doesn't hear every other board's
            // events; the hard-drop flash itself is still expanded for every board so it's
            // visible wherever it happened.
            if (onPrimaryBoard) {
                if (e.fallingClear) {
                    if (e.combo >= 0) {
                        AudioManager.getInstance().playClearSound(e.combo);
                        if (e.allClear) AudioManager.getInstance().playAllClearSound();
                    }
                } else {
                    AudioManager.getInstance().playPlaceSound(isLocalSlot(e.playerId));
                    if (e.combo >= 0) {
                        AudioManager.getInstance().playClearSound(e.combo);
                        if (e.lines == 4) AudioManager.getInstance().playClearTetrisSound();
                        if (e.spinType == HardDropEffect.SPIN_TSPIN
                                || e.spinType == HardDropEffect.SPIN_ALL_SPIN) {
                            AudioManager.getInstance().playSpinClearSound();
                        }
                        if (e.allClear) AudioManager.getInstance().playAllClearSound();
                    }
                    if (ripples != null) ripples.poof(seatFor(e.playerId));
                }
            }
            if (e.fallingClear) continue;
            ParticleFactory.expandHardDropFlash(e.pieceType, e.doubledX, e.doubledY, e.pieceRotation,
                    e.boardIndex, particles, particleRng);
        }
    }

    private void handlePieceSwap(PieceSwapBroadcast p) {
        Board board = boardFor(p.playerId);
        if (board == null) return;
        board.swapActivePiece(seatFor(p.playerId), p.pieceType);
        if (ripples != null && board == primaryBoard()) ripples.poof(seatFor(p.playerId));
    }

    private void handleHoldSound(HoldSoundBroadcast p) {
        if (p.success) {
            AudioManager.getInstance().playHoldSound(isLocalSlot(p.playerId), true);
        } else if (isLocalSlot(p.playerId)) {
            AudioManager.getInstance().playHoldSound(true, false);
        }
    }

    private void handleAbilityActivate(AbilityActivateBroadcast p) {
        AudioManager.getInstance().playAbilityActivateSound();
    }

    /**
     * Plays {@code sfx_abilityavailable} once per local seat when that seat's meter first
     * reaches full (rising edge). Remote seats are ignored.
     */
    private void updateAbilityAvailableSounds(CharacterModeData mode) {
        if (mode.meterFill == null || mode.meterMax == null) return;
        int n = Math.min(abilityWasReady.length,
                Math.min(mode.meterFill.length, mode.meterMax.length));
        for (int i = 0; i < n; i++) {
            if (!isLocalSlot(i)) {
                abilityWasReady[i] = false;
                continue;
            }
            float max = mode.meterMax[i];
            boolean ready = max > 0f && mode.meterFill[i] >= max;
            if (ready && !abilityWasReady[i]) {
                AudioManager.getInstance().playAbilityAvailableSound();
            }
            abilityWasReady[i] = ready;
        }
    }

    private void handleBumpSound(BumpSoundBroadcast p) {
        boolean self = isLocalSlot(p.playerId) || isLocalSlot(p.otherPlayerId);
        AudioManager.getInstance().playBumpSound(self);
    }

    /** Starts the HP-bar appear animation when {@code ENTERING} ends. */
    private void noteBossPhase(PveModeData mode) {
        int phase = mode.bossPhase;
        int entering = BossController.Phase.ENTERING.ordinal();
        if (phase == entering) {
            bossShards.clear();
            bossShardsSpawned = false;
            bossParticles.clear();
            bossChargeSpawnAcc = 0f;
            attackBurstSpawned = false;
            flashInShockwaveSpawned = false;
        }
        if (prevBossPhase == entering && phase != entering && phase >= 0) {
            BossDef def = BossRegistry.byId(mode.bossId);
            BossIntroAnim intro = def != null && def.intro != null ? def.intro : BossIntroAnim.FLASH_IN;
            if (intro == BossIntroAnim.FLASH_IN) {
                bossHpBarFade = 1f;
                bossHpBarFading = false;
            } else {
                bossHpBarFade = 0f;
                bossHpBarFading = true;
            }
        }
        prevBossPhase = phase;
    }

    /** Switches between the default single/dual layout and the bossfight layout from live PvE data. */
    private void updateDrawModeFromPve(PveModeData mode) {
        if (game.getMode() != GameMode.PVE || mode == null) return;
        boolean boss = mode.displayMode == PveBoardDisplay.BOARD_BOSSFIGHT;
        bossLayoutTarget = boss ? 1f : 0f;
        if (boss || bossLayoutBlend > 0.001f) {
            // Stay on BOSSFIGHT while the parting animation is active so both boards keep rendering.
            drawMode = GameDrawMode.BOSSFIGHT;
        } else {
            drawMode = game.getBoards().size() > 1 ? GameDrawMode.DUAL_BOARD : GameDrawMode.SINGLE_BOARD;
        }
    }

    @Override
    public void dispose() {
        Controllers.removeListener(controllerAdapter);
        AudioManager.getInstance().stopMusic();
        if (ripples != null) ripples.dispose();
        if (bossRipple != null) bossRipple.dispose();
        if (shockwave != null) shockwave.dispose();
    }

    private enum GameDrawMode { NONE, SINGLE_BOARD, DUAL_BOARD, BOSSFIGHT }
}
