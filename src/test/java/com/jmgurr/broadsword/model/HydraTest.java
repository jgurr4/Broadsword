package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T11: the Hydra boss and the Triforce. Stationary heads fire staggered
 * Fireballs down clear cardinal lanes, only the sword hurts them (Light and
 * the Flute do nothing), all heads down wins the run, and a won dungeon
 * stays won across death and reload.
 */
class HydraTest {

    /** One screen, exit west row 5, hydra at (4,5): body x=3..5 row 5, heads row 6. */
    private static final String ARENA = """
            # dungeon: hydra-test
            [r]
            ################
            #..............#
            #..............#
            #..............#
            #..............#
            E...H..........#
            #..............#
            #..............#
            #..............#
            ################
            """;

    /** A two-room dungeon: entry a (west exit) -> boss room b, mirroring hydra.txt. */
    private static final String TWO_ROOM = """
            # dungeon: hydra-two
            [a] doors: E=b
            ################
            #..............#
            #..............#
            #..............#
            #..............#
            E..............d
            #..............#
            #..............#
            #..............#
            ################
            [b] doors: W=a
            ################
            #..............#
            #.......H......#
            #..............#
            #..............#
            L..............#
            #..............#
            #..............#
            #..............#
            ################
            """;

    private static Sim arena(long seed) {
        Sim sim = new Sim(seed);
        sim.setDungeon(Dungeon.parse(ARENA));
        sim.enterDungeon();
        return sim;
    }

    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty;
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty) return;
        }
        fail("Link never stepped " + d + " from " + sim.link().tx + "," + sim.link().ty);
    }

    private static List<Enemy> heads(Sim sim) {
        return sim.enemies().stream().filter(e -> e.kind == EnemyKind.HYDRA_HEAD).toList();
    }

    private static List<Enemy> bodies(Sim sim) {
        return sim.enemies().stream().filter(e -> e.kind == EnemyKind.HYDRA_BODY).toList();
    }

    /** Park Link on a free tile facing the hydra. */
    private static void park(Sim sim, int tx, int ty) {
        Link l = sim.link();
        l.tx = tx;
        l.ty = ty;
    }

    private static Enemy firstHead(Sim sim) {
        List<Enemy> h = heads(sim);
        assertFalse(h.isEmpty());
        return h.get(0);
    }

    // ---- spawn and layout --------------------------------------------------

    @Test
    void enteringTheBossRoomSpawnsTheHydraWithoutClouds() {
        Sim sim = arena(5L);
        assertEquals(3, bodies(sim).size(), "the body spans three tiles");
        assertEquals(3, heads(sim).size(), "three heads");
        for (Enemy e : sim.enemies()) {
            assertEquals(0f, e.spawning, "the boss never arrives in a cloud");
            assertTrue(e.stationary);
        }
        assertFalse(sim.isDungeonWalkable(4, 5), "the body tile is solid");
        assertFalse(sim.isDungeonWalkable(3, 6), "a head tile is solid");
        assertTrue(sim.isDungeonWalkable(4, 7), "the tile below the heads is free");
    }

    @Test
    void hydraHeadTilesMatchTheAuthoredBossRoom() {
        DungeonScreen boss = Dungeon.loadHydra().screens().stream()
                .filter(s -> s.bossTile() != null).findFirst().orElseThrow();
        ScreenPos b = boss.bossTile();
        assertEquals(new ScreenPos(0, 0, 7, 4), b);
        for (ScreenPos p : boss.hydraBodyTiles()) {
            assertEquals(b.ty(), p.ty());
        }
        for (ScreenPos p : boss.hydraHeadTiles()) {
            assertEquals(b.ty() + 1, p.ty());
            assertTrue(boss.grid().get(p.tx(), p.ty()).walkable, "heads sit on floor: reachable");
        }
        // Link can reach and swing at every head: each has a free neighbouring tile
        for (ScreenPos p : boss.hydraHeadTiles()) {
            boolean reachable = boss.grid().get(p.tx() - 1, p.ty()).walkable
                    || boss.grid().get(p.tx() + 1, p.ty()).walkable
                    || boss.grid().get(p.tx(), p.ty() + 1).walkable;
            assertTrue(reachable, "head at " + p + " has a free tile to swing from");
        }
    }

    @Test
    void theAuthoredBossRoomIsReachableAndBeatble() {
        Sim sim = new Sim(11L);
        sim.setDungeon(Dungeon.loadHydra());
        sim.enterDungeon();
        // stand where the west door of s12 drops Link (the run reaches it through s11)
        int s12 = sim.dungeon().indexOf(sim.dungeon().screens().stream()
                .filter(s -> s.bossTile() != null).findFirst().orElseThrow().id());
        Link l = sim.link();
        l.sx = s12;
        l.tx = 0;
        l.ty = 5;
        sim.placeScreenEnemies(); // the Hydra on the authored tiles
        assertEquals(3, heads(sim).size(), "the boss is waiting in the authored room");
        // walk east along the open door row to the tile west of the west head
        ScreenPos head = sim.dungeonScreen().hydraHeadTiles().get(0);
        for (int i = 0; i < 20 && l.tx != head.tx() - 1; i++) step(sim, Link.Dir.RIGHT);
        assertEquals(head.tx() - 1, l.tx, "the door row walks up beside the head");
        assertEquals(head.ty(), l.ty);
        l.facing = Link.Dir.RIGHT;
        sim.tick(Sim.SWORD_COOLDOWN + 0.01f, Link.Dir.RIGHT, true);
        assertTrue(heads(sim).stream().anyMatch(h -> h.hp < Sim.HYDRA_HEAD_HP),
                "the blade reaches the head");
    }

    // ---- firing -------------------------------------------------------------

    @Test
    void aHeadFiresDownAClearCardinalLane() {
        Sim sim = arena(5L);
        Enemy head = firstHead(sim);
        head.fireTimer = 0.05f;
        park(sim, head.tx, head.ty + 2); // straight below, clear lane
        for (int i = 0; i < 10 && sim.projectiles().isEmpty(); i++) {
            sim.tick(0.02f, null);
        }
        assertEquals(1, sim.projectiles().size(), "the head spits a Fireball");
    }

    @Test
    void noLineOfSightMeansNoFire() {
        Sim sim = arena(5L);
        Enemy head = firstHead(sim);
        head.fireTimer = 0f;
        park(sim, 7, 8); // no cardinal lane to any head
        for (int i = 0; i < 200; i++) sim.tick(0.05f, null);
        assertEquals(0, sim.projectiles().size(), "a head never fires on a diagonal");
    }

    @Test
    void fireRateEscalatesAsHeadsFall() {
        Sim sim = arena(5L);
        List<Enemy> h = heads(sim);
        // with all three heads up, a shot sets the timer to the 3-head interval
        h.get(0).fireTimer = 0.02f;
        park(sim, h.get(0).tx, h.get(0).ty + 2);
        for (int i = 0; i < 10 && sim.projectiles().isEmpty(); i++) sim.tick(0.02f, null);
        assertEquals(1, sim.projectiles().size());
        float threeHeadInterval = h.get(0).fireTimer;

        h.get(1).alive = false;
        h.get(2).alive = false;
        sim.projectiles().clear();
        h.get(0).fireTimer = 0.02f;
        for (int i = 0; i < 10 && sim.projectiles().isEmpty(); i++) sim.tick(0.02f, null);
        assertEquals(1, sim.projectiles().size());
        assertTrue(h.get(0).fireTimer < threeHeadInterval,
                "with fewer heads each remaining head fires faster");
    }

    // ---- what hurts ---------------------------------------------------------

    @Test
    void swordFellsOneHeadInFourHits() {
        Sim sim = arena(5L);
        Enemy head = firstHead(sim);
        park(sim, head.tx, head.ty + 1); // the tile below a head is free; face up into it
        sim.link().facing = Link.Dir.UP;
        for (int i = 0; i < Sim.HYDRA_HEAD_HP; i++) {
            sim.tick(Sim.SWORD_COOLDOWN + 0.01f, Link.Dir.UP, true);
            assertEquals(Sim.HYDRA_HEAD_HP - i - 1, head.hp, "hit " + (i + 1));
        }
        assertFalse(head.alive);
    }

    @Test
    void swordBouncesOffTheBody() {
        Sim sim = arena(5L);
        Enemy body = bodies(sim).get(0);
        Link l = sim.link();
        l.tx = body.tx; // stand on the head's tile below the wing and swing up at it
        l.ty = body.ty + 1;
        l.facing = Link.Dir.UP;
        for (int i = 0; i < 5; i++) sim.tick(Sim.SWORD_COOLDOWN + 0.01f, Link.Dir.UP, true);
        for (Enemy b : bodies(sim)) {
            assertTrue(b.alive, "the body is invulnerable scenery");
            assertEquals(Integer.MAX_VALUE, b.hp);
        }
    }

    @Test
    void lightDoesNothingToTheHydra() {
        Sim sim = arena(5L);
        Enemy head = firstHead(sim);
        park(sim, head.tx, head.ty + 2); // the head sits inside the 2-tile beam
        int hp0 = head.hp;
        assertTrue(sim.castLight());
        assertEquals(hp0, head.hp, "Light does not even wound a head");
        assertTrue(head.alive);
        for (Enemy b : bodies(sim)) {
            assertTrue(b.alive, "and nothing to the body");
        }
    }

    @Test
    void theFluteDoesNothingToTheHydra() {
        Sim sim = new Sim(5L);
        sim.setDungeon(Dungeon.parse(ARENA));
        Link l = sim.link();
        ScreenPos f = sim.world().flute();
        // grant the Flute the way the world hands it out: walk onto its overworld tile
        l.sx = f.sx();
        l.sy = f.sy();
        l.tx = f.tx() - 1;
        l.ty = f.ty();
        for (int i = 0; i < 40 && !sim.hasFlute(); i++) sim.tick(Sim.STEP_INTERVAL + 0.01f, Link.Dir.RIGHT);
        assertTrue(sim.hasFlute());
        sim.enterDungeon();
        int n = heads(sim).size();
        assertTrue(sim.playFlute(), "the tune plays");
        assertEquals(n, heads(sim).stream().filter(e -> e.alive).count(),
                "but the Hydra ignores it entirely");
    }

    // ---- heads never move ----------------------------------------------------

    @Test
    void headsAndBodyNeverMove() {
        Sim sim = arena(5L);
        List<int[]> at = new ArrayList<>();
        for (Enemy e : sim.enemies()) at.add(new int[] { e.tx, e.ty });
        park(sim, 3, 8);
        for (int i = 0; i < 600; i++) sim.tick(0.05f, null);
        for (int i = 0; i < sim.enemies().size(); i++) {
            Enemy e = sim.enemies().get(i);
            assertEquals(at.get(i)[0], e.tx);
            assertEquals(at.get(i)[1], e.ty);
        }
    }

    // ---- victory -------------------------------------------------------------

    @Test
    void lastHeadFallsVictoryAutosaveAndClearedDungeon() {
        Sim sim = arena(5L);
        List<SaveState> saved = new ArrayList<>();
        sim.setSaveSink(saved::add);
        int savesBefore = saved.size();
        for (Enemy h : heads(sim)) h.alive = false;
        step(sim, Link.Dir.UP); // any step: the boss check runs on the enemy clock
        for (int i = 0; i < 40 && !sim.won(); i++) sim.tick(0.05f, null);
        assertTrue(sim.won(), "all heads down: the Triforce is claimed");
        assertTrue(saved.size() > savesBefore, "collecting the piece autosaves");
        assertTrue(sim.saveState().bossDefeated());
        assertTrue(bodies(sim).isEmpty(), "the body sinks with the last head");

        Sim back = new Sim(SaveState.parse(sim.saveState().format()).orElseThrow());
        back.setDungeon(Dungeon.parse(ARENA));
        back.enterDungeon();
        assertTrue(back.dungeonRun().bossDefeated());
        assertEquals(0, back.enemies().size(), "a cleared dungeon never respawns enemies");
    }

    @Test
    void victoryPersistsAcrossDeathAndReloadInATwoRoomDungeon() {
        Sim sim = new Sim(9L);
        sim.setDungeon(Dungeon.parse(TWO_ROOM));
        sim.enterDungeon();
        assertEquals("a", sim.dungeonScreen().id());
        // walk east along row 5 through the door into the boss room
        for (int i = 0; i < 20 && !sim.dungeonScreen().id().equals("b"); i++) {
            step(sim, Link.Dir.RIGHT);
        }
        assertEquals("b", sim.dungeonScreen().id());
        assertEquals(3, heads(sim).size(), "the boss is waiting");
        for (Enemy h : heads(sim)) h.alive = false;
        for (int i = 0; i < 40 && !sim.won(); i++) sim.tick(0.05f, null);
        assertTrue(sim.won());

        // reload: entering again resumes a cleared dungeon with nothing alive
        Sim back = new Sim(SaveState.parse(sim.saveState().format()).orElseThrow());
        back.setDungeon(Dungeon.parse(TWO_ROOM));
        back.enterDungeon();
        assertTrue(back.dungeonRun().bossDefeated());
        for (int i = 0; i < 20 && !back.dungeonScreen().id().equals("b"); i++) {
            Link b = back.link();
            while (b.ty != 5) step(back, b.ty < 5 ? Link.Dir.DOWN : Link.Dir.UP);
            step(back, Link.Dir.RIGHT);
        }
        assertEquals("b", back.dungeonScreen().id());
        assertTrue(back.enemies().isEmpty(), "the boss room stays cleared");
        // and the old boss tiles are walkable again
        assertTrue(back.isDungeonWalkable(8, 2), "the fallen Hydra no longer blocks the room");
    }

    @Test
    void v5SavesLoadWithTheBossAlive() {
        Optional<SaveState> back = SaveState.parse("""
                version=5
                seed=9
                link=1,2,3,4
                facing=LEFT
                magic=2
                secret=1
                cave=-1
                flute=0
                """);
        assertTrue(back.isPresent());
        assertFalse(back.get().bossDefeated());
    }

    @Test
    void bossFlagRoundTrips() {
        SaveState s = new Sim(3L).saveState();
        SaveState won = new SaveState(s.seed(), s.sx(), s.sy(), s.tx(), s.ty(), s.facing(),
                s.magic(), s.secretRevealed(), s.caveKey(), s.fluteTaken(), s.dungeonKeys(),
                s.inDungeon(), s.openedLocks(), s.takenLoot(), true);
        assertEquals(Optional.of(won), SaveState.parse(won.format()));
    }
}
