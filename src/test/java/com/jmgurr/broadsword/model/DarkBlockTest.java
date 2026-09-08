package com.jmgurr.broadsword.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

/**
 * T10: dark screens and shoveable blocks on the Sim seam — lighting lifetime,
 * push mechanics, authored triggers, and the keep-vs-reset table.
 */
class DarkBlockTest {

    /**
     * s: the entry (E exit west, d door east to p), dark, grunt at (6,5), S door to q.
     * p: W door to s, N door to q. Row 3 holds block A (1,3), block B (3,3), the
     * hidden loot authored at (7,3) and block C (11,3); B's first shove reveals.
     * q: N door to s, S door to p.
     */
    private static final String FIXTURE = """
            # dungeon: dark-block-test
            [s] doors: E=p S=q dark: true
            ################
            #..............#
            #..............#
            #..............#
            #..............#
            E.....g........d
            #..............#
            #..............#
            #..............#
            ########.d######
            [p] doors: W=s N=q
            trigger: B@(3,3) -> i@(7,3)
            ########d#######
            #..............#
            #..............#
            dB.B.......B...#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            ################
            [q] doors: N=s S=p
            #######d########
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            ########d#######
            """;

    private static final String P = "p";

    /** Fresh sim standing in [p] with the room cleared. */
    private static Sim enterP() {
        Sim sim = new Sim(4242L);
        sim.setDungeon(Dungeon.parse(FIXTURE));
        sim.enterDungeon();
        park(sim, P, 5, 5);
        return sim;
    }

    /** Test setup only: move Link within the dungeon and clear the room. */
    private static void park(Sim sim, String id, int tx, int ty) {
        int idx = sim.dungeon().indexOf(id);
        assertTrue(idx >= 0);
        sim.link().sx = idx;
        sim.link().sy = 0;
        sim.link().tx = tx;
        sim.link().ty = ty;
        sim.enemies().clear();
    }

