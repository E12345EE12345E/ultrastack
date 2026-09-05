package me.ethanchen.server;

import java.util.ArrayList;
import java.util.Arrays;

import me.ethanchen.game.GameConstants;
import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.LineClearResult;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.game.board.Piece;
import me.ethanchen.game.board.PieceQueue;
import me.ethanchen.game.board.SpinType;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.pve.PveRules;
import me.ethanchen.game.pve.PveSessionState;
import me.ethanchen.network.dto.HardDropEffect;
import me.ethanchen.network.packets.s2c.AbilityActivateBroadcast;
import me.ethanchen.network.packets.s2c.BumpSoundBroadcast;
import me.ethanchen.network.packets.s2c.HoldSoundBroadcast;
import me.ethanchen.network.packets.s2c.NetParticle;
import me.ethanchen.network.packets.s2c.ParticleSpawner;
import me.ethanchen.network.packets.s2c.PieceSwapBroadcast;
import me.ethanchen.network.packets.s2c.gamemode.PuzzleModeData;
import me.ethanchen.network.packets.s2c.gamemode.ScoreModeData;

/**
 * Authoritative server-side game instance. Orchestrates the per-tick update loop and delegates
 * each concern to a dedicated collaborator:
 *
 * <ul>
 *   <li>{@link PlacementEffects}       — particle/sound queuing
 *   <li>{@link ScoreModeScorer}        — one instance per board for score modes
 *   <li>{@link PveScorer}              — one instance per board for PvE (shares formulas, not ScoreModeData)
 *   <li>{@link BlockedSpawnController} — one instance per board, blocked-piece cycling and hold-while-blocked
 *   <li>{@link GameEndController}      — per-board win/elimination tracking, grace period, and end-game broadcast
 * </ul>
 *
 * <p>Board-specific mechanics (abilities, meter fill, scoring, combo/gravity, blocked/explode)
 * are always resolved through {@link GameHandler#boardFor}/{@code seatOf}/{@code slotsOnBoard} so
 * they act only on the acting player's own board; {@code piecesPlaced}, {@link BumpStats},
 * {@code clearSpinStats}, and {@link #globalScore} remain session-wide
 * (indexed by global slot) since they describe the whole game session, not a single board.
 */
public class ServerGame {
    private volatile boolean inProgress; public boolean isInProgress() { return inProgress; }
    private volatile long lastUpdateMs;
    private int deltaTime;
    private GameMode gameMode;
    private int players;
    private GameHandler game;
    private GameRoomContext room;
    private int t;
    private int[] highestMoveId;
    private int[] piecesPlaced;
    private long[] hardDropBlockedUntilMs;
    private BumpStats bumpStats;
    private ClearSpinStats clearSpinStats;
    /** Session-wide aggregate score, updated in real time as every board's scorer scores points. */
    private long globalScore;

    private final PlacementEffects effects = new PlacementEffects();
    /** One scorer per board for MULTIPLAYER_SCORE / CHARACTER_SCORE; null in PvE. */
    private ScoreModeScorer[] scorers;
    /** One scorer per board for PvE; null outside PvE. */
    private PveScorer[] pveScorers;
    /** One blocked/explode controller per board; see {@link BlockedSpawnController}. */
    private BlockedSpawnController[] blocked;
    /** One Noob-ability gravity controller per board; see {@link NoobGravityController}. */
    private NoobGravityController[] noobGravity;
    private final GameEndController endCtrl = new GameEndController();
    private final MeterController meterController = new MeterController();
    private ActiveLoadout[] loadouts;
    /** Selected level/difficulty snapshot for the current PvE session; null for every other mode. */
    private PveSessionState pveSession;
    /** Drives PvE section progression; null unless {@link #gameMode} is {@code PVE}. */
    private PveSectionController pveSectionController;

    public ServerGame(GameRoomContext room) {
        inProgress = false;
        gameMode = GameMode.NONE;
        this.room = room;
    }

    /**
     * Starts a new game. Synchronized (along with the other mutation entry points below) so
     * that a disconnect arriving on the {@code ServerCore} thread via
     * {@link #handleDisconnectedPlayer(int)} can never interleave with an in-flight
     * {@link #update()}/{@link #applyMoves(int, int[], byte[])} call on the room thread.
     */
    public synchronized boolean startGame(GameMode gameMode, int players, int msToStart) {
        return startGame(gameMode, players, msToStart, null);
    }

