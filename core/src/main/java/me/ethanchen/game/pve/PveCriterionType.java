package me.ethanchen.game.pve;

/**
 * Kinds of pass criteria a {@link PveSection} can require. Extensible like
 * {@link me.ethanchen.game.progression.ArtifactEffectType} — add new constants here as new
 * section objectives are needed.
 */
public enum PveCriterionType {
    /** Section-relative score gained since the section started (see {@code sectionScore}). */
    SCORE,
    /** Elapsed time (ms) since the section started. */
    TIME
}
