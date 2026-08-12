package me.ethanchen.server;

import java.util.ArrayList;
import java.util.List;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.pve.PveBoardDisplay;
import me.ethanchen.game.pve.PveCriterion;
import me.ethanchen.game.pve.PveEnvironment;
import me.ethanchen.game.pve.PveLevelData;
import me.ethanchen.game.pve.PveSection;
import me.ethanchen.game.pve.boss.BossDef;
import me.ethanchen.game.pve.boss.BossRegistry;
import me.ethanchen.network.packets.s2c.gamemode.PveModeData;

/**
 * Drives a PvE session's section-by-section progression: evaluates each section's OR-of-AND pass
 * criteria every tick, applies environment overrides (gravity, garbage) on section entry, runs
 * each board's {@link GarbageIntervalRunner}, ticks an optional {@link BossController} for boss
 * sections, and resolves the whole session (via {@link SessionEndCallback}) on a win (last section
 * cleared) or a loss (a section's timeout re-check still fails its criteria).
 */
public class PveSectionController {

    /** Reports how the whole PvE session ended, so {@code ServerGame} can resolve every board. */
    public interface SessionEndCallback {
        /** @param sectionsCleared number of sections fully cleared before the session ended */
        void onSessionEnd(boolean win, int sectionsCleared);
    }

    private final PveLevelData level;
    private final GameHandler game;
    private final int numBoards;
    private final SessionEndCallback callback;
    private final GarbageIntervalRunner[] garbageRunners;

    private int sectionIndex = -1;
    private long sectionElapsedMs;
    private long sectionStartGlobalScore;
    private boolean timeoutRechecked;
    private boolean ended;
    private BossController bossController;

    public PveSectionController(PveLevelData level, GameHandler game, int numBoards, SessionEndCallback callback) {
        this.level = level;
        this.game = game;
        this.numBoards = numBoards;
        this.callback = callback;
        this.garbageRunners = new GarbageIntervalRunner[numBoards];
        for (int i = 0; i < numBoards; i++) garbageRunners[i] = new GarbageIntervalRunner();
        enterSection(0, 0L);
    }

    public int getSectionIndex() { return sectionIndex; }
    public long getSectionElapsedMs() { return sectionElapsedMs; }
    public long getSectionScore(long globalScore) { return globalScore - sectionStartGlobalScore; }
    public BossController getBossController() { return bossController; }

    /** Advances the section state machine by one tick. No-op once the session has ended. */
    public void tick(int deltaMs, long globalScore) {
        if (ended || level.sections == null || sectionIndex < 0 || sectionIndex >= level.sections.length) return;
        sectionElapsedMs += deltaMs;
        PveSection section = level.sections[sectionIndex];
        long sectionScore = getSectionScore(globalScore);

        for (int b = 0; b < numBoards; b++) {
            int amount = garbageRunners[b].tick(deltaMs);
            if (amount > 0 && b < game.getBoards().size()) {
                game.getBoards().get(b).spawnGarbageRows(amount, garbageRunners[b].style(), garbageRunners[b].rng());
            }
        }

        if (section.isBossSection()) {
            if (bossController != null && bossController.tick(deltaMs, sectionScore)) {
                advanceOrWin(globalScore);
            }
            return;
        }

        if (evaluateCriteria(section, sectionScore, sectionElapsedMs)) {
            advanceOrWin(globalScore);
            return;
        }

        if (section.hasTimeout() && sectionElapsedMs >= section.timeoutMs && !timeoutRechecked) {
            timeoutRechecked = true;
            if (evaluateCriteria(section, sectionScore, sectionElapsedMs)) {
                advanceOrWin(globalScore);
            } else {
                endSession(false, sectionIndex);
            }
        }
    }