    /**
     * As {@link #startGame(GameMode, int, int)}, but additionally captures a per-slot character
     * loadout snapshot for CHARACTER_ modes (implementation.md, Part 4). {@code loadouts} may be
     * null or contain null entries for modes/slots without an active character.
     */
    public synchronized boolean startGame(GameMode gameMode, int players, int msToStart, ActiveLoadout[] loadouts) {
        return startGame(gameMode, players, msToStart, loadouts, null);
    }

    /**
     * As {@link #startGame(GameMode, int, int, ActiveLoadout[])}, but additionally captures the
     * selected level/difficulty for a PvE session (implementation.md, Part 6). {@code pveSession}
     * is ignored for every mode other than {@link GameMode#PVE}.
     */
    public synchronized boolean startGame(GameMode gameMode, int players, int msToStart,
                                           ActiveLoadout[] loadouts, PveSessionState pveSession) {
        if (inProgress) return false;
        inProgress = true;
        lastUpdateMs = System.currentTimeMillis();
        this.gameMode = gameMode;
        this.players = players;
        this.loadouts = gameMode.supportsCharacters() ? loadouts : null;
        this.pveSession = (gameMode == GameMode.PVE) ? pveSession : null;
        this.game = new GameHandler(players);
        if (this.pveSession != null) {
            this.game.init(gameMode, new PveRules(this.pveSession.levelData), msToStart);
        } else {
            this.game.init(gameMode, msToStart);
        }

        long gameStartMs = System.currentTimeMillis() + msToStart;
        // Score modes use the four-minute timer; PvE / puzzle ignore gameEndTargetMs.
        long gameEndTargetMs = (gameMode == GameMode.MULTIPLAYER_SCORE || gameMode == GameMode.CHARACTER_SCORE)
                ? gameStartMs + GameConstants.SCORE_MODE_DURATION_MS
                : Long.MAX_VALUE;

        this.highestMoveId = new int[players];
        this.piecesPlaced = new int[players];
        this.hardDropBlockedUntilMs = new long[players];
        this.bumpStats = new BumpStats(players);
        this.clearSpinStats = new ClearSpinStats(players);
        Arrays.fill(this.highestMoveId, -1);
        globalScore = 0;

        int numBoards = game.getBoards().size();
        boolean useScoreModeScorers = gameMode == GameMode.MULTIPLAYER_SCORE || gameMode == GameMode.CHARACTER_SCORE;
        boolean usePveScorers = gameMode == GameMode.PVE;
        scorers = useScoreModeScorers ? new ScoreModeScorer[numBoards] : null;
        pveScorers = usePveScorers ? new PveScorer[numBoards] : null;
        blocked = new BlockedSpawnController[numBoards];
        noobGravity = new NoobGravityController[numBoards];
        for (int b = 0; b < numBoards; b++) {
            int[] boardSlots = game.slotsOnBoard(b);
            if (useScoreModeScorers) {
                ScoreModeScorer scorer = new ScoreModeScorer();
                scorer.reset(b, boardSlots, game);
                scorers[b] = scorer;
            } else if (usePveScorers) {
                PveScorer scorer = new PveScorer();
                scorer.reset(b, boardSlots, game);
                pveScorers[b] = scorer;
            }
            BlockedSpawnController bsc = new BlockedSpawnController();
            bsc.reset(boardSlots.length);
            blocked[b] = bsc;
            noobGravity[b] = new NoobGravityController();
        }
        endCtrl.reset(gameStartMs, gameEndTargetMs, numBoards);
        t = 0;

        if (this.pveSession != null) {
            pveSectionController = new PveSectionController(this.pveSession.levelData, game, numBoards,
                    (win, sectionsCleared) -> endCtrl.beginPveSessionEnd(win, sectionsCleared, this.gameMode,
                            globalScore, computeBoardScorePerPlayer(), bumpStats, piecesPlaced, clearSpinStats));
        } else {
            pveSectionController = null;
        }

        if (this.loadouts != null) {
            if (scorers != null) {
                for (ScoreModeScorer scorer : scorers) scorer.setBonusProvider(this::characterScoreBonusPercent);
            }
            if (pveScorers != null) {
                for (PveScorer scorer : pveScorers) scorer.setBonusProvider(this::characterScoreBonusPercent);
            }
            meterController.reset(players, this.loadouts, game, numBoards);
            applyBagOverrides();
            applyGravityPassives();
        } else {
            if (scorers != null) {
                for (ScoreModeScorer scorer : scorers) scorer.setBonusProvider(null);
            }
            if (pveScorers != null) {
                for (PveScorer scorer : pveScorers) scorer.setBonusProvider(null);
            }
        }
        return true;
    }

