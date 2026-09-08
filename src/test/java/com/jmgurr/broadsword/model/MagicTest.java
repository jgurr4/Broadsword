package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Magic meter, Light spell, the Secret tree and the Cave (T6), driven through
 * the headless Sim seam. No libgdx.
 */
class MagicTest {

    private static final long[] SAMPLE = { 1L, 2L, 3L, 7L, 42L, 1337L, 90210L, 0xCAFEL };

    // ---- helpers (same arena pattern as CombatTest) ------------------------

    /** Sim with Link parked at a chosen tile of a cleared 16x10 arena. */
    private static Sim arena(int sx, int sy, int tx, int ty) {
        Sim sim = new Sim(1L);
        World w = sim.world();
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                w.screen(sx, sy).set(x, y, Tile.GRASS);
            }
        }
        Link l = sim.link();
        l.sx = sx;
        l.sy = sy;
        l.tx = tx;
        l.ty = ty;
        sim.enemies().clear();
        return sim;
    }

    /** Arena with Link facing right at (8,5); returns the sim. */
    private static Sim arenaRight() {
        Sim sim = arena(World.SPAWN_SX, World.SPAWN_SY, 8, 5);
        sim.link().facing = Link.Dir.RIGHT;
        return sim;
    }

    private static Enemy grunt(Sim sim, int tx, int ty) {
        Enemy e = new Enemy(EnemyKind.GRUNT, tx, ty, 1L);
        sim.enemies().add(e);
        return e;
    }

    /** Hold a direction until Link has taken {@code n} tile steps. */
    private static void step(Sim sim, Link.Dir d, int n) {
        for (int i = 0; i < n; i++) {
            step(sim, d);
        }
    }

    /** Hold a direction long enough for Link to take one tile step. */
    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty;
            boolean cave = sim.inCave();
            int sx = sim.link().sx, sy = sim.link().sy;
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty || sim.inCave() != cave
                    || sim.link().sx != sx || sim.link().sy != sy) {
                return;
            }
        }
        fail("Link never stepped " + d);
    }

    // ---- magic meter --------------------------------------------------------

    @Test
    void magicIsFullAndUntouchedByTheFire() {
        Sim sim = arenaRight();
        assertEquals(World.MAX_MAGIC, sim.magic());
        assertTrue(sim.fireReady());
        assertTrue(sim.castLight());
        assertFalse(sim.fireReady(), "one cast per screen");
        assertFalse(sim.castLight(), "no charge left on this screen");

        // entering another screen hands over a fresh charge
        for (int y = 0; y < World.SCREEN_H; y++) {
            sim.world().screen(World.SPAWN_SX + 1, World.SPAWN_SY).set(0, y, Tile.GRASS);
        }
        step(sim, Link.Dir.RIGHT, World.SCREEN_W - sim.link().tx + 1);
        assertTrue(sim.fireReady(), "a new screen means a new charge");
        assertTrue(sim.castLight());
        assertEquals(World.MAX_MAGIC, sim.magic(), "the fire never spends Magic");

        // nothing in V1 spends Magic any more: a death cannot "refill" what was never spent
        Link l = sim.link();
        while (sim.phase() == Sim.Phase.PLAYING) {
            l.hearts = Sim.ENEMY_DAMAGE; // one hit kills
            grunt(sim, l.tx + 1, l.ty);
            sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
            sim.enemies().clear();
        }
        sim.respawn();
        assertEquals(World.MAX_MAGIC, sim.magic());
    }

    // ---- Light spell ---------------------------------------------------------

    @Test
    void lightHitsEnemiesAtOneAndTwoWithDamageAndKnockback() {
        Sim sim = arenaRight();
        Link l = sim.link();
        Enemy near = grunt(sim, l.tx + 1, l.ty);
        Enemy far = grunt(sim, l.tx + 2, l.ty);
        near.hp = far.hp = 2;
        assertTrue(sim.castLight());
        assertEquals(1, near.hp, "1 damage at range 1");
        assertEquals(1, far.hp, "1 damage at range 2");
        assertEquals(l.tx + 3, far.tx, "knocked back one tile along the beam");
        assertEquals(l.tx + 1, near.tx, "knockback into the other enemy is blocked");
        assertTrue(sim.lightVisible());
    }

    @Test
    void lightPassesThroughObstacles() {
        Sim sim = arenaRight();
        Link l = sim.link();
        sim.world().screen(l.sx, l.sy).set(l.tx + 1, l.ty, Tile.ROCK);
        Enemy beyond = grunt(sim, l.tx + 2, l.ty);
        beyond.hp = 2;
        assertTrue(sim.castLight());
        assertEquals(1, beyond.hp, "the beam goes straight through the rock");
    }

    @Test
    void lightBurnsFlammableTreesButNotPlainOnes() {
        Sim sim = arenaRight();
        Link l = sim.link();
        Screen s = sim.world().screen(l.sx, l.sy);
        s.set(l.tx + 1, l.ty, Tile.FLAMMABLE_TREE);
        s.set(l.tx + 2, l.ty, Tile.TREE);
        assertTrue(sim.castLight());
        assertEquals(Tile.GRASS, s.get(l.tx + 1, l.ty), "the flammable tree burns away");
        assertEquals(Tile.TREE, s.get(l.tx + 2, l.ty), "a plain tree is immune");
    }

    @Test
    void burningTheSecretTreeRevealsTheStairs() {
        Sim sim = new Sim(2L);
        World w = sim.world();
        ScreenPos p = w.secretTree();
        Link l = sim.link();
        l.sx = p.sx();
        l.sy = p.sy();
        // stand on a walkable side of the tree, face it, cast
        for (Link.Dir d : Link.Dir.values()) {
            int sx2 = p.tx() - d.dx, sy2 = p.ty() - d.dy;
            if (w.walkable(p.sx(), p.sy(), sx2, sy2)) {
                l.tx = sx2;
                l.ty = sy2;
                l.facing = d;
                break;
            }
        }
        assertTrue(sim.castLight());
        assertEquals(Tile.STAIRS, w.screen(p.sx(), p.sy()).get(p.tx(), p.ty()));
        assertTrue(sim.secretRevealed());
    }

    // ---- the Cave -------------------------------------------------------------

    /** Link on a walkable side of the Secret tree, facing it; burns it with Light to reveal the stairs. */
    private static Sim atSecretStairs(long seed) {
        Sim sim = new Sim(seed);
        World w = sim.world();
        ScreenPos p = w.secretTree();
        Link l = sim.link();
        l.sx = p.sx();
        l.sy = p.sy();
        for (Link.Dir d : Link.Dir.values()) {
            int tx = p.tx() - d.dx, ty = p.ty() - d.dy;
            if (w.walkable(p.sx(), p.sy(), tx, ty)) {
                l.tx = tx;
                l.ty = ty;
                l.facing = d;
                assertTrue(sim.castLight(), "Light one tile ahead burns the Secret tree");
                return sim;
            }
        }
        throw new AssertionError("secret tree has no walkable side");
    }

    @Test
    void stairsTakeLinkIntoTheCaveAndBackOut() {
        Sim sim = atSecretStairs(3L);
        ScreenPos p = sim.world().secretTree();
        Link.Dir into = sim.link().facing; // the stairs are one step ahead of Link's face
        step(sim, into);
        assertTrue(sim.inCave(), "stepping onto the stairs enters the Cave");
        assertEquals(World.CAVE_ENTRY_TX, sim.link().tx);
        assertEquals(World.CAVE_ENTRY_TY, sim.link().ty);
        assertTrue(sim.world().caveTerrain().walkable(0, 0, World.CAVE_ENTRY_TX, World.CAVE_ENTRY_TY));
        assertFalse(sim.world().caveTerrain().walkable(0, 0, 0, 0), "the Cave is walled in");

        // walk south onto the return stairs: back out onto the overworld
        for (int i = 0; i < World.SCREEN_H; i++) {
            if (!sim.inCave()) {
                break;
            }
            step(sim, Link.Dir.DOWN);
        }
        assertFalse(sim.inCave(), "the Cave stairs lead back out");
        assertEquals(p.sx(), sim.link().sx);
        assertEquals(p.sy(), sim.link().sy);
        assertNotEquals(Tile.STAIRS,
                sim.world().screen(sim.link().sx, sim.link().sy).get(sim.link().tx, sim.link().ty),
                "Link never lands on the stairs tile");
    }

    @Test
    void caveIsSafeAndEnteringItRefillsTheFireCharge() {
        // atSecretStairs burns the tree, which spends this screen's charge
        Sim sim = atSecretStairs(4L);
        assertFalse(sim.fireReady());
        int m = sim.magic();
        step(sim, sim.link().facing);
        assertTrue(sim.inCave());
        assertTrue(sim.fireReady(), "entering the cave is entering a new screen");
        assertTrue(sim.castLight());
        assertEquals(m, sim.magic(), "the fire inside the cave still costs no Magic");
        sim.link().hearts = Sim.ENEMY_DAMAGE;
        sim.tick(Sim.ENEMY_STEP_INTERVAL * 3, null);
        assertEquals(Sim.Phase.PLAYING, sim.phase(), "nothing hurts Link in the Cave");
    }

    // ---- persistence -----------------------------------------------------------

    @Test
    void saveRoundTripsMagicSecretAndCave() {
        Sim sim = atSecretStairs(5L);
        sim.castLight();
        step(sim, sim.link().facing); // into the Cave
        SaveState s = sim.saveState();
        Optional<SaveState> back = SaveState.parse(s.format());
        assertTrue(back.isPresent());
        assertEquals(s, back.get());

        Sim reloaded = new Sim(back.get());
        assertEquals(s.magic(), reloaded.magic());
        assertTrue(reloaded.secretRevealed());
        assertEquals(Tile.STAIRS,
                reloaded.world().screen(sim.world().secretTree().sx(), sim.world().secretTree().sy())
                        .get(sim.world().secretTree().tx(), sim.world().secretTree().ty()),
                "the stairs stay revealed in the re-derived world");
        assertTrue(reloaded.inCave());
        assertEquals(sim.currentCave().entry(), reloaded.currentCave().entry(), "the same cave, from its key");
        assertEquals(World.MAX_MAGIC, s.magic(), "burning the tree and casting cost no Magic");
        assertEquals(s.magic(), reloaded.magic(), "and Magic stays full after reload");
    }

    @Test
    void v2SavesLoadWithTheSecretCaveFlag() {
        String v2 = "version=2\nseed=9\nlink=20,5,8,5\nfacing=DOWN\nmagic=3\nsecret=1\ncave=1\n";
        Optional<SaveState> s = SaveState.parse(v2);
        assertTrue(s.isPresent());
        assertEquals(SaveState.CAVE_SECRET_V2, s.get().caveKey());
        Sim reloaded = new Sim(s.get());
        assertTrue(reloaded.inCave());
        assertEquals(reloaded.world().secretTree(), reloaded.currentCave().entry());
    }

    @Test
    void v1SavesLoadAsAFreshMagicRun() {
        String v1 = "version=1\nseed=9\nlink=20,5,8,5\nfacing=DOWN\n";
        Optional<SaveState> s = SaveState.parse(v1);
        assertTrue(s.isPresent());
        assertEquals(World.MAX_MAGIC, s.get().magic());
        assertFalse(s.get().secretRevealed());
        assertEquals(SaveState.NO_CAVE, s.get().caveKey());
    }

    // ---- generation -----------------------------------------------------------

    @Test
    void exactlyOneSecretTreeInBand() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            ScreenPos p = w.secretTree();
            assertEquals(Tile.FLAMMABLE_TREE, w.screen(p.sx(), p.sy()).get(p.tx(), p.ty()), "seed " + seed);
            int dist = World.distanceFromSpawn(p.sx(), p.sy());
            assertTrue(dist >= 6 && dist <= 15, "seed " + seed + " secret at dist " + dist);
        }
    }

    @Test
    void flammableTreesAreAboutAOneInFiveOfAllTrees() {
        for (long seed : SAMPLE) {
            World w = WorldGenerator.generate(seed);
            int trees = 0, flam = 0;
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    Screen s = w.screen(sx, sy);
                    for (int y = 0; y < World.SCREEN_H; y++) {
                        for (int x = 0; x < World.SCREEN_W; x++) {
                            if (s.get(x, y) == Tile.TREE) {
                                trees++;
                            } else if (s.get(x, y) == Tile.FLAMMABLE_TREE) {
                                flam++;
                            }
                        }
                    }
                }
            }
            double rate = (double) flam / (trees + flam);
            assertTrue(rate > 0.15 && rate < 0.25, "seed " + seed + " rate " + rate);
        }
    }
}
