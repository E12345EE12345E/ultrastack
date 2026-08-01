package me.ethanchen.lwjgl3.menuscreens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

import me.ethanchen.game.board.Board;
import me.ethanchen.game.board.Piece;
import me.ethanchen.lwjgl3.ClientApp;
import me.ethanchen.lwjgl3.render.BoardRenderer;
import me.ethanchen.lwjgl3.render.shader.ChromaticAberrationRenderer;
import me.ethanchen.lwjgl3.render.shader.MovementBlurRenderer;

public class ShaderTestScreen extends MenuScreen {
    private final Board board;
    private final Piece piece;
    private final MovementBlurRenderer movementBlurRenderer;
    private final ChromaticAberrationRenderer chromaticAberrationRenderer;


    public ShaderTestScreen(ClientApp app) {
        super(app, app.getShapes(), app.getSprites(), app.getFont());
        board = new Board(Board.Presets.STANDARD_SINGLE);
        piece = Piece.T();
        piece.location = new Vector2(5, 0);
        board.getActivePieces().add(piece);
        movementBlurRenderer = new MovementBlurRenderer();
        chromaticAberrationRenderer = new ChromaticAberrationRenderer();
    }


    @Override
    public void update() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            BoardRenderer.getInstance().getGlowRenderer().reloadShader();
            movementBlurRenderer.reloadShader();
            chromaticAberrationRenderer.reloadShader();
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
        movementBlurRenderer.dispose();
        chromaticAberrationRenderer.dispose();
    }


    @Override
    public void render() {
        float deltaTime = Gdx.graphics.getDeltaTime();
        float tileSize = BoardRenderer.computeTileSize(board, 0.85f);
        float originX = BoardRenderer.centeredOriginX(board, tileSize);
        float originY = BoardRenderer.centeredOriginY(board, tileSize);

        float[] glowStrengths = new float[] { 1.0f };

        chromaticAberrationRenderer.begin();

        // Render movement blur from (5,10) to (5,0)
        movementBlurRenderer.draw(originX, originY, tileSize, piece, 5, 10, 5, 0, 0.25f);

        // Render board with active piece at (5,0) and glow
        BoardRenderer.getInstance().drawBoardGrid(board, originX, originY, tileSize, shapes);
        BoardRenderer.getInstance().drawBoard(board, originX, originY, tileSize, sprites, glowStrengths);

        chromaticAberrationRenderer.end(0f, 0.1f);

        super.render();
    }
}