    /** Replaces a seated player's queue with their character's bag override, if any (e.g. Wizard). */
    private void applyBagOverrides() {
        for (int slot = 0; slot < loadouts.length; slot++) {
            ActiveLoadout loadout = loadouts[slot];
            if (loadout == null || loadout.character == null || loadout.character.bagOverride == null) continue;
            Board board = game.boardFor(slot);
            if (board == null) continue;
            PieceQueue.BagTypes bag = loadout.character.bagOverride;
            board.setPieceQueue(game.seatOf(slot), new PieceQueue(new java.util.Random().nextInt(), bag));
        }
    }

    /** Applies always-on per-player gravity multipliers from character passives (e.g. The Noob). */
    private void applyGravityPassives() {
        for (int i = 0; i < loadouts.length; i++) {
            ActiveLoadout loadout = loadouts[i];
            float mult = (loadout != null && loadout.character != null)
                    ? loadout.character.passiveGravitySpeedMultiplier : 1f;
            game.setPlayerGravitySpeedMult(i, mult);
        }
    }

    /**
     * Combines the placer's equipped artifact score bonuses (piece-specific a/b and equipped
     * any-piece score effects) with their character's flat passive score bonus (e.g. 3-Mino's
     * +50% on I3/L3 clears) into one percentage.
     */
    private float characterScoreBonusPercent(int playerId, byte pieceType, boolean lineClear, boolean spin) {
        if (loadouts == null || playerId < 0 || playerId >= loadouts.length) return 0f;
        ActiveLoadout loadout = loadouts[playerId];
        if (loadout == null) return 0f;
        float bonus = loadout.scoreBonusPercent(pieceType, lineClear, spin);
        if (lineClear && loadout.character != null && loadout.character.hasPassiveBonusFor(pieceType)) {
            bonus += loadout.character.passiveLineClearScoreBonusPercent;
        }
        return bonus;
    }

    public synchronized void stopGame() {
        this.gameMode = GameMode.NONE;
        this.game = null;
        this.players = 0;
        this.highestMoveId = null;
        this.loadouts = null;
        this.scorers = null;
        this.pveScorers = null;
        this.blocked = null;
        this.noobGravity = null;
        this.globalScore = 0;
        this.pveSession = null;
        this.pveSectionController = null;
        meterController.clear();
        inProgress = false;
        room.onGameStopped();
    }

    public int getHighestMoveId(int playerId) {
        if (highestMoveId == null || playerId < 0 || playerId >= highestMoveId.length) return -1;
        return highestMoveId[playerId];
    }

