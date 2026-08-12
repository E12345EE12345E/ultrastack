package me.ethanchen.game.pve;

/** How the client should lay out the board(s) while a {@link PveSection} is active. */
public enum PveBoardDisplay {
    /** Normal side-by-side board layout (single or multi-board, per {@code GameDrawMode}). */
    BOARD_DEFAULT,
    /** Bossfight layout: board(s) shrunk to one side, boss portrait + HP bar on the other. */
    BOARD_BOSSFIGHT
}
