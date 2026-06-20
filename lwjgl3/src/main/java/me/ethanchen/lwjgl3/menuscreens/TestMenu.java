package me.ethanchen.lwjgl3.menuscreens;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Tile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.BoardRenderer;
public class TestMenu extends MenuScreen {
    private static final float[] TEST_GLOW = {1.0f, 0.5f};

    private GameHandler game;

    public TestMenu(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        game = new GameHandler(2);
        Board board = new Board(Board.Presets.STANDARD_DUO);
        game.getBoards().add(board);
        board.spawnInitialPieces();
        populateTestTiles(board);
    }

    private void populateTestTiles(Board board) {
        Tile[][] tiles = board.getBoard();
        byte[] types = {
            Tile.I, Tile.J, Tile.L, Tile.O, Tile.S, Tile.T, Tile.Z,
            Tile.I3, Tile.L3, Tile.GARBAGE
        };
        for (int i = 0; i < types.length; i++) {
            tiles[2][i] = new Tile(types[i], Tile.SINGLE_TILE);
        }
        for (int i = 0; i < 16; i++) {
            tiles[4 + i / board.bw()][i % board.bw()] = new Tile(Tile.T, (byte) i);
        }
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void update() {
    }

    @Override
    public void render() {
        Board board = game.getBoards().get(0);
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
        float originX = BoardRenderer.centeredOriginX(board, tileSize);
        float originY = BoardRenderer.centeredOriginY(board, tileSize);

        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites, TEST_GLOW);
        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);

        elements.forEach(element -> element.render(shapes, sprites, font));
    }
}
