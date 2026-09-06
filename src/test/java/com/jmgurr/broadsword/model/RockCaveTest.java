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
                assertEquals(ca.enemy() == null ? null : ca.enemy().kind(),
                        cb.enemy() == null ? null : cb.enemy().kind());
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
    void everyRockCaveRoomIsWalledFourByThreeWithExitStairs() {
        for (long seed : SAMPLE) {
            for (Cave c : rockCaves(WorldGenerator.generate(seed))) {
                Screen r = c.room();
                // interior: 4x3 walkable, centered, with the entry tile inside it
                for (int y = World.ROCK_CAVE_Y0; y <= World.ROCK_CAVE_Y1; y++) {
                    for (int x = World.ROCK_CAVE_X0; x <= World.ROCK_CAVE_X1; x++) {
                        assertTrue(r.get(x, y).walkable, "seed " + seed + " interior (" + x + "," + y + ")");
                    }
                }
                assertEquals(World.ROCK_CAVE_ENTRY_TX, c.entryTx());
                assertEquals(World.ROCK_CAVE_ENTRY_TY, c.entryTy());
                assertEquals(Tile.STAIRS, r.get(c.entryTx(), c.entryTy() + 1), "exit stairs one step south");
                // walled in: no walkable tile on the room's border
                for (int x = 0; x < World.SCREEN_W; x++) {
                    assertFalse(r.get(x, 0).walkable);
                    assertFalse(r.get(x, World.SCREEN_H - 1).walkable);
                }
                for (int y = 0; y < World.SCREEN_H; y++) {
                    assertFalse(r.get(0, y).walkable);
                    assertFalse(r.get(World.SCREEN_W - 1, y).walkable);
                }
                // at most one enemy, and it is an Octorock inside the interior
                if (c.enemy() != null) {
                    assertEquals(EnemyKind.OCTOROCK, c.enemy().kind(), "seed " + seed);
                    assertTrue(r.get(c.enemy().tx(), c.enemy().ty()).walkable, "enemy stands on floor");
                }
                // no other content: the room holds only rock, floor, and the exit stairs
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        Tile t = r.get(x, y);
                        assertTrue(t == Tile.ROCK || t == Tile.GRASS || t == Tile.STAIRS,
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
    void caveHasAtMostOneOctorockAndItIsLiveOnEntry() {
        boolean sawOne = false, sawZero = false;
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed));
            for (Cave c : caves) {
                if (c.enemy() != null) {
                    sawOne = true;
                } else {
                    sawZero = true;
                }
            }
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            Cave c = caves.get(0);
            atCaveEntrance(c, sim.world(), sim);
            step(sim, sim.link().facing);
            assertTrue(sim.inCave());
            long live = sim.enemies().stream().filter(e -> e.alive).count();
            assertEquals(c.enemy() == null ? 0 : 1, live, "seed " + seed + ": cave enemy count");
        }
        assertTrue(sawOne && sawZero, "sample should include guarded and empty caves");
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
    void caveEnemiesAndProjectilesStayInsideTheRoom() {
        for (long seed : SAMPLE) {
            java.util.List<Cave> caves = rockCaves(WorldGenerator.generate(seed)).stream()
                    .filter(c -> c.enemy() != null).toList();
            if (caves.isEmpty()) {
                continue;
            }
            Sim sim = new Sim(seed);
            Cave c = caves.get(0);
            atCaveEntrance(c, sim.world(), sim);
            step(sim, sim.link().facing);
            // let the Octorock run: it must never leave the 4x3 interior
            for (int i = 0; i < 200; i++) {
                sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
                for (Enemy e : sim.enemies()) {
                    if (!e.alive || Sim.spawning(e)) {
                        continue;
                    }
                    assertTrue(e.tx >= World.ROCK_CAVE_X0 && e.tx <= World.ROCK_CAVE_X1
                                    && e.ty >= World.ROCK_CAVE_Y0 && e.ty <= World.ROCK_CAVE_Y1,
                            "seed " + seed + ": Octorock escaped the room at " + e.tx + "," + e.ty);
                }
            }
            return; // one guarded cave exercised is enough
        }
    }
}
