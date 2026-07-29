package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.lwjgl3.input.LocalPlayerRoster;

/**
 * One local player controlled by this client during a game. Built once from
 * {@link me.ethanchen.network.packets.s2c.StartGameBroadcast#localPlayerIds} and frozen for
 * the duration of the match.
 */
class LocalPlayer {
    final int slot;
    final int localIndex;
    final LocalPlayerRoster.InputSource source;
    final int controllerSlot;
    final GameInputHandler input;
    final ClientMovePredictor predictor;
    boolean holdAvailable = true;
    boolean ownPieceHoldGlow = false;

    LocalPlayer(int slot, int localIndex, LocalPlayerRoster.InputSource source, int controllerSlot,
                GameInputHandler input, ClientMovePredictor predictor) {
        this.slot = slot;
        this.localIndex = localIndex;
        this.source = source;
        this.controllerSlot = controllerSlot;
        this.input = input;
        this.predictor = predictor;
    }
}
