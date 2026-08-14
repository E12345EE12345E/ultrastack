package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.MoveType;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.settings.GameSettings;

/**
 * Manages keyboard and controller input for an in-progress game, translating raw key/button
 * events into {@link MoveType} calls on the {@link ClientMovePredictor}. Extracted from
 * {@link GameScreen} to isolate DAS/ARR timing state, duplicate key/controller dispatch, and
 * soft-drop repeat logic.
 *
 * <p>Call {@link #keyDown}/{@link #keyUp} from the screen's input processor methods, and
 * {@link #tick} once per frame to advance auto-shift and soft-drop timers.
 */
class GameInputHandler {

    private static final int SOFT_DROP_INTERVAL_MS = 50;

    // DAS/ARR state
    private boolean leftHeld  = false;
    private boolean rightHeld = false;
    private int     heldDirection = 0; // -1 = left, 0 = none, 1 = right
    private int     dasTimer = 0;
    private int     arrTimer = 0;
    private boolean dasCharged = false;

    // Soft-drop repeat state
    private boolean softDropHeld  = false;
    private int     softDropTimer = 0;

    private final ClientApp app;
    private final int playerId;
    private final GameHandler game;
    private final ClientMovePredictor predictor;

    GameInputHandler(ClientApp app, int playerId, GameHandler game, ClientMovePredictor predictor) {
        this.app = app;
        this.playerId = playerId;
        this.game = game;
        this.predictor = predictor;
    }

    // -------------------------------------------------------------------------
    // Tick (call once per frame)
    // -------------------------------------------------------------------------

    /**
     * Advances DAS/ARR and soft-drop repeat timers by {@code deltaMs} milliseconds. Must be
     * called from {@link GameScreen#update()} after updating the game handler.
     *
     * @param deltaMs      elapsed milliseconds since the last tick
     * @param board        current local board (needed to check piece availability)
     * @param holdAvailable current hold-availability flag (from server state)
     */
    void tick(int deltaMs, Board board, boolean holdAvailable) {
        tickAutoShift(deltaMs, board, holdAvailable);
        tickSoftDrop(deltaMs, board, holdAvailable);
    }

    // -------------------------------------------------------------------------
    // Keyboard input
    // -------------------------------------------------------------------------

    boolean keyDown(int keycode, Board board, boolean holdAvailable) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        boolean isLeftKey  = keycode == keys.left  || (keys.left2  != -1 && keycode == keys.left2);
        boolean isRightKey = keycode == keys.right || (keys.right2 != -1 && keycode == keys.right2);
        if (isLeftKey || isRightKey) return handleDirectionDown(isLeftKey, board, holdAvailable);

        boolean isSoftDrop = keycode == keys.softDrop || (keys.softDrop2 != -1 && keycode == keys.softDrop2);
        if (isSoftDrop) return handleSoftDropDown(board, holdAvailable);

        boolean isAbility = keycode == keys.ability || (keys.ability2 != -1 && keycode == keys.ability2);
        if (isAbility) { app.sendAbilityRequest(predictor.getLocalIndex()); return true; }

        if (!canAct(board)) return false;
        MoveType type = null;
        if      (keycode == keys.hardDrop  || (keys.hardDrop2  != -1 && keycode == keys.hardDrop2))  type = MoveType.HARD_DROP;
        else if (keycode == keys.rotateCw  || (keys.rotateCw2  != -1 && keycode == keys.rotateCw2))  type = MoveType.ROTATE_CW;
        else if (keycode == keys.rotateCcw || (keys.rotateCcw2 != -1 && keycode == keys.rotateCcw2)) type = MoveType.ROTATE_CCW;
        else if (keycode == keys.rotate180 || (keys.rotate180_2 != -1 && keycode == keys.rotate180_2)) type = MoveType.ROTATE_180;
        else if (keycode == keys.hold      || (keys.hold2       != -1 && keycode == keys.hold2))      type = MoveType.HOLD;
        if (type == null) return false;

