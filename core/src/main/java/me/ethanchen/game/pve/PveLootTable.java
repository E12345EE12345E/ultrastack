package me.ethanchen.game.pve;

import java.util.Random;

import me.ethanchen.game.progression.Artifact;

/** Rolls the artifact granted for clearing a PvE level, e.g. via {@code ArtifactRoller.roll}. */
@FunctionalInterface
public interface PveLootTable {
    /**
     * @param difficulty index into the level's {@code difficultyJsonPaths} registration array
     *                   (0 = first path, 1 = second, …)
     */
    Artifact roll(Random rng, long xp, int difficulty);
}
