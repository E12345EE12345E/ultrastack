package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Tile;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.shader.RippleCircleRenderer;
import me.ethanchen.lwjgl3.render.shader.RippleShaderColor;

public class ShaderTestScreen extends MenuScreen {
    private final Board board;
    private final RippleCircleRenderer rippleCircleRenderer;
    private float elapsedTime = 0f;
    private final RippleShaderColor testColorData;

    public ShaderTestScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        board = new Board(Board.Presets.STANDARD_SINGLE);
        // Set tile at (5,10) to a mino (T-mino) to mark the center
        board.getBoard()[10][5] = Tile.T();
        rippleCircleRenderer = new RippleCircleRenderer();
        testColorData = new RippleShaderColor(
                new Color[] { Color.CYAN, Color.MAGENTA },
                0.88f, 1.0f,
                RippleShaderColor.ColorMode.NOISE,
                0.2f
        );
    }

    @Override
    public void update() {
        elapsedTime += Gdx.graphics.getDeltaTime();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            BoardRenderer.getInstance().getGlowRenderer().reloadShader();
            rippleCircleRenderer.reloadShader();
            Gdx.app.log("ShaderTestScreen", "All shaders reloaded via F5.");
        }
    }

    @Override
    protected void onEscPressed() {
        app.switchMenu(new MainMenu(app));
    }

    @Override
    public void dispose() {
        super.dispose();
        rippleCircleRenderer.dispose();
    }

    @Override
    public void render() {
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
        float originX = BoardRenderer.centeredOriginX(board, tileSize);
        float originY = BoardRenderer.centeredOriginY(board, tileSize);

        // Render board grid and tiles
        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);
        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites, null);

        // Calculate dynamic width multiplier: 1 + 0.5 * sin(elapsedTime * 0.1)
        float widthMult = 1.0f + 0.5f * (float) Math.sin(elapsedTime * 0.1f);

        // Render rippling circle/ellipse displayed at (5,10) with radius 4, dynamic widthMult, heightMult 1.0, average thickness 1.6 tiles, max ripple 0.2 tiles
        rippleCircleRenderer.draw(originX, originY, tileSize, 5f, 10f, 4f, widthMult, 1.0f, 1.6f, 0.2f, testColorData);

        super.render();
    }
}
