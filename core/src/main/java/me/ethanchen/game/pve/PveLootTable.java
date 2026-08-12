package me.ethanchen.game.pve;

import java.util.Random;

import me.ethanchen.game.progression.Artifact;

/** Rolls the artifact granted for clearing a PvE level, e.g. via {@code ArtifactRoller.roll}. */
@FunctionalInterface
public interface PveLootTable {
    Artifact roll(Random rng, long xp);
}
