package me.ethanchen.game.pve;

/**
 * One leaf condition in a {@link PveSection}'s pass criteria, e.g. {@code {SCORE, 4000}}.
 * Public no-arg constructor + public fields so libGDX {@code Json} can deserialize it directly.
 */
public class PveCriterion {
    public PveCriterionType type;
    public long value;

    public PveCriterion() {}

    public PveCriterion(PveCriterionType type, long value) {
        this.type = type;
        this.value = value;
    }
}