        predictor.queueMove(type, board, game, holdAvailable);
        return true;
    }

    boolean keyUp(int keycode) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        boolean isLeftKey  = keycode == keys.left  || (keys.left2  != -1 && keycode == keys.left2);
        boolean isRightKey = keycode == keys.right || (keys.right2 != -1 && keycode == keys.right2);
        if (isLeftKey || isRightKey) return handleDirectionUp(isLeftKey);

        boolean isSoftDrop = keycode == keys.softDrop || (keys.softDrop2 != -1 && keycode == keys.softDrop2);
        if (isSoftDrop) { softDropHeld = false; return true; }

        return false;
    }

    // -------------------------------------------------------------------------
    // Controller input
    // -------------------------------------------------------------------------

    boolean controllerButtonDown(int b, Board board, boolean holdAvailable) {
        GameSettings.MovementKeys keys = app.getSettings().movement;

        if (isCtrlLeft(b) || isCtrlRight(b)) return handleDirectionDown(isCtrlLeft(b), board, holdAvailable);

        boolean isSoftDrop = b != -1 && (b == keys.ctrlSoftDrop || b == keys.ctrlSoftDrop2);
        if (isSoftDrop) return handleSoftDropDown(board, holdAvailable);

        boolean isAbility = b != -1 && (b == keys.ctrlAbility || b == keys.ctrlAbility2);
        if (isAbility) { app.sendAbilityRequest(predictor.getLocalIndex()); return true; }

        if (!canAct(board)) return false;
        MoveType type = null;
        if      (b != -1 && (b == keys.ctrlHardDrop  || b == keys.ctrlHardDrop2))  type = MoveType.HARD_DROP;
        else if (b != -1 && (b == keys.ctrlRotateCw   || b == keys.ctrlRotateCw2))  type = MoveType.ROTATE_CW;
        else if (b != -1 && (b == keys.ctrlRotateCcw  || b == keys.ctrlRotateCcw2)) type = MoveType.ROTATE_CCW;
        else if (b != -1 && (b == keys.ctrlRotate180  || b == keys.ctrlRotate180_2)) type = MoveType.ROTATE_180;
        else if (b != -1 && (b == keys.ctrlHold       || b == keys.ctrlHold2))      type = MoveType.HOLD;
        if (type == null) return false;

        predictor.queueMove(type, board, game, holdAvailable);
        return true;
    }

    boolean controllerButtonUp(int b) {
        if (isCtrlLeft(b) || isCtrlRight(b)) return handleDirectionUp(isCtrlLeft(b));

        GameSettings.MovementKeys keys = app.getSettings().movement;
        boolean isSoftDrop = b != -1 && (b == keys.ctrlSoftDrop || b == keys.ctrlSoftDrop2);
        if (isSoftDrop) { softDropHeld = false; return true; }

        return false;
    }

    // -------------------------------------------------------------------------
    // Shared direction/soft-drop handlers
    // -------------------------------------------------------------------------

    private boolean handleDirectionDown(boolean isLeft, Board board, boolean holdAvailable) {
        if (isLeft) leftHeld = true; else rightHeld = true;
        heldDirection = isLeft ? -1 : 1;
        dasTimer  = 0;
        arrTimer  = 0;
        dasCharged = false;
        if (!game.isStarted()) return true;
        if (board.getActivePieces().size() <= seat()) return true;
        predictor.queueMove(isLeft ? MoveType.LEFT : MoveType.RIGHT, board, game, holdAvailable);
        return true;
    }

    private boolean handleDirectionUp(boolean isLeft) {
        if (isLeft) leftHeld = false; else rightHeld = false;
        boolean otherHeld = isLeft ? rightHeld : leftHeld;
        if (otherHeld) {
            heldDirection = isLeft ? 1 : -1;
            dasTimer  = 0;
            arrTimer  = 0;
            dasCharged = false;
        } else {
            heldDirection = 0;
        }
        return true;
    }

    private boolean handleSoftDropDown(Board board, boolean holdAvailable) {
        softDropHeld  = true;
        softDropTimer = 0;
        if (game.isStarted() && isSoftDropFasterThanGravity()) {
            if (board.getActivePieces().size() > seat()) {
                predictor.queueMove(MoveType.SOFT_DROP, board, game, holdAvailable);
                game.resetGravityTimer(playerId);
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Per-tick timers
    // -------------------------------------------------------------------------

    private void tickAutoShift(int deltaMs, Board board, boolean holdAvailable) {
        if (heldDirection == 0 || !game.isStarted()) return;
        if (board.getActivePieces().size() <= seat()) return;

        GameSettings s = app.getSettings();
        if (!dasCharged) {
            dasTimer += deltaMs;
            if (dasTimer >= s.das) { dasCharged = true; arrTimer = 0; }
        } else {
            arrTimer += deltaMs;
            while (arrTimer >= s.arr) {
                arrTimer -= s.arr;
                predictor.queueMove(heldDirection < 0 ? MoveType.LEFT : MoveType.RIGHT,
                        board, game, holdAvailable);
            }
        }
    }

    private void tickSoftDrop(int deltaMs, Board board, boolean holdAvailable) {
        if (!softDropHeld || !game.isStarted()) return;
        if (!isSoftDropFasterThanGravity()) return;
        if (board.getActivePieces().size() <= seat()) return;

        softDropTimer += deltaMs;
        while (softDropTimer >= SOFT_DROP_INTERVAL_MS) {
            softDropTimer -= SOFT_DROP_INTERVAL_MS;
            predictor.queueMove(MoveType.SOFT_DROP, board, game, holdAvailable);
            game.resetGravityTimer(playerId);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Soft drop applies only when felt gravity (after speed modifiers) is slower than soft-drop rate. */
    private boolean isSoftDropFasterThanGravity() {
        return game.getEffectiveGravityMs(playerId) > SOFT_DROP_INTERVAL_MS;
    }

    private boolean canAct(Board board) {
        return game.isStarted() && board.getActivePieces().size() > seat();
    }

    private int seat() {
        return game.seatOf(playerId);
    }

    private boolean isCtrlLeft(int b) {
        GameSettings.MovementKeys k = app.getSettings().movement;
        return b != -1 && (b == k.ctrlLeft || b == k.ctrlLeft2);
    }

    private boolean isCtrlRight(int b) {
        GameSettings.MovementKeys k = app.getSettings().movement;
        return b != -1 && (b == k.ctrlRight || b == k.ctrlRight2);
    }
}
