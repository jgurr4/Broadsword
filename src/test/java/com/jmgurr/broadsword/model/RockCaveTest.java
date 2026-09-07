package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T7 rock-formation caves: seeded carving, entry/exit round-trip, the
 * optional Octorock, and save participation. Headless through the Sim seam.
 */
class RockCaveTest {

    private static final long[] SAMPLE = { 1L, 2L, 3L, 7L, 42L, 1337L, 90210L, 0xCAFEL };

    private static int rockScreens(World w) {
        int n = 0;
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                if (w.archetype(sx, sy) == Archetype.ROCKFIELD || w.archetype(sx, sy) == Archetype.MOUNTAIN) {
                    n++;
                }
            }
        }
        return n;
    }

    /** The rock caves of a world (every cave except the sealed secret one). */
    private static java.util.List<Cave> rockCaves(World w) {
        return w.caves().values().stream()
                .filter(c -> !w.isSecretTree(c.entry().sx(), c.entry().sy(), c.entry().tx(), c.entry().ty()))
                .toList();
    }

    @Test
    void carvingIsDeterministicPerSeed() {
        for (long seed : SAMPLE) {
            World a = WorldGenerator.generate(seed);
            World b = WorldGenerator.generate(seed);
            assertEquals(a.caves().keySet(), b.caves().keySet(), "seed " + seed);
            for (int key : a.caves().keySet()) {
                Cave ca = a.caves().get(key), cb = b.caves().get(key);
                assertEquals(ca.entry(), cb.entry());
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        assertEquals(ca.room().get(x, y), cb.room().get(x, y));
                    }
                }
            }
        }
    }

    @Test
    void aboutHalfTheRockScreensGetACave() {
        int screens = 0, caves = 0;
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            screens += rockScreens(w);
            caves += rockCaves(w).size();
        }
        double rate = (double) caves / screens;
        assertTrue(rate > 0.35 && rate < 0.65, "cave rate " + rate + " over " + screens + " rock screens");
    }

    @Test
    void everyRockCaveRoomIsOneDarkScreenWalledInWithExitStairs() {
        for (long seed : SAMPLE) {
            for (Cave c : rockCaves(WorldGenerator.generate(seed))) {
                Screen r = c.room();
                assertEquals(World.ROCK_CAVE_ENTRY_TX, c.entryTx());
                assertEquals(World.ROCK_CAVE_ENTRY_TY, c.entryTy());
                assertEquals(Tile.STAIRS, r.get(c.entryTx(), c.entryTy() + 1), "exit stairs one step south");
                // walled in: no walkable tile on the room's border
                for (int x = 0; x < World.SCREEN_W; x++) {
                    assertEquals(Tile.ROCK, r.get(x, 0), "seed " + seed + " south wall");
                    assertEquals(Tile.ROCK, r.get(x, World.SCREEN_H - 1), "seed " + seed + " north wall");
                }
                for (int y = 0; y < World.SCREEN_H; y++) {
                    assertEquals(Tile.ROCK, r.get(0, y), "seed " + seed + " west wall");
                    assertEquals(Tile.ROCK, r.get(World.SCREEN_W - 1, y), "seed " + seed + " east wall");
                }
                // the whole interior is dark cave floor apart from the exit stairs
                for (int y = 1; y < World.SCREEN_H - 1; y++) {
                    for (int x = 1; x < World.SCREEN_W - 1; x++) {
                        Tile t = r.get(x, y);
                        assertTrue(t == Tile.CAVE_FLOOR || t == Tile.STAIRS,
                                "seed " + seed + " unexpected tile " + t + " at " + x + "," + y);
                    }
                }
            }
        }
    }

    @Test
    void caveEntrancesAreStairsWithAWalkableNeighbour() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (Cave c : rockCaves(w)) {
                ScreenPos e = c.entry();
                assertEquals(Tile.STAIRS, w.screen(e.sx(), e.sy()).get(e.tx(), e.ty()), "seed " + seed);
                boolean side = false;
                for (int[] d : new int[][] { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } }) {
                    if (w.walkable(e.sx(), e.sy(), e.tx() + d[0], e.ty() + d[1])) {
                        side = true;
                    }
                }
                assertTrue(side, "seed " + seed + ": entrance has no walkable side to enter from");
                assertSame(w.caveAt(e.sx(), e.sy(), e.tx(), e.ty()), c, "caveAt finds the cave");
            }
        }
    }

    /** Park Link on a walkable side of the entrance, facing it. */
    private static Sim atCaveEntrance(Cave c, World w, Sim sim) {
        ScreenPos e = c.entry();
        Link l = sim.link();
        for (int[] d : new int[][] { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } }) {
            if (w.walkable(e.sx(), e.sy(), e.tx() + d[0], e.ty() + d[1])) {
                l.sx = e.sx();
                l.sy = e.sy();
                l.tx = e.tx() + d[0];
                l.ty = e.ty() + d[1];
                l.facing = d[0] == 0 ? (d[1] < 0 ? Link.Dir.DOWN : Link.Dir.UP)
                        : (d[0] < 0 ? Link.Dir.RIGHT : Link.Dir.LEFT);
                return sim;
            }
        }
        throw new AssertionError("entrance has no walkable side");
    }

    /** Hold a direction until something moves (tile, screen, or cave). */
    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty, sx = sim.link().sx, sy = sim.link().sy;
            boolean cave = sim.inCave();
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty || sim.inCave() != cave
                    || sim.link().sx != sx || sim.link().sy != sy) {
                return;
            }
        }
        fail("Link never stepped " + d);
    }

    @Test
    void entryAndExitRoundTripReturnsToTheEntryScreenTile() {
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed));
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            Cave c = caves.get(0);
            atCaveEntrance(c, sim.world(), sim);
            int sx = sim.link().sx, sy = sim.link().sy, tx = sim.link().tx, ty = sim.link().ty;
            Link.Dir facing = sim.link().facing;

            step(sim, facing); // onto the hole: into the cave
            assertTrue(sim.inCave(), "seed " + seed + ": the hole is an entrance");
            assertEquals(c.entryTx(), sim.link().tx);
            assertEquals(c.entryTy(), sim.link().ty);
            assertSame(sim.world().screen(sx, sy), sim.world().screen(sim.link().sx, sim.link().sy));

            step(sim, Link.Dir.DOWN); // onto the exit stairs: back out
            assertFalse(sim.inCave(), "seed " + seed + ": the exit returns to the overworld");
            assertEquals(sx, sim.link().sx);
            assertEquals(sy, sim.link().sy);
            assertNotEquals(Tile.STAIRS,
                    sim.world().screen(sim.link().sx, sim.link().sy).get(sim.link().tx, sim.link().ty),
                    "Link never lands back on the hole");
        }
    }

    @Test
    void cavesHaveNoEnemies() {
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed));
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            Cave c = caves.get(0);
            atCaveEntrance(c, sim.world(), sim);
            step(sim, sim.link().facing);
            assertTrue(sim.inCave());
            assertEquals(0, sim.enemies().size(), "seed " + seed + ": a cave holds nothing");
            for (int i = 0; i < 40; i++) {
                sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
            }
            assertEquals(0, sim.enemies().size(), "seed " + seed + ": nothing spawns inside a cave");
        }
    }

    @Test
    void enteringAndLeavingAutosaveAndTheSaveRoundTripsTheCave() {
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed));
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            ScreenPos e0 = caves.get(0).entry();
            Cave c = sim.world().caveAt(e0.sx(), e0.sy(), e0.tx(), e0.ty());
            java.util.List<SaveState> saved = new java.util.ArrayList<>();
            sim.setSaveSink(saved::add);
            atCaveEntrance(c, sim.world(), sim);
            step(sim, sim.link().facing); // enter: one autosave
            assertEquals(1, saved.size(), "entering a cave autosaves");
            assertSame(c, sim.currentCave());

            SaveState s = sim.saveState();
            Optional<SaveState> back = SaveState.parse(s.format());
            assertTrue(back.isPresent());
            assertEquals(s, back.get());

            Sim reloaded = new Sim(back.get());
            assertTrue(reloaded.inCave(), "the cave is a transition worth restoring");
            assertEquals(c.entry(), reloaded.currentCave().entry());
            assertEquals(World.packCave(c.entry().sx(), c.entry().sy(), c.entry().tx(), c.entry().ty()),
                    s.caveKey());

            reloaded.setSaveSink(saved::add);
            step(reloaded, Link.Dir.DOWN); // exit: one more autosave
            assertEquals(2, saved.size(), "leaving a cave autosaves");
            assertFalse(reloaded.inCave());
        }
    }

    @Test
    void linkCannotWalkThroughTheCaveWalls() {
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed));
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            Cave c = caves.get(0);
            atCaveEntrance(c, sim.world(), sim);
            step(sim, sim.link().facing);
            assertTrue(sim.inCave());
            // walk north until nothing moves: Link must stop against the wall
            for (int i = 0; i < World.SCREEN_H + 2; i++) {
                sim.tick(Sim.STEP_INTERVAL, Link.Dir.UP);
            }
            assertTrue(sim.inCave(), "seed " + seed + ": the walls hold");
            assertTrue(sim.link().ty > 0 && sim.link().ty < World.SCREEN_H - 1
                    && sim.link().tx > 0 && sim.link().tx < World.SCREEN_W - 1,
                    "seed " + seed + ": Link stayed inside the room at " + sim.link().tx + "," + sim.link().ty);
            return; // one cave exercised is enough
        }
    }
}
