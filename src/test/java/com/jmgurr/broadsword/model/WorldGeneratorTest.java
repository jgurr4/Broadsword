package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Generation invariants over a sample of seeds: every generated world must hold them all. */
class WorldGeneratorTest {

    private static final long[] SAMPLE = { 0L, 1L, 2L, 3L, 7L, 42L, 123L, 999L, 31337L, 1234567890L,
            55555555L, 8675309L, 2L * 31, 987654321L, 555L };

    private static String fingerprint(World w) {
        StringBuilder sb = new StringBuilder();
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                sb.append(w.archetype(sx, sy)).append(':').append(w.tier(sx, sy)).append(':')
                        .append(w.enemies(sx, sy)).append('\n');
                Screen s = w.screen(sx, sy);
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        sb.append(s.get(x, y).ordinal());
                    }
                }
            }
        }
        return sb.toString();
    }

    @Test
    void sameSeedProducesIdenticalWorld() {
        for (long seed : new long[] { 1L, 42L, 999L }) {
            assertEquals(fingerprint(WorldGenerator.generate(seed)), fingerprint(WorldGenerator.generate(seed)),
                    "seed " + seed);
        }
    }

    @Test
    void differentSeedsProduceDifferentWorlds() {
        assertNotEquals(fingerprint(WorldGenerator.generate(1L)), fingerprint(WorldGenerator.generate(2L)));
    }

    @Test
    void everySampledWorldIsValid() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            assertEquals(0, w.generationAttempts(), "seed " + seed + " should be valid on the first try");
            assertTrue(WorldGenerator.archetypeConstraintHolds(w), "seed " + seed);
            assertTrue(WorldGenerator.entranceIsPlaced(w), "seed " + seed);
            assertTrue(WorldGenerator.spawnIsWalkable(w), "seed " + seed);
            assertTrue(WorldGenerator.allScreensReachable(w), "seed " + seed);
            assertTrue(WorldGenerator.enemiesAreLegal(w), "seed " + seed);
        }
    }

    @Test
    void failedValidationRetriesWithNextSeedThenGivesUp() {
        World w = WorldGenerator.generate(100L, x -> x.generationAttempts() == 2);
        assertEquals(2, w.generationAttempts());
        assertEquals(102L, w.usedSeed());
        assertThrows(IllegalStateException.class,
                () -> WorldGenerator.generate(1L, unused -> false));
    }

    @Test
    void exactlyOneEntranceOffSpawn() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            ScreenPos e = w.dungeonEntrance();
            assertFalse(e.sx() == World.SPAWN_SX && e.sy() == World.SPAWN_SY,
                    "seed " + seed + " entrance sits on the spawn screen");
            assertEquals(Tile.ENTRANCE, w.screen(e.sx(), e.sy()).get(e.tx(), e.ty()), "seed " + seed);
            assertTrue(w.walkable(e.sx(), e.sy(), e.tx(), e.ty()), "seed " + seed);
        }
    }

    @Test
    void landmarksSitAtFixedOffsetsFromTheEntrance() {
        Map<Landmark, ScreenPos> first = null;
        ScreenPos firstEntrance = null;
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            Map<Landmark, ScreenPos> lm = w.landmarks();
            assertEquals(4, lm.size(), "seed " + seed);
            for (Map.Entry<Landmark, ScreenPos> e : lm.entrySet()) {
                ScreenPos a = e.getValue();
                assertTrue(a.sx() >= 0 && a.sy() >= 0 && a.sx() + 2 <= World.WORLD_W
                        && a.sy() + 2 <= World.WORLD_H, "seed " + seed + " " + e.getKey() + " out of bounds");
                // landmark areas do not overlap each other
                for (Map.Entry<Landmark, ScreenPos> o : lm.entrySet()) {
                    if (o.getKey() == e.getKey()) {
                        continue;
                    }
                    boolean overlap = Math.abs(o.getValue().sx() - a.sx()) >= 2
                            || Math.abs(o.getValue().sy() - a.sy()) >= 2;
                    assertTrue(overlap, "seed " + seed + ": " + e.getKey() + " overlaps " + o.getKey());
                }
            }
            if (first == null) {
                first = lm;
                firstEntrance = w.dungeonEntrance();
            } else {
                for (Landmark k : Landmark.values()) {
                    assertEquals(first.get(k).sx() - firstEntrance.sx(),
                            lm.get(k).sx() - w.dungeonEntrance().sx(), "seed " + seed + " " + k);
                    assertEquals(first.get(k).sy() - firstEntrance.sy(),
                            lm.get(k).sy() - w.dungeonEntrance().sy(), "seed " + seed + " " + k);
                }
            }
        }
    }

    /**
     * Every interior screen keeps its lane cross. Shore screens do not: the ocean
     * floods the lane there, which is the point (no bridge to nowhere), and the
     * world edge needs no crossing.
     */
    @Test
    void everyScreenKeepsItsLanes() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 1; sy + 1 < World.WORLD_H; sy++) {
                for (int sx = 1; sx + 1 < World.WORLD_W; sx++) {
                    Screen s = w.screen(sx, sy);
                    for (int x = 1; x + 1 < World.SCREEN_W; x++) {
                        assertTrue(s.get(x, World.LANE_Y).walkable, "seed " + seed + " lane row " + sx + "," + sy);
                    }
                    for (int y = 1; y + 1 < World.SCREEN_H; y++) {
                        assertTrue(s.get(World.LANE_X, y).walkable, "seed " + seed + " lane col " + sx + "," + sy);
                    }
                }
            }
        }
    }

    /** A border tile is walkable on both sides or neither: no invisible walls. */
    @Test
    void screenBordersMatchAcrossEveryEdge() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            assertTrue(WorldGenerator.screenBordersMatch(w), "seed " + seed);
        }
    }

    /** The shore has no bridge to nowhere: the sea reaches the world edge with no lane gap. */
    @Test
    void shoresHaveNoBridgeAcrossTheSea() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                if (w.landmarkAt(sx, 0) != null || w.landmarkAt(sx, World.WORLD_H - 1) != null) {
                    continue; // landmarks own their tiles
                }
                for (int x = 0; x < World.SCREEN_W; x++) {
                    assertEquals(Tile.WATER, w.screen(sx, 0).get(x, 0), "seed " + seed + " north edge");
                    assertEquals(Tile.WATER, w.screen(sx, World.WORLD_H - 1).get(x, World.SCREEN_H - 1),
                            "seed " + seed + " south edge");
                }
            }
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                if (w.landmarkAt(0, sy) != null || w.landmarkAt(World.WORLD_W - 1, sy) != null) {
                    continue;
                }
                for (int y = 0; y < World.SCREEN_H; y++) {
                    assertEquals(Tile.WATER, w.screen(0, sy).get(0, y), "seed " + seed + " west edge");
                    assertEquals(Tile.WATER, w.screen(World.WORLD_W - 1, sy).get(World.SCREEN_W - 1, y),
                            "seed " + seed + " east edge");
                }
            }
        }
    }

    /** The river channel uses the same columns on the top and bottom edge of every river screen. */
    @Test
    void riversContinueAcrossScreenBorders() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 0; sy + 1 < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    if (w.landmarkAt(sx, sy) != null || w.landmarkAt(sx, sy + 1) != null) {
                        continue; // landmarks own their tiles
                    }
                    Screen top = w.screen(sx, sy), bottom = w.screen(sx, sy + 1);
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        if (x == World.LANE_X) {
                            continue; // the bridge row is walkable by design
                        }
                        assertEquals(top.get(x, World.SCREEN_H - 1) == Tile.WATER,
                                bottom.get(x, 0) == Tile.WATER,
                                "seed " + seed + " river break at " + sx + "," + sy);
                    }
                }
            }
        }
    }

    /** The island: the whole border ring is Shore, and the ocean band has one depth. */
    @Test
    void borderIsOneContinuousCoastline() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                assertEquals(Archetype.SHORE, w.archetype(sx, 0), "seed " + seed);
                assertEquals(Archetype.SHORE, w.archetype(sx, World.WORLD_H - 1), "seed " + seed);
            }
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                assertEquals(Archetype.SHORE, w.archetype(0, sy), "seed " + seed);
                assertEquals(Archetype.SHORE, w.archetype(World.WORLD_W - 1, sy), "seed " + seed);
            }
            int depth = waterRun(w, 12, 0, true);
            assertTrue(depth >= 2 && depth <= 3, "seed " + seed);
            // corner screens hold two seas, so probe only the straight stretches
            for (int sx = 1; sx + 1 < World.WORLD_W; sx++) {
                if (w.landmarkAt(sx, 0) == null) {
                    assertEquals(depth, waterRun(w, sx, 0, true), "seed " + seed + " top edge " + sx);
                }
                if (w.landmarkAt(sx, World.WORLD_H - 1) == null) {
                    assertEquals(depth, waterRun(w, sx, World.WORLD_H - 1, false),
                            "seed " + seed + " bottom edge " + sx);
                }
            }
        }
    }

    /** Length of the water strip at the top (from) or bottom (to) edge of a shore screen. */
    private static int waterRun(World w, int sx, int sy, boolean from) {
        Screen s = w.screen(sx, sy);
        int n = 0;
        for (int y = from ? 0 : World.SCREEN_H - 1; s.get(1, y) == Tile.WATER;) {
            n++;
            y += from ? 1 : -1;
        }
        return n;
    }

    @Test
    void enemyCountsMatchTierAndRoster() {
        int[][] ranges = { { 0, 2 }, { 0, 3 }, { 1, 3 }, { 1, 4 } };
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    int tier = w.tier(sx, sy);
                    assertTrue(tier >= 1 && tier <= 4, "seed " + seed + " tier " + tier);
                    int count = w.enemies(sx, sy).size();
                    int[] r = ranges[tier - 1];
                    boolean safe = (sx == World.SPAWN_SX && sy == World.SPAWN_SY) || w.isCemetery(sx, sy)
                            || (sx == w.dungeonEntrance().sx() && sy == w.dungeonEntrance().sy());
                    if (safe) {
                        assertEquals(0, count, "seed " + seed + " safe screen at " + sx + "," + sy);
                    } else {
                        assertTrue(count >= r[0] && count <= r[1],
                                "seed " + seed + " at " + sx + "," + sy + " tier " + tier + " count " + count);
                    }
                    for (EnemySpawn e : w.enemies(sx, sy)) {
                        if (tier == 1) {
                            assertEquals(EnemyKind.GRUNT, e.kind(), "seed " + seed + " tier-1 roster");
                        }
                        assertTrue(w.walkable(sx, sy, e.tx(), e.ty()), "seed " + seed + " enemy in wall");
                    }
                }
            }
        }
    }

    @Test
    void tierBandsFollowDistanceFromSpawn() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    int dist = World.distanceFromSpawn(sx, sy), tier = w.tier(sx, sy);
                    int[] bounds = dist <= 5 ? new int[] { 1, 1 } : dist <= 10 ? new int[] { 1, 2 }
                            : dist <= 15 ? new int[] { 2, 3 } : new int[] { 3, 4 };
                    assertTrue(tier >= bounds[0] && tier <= bounds[1],
                            "seed " + seed + " at " + sx + "," + sy + " dist " + dist + " tier " + tier);
                }
            }
        }
    }

    /** No archetype fills all four neighbours of any screen. */
    @Test
    void noScreenHasOneArchetypeOnAllSides() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 1; sy + 1 < World.WORLD_H; sy++) {
                for (int sx = 1; sx + 1 < World.WORLD_W; sx++) {
                    Archetype a = w.archetype(sx, sy);
                    boolean ringed = a == w.archetype(sx - 1, sy) && a == w.archetype(sx + 1, sy)
                            && a == w.archetype(sx, sy - 1) && a == w.archetype(sx, sy + 1);
                    assertFalse(ringed, "seed " + seed + " at " + sx + "," + sy + " ringed by " + a);
                }
            }
        }
    }
}
