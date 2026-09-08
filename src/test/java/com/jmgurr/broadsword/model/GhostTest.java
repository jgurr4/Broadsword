package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ghosts + Flute (ticket T8): Cemetery-only spawn ramp, ethereal movement,
 * contact damage, the closed-list negatives (sword and Light do nothing), and
 * the Flute's dispel-all with its one-use-per-visit reset.
 */
class GhostTest {

    @Test
    void cemeteryScreensHeldNoGeneratorEnemies() {
        // the tier roster skips the Cemetery: Ghosts are its only enemy
        for (long seed : new long[] { 2L, 7L, 11L, 23L, 42L, 99L, 1234L, 5150L }) {
            World w = WorldGenerator.generate(seed);
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                for (int sy = 0; sy < World.WORLD_H; sy++) {
                    if (w.isCemetery(sx, sy)) {
                        assertEquals(0, w.enemies(sx, sy).size(),
                                "seed " + seed + ": cemetery (" + sx + "," + sy + ")");
                    }
                }
            }
        }
    }

    @Test
    void tierRostersNeverContainGhosts() {
        for (long seed : new long[] { 1L, 2L, 3L, 42L, 5150L }) {
            World w = WorldGenerator.generate(seed);
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                for (int sy = 0; sy < World.WORLD_H; sy++) {
                    for (EnemySpawn e : w.enemies(sx, sy)) {
                        assertFalse(e.kind() == EnemyKind.GHOST,
                                "seed " + seed + ": Ghost in the tier roster at (" + sx + "," + sy + ")");
                    }
                }
            }
        }
    }

    @Test
    void nonCemeteryScreensNeverSpawnGhosts() {
        Sim sim = new Sim(1L); // spawn screen is not a Cemetery
        for (int i = 0; i < 2000; i++) {
            sim.tick(0.05f, null);
        }
        assertEquals(0, ghosts(sim).size());
    }

    @Test
    void ghostsRampUpToTheCapWhileLinkLingers() {
        Sim sim = cemeterySim(2L);
        assertEquals(0, ghosts(sim).size(), "Ghosts arrive on the ramp, not on entry");
        // the ramp reaches the first Ghost inside ~2s
        tick(sim, 2.5f);
        assertTrue(ghosts(sim).size() >= 1, "a Ghost arrived within ~2s");
        // and the cap is reached and never exceeded
        tick(sim, 60f);
        assertEquals(Sim.GHOST_CAP, ghosts(sim).size(), "the ramp caps at GHOST_CAP");
    }

    @Test
    void ghostDriftsThroughWallsTowardLink() {
        Sim sim = cemeterySim(2L);
        Link link = sim.link();
        // drop a Ghost in the far corner, on a tile Link could never walk to
        Enemy g = spawnGhost(sim, 1, 1);
        link.hearts = World.MAX_HEARTS;
        float before = dist(g, link);
        for (int i = 0; i < 300 && dist(g, link) > 0.5f; i++) {
            sim.tick(0.05f, null);
        }
        assertTrue(dist(g, link) < before, "the Ghost drifted toward Link");
        assertTrue(g.solidPass, "the Ghost must have crossed a tile no walker could enter");
    }

    @Test
    void ghostContactDrainsHalfAHeartAndIframesApply() {
        Sim sim = cemeterySim(2L);
        Link link = sim.link();
        link.hearts = World.MAX_HEARTS;
        Enemy g = spawnGhost(sim, link.tx + 1, link.ty);
        float h0 = link.hearts;
        for (int i = 0; i < 100 && link.hearts == h0; i++) {
            sim.tick(0.05f, null);
        }
        assertEquals(h0 - Sim.ENEMY_DAMAGE, link.hearts, 0.001f, "touch = half a Heart");
        assertTrue(sim.invulnerable(), "i-frames after contact");
        g.fx = link.tx; // keep it touching, inside the i-frame window
        g.fy = link.ty;
        tick(sim, 0.4f);
        assertEquals(h0 - Sim.ENEMY_DAMAGE, link.hearts, 0.001f, "i-frames swallow contact during the window");
    }

    @Test
    void swordDoesNothingToGhosts() {
        Sim sim = cemeterySim(2L);
        Link link = sim.link();
        Enemy g = spawnGhost(sim, link.tx + 1, link.ty);
        link.facing = Link.Dir.RIGHT;
        for (int i = 0; i < 20; i++) {
            sim.swing();
            sim.tick(Sim.SWORD_COOLDOWN, null);
        }
        assertTrue(g.alive, "the sword never kills a Ghost");
        assertEquals(1, g.hp, "the sword does not even wound it");
    }

    @Test
    void lightDoesNothingToGhosts() {
        Sim sim = cemeterySim(2L);
        Link link = sim.link();
        link.facing = Link.Dir.RIGHT;
        Enemy g = spawnGhost(sim, link.tx + 1, link.ty);
        sim.castLight();
        sim.tick(0.05f, null);
        assertTrue(g.alive, "Light never kills a Ghost");
        assertEquals(1, g.hp, "Light does not even wound it");
    }

    @Test
    void fluteDispelsAllGhostsAndStopsTheSpawningForTheVisit() {
        Sim sim = cemeterySim(2L);
        tick(sim, 5f);
        assertTrue(ghosts(sim).size() >= 2, "several Ghosts before the tune");
        int magic0 = sim.magic();
        assertTrue(sim.playFlute());
        assertEquals(magic0, sim.magic(), "the Flute costs no Magic");
        assertEquals(0, ghosts(sim).size(), "the tune dispels every Ghost on the screen");
        tick(sim, 60f);
        assertEquals(0, ghosts(sim).size(), "no Ghost returns while Link stays put");
        assertFalse(sim.playFlute(), "one tune per screen visit");
    }

    @Test
    void leavingAndReenteringRestoresTheSpawningAndTheFlute() {
        Sim sim = cemeterySim(2L);
        tick(sim, 5f);
        assertTrue(sim.playFlute());
        int sx = sim.link().sx, sy = sim.link().sy;
        walkOffScreenAndBack(sim);
        tick(sim, 5f);
        assertTrue(ghosts(sim).size() >= 1, "the ramp restarts on re-entry");
        assertTrue(sim.playFlute(), "the Flute use resets per visit too");
    }

    @Test
    void ghostContactEndsTheRunAtZeroHearts() {
        Sim sim = cemeterySim(2L);
        Link link = sim.link();
        link.hearts = World.MAX_HEARTS;
        spawnGhost(sim, link.tx + 1, link.ty);
        for (int i = 0; i < 20 && sim.phase() == Sim.Phase.PLAYING; i++) {
            tick(sim, Sim.I_FRAME_DURATION + 0.1f);
        }
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());
    }

    @Test
    void aGhostThatLeaksOffTheScreenDespawns() {
        Sim sim = cemeterySim(2L);
        Enemy g = spawnGhost(sim, 0, sim.link().ty);
        g.fx = -0.6; // past the west edge tile centre
        g.fy = sim.link().ty;
        tick(sim, 0.1f);
        assertFalse(g.alive || sim.enemies().contains(g), "off the screen means gone");
    }

    @Test
    void fluteSitsOnAWalkableCemeteryTileInEveryWorld() {
        for (long seed : new long[] { 2L, 7L, 11L, 23L, 42L, 99L, 1234L, 5150L }) {
            World w = WorldGenerator.generate(seed);
            ScreenPos f = w.flute();
            assertTrue(f != null, "seed " + seed + ": every world has a Flute");
            assertTrue(w.isCemetery(f.sx(), f.sy()), "seed " + seed + ": the Flute is in the Cemetery");
            assertTrue(w.walkable(f.sx(), f.sy(), f.tx(), f.ty()), "seed " + seed + ": its tile is walkable");
        }
    }

    @Test
    void walkingOntoTheFluteTilePicksItUpAndTheSaveKeepsIt() {
        Sim sim = new Sim(2L);
        ScreenPos f = sim.world().flute();
        Link link = sim.link();
        link.sx = f.sx();
        link.sy = f.sy();
        // stand next to the Flute with a clear step onto it
        link.tx = f.tx() - 1;
        link.ty = f.ty();
        assertFalse(sim.hasFlute());
        java.util.List<SaveState> saved = new java.util.ArrayList<>();
        sim.setSaveSink(saved::add);
        for (int i = 0; i < 40 && !sim.hasFlute(); i++) {
            sim.tick(Sim.STEP_INTERVAL + 0.01f, Link.Dir.RIGHT);
        }
        assertTrue(sim.hasFlute(), "one step onto the tile and it is his");
        assertTrue(saved.contains(sim.saveState()), "picking up the Flute autosaves");
        SaveState s = sim.saveState();
        Sim reloaded = new Sim(SaveState.parse(s.format()).orElseThrow());
        assertTrue(reloaded.hasFlute(), "the Flute survives a reload");
    }

    @Test
    void playingTheFluteWithoutTheItemDoesNothing() {
        Sim sim = new Sim(2L);
        ScreenPos f = sim.world().flute();
        sim.link().sx = f.sx();
        sim.link().sy = f.sy();
        sim.link().tx = f.tx() - 2;
        sim.link().ty = World.LANE_Y;
        assertFalse(sim.hasFlute());
        tick(sim, 5f);
        assertFalse(sim.playFlute(), "no Flute, no tune");
        assertTrue(ghosts(sim).size() >= 1, "and the Ghosts stay");
    }

    // --- helpers ---------------------------------------------------------------

    /** Step east/west until Link's screen changes, then walk straight back. */
    private static void walkOffScreenAndBack(Sim sim) {
        int sx = sim.link().sx, sy = sim.link().sy;
        Link.Dir out = sx == 0 ? Link.Dir.RIGHT
                : sx == World.WORLD_W - 1 ? Link.Dir.LEFT
                : sx < World.WORLD_W / 2 ? Link.Dir.LEFT : Link.Dir.RIGHT;
        Link.Dir back = out == Link.Dir.LEFT ? Link.Dir.RIGHT : Link.Dir.LEFT;
        for (int i = 0; i < 200 && sim.link().sx == sx && sim.link().sy == sy; i++) {
            sim.tick(Sim.STEP_INTERVAL + 0.01f, out);
        }
        if (sim.link().sx == sx && sim.link().sy == sy) {
            throw new AssertionError("could not walk off screen (" + sx + "," + sy + ")");
        }
        for (int i = 0; i < 200 && (sim.link().sx != sx || sim.link().sy != sy); i++) {
            sim.tick(Sim.STEP_INTERVAL + 0.01f, back);
        }
        assertEquals(sx, sim.link().sx);
        assertEquals(sy, sim.link().sy);
    }

    private static void tick(Sim sim, float seconds) {
        int n = Math.round(seconds / 0.05f);
        for (int i = 0; i < n; i++) {
            sim.tick(0.05f, null);
        }
    }

    private static java.util.List<Enemy> ghosts(Sim sim) {
        return sim.enemies().stream().filter(e -> e.alive && e.ethereal).toList();
    }

    private static float dist(Enemy e, Link link) {
        return (float) Math.hypot(e.fx - link.tx, e.fy - link.ty);
    }

    /**
     * A Sim holding the Flute, Link on the Cemetery's lane row (walkable
     * across every screen, so tests can always walk off and back), one step
     * short of the Flute tile. Hearts are set high: the ramp tests outlive
     * three touches.
     */
    private static Sim cemeterySim(long seed) {
        Sim sim = new Sim(seed);
        ScreenPos f = sim.world().flute();
        Link link = sim.link();
        link.sx = f.sx();
        link.sy = f.sy();
        link.tx = f.tx() - 1;
        link.ty = World.LANE_Y;
        link.hearts = 999f;
        for (int i = 0; i < 40 && !sim.hasFlute(); i++) {
            sim.tick(Sim.STEP_INTERVAL + 0.01f, Link.Dir.RIGHT);
        }
        assertTrue(sim.hasFlute(), "the Flute tile is one step east along the lane");
        return sim;
    }

    /** Drop an ethereal Ghost at a tile and let it exist immediately. */
    private static Enemy spawnGhost(Sim sim, int tx, int ty) {
        Enemy g = new Enemy(EnemyKind.GHOST, tx, ty, 1, 1234L);
        g.ethereal = true;
        g.spawning = 0;
        sim.enemies().add(g);
        return g;
    }
}