    /** Fills a live {@link PveModeData} snapshot for network broadcast. */
    public void populateModeData(PveModeData out, long globalScore) {
        if (out == null) return;
        out.sectionIndex = sectionIndex;
        out.sectionElapsedMs = sectionElapsedMs;
        out.sectionScore = getSectionScore(globalScore);
        out.totalScore = globalScore;
        PveSection section = (level.sections != null && sectionIndex >= 0 && sectionIndex < level.sections.length)
                ? level.sections[sectionIndex] : null;
        out.sectionTimeoutMs = (section != null && section.hasTimeout()) ? section.timeoutMs : -1L;
        out.displayMode = (section != null && section.display != null) ? section.display : PveBoardDisplay.BOARD_DEFAULT;
        if (bossController != null) {
            out.bossId = bossController.getBossId();
            out.bossHp = bossController.getHp();
            out.bossMaxHp = bossController.getMaxHp();
            out.bossPhase = bossController.getPhase().ordinal();
            out.bossPhaseElapsedMs = bossController.getPhaseElapsedMs();
            out.bossPhaseDurationMs = bossController.getPhaseDurationMs();
            out.objectiveLines = new String[]{
                    "DEFEAT BOSS",
                    bossController.getHp() + " / " + bossController.getMaxHp() + " HP"
            };
        } else {
            out.bossId = (section != null && section.env != null) ? section.env.bossId : -1;
            out.bossHp = 0;
            out.bossMaxHp = 0;
            out.bossPhase = -1;
            out.bossPhaseElapsedMs = 0;
            out.bossPhaseDurationMs = 0;
            out.objectiveLines = formatObjectiveLines(section, out.sectionScore, sectionElapsedMs);
        }
    }

    /**
     * Builds HUD lines for the current section's OR-of-AND pass criteria, with live progress
     * (e.g. {@code SCORE 1200/4000}, {@code TIME 0:45/1:00}). Groups are separated by {@code or}.
     */
    private static String[] formatObjectiveLines(PveSection section, long sectionScore, long elapsedMs) {
        if (section == null || section.pass == null || section.pass.length == 0) return new String[0];
        List<String> lines = new ArrayList<>();
        boolean firstGroup = true;
        for (PveCriterion[] and : section.pass) {
            if (and == null || and.length == 0) continue;
            if (!firstGroup) lines.add("or");
            firstGroup = false;
            for (PveCriterion c : and) {
                if (c == null || c.type == null) continue;
                switch (c.type) {
                    case SCORE:
                        lines.add("SCORE " + sectionScore + "/" + c.value);
                        break;
                    case TIME:
                        lines.add("TIME " + formatMmSs(elapsedMs) + "/" + formatMmSs(c.value));
                        break;
                    default:
                        lines.add(c.type.name() + " " + c.value);
                        break;
                }
            }
        }
        return lines.toArray(new String[0]);
    }

    private static String formatMmSs(long ms) {
        long mins = Math.max(0, ms) / 60000;
        long secs = (Math.max(0, ms) % 60000) / 1000;
        return mins + ":" + String.format("%02d", secs);
    }

    private boolean evaluateCriteria(PveSection section, long sectionScore, long elapsedMs) {
        if (section.pass == null) return false;
        for (PveCriterion[] and : section.pass) {
            if (and == null || and.length == 0) continue;
            boolean allMet = true;
            for (PveCriterion c : and) {
                if (!criterionMet(c, sectionScore, elapsedMs)) { allMet = false; break; }
            }
            if (allMet) return true;
        }
        return false;
    }

    private boolean criterionMet(PveCriterion c, long sectionScore, long elapsedMs) {
        if (c == null || c.type == null) return false;
        switch (c.type) {
            case SCORE: return sectionScore >= c.value;
            case TIME:  return elapsedMs >= c.value;
            default:    return false;
        }
    }

    private void advanceOrWin(long globalScore) {
        int next = sectionIndex + 1;
        if (level.sections == null || next >= level.sections.length) {
            endSession(true, next);
            return;
        }
        enterSection(next, globalScore);
    }

    private void enterSection(int index, long globalScore) {
        sectionIndex = index;
        sectionElapsedMs = 0;
        sectionStartGlobalScore = globalScore;
        timeoutRechecked = false;
        bossController = null;
        PveSection section = level.sections[index];
        applyEnvironment(section.env);
        if (section.isBossSection() && section.env != null && section.env.hasBoss()) {
            BossDef def = BossRegistry.byId(section.env.bossId);
            if (def != null) {
                bossController = new BossController(def, BossController.garbageOnAllBoards(game));
            }
        }
    }

    private void applyEnvironment(PveEnvironment env) {
        for (int b = 0; b < numBoards; b++) {
            if (env != null && env.gravityMs != null) game.setGravity(b, env.gravityMs);
            if (env != null && env.gravitySpeedFactor != null) game.setGravitySpeedFactor(b, env.gravitySpeedFactor);
            garbageRunners[b].reset(env != null ? env.garbage : null);
        }
    }

    private void endSession(boolean win, int sectionsCleared) {
        if (ended) return;
        ended = true;
        callback.onSessionEnd(win, sectionsCleared);
    }
}