    private static void place(Sim sim, int tx, int ty) {
        sim.link().tx = tx;
        sim.link().ty = ty;
    }

    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty, sx = sim.link().sx;
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty || sim.link().sx != sx) {
                return;
            }
        }
        fail("Link never stepped " + d + " from " + sim.link().tx + "," + sim.link().ty);
    }

    /** Tick long enough for a step, but do not require that Link moved. */
    private static void nudge(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            sim.tick(Sim.STEP_INTERVAL, d);
        }
    }

    private static boolean blockAt(Sim sim, int tx, int ty) {
        return sim.dungeonRun().blocks(sim.dungeonScreenIndex(), sim.dungeonScreen())
                .stream().anyMatch(p -> p.tx() == tx && p.ty() == ty);
    }

    private static Lootable hiddenAt(DungeonScreen s, int tx, int ty) {
        return s.hiddenLoot().stream().filter(h -> h.tx() == tx && h.ty() == ty).findFirst()
                .orElseThrow(() -> new AssertionError("no hidden loot authored at " + tx + "," + ty));
    }

    // ---- parse --------------------------------------------------------------

    @Test
    void parsesBlocksAndTriggers() {
        Dungeon d = Dungeon.parse(FIXTURE);
        DungeonScreen p = d.screen(P);
        assertEquals(3, p.blocks().size());
        assertEquals(1, p.hiddenLoot().size());
        assertEquals(1, p.triggers().size());
        assertEquals(hiddenAt(p, 7, 3), p.triggers().get(new ScreenPos(0, 0, 3, 3)));
    }

    @Test
    void rejectsTriggerWithoutABlockAndDuplicateTargets() {
        String head = "# dungeon: bad\n[s] doors: \n";
        String grid = """
                E..............#
                #..............#
                #..............#
                #...B..i.......#
                #..............#
                #..............#
                #..............#
                #..............#
                #..............#
                ################
                """;
        // the named tile has no block
        assertThrows(IllegalStateException.class, () -> Dungeon.parse(head
                + "trigger: B@(6,3) -> i@(7,3)\n" + grid));
        // two triggers to the same tile
        assertThrows(IllegalStateException.class, () -> Dungeon.parse(head
                + "trigger: B@(4,3) -> i@(7,3)\n" + "trigger: B@(4,3) -> i@(7,3)\n" + grid));
    }

    // ---- dark screens -------------------------------------------------------

    @Test
    void darkScreenIsObscuredUntilLightIsCast() {
        Sim sim = new Sim(1L);
        sim.setDungeon(Dungeon.parse(FIXTURE));
        sim.enterDungeon();
        assertEquals("s", sim.dungeonScreen().id());
        assertTrue(sim.screenIsDark());
        assertTrue(sim.castLight());
        assertFalse(sim.screenIsDark());
    }

    @Test
    void litScreenStaysLitForTheVisitButDarkResetsOnReEntry() {
        Sim sim = new Sim(1L);
        sim.setDungeon(Dungeon.parse(FIXTURE));
        sim.enterDungeon();
        assertTrue(sim.castLight());
        park(sim, P, 1, 1);
        park(sim, "s", 1, 1);
        assertFalse(sim.screenIsDark(), "a lit screen stays lit for the rest of the visit");
        sim.leaveDungeon();
        sim.enterDungeon();
        assertTrue(sim.screenIsDark(), "dark screens reset when the dungeon is re-entered");
    }

    @Test
    void deathRespawnInDungeonResetsTheLighting() {
        Sim sim = new Sim(1L);
        sim.setDungeon(Dungeon.parse(FIXTURE));
        sim.enterDungeon();
        assertTrue(sim.castLight());
        // die by grunt contact: park a live grunt on Link and tick the hits
        Enemy grunt = new Enemy(EnemyKind.GRUNT, sim.link().tx, sim.link().ty, 0L);
        grunt.spawning = 0;
        sim.enemies().clear();
        sim.enemies().add(grunt);
        for (int i = 0; i < 6 && sim.phase() != Sim.Phase.GAME_OVER; i++) {
            grunt.tx = sim.link().tx;
            grunt.ty = sim.link().ty;
            sim.tick(Sim.ENEMY_STEP_INTERVAL, null); // contact: 1 Heart per enemy step
            sim.tick(Sim.I_FRAME_DURATION + 1, null); // i-frames off before the next hit
        }
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());
        sim.respawn();
        assertTrue(sim.inDungeon(), "dying inside respawns at the dungeon entrance");
        assertTrue(sim.screenIsDark(), "respawn starts a new visit: dark screens are dark again");
    }

    @Test
    void enemiesStayActiveOnDarkScreens() {
        Sim sim = new Sim(1L);
        sim.setDungeon(Dungeon.parse(FIXTURE));
        sim.enterDungeon();
        assertTrue(sim.screenIsDark());
        assertFalse(sim.enemies().isEmpty(), "the grunt spawns on the dark screen");
        place(sim, 4, 5); // within the grunt's chase radius
        Enemy grunt = sim.enemies().get(0);
        int tx0 = grunt.tx, ty0 = grunt.ty;
        for (int i = 0; i < 400; i++) {
            sim.tick(16L, null); // Link holds still
            if (grunt.tx != tx0 || grunt.ty != ty0) return;
        }
        fail("the grunt never moved on the dark screen");
    }

    // ---- pushing ------------------------------------------------------------

    @Test
    void pushMovesTheBlockOneTileAndAdvancesLink() {
        Sim sim = enterP();
        place(sim, 2, 3);
        step(sim, Link.Dir.RIGHT);
        assertEquals(3, sim.link().tx);
        assertEquals(3, sim.link().ty);
        assertFalse(blockAt(sim, 3, 3));
        assertTrue(blockAt(sim, 4, 3));
    }

    @Test
    void blockWillNotShoveIntoWallOrDoor() {
        Sim sim = enterP();
        // into the wall: shove block B north to row 1, then try again
        place(sim, 3, 4);
        step(sim, Link.Dir.UP); // B (3,3) -> (3,2), Link follows to (3,3)
        step(sim, Link.Dir.UP); // B (3,2) -> (3,1), Link follows to (3,2)
        assertTrue(blockAt(sim, 3, 1));
        place(sim, 3, 2);
        nudge(sim, Link.Dir.UP);
        assertTrue(blockAt(sim, 3, 1), "the block stays: the far tile is wall");
        assertEquals(2, sim.link().ty);
    }

    @Test
    void blockWillNotShoveIntoDoorTile() {
        Sim sim = enterP();
        // block A sits beside the west door: the far tile is the door
        place(sim, 2, 3);
        nudge(sim, Link.Dir.LEFT);
        assertTrue(blockAt(sim, 1, 3), "a block never sits in a door");
        assertEquals(2, sim.link().tx);
    }

    @Test
    void blockWillNotShoveIntoAnotherBlock() {
        Sim sim = enterP();
        place(sim, 4, 3);
        step(sim, Link.Dir.LEFT); // B (3,3) -> (2,3)
        assertTrue(blockAt(sim, 2, 3));
        nudge(sim, Link.Dir.LEFT); // (1,3) is block A: the shove stops
        assertTrue(blockAt(sim, 2, 3));
        assertEquals(3, sim.link().tx);
    }

    @Test
    void blockWillNotShoveOntoLoot() {
        Sim sim = enterP();
        // shove block C west until the loot tile stops it
        place(sim, 12, 3);
        nudge(sim, Link.Dir.LEFT);
        nudge(sim, Link.Dir.LEFT);
        nudge(sim, Link.Dir.LEFT);
        nudge(sim, Link.Dir.LEFT);
        assertTrue(blockAt(sim, 8, 3), "the block stops east of the loot at (7,3)");
        assertEquals(9, sim.link().tx);
    }

    @Test
    void pushedBlocksSurviveScreenTransitions() {
        Sim sim = enterP();
        place(sim, 3, 4);
        step(sim, Link.Dir.UP);
        place(sim, 3, 3);
        step(sim, Link.Dir.UP);
        assertTrue(blockAt(sim, 3, 1));
        park(sim, "q", 1, 1);
        park(sim, P, 1, 1);
        assertTrue(blockAt(sim, 3, 1), "the push survives leaving and returning to the screen");
    }

    // ---- triggers -----------------------------------------------------------

    @Test
    void firstPushRevealsTheHiddenItemOnceAndLinkCanTakeIt() {
        Sim sim = enterP();
        Lootable hidden = hiddenAt(sim.dungeonScreen(), 7, 3);
        assertFalse(sim.dungeonRun().revealed(hidden.id()));

        place(sim, 2, 3);
        step(sim, Link.Dir.RIGHT); // B (3,3) -> (4,3): the first shove
        assertTrue(sim.dungeonRun().revealed(hidden.id()));

        // walk onto the revealed loot's tile: standable, and taken
        place(sim, 7, 4);
        step(sim, Link.Dir.UP);
        assertTrue(sim.dungeonRun().taken(hidden.id()));

        // shove back over the trigger tile: revealed stays revealed, nothing re-grants
        place(sim, 5, 3);
        step(sim, Link.Dir.LEFT);
        place(sim, 3, 3);
        step(sim, Link.Dir.RIGHT);
        assertTrue(sim.dungeonRun().taken(hidden.id()));
    }

    @Test
    void blocksAndRevealsSurviveExitAndReEntry() {
        Sim sim = enterP();
        place(sim, 2, 3);
        step(sim, Link.Dir.RIGHT);
        Lootable hidden = hiddenAt(sim.dungeon().screen(P), 7, 3);
        assertTrue(blockAt(sim, 4, 3));

        park(sim, "s", 0, 5);
        step(sim, Link.Dir.LEFT); // the E exit tile leads out
        assertFalse(sim.inDungeon());
        sim.enterDungeon();
        park(sim, P, 1, 1);
        assertTrue(blockAt(sim, 4, 3), "blocks are kept across exit and re-entry");
        assertTrue(sim.dungeonRun().revealed(hidden.id()), "revealed stays revealed");
    }

    @Test
    void hydraIsAuthoredWithBlocksAndTriggers() {
        Dungeon d = Dungeon.loadHydra();
        int blocks = 0, hidden = 0, triggers = 0, dark = 0;
        for (DungeonScreen s : d.screens()) {
            blocks += s.blocks().size();
            hidden += s.hiddenLoot().size();
            triggers += s.triggers().size();
            if (s.dark()) dark++;
        }
        assertTrue(blocks >= 1, "hydra has a block");
        assertEquals(triggers, hidden, "every hidden loot comes from a trigger");
        assertTrue(hidden >= 1, "hydra has hidden loot");
        assertTrue(dark >= 1, "hydra has a dark screen");
    }
}
