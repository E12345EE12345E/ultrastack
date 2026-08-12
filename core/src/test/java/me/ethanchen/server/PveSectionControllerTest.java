package me.ethanchen.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.ethanchen.game.GameHandler;
import me.ethanchen.game.GameMode;
import me.ethanchen.game.pve.GarbageInterval;
import me.ethanchen.game.pve.GarbageStyle;
import me.ethanchen.game.pve.PveBoardSpec;
import me.ethanchen.game.pve.PveCriterion;
import me.ethanchen.game.pve.PveCriterionType;
import me.ethanchen.game.pve.PveEnvironment;
import me.ethanchen.game.pve.PveLevelData;
import me.ethanchen.game.pve.PveRules;
import me.ethanchen.game.pve.PveSection;

/** Unit tests for {@link PveSectionController}: criteria evaluation, timeouts, and section advance. */
class PveSectionControllerTest {

    private static PveBoardSpec oneSeatBoard() {
        PveBoardSpec spec = new PveBoardSpec();
        spec.width = 10;
        spec.height = 20;
        spec.spawns = new int[][]{{4, 18}};
        return spec;
    }

    private GameHandler newSinglePlayerGame(PveLevelData level) {
        GameHandler game = new GameHandler(1);
        game.init(GameMode.PVE, new PveRules(level), 0);
        return game;
    }

    private static final class Recorder implements PveSectionController.SessionEndCallback {
        boolean ended;
        boolean win;
        int sectionsCleared;

        @Override
        public void onSessionEnd(boolean win, int sectionsCleared) {
            this.ended = true;
            this.win = win;
            this.sectionsCleared = sectionsCleared;
        }
    }

    @Test
    void orOfAndCriteriaPassesWhenEitherGroupIsFullySatisfied() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        // OR of two AND-groups: (SCORE>=1000 AND TIME>=500) OR (SCORE>=2000)
        section.pass = new PveCriterion[][]{
                { new PveCriterion(PveCriterionType.SCORE, 1000), new PveCriterion(PveCriterionType.TIME, 500) },
                { new PveCriterion(PveCriterionType.SCORE, 2000) },
        };
        PveSection second = new PveSection(); // last section; clearing it is a win
        second.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 999999) }};
        level.sections = new PveSection[]{ section, second };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        // First AND-group needs both SCORE and TIME; score alone at t=100ms isn't enough.
        ctrl.tick(100, 1500);
        assertEquals(0, ctrl.getSectionIndex());

        // Advancing time to 500ms while score stays at 1500 satisfies the first AND-group.
        ctrl.tick(400, 1500);
        assertEquals(1, ctrl.getSectionIndex());
        assertFalse(rec.ended);
    }

    @Test
    void secondOrGroupAloneIsSufficient() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{
                { new PveCriterion(PveCriterionType.SCORE, 1000), new PveCriterion(PveCriterionType.TIME, 999999) },
                { new PveCriterion(PveCriterionType.SCORE, 5000) },
        };
        level.sections = new PveSection[]{ section };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        ctrl.tick(10, 5000); // second OR group satisfied immediately; TIME requirement of the first is irrelevant
        assertTrue(rec.ended);
        assertTrue(rec.win);
        assertEquals(1, rec.sectionsCleared);
    }

    @Test
    void timeoutReCheckFailsTheRunWhenCriteriaStillUnmet() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 999999) }};
        section.timeoutMs = 1000;
        level.sections = new PveSection[]{ section };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        ctrl.tick(999, 0);
        assertFalse(rec.ended);
        ctrl.tick(1, 0); // crosses timeoutMs with criteria still unmet -> loss
        assertTrue(rec.ended);
        assertFalse(rec.win);
        assertEquals(0, rec.sectionsCleared);
    }

    @Test
    void timeoutReCheckPassesWhenCriteriaAreMetOnTheFinalTick() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 100) }};
        section.timeoutMs = 1000;
        level.sections = new PveSection[]{ section };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        // Score reaches the threshold exactly as the timeout fires; the re-check must still pass.
        ctrl.tick(1000, 100);
        assertTrue(rec.ended);
        assertTrue(rec.win);
    }

    @Test
    void sectionEntryAppliesGravityOverride() {
        PveLevelData level = new PveLevelData();
        PveSection first = new PveSection();
        first.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 100) }};
        PveSection second = new PveSection();
        second.env = new PveEnvironment();
        second.env.gravityMs = 42;
        second.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 999999) }};
        level.sections = new PveSection[]{ first, second };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        ctrl.tick(10, 100); // clears section 0, enters section 1
        assertEquals(1, ctrl.getSectionIndex());
        assertEquals(42, game.getGravity(0));
    }

    @Test
    void garbageIntervalSpawnsGarbageOnTheBoard() {
        PveLevelData level = new PveLevelData();
        PveSection section = new PveSection();
        section.pass = new PveCriterion[][]{{ new PveCriterion(PveCriterionType.SCORE, 999999) }};
        section.env = new PveEnvironment();
        section.env.garbage = new GarbageInterval[]{ new GarbageInterval(100, 0, GarbageStyle.DEFAULT, 1) };
        level.sections = new PveSection[]{ section };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        assertFalse(game.getBoards().get(0).hasGarbage());
        ctrl.tick(150, 0); // crosses the 100ms interval once
        assertTrue(game.getBoards().get(0).hasGarbage());
    }

    @Test
    void bossSectionNeverAutoAdvancesWithoutABossController() {
        PveLevelData level = new PveLevelData();
        PveSection boss = new PveSection();
        boss.pass = new PveCriterion[0][]; // empty = boss section
        level.sections = new PveSection[]{ boss };
        level.board1 = oneSeatBoard();

        GameHandler game = newSinglePlayerGame(level);
        Recorder rec = new Recorder();
        PveSectionController ctrl = new PveSectionController(level, game, game.getBoards().size(), rec);

        ctrl.tick(100000, 999999999L);
        assertFalse(rec.ended);
        assertEquals(0, ctrl.getSectionIndex());
    }
}
