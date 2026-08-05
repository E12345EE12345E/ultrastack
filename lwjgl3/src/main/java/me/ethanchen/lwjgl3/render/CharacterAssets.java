package me.ethanchen.lwjgl3.render;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import me.ethanchen.game.board.Piece;
import me.ethanchen.game.progression.Artifact;
import me.ethanchen.game.progression.CharacterDef;
import me.ethanchen.game.progression.CharacterRegistry;

/**
 * Shared texture cache for character portraits and artifact piece icons (implementation.md,
 * Part 3/4/6), used by the in-game HUD, character/artifact screens, and fusion screen alike.
 */
public final class CharacterAssets {
    private CharacterAssets() {}

    private static final Map<String, Texture> CACHE = new HashMap<>();

    private static Texture load(String file) {
        return CACHE.computeIfAbsent(file, f -> {
            Texture t = new Texture(Gdx.files.internal(f));
            // Nearest: artifact/portrait assets are low-res pixel art; Linear blurs them when scaled.
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            return t;
        });
    }

    public static Texture portraitFor(int characterId) {
        CharacterDef def = CharacterRegistry.byId(characterId);
        return load(def != null ? def.portraitFile : "char_img/placeholder_character.png");
    }

    /** Null for non-tetromino artifact types (not obtainable yet, so this should never happen today). */
    public static Texture artifactIconFor(byte pieceType) {
        String letter;
        switch (pieceType) {
            case Piece.I: letter = "i"; break;
            case Piece.J: letter = "j"; break;
            case Piece.L: letter = "l"; break;
            case Piece.O: letter = "o"; break;
            case Piece.S: letter = "s"; break;
            case Piece.T: letter = "t"; break;
            case Piece.Z: letter = "z"; break;
            default: return null;
        }
        return load("char_img/artifacts/artifact_piece_" + letter + ".png");
    }

    public static Texture artifactIconFor(Artifact artifact) {
        return artifact != null ? artifactIconFor(artifact.pieceType) : null;
    }
}