    public synchronized void applyMoves(int playerId, int[] ids, byte[] types) {
        if (!inProgress || endCtrl.isGameEnded() || game == null || ids == null || types == null) return;
        if (ids.length != types.length) return;
        if (playerId < 0 || playerId >= players) return;
        Board board = game.boardFor(playerId);
        if (board == null) return;
        int seat = game.seatOf(playerId);
        if (board.getActivePieces().size() <= seat) return;
        MoveType[] moveValues = MoveType.values();
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] <= highestMoveId[playerId]) continue;
            highestMoveId[playerId] = ids[i];
            if (types[i] < 0 || types[i] >= moveValues.length) continue;
            MoveType move = moveValues[types[i]];
            if (move == MoveType.HARD_DROP) {
                if (System.currentTimeMillis() < hardDropBlockedUntilMs[playerId]) {
                    // suppressed after auto-lock
                } else {
                    LineClearResult result = board.hardDrop(seat);
                    if (result != null && result.placed) {
                        processPlacement(result);
                    } else if (result != null && result.blockedByPlayerId >= 0) {
                        checkBlocked(board, playerId, result.blockedByPlayerId);
                    }
                }
            } else if (move == MoveType.HOLD) {
                if (!computeHoldAvailable(playerId)) {
                    effects.addHoldSound((byte) playerId, (byte) game.boardIndexOf(playerId), false);
                } else {
                    Piece currentPiece = board.getActivePieces().size() > seat
                            ? board.getActivePieces().get(seat) : null;
                    if (currentPiece != null && currentPiece.isBlockedFromSpawning) {
                        blocked[game.boardIndexOf(playerId)].applyBlockedHold(seat, playerId, board, effects);
                    } else if (board.useHold(seat)) {
                        blocked[game.boardIndexOf(playerId)].setLastHoldUsedMs(System.currentTimeMillis());
                        effects.addHoldSound((byte) playerId, (byte) game.boardIndexOf(playerId), true);
                    }
                }
            } else {
                boolean moved = board.applyMove(seat, move);
                if (!moved && (move == MoveType.LEFT || move == MoveType.RIGHT)) {
                    Piece moverPiece = board.getActivePiece(seat);
                    if (!moverPiece.isBlockedFromSpawning) {
                        int xdiff = (move == MoveType.LEFT) ? -1 : 1;
                        int blockerSeat = board.getLateralBlocker(seat, xdiff);
                        if (blockerSeat >= 0) {
                            checkBump(board, playerId, board.globalSlotForSeat(blockerSeat));
                        }
                    }
                }
                LineClearResult lockResult = board.tryMovementLock(seat);
                if (lockResult != null && lockResult.placed) {
                    processPlacement(lockResult);
                    if (!lockResult.manual) {
                        hardDropBlockedUntilMs[playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                    }
                }
            }
        }
    }

    /**
     * Immediately replaces player {@code playerId}'s active piece with a fresh piece of
     * {@code type} at their spawn position. Does not consume the piece queue or touch hold
     * state. Queues a {@link PieceSwapBroadcast} for the next net-update pass.
     *
     * @return true if the swap was applied
     */
    public synchronized boolean swapActivePiece(int playerId, byte type) {
        if (!canSwapActivePiece(playerId)) return false;
        Board board = game.boardFor(playerId);
        board.swapActivePiece(game.seatOf(playerId), type);
        effects.addPieceSwap((byte) playerId, type, (byte) game.boardIndexOf(playerId));
        return true;
    }

    /**
     * Like {@link #swapActivePiece(int, byte)}, but also forces the player's hold-used flag
     * to {@code holdUsed}.
     *
     * @return true if the swap was applied
     */
    public synchronized boolean swapActivePiece(int playerId, byte type, boolean holdUsed) {
        if (!canSwapActivePiece(playerId)) return false;
        Board board = game.boardFor(playerId);
        board.swapActivePiece(game.seatOf(playerId), type, holdUsed);
        effects.addPieceSwap((byte) playerId, type, (byte) game.boardIndexOf(playerId));
        return true;
    }

    private boolean canSwapActivePiece(int playerId) {
        if (!inProgress || endCtrl.isGameEnded() || game == null) return false;
        if (playerId < 0 || playerId >= players) return false;
        Board board = game.boardFor(playerId);
        if (board == null) return false;
        return board.getActivePieces().size() > game.seatOf(playerId);
    }

    /**
     * Shared post-placement logic: increments the placement counter, applies mode-specific
     * scoring on the placer's own board, and queues sounds/particles.
     */
    private void processPlacement(LineClearResult result) {
        piecesPlaced[result.playerId]++;
        clearSpinStats.record(result);
        int priorCombo = game.getCombo(result.boardIndex);
        long points = scoreHardDropForMode(result);
        if (points > 0) globalScore += points;
        if (loadouts != null && points > 0) {
            meterController.onScoreEvent(result.playerId, points, result.pieceType,
                    result.numClearedRows() > 0, result.spinType != SpinType.NONE);
        }
        effects.queueHardDropEffect(result, priorCombo);
        effects.queueResultParticles(result, game.getBoards().get(result.boardIndex).bw());
    }

    /**
     * Post-landing logic for a falling column: scores flat falling clears on the board it
     * landed on, updates combo/B2B counters, feeds meter/stats when attributed, and queues
     * landing flash + clear particles/SFX.
     */
    private void processFallingLanding(LineClearResult result) {
        boolean attributed = result.playerId >= 0;
        int priorCombo = game.getCombo(result.boardIndex);

        if (attributed) {
            clearSpinStats.record(result);
        }

        long points = 0L;
        if (attributed) {
            points = scoreFallingClearForMode(result);
        } else {
            // Unattributed falling clears still update combo/B2B counters; score stays zero.
            game.applyClearToCounters(result);
        }
        if (points > 0) globalScore += points;
        if (loadouts != null && attributed && points > 0) {
            meterController.onScoreEvent(result.playerId, points, result.pieceType,
                    result.numClearedRows() > 0, false);
        }

        effects.queueFallingLandingFlash(result);
        effects.queueResultParticles(result, game.getBoards().get(result.boardIndex).bw());
        effects.queueFallingClearEffect(result, priorCombo);
    }

    /** Routes hard-drop scoring to the active mode's board scorer, or updates counters only. */
    private long scoreHardDropForMode(LineClearResult result) {
        if (gameMode == GameMode.PVE) {
            PveScorer scorer = pveScorerFor(result.boardIndex);
            return scorer != null ? scorer.scoreHardDrop(result, effects) : 0L;
        }
        if (gameMode == GameMode.MULTIPLAYER_SCORE || gameMode == GameMode.CHARACTER_SCORE) {
            ScoreModeScorer scorer = scoreModeScorerFor(result.boardIndex);
            return scorer != null ? scorer.scoreHardDrop(result, effects) : 0L;
        }
        game.applyClearToCounters(result);
        return 0L;
    }

    private long scoreFallingClearForMode(LineClearResult result) {
        if (gameMode == GameMode.PVE) {
            PveScorer scorer = pveScorerFor(result.boardIndex);
            return scorer != null ? scorer.scoreFallingClear(result, effects) : 0L;
        }
        if (gameMode == GameMode.MULTIPLAYER_SCORE || gameMode == GameMode.CHARACTER_SCORE) {
            ScoreModeScorer scorer = scoreModeScorerFor(result.boardIndex);
            return scorer != null ? scorer.scoreFallingClear(result, effects) : 0L;
        }
        game.applyClearToCounters(result);
        return 0L;
    }

    private ScoreModeScorer scoreModeScorerFor(int boardIndex) {
        return (scorers != null && boardIndex >= 0 && boardIndex < scorers.length) ? scorers[boardIndex] : null;
    }

    private PveScorer pveScorerFor(int boardIndex) {
        return (pveScorers != null && boardIndex >= 0 && boardIndex < pveScorers.length)
                ? pveScorers[boardIndex] : null;
    }

    // -------------------------------------------------------------------------
    // Bump / blocked events
    // -------------------------------------------------------------------------

    /** Below this recent-movement threshold, a player counts as having moved "recently". */
    private static final float BUMP_TIMER_THRESHOLD_MS = 800f;

    private void checkBump(Board board, int playerA, int playerB) {
        int seatA = game.seatOf(playerA);
        int seatB = game.seatOf(playerB);
        int boardIndex = game.boardIndexOf(playerA);
        boolean moverRecent = board.getActivePiece(seatA).movementTimer < BUMP_TIMER_THRESHOLD_MS;
        boolean blockerRecent = board.getActivePiece(seatB).movementTimer < BUMP_TIMER_THRESHOLD_MS;
        if (moverRecent && blockerRecent) {
            bumpedEvent(playerA, playerB, boardIndex);
        } else {
            stationaryBumpEvent(playerA, playerB, boardIndex);
        }
    }

    private void checkBlocked(Board board, int droppedPlayerId, int blockingPlayerId) {
        int blockingSeat = game.seatOf(blockingPlayerId);
        int boardIndex = game.boardIndexOf(droppedPlayerId);
        if (board.getActivePiece(blockingSeat).movementTimer < BUMP_TIMER_THRESHOLD_MS) {
            blockedEvent(droppedPlayerId, blockingPlayerId, boardIndex);
        } else {
            stationaryBlockedEvent(droppedPlayerId, blockingPlayerId, boardIndex);
        }
    }

    /** Fired when two players mutually block each other's lateral movement while
     *  both moved/rotated/soft-dropped recently. */
    private void bumpedEvent(int playerA, int playerB, int boardIndex) {
        bumpStats.incrementBump(playerA, playerB);
        effects.addBumpSound((byte) playerA, (byte) playerB, (byte) boardIndex, false);
    }

    /** Fired when a hard-dropped piece rests on another player's recently-moved
     *  piece without locking. */
    private void blockedEvent(int droppedPlayerId, int blockingPlayerId, int boardIndex) {
        bumpStats.incrementBlock(droppedPlayerId);
        effects.addBumpSound((byte) droppedPlayerId, (byte) blockingPlayerId, (byte) boardIndex, true);
    }

    /** Fired when a lateral move is cancelled by another player's piece but the pair does not
     *  qualify as a mutual bump. Credits only the mover. */
    private void stationaryBumpEvent(int mover, int blocker, int boardIndex) {
        bumpStats.incrementStationaryBump(mover);
        effects.addBumpSound((byte) mover, (byte) blocker, (byte) boardIndex, false);
    }

    /** Fired when a hard drop is blocked by another player's piece that has not moved recently.
     *  Credits only the dropper. */
    private void stationaryBlockedEvent(int droppedPlayerId, int blockingPlayerId, int boardIndex) {
        bumpStats.incrementStationaryBlock(droppedPlayerId);
        effects.addBumpSound((byte) droppedPlayerId, (byte) blockingPlayerId, (byte) boardIndex, true);
    }

    // -------------------------------------------------------------------------
    // Main update loop (unified for all modes)
    // -------------------------------------------------------------------------

    public synchronized void update() {
        deltaTime = (int)(System.currentTimeMillis() - lastUpdateMs);

        switch (gameMode) {
            case MULTIPLAYER_SCORE:
            case MULTIPLAYER_PUZZLE:
            case CHARACTER_SCORE:
            case PVE:
                if (loadouts != null && game != null) {
                    for (int b = 0; b < noobGravity.length; b++) {
                        if (!endCtrl.isBoardRunning(b)) continue;
                        noobGravity[b].tick(deltaTime);
                        game.setGravitySpeedFactor(b, noobGravity[b].gravitySpeedFactor());
                        meterController.setExternalPassiveFillMultiplier(b, noobGravity[b].passiveMeterFillMultiplier());
                    }
                }
                updateGameTick();
                if (loadouts != null && game != null && game.isStarted() && !endCtrl.isGameEnded()) {
                    meterController.tickPassive(deltaTime / 1000f);
                }
                if (game != null) sendNetUpdates();
                break;
            default:
                break;
        }

        endCtrl.tickGrace(gameMode, room, this::stopGame);

        lastUpdateMs = System.currentTimeMillis();
        t++;
    }

    /**
     * Common per-tick update shared by all active game modes. The only mode-specific behaviour
     * (the win condition) is delegated to {@link me.ethanchen.game.GameModeRules#isWinConditionMet},
     * evaluated independently for every still-running board.
     */
    private void updateGameTick() {
        if (endCtrl.isGameEnded()) return;
        game.update(deltaTime);
        if (gameMode == GameMode.PVE && pveSectionController != null && game.isStarted() && !endCtrl.isGameEnded()) {
            pveSectionController.tick(deltaTime, globalScore);
        }
        for (LineClearResult r : game.getAndClearPendingLockResults()) {
            if (!endCtrl.isBoardRunning(r.boardIndex)) continue;
            if (r.fallingClear) {
                processFallingLanding(r);
            } else if (r.placed) {
                processPlacement(r);
                if (!r.manual) {
                    hardDropBlockedUntilMs[r.playerId] = System.currentTimeMillis() + GameConstants.HARD_DROP_SUPPRESS_MS;
                }
            }
        }
        if (game.isStarted() && !endCtrl.isGameEnded()) {
            long[] boardScorePerPlayer = computeBoardScorePerPlayer();
            for (int b = 0; b < blocked.length; b++) {
                if (!endCtrl.isBoardRunning(b)) continue;
                int boardIndex = b;
                blocked[b].update(deltaTime / 1000f, game.getBoards().get(b),
                        () -> endCtrl.beginBoardLoss(boardIndex, gameMode, globalScore, boardScorePerPlayer,
                                bumpStats, piecesPlaced, clearSpinStats));
            }
            for (int b = 0; b < game.getBoards().size(); b++) {
                if (!endCtrl.isBoardRunning(b)) continue;
                endCtrl.checkWinCondition(b, gameMode, game, globalScore, boardScorePerPlayer,
                        bumpStats, piecesPlaced, clearSpinStats);
            }
        }
    }

    /** Each player's own board's current score, indexed by global slot; used to freeze the personal result at game end. */
    private long[] computeBoardScorePerPlayer() {
        long[] out = new long[players];
        for (int slot = 0; slot < players; slot++) {
            int boardIndex = game.boardIndexOf(slot);
            if (pveScorers != null) {
                PveScorer scorer = pveScorerFor(boardIndex);
                out[slot] = scorer != null ? scorer.getTotalScore() : 0L;
            } else {
                ScoreModeScorer scorer = scoreModeScorerFor(boardIndex);
                out[slot] = scorer != null ? scorer.getTotalScore() : 0L;
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Public accessors (delegating to collaborators)
    // -------------------------------------------------------------------------

    public ArrayList<NetParticle> getAndClearPendingParticles() {
        return effects.getAndClearPendingParticles();
    }

    public ArrayList<ParticleSpawner> getAndClearPendingSpawners() {
        return effects.getAndClearPendingSpawners();
    }

    public ArrayList<HardDropEffect> getAndClearPendingHardDropEffects() {
        return effects.getAndClearPendingHardDropEffects();
    }

    public ArrayList<HoldSoundBroadcast> getAndClearPendingHoldSounds() {
        return effects.getAndClearPendingHoldSounds();
    }

    public ArrayList<BumpSoundBroadcast> getAndClearPendingBumpSounds() {
        return effects.getAndClearPendingBumpSounds();
    }

    public ArrayList<PieceSwapBroadcast> getAndClearPendingPieceSwaps() {
        return effects.getAndClearPendingPieceSwaps();
    }

    public ArrayList<AbilityActivateBroadcast> getAndClearPendingAbilityActivateSounds() {
        return effects.getAndClearPendingAbilityActivateSounds();
    }

    public boolean computeHoldAvailable(int playerId) {
        if (game == null) return true;
        Board board = game.boardFor(playerId);
        if (board == null) return true;
        return blocked[game.boardIndexOf(playerId)].computeHoldAvailable(game.seatOf(playerId), board);
    }

    public boolean computeOwnPieceHoldGlow(int playerId) {
        if (game == null) return false;
        Board board = game.boardFor(playerId);
        if (board == null) return false;
        return blocked[game.boardIndexOf(playerId)].computeOwnPieceHoldGlow(game.seatOf(playerId), board);
    }

    /** Board 0's explode progress, for the legacy single-board broadcast. */
    public float getExplodeProgress() {
        return getExplodeProgress(0);
    }

    public float getExplodeProgress(int boardIndex) {
        return (blocked != null && boardIndex >= 0 && boardIndex < blocked.length)
                ? blocked[boardIndex].getExplodeProgress() : -1f;
    }

    /** Board 0's score-mode data, for the legacy single-board broadcast. */
    public ScoreModeData getScoreModeData() {
        return getScoreModeData(0);
    }

    public ScoreModeData getScoreModeData(int boardIndex) {
        ScoreModeScorer scorer = scoreModeScorerFor(boardIndex);
        ScoreModeData d = scorer != null ? scorer.getScoreModeData() : new ScoreModeData();
        d.totalScore = globalScore;
        return d;
    }

    public PuzzleModeData getPuzzleModeData() {
        PuzzleModeData d = new PuzzleModeData();
        d.elapsedMs = endCtrl.getFrozenPuzzleElapsedMs(endCtrl.getGameStartMs(), game != null && game.isStarted());
        return d;
    }

    public boolean isGameEnded() {
        return endCtrl.isGameEnded();
    }

    /**
     * Populates the mode-specific data fields of {@code b} for the current game mode.
     * Replaces the {@code switch (gameMode)} that previously lived in
     * {@link GameRoom#sendNetUpdates}. Uses board 0's data — every client currently renders only
     * board 0, so this matches today's single-board reality.
     */
    public void populateModeData(me.ethanchen.network.packets.s2c.LightGameStateBroadcast b) {
        if (gameMode == me.ethanchen.game.GameMode.MULTIPLAYER_SCORE) {
            b.scoreMode = getScoreModeData(0);
        } else if (gameMode == me.ethanchen.game.GameMode.MULTIPLAYER_PUZZLE) {
            b.puzzleMode = getPuzzleModeData();
        } else if (gameMode == me.ethanchen.game.GameMode.CHARACTER_SCORE) {
            b.scoreMode = getScoreModeData(0);
            if (loadouts != null) {
                b.characterMode = meterController.getCharacterModeData();
                b.characterMode.globalGravitySpeedFactor = game.getGravitySpeedFactor(0);
            }
        } else if (gameMode == me.ethanchen.game.GameMode.PVE) {
            if (pveSectionController != null) {
                me.ethanchen.network.packets.s2c.gamemode.PveModeData pve =
                        new me.ethanchen.network.packets.s2c.gamemode.PveModeData();
                pveSectionController.populateModeData(pve, globalScore);
                PveScorer board0 = pveScorerFor(0);
                if (board0 != null) board0.populateBoardVisuals(pve);
                b.pveMode = pve;
            }
            if (loadouts != null) {
                b.characterMode = meterController.getCharacterModeData();
                b.characterMode.globalGravitySpeedFactor = game.getGravitySpeedFactor(0);
            }
        }
    }

    /** Selected PvE level/difficulty for the current session, or {@code null} outside PvE. */
    public PveSessionState getPveSession() {
        return pveSession;
    }

    /**
     * Activates {@code playerId}'s character ability if their meter is full (implementation.md,
     * Part 1/4): 3-Mino fills skyline gaps with garbage, Wizard forces an I, The Noob disables
     * gravity and avalanches unsupported tiles. Every effect is scoped to the activator's own
     * board. No-op (returns false) for non-character modes, an unready meter, or an invalid player.
     */
    public synchronized boolean activateAbility(int playerId) {
        if (loadouts == null || !canActivateAbility(playerId)) return false;
        if (playerId < 0 || playerId >= loadouts.length || loadouts[playerId] == null) return false;
        CharacterDef character = loadouts[playerId].character;
        if (character == null) return false;

        int boardIndex = game.boardIndexOf(playerId);
        Board board = game.boardFor(playerId);
        int seat = game.seatOf(playerId);

        boolean activated;
        switch (character.ability) {
            case FILL_SKYLINE_GAPS:
                if (!meterController.tryConsume(playerId)) return false;
                activated = activateFillSkylineGaps(board, boardIndex);
                break;
            case FORCE_I:
                if (!canSwapActivePiece(playerId)) return false;
                if (!meterController.tryConsume(playerId)) return false;
                activated = swapActivePiece(playerId, Piece.I);
                if (activated) {
                    board.getActivePiece(seat).fallTrigger = true;
                }
                break;
            case DISABLE_AND_RAMP_GRAVITY:
                if (!meterController.tryConsume(playerId)) return false;
                noobGravity[boardIndex].activate();
                game.setGravitySpeedFactor(boardIndex, noobGravity[boardIndex].gravitySpeedFactor());
                meterController.setExternalPassiveFillMultiplier(boardIndex, noobGravity[boardIndex].passiveMeterFillMultiplier());
                board.triggerOverhangFall(playerId);
                activated = true;
                break;
            default:
                return false;
        }
        if (activated) effects.addAbilityActivateSound((byte) playerId, (byte) boardIndex);
        return activated;
    }

    /**
     * True when the game is running, {@code playerId} is a valid seated slot, and their piece
     * is not in blocked-cycling (abilities are unavailable while spawn-blocked).
     */
    private boolean canActivateAbility(int playerId) {
        if (!inProgress || endCtrl.isGameEnded() || game == null) return false;
        if (playerId < 0 || playerId >= players) return false;
        Board board = game.boardFor(playerId);
        if (board == null) return false;
        int seat = game.seatOf(playerId);
        if (board.getActivePieces().size() <= seat) return false;
        return !board.getActivePieces().get(seat).isBlockedFromSpawning;
    }

    /**
     * Fills skyline-band gaps with garbage on {@code board} and queues hard-drop flash particles
     * for each filled cell. Always returns true after a successful meter consume (even if no
     * cells were filled).
     */
    private boolean activateFillSkylineGaps(Board board, int boardIndex) {
        int[][] filled = board.fillSkylineGaps();
        effects.queueHardDropCellFlashes(filled, boardIndex);
        return true;
    }

    public void sendNetUpdates() {
        if (t % GameConstants.NET_UPDATE_BROADCAST_INTERVAL_TICKS == 0 && game != null) {
            room.sendNetUpdates();
        }
    }

    public GameHandler getGame() {
        return game;
    }

    /** Effective gravity interval for network prediction, including character/ability modifiers. */
    public int getEffectiveGravityMs(int playerId) {
        if (game == null) return 0;
        return game.getEffectiveGravityMs(playerId);
    }

    /** Per-player gravity accumulator for network prediction. */
    public int getGravityTickCounter(int playerId) {
        if (game == null) return 0;
        return game.getGravityTickCounter(playerId);
    }

    /** Snapshot of every seated slot's gravity accumulator for {@link LightGameStateBroadcast}. */
    public int[] getGravityTickCounters() {
        if (game == null) return new int[0];
        return game.copyGravityTickCounters();
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public long getGameStartMs() {
        return endCtrl.getGameStartMs();
    }

    public int[] getPiecesPlaced() {
        return piecesPlaced;
    }

    /**
     * Called when a player disconnects mid-game.
     */
    public synchronized void handleDisconnectedPlayer(int id) {
        endCtrl.beginGameEndDisconnect(gameMode, globalScore, computeBoardScorePerPlayer(),
                bumpStats, piecesPlaced, clearSpinStats);
    }
}
