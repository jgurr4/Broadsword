package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Combat, Grunt AI and death/respawn rules, driven through the headless Sim
 * seam: seed + scripted inputs in, state transitions out. No libgdx.
 */
class CombatTest {

    // ---- helpers ----------------------------------------------------------

    /** Sim with Link parked at a chosen tile and a cleared 16x10 arena around him. */
    private static Sim arena(Sim sim, int sx, int sy, int tx, int ty) {
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
        // start from an empty screen; tests place the enemies they care about
        sim.enemies().clear();
        return sim;
    }

    private static Sim arenaAtSpawn() {
        return arena(new Sim(1L), World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
    }

    private static Enemy grunt(Sim sim, int tx, int ty) {
        Enemy e = new Enemy(EnemyKind.GRUNT, tx, ty, 1234L);
        sim.enemies().add(e);
        return e;
    }

    /** A Grunt with rock behind it: knockback is blocked, the tile stays fixed. */
    private static Enemy pinnedGrunt(Sim sim, int tx, int ty) {
        Enemy e = grunt(sim, tx, ty);
        sim.world().screen(sim.link().sx, sim.link().sy).set(tx + 1, ty, Tile.ROCK);
        return e;
    }

    /** First screen (as {sx, sy}) with a generator-placed Grunt. */
    private static int[] firstGruntScreen(World w) {
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                for (EnemySpawn s : w.enemies(sx, sy)) {
                    if (s.kind() == EnemyKind.GRUNT) {
                        return new int[] {sx, sy};
                    }
                }
            }
        }
        return null;
    }

    /** Stand Link on the tile left of the target, face right, swing until dead. */
    private static void kill(Sim sim, Enemy target) {
        Link l = sim.link();
        for (int guard = 0; target.alive && guard < 50; guard++) {
            l.hearts = World.MAX_HEARTS; // this helper is about killing, not dying
            l.tx = target.tx - 1;
            l.ty = target.ty;
            l.facing = Link.Dir.RIGHT;
            sim.swing();
            idle(sim, Sim.SWORD_COOLDOWN);
        }
        assertFalse(target.alive);
    }

    /** One frame of idle time. */
    private static void idle(Sim sim, float seconds) {
        sim.tick(seconds, null);
    }

    /** Exactly one enemy step (Link gives no input). */
    private static void enemyStep(Sim sim) {
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
    }

    /** Let every spawning cloud on the current screen materialise. */
    private static void materialize(Sim sim) {
        sim.tick(Sim.ENEMY_SPAWN_DURATION, null);
        assertTrue(sim.enemies().stream().noneMatch(Sim::spawning));
    }

    /** Hold a direction long enough for Link to take one tile step. */
    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty, sx = sim.link().sx, sy = sim.link().sy;
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty || sim.link().sx != sx || sim.link().sy != sy) {
                return;
            }
        }
        fail("Link never stepped " + d);
    }

    private static int aliveAt(Sim sim, int tx, int ty) {
        int n = 0;
        for (Enemy e : sim.enemies()) {
            if (e.alive && e.tx == tx && e.ty == ty) {
                n++;
            }
        }
        return n;
    }

    // ---- sword ------------------------------------------------------------

    @Test
    void swordKillsGruntInTwoHits() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        Enemy g = pinnedGrunt(sim, l.tx + 1, l.ty);
        l.facing = Link.Dir.RIGHT;

        sim.swing();
        assertTrue(g.alive, "one hit is not enough (Grunt has 2 HP)");
        assertEquals(Sim.GRUNT_HP - 1, g.hp);

        idle(sim, Sim.SWORD_COOLDOWN);
        l.tx = g.tx - 1; // the survivor may have wandered; stand beside it again
        l.ty = g.ty;
        sim.swing();
        assertFalse(g.alive);
    }

    @Test
    void swordHitsOnlyTheTileAhead() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        Enemy ahead = grunt(sim, l.tx + 1, l.ty);
        Enemy behind = grunt(sim, l.tx - 1, l.ty);
        Enemy side = grunt(sim, l.tx, l.ty - 1);
        l.facing = Link.Dir.RIGHT;

        sim.swing();

        assertEquals(Sim.GRUNT_HP - 1, ahead.hp);
        assertEquals(Sim.GRUNT_HP, behind.hp);
        assertEquals(Sim.GRUNT_HP, side.hp);
        assertTrue(behind.alive && side.alive);
    }

    @Test
    void swordRespectsCooldown() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy g = pinnedGrunt(sim, l.tx + 1, l.ty);

        sim.swing();
        assertEquals(Sim.GRUNT_HP - 1, g.hp);
        sim.swing(); // inside the cooldown: must be ignored
        assertTrue(g.alive);
        assertEquals(Sim.GRUNT_HP - 1, g.hp);

        // after the cooldown the next swing lands
        idle(sim, Sim.SWORD_COOLDOWN);
        sim.swing();
        assertFalse(g.alive);
    }

    @Test
    void swingIsVisibleWhileTheBladeIsOut() {
        Sim sim = arenaAtSpawn();
        sim.link().facing = Link.Dir.RIGHT;
        assertFalse(sim.swinging());
        sim.swing();
        assertTrue(sim.swinging());
        idle(sim, Sim.SWORD_SWING_DURATION);
        assertFalse(sim.swinging());
    }

    // ---- grunt hit reaction ----------------------------------------------

    @Test
    void swordHitStunsAndKnocksBackOneTile() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy g = grunt(sim, l.tx + 1, l.ty);
        int tx0 = g.tx, ty0 = g.ty;

        sim.swing();

        assertEquals(tx0 + 1, g.tx, "knocked back one tile, away from Link");
        assertEquals(ty0, g.ty);
        assertTrue(g.stunned);

        // the stun costs it its next step
        enemyStep(sim);
        assertEquals(tx0 + 1, g.tx);
        assertEquals(ty0, g.ty);
        assertFalse(g.stunned, "stun lasts one step");
    }

    @Test
    void knockbackBlockedWhenTargetTileIsOccupied() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy g = grunt(sim, l.tx + 1, l.ty);
        sim.world().screen(l.sx, l.sy).set(l.tx + 2, l.ty, Tile.ROCK);

        sim.swing();

        assertEquals(1, aliveAt(sim, l.tx + 1, l.ty), "blocked knockback still damages");
        assertTrue(g.stunned);
        assertEquals(l.tx + 1, g.tx);
    }

    @Test
    void knockbackBlockedByAnotherEnemy() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy g = grunt(sim, l.tx + 1, l.ty);
        grunt(sim, l.tx + 2, l.ty);

        sim.swing();

        assertEquals(l.tx + 1, g.tx);
    }

    // ---- grunt movement ---------------------------------------------------

    @Test
    void gruntChasesWhenClose() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.tx = 2;
        l.ty = 5;
        Enemy g = grunt(sim, 8, 5); // 6 tiles away: inside aggro

        enemyStep(sim);

        assertEquals(7, g.tx, "closes one tile on Link");
        assertEquals(5, g.ty);
    }

    @Test
    void gruntIgnoresLinkWhenFar() {
        Sim sim = arena(new Sim(1L), World.SPAWN_SX, World.SPAWN_SY, 0, 5);
        Link l = sim.link();
        l.tx = 0;
        l.ty = 5;
        // far enough that even 50 wandering steps cannot reach aggro range
        Enemy g = grunt(sim, Sim.GRUNT_AGGRO_TILES + 6, 5);

        // A chaser closes a tile every single step. A far Grunt random-walks:
        // it must wander (some steps move) and many steps must fail to reduce
        // the distance to Link.
        int moved = 0, nonClosing = 0;
        for (int i = 0; i < 50; i++) {
            int dist = Math.abs(g.tx - l.tx) + Math.abs(g.ty - l.ty);
            int px = g.tx, py = g.ty;
            enemyStep(sim);
            if (g.tx != px || g.ty != py) {
                moved++;
            }
            int after = Math.abs(g.tx - l.tx) + Math.abs(g.ty - l.ty);
            if (after >= dist) {
                nonClosing++;
            }
        }
        assertTrue(moved > 0, "a patrolling Grunt still wanders");
        assertTrue(nonClosing > 0, "a far Grunt does not lock on: it fails to close on some steps");
    }

    @Test
    void gruntStaysOnItsScreenAndItsTiles() {
        Sim sim = arena(new Sim(3L), World.SPAWN_SX, World.SPAWN_SY, 1, 5);
        sim.world().screen(World.SPAWN_SX, World.SPAWN_SY).set(0, 5, Tile.WATER);
        Link l = sim.link();
        l.tx = 1;
        l.ty = 5;
        Enemy g = grunt(sim, 12, 5);

        for (int i = 0; i < 500; i++) {
            enemyStep(sim);
            assertTrue(g.tx >= 0 && g.tx < World.SCREEN_W, "screen-local: " + g.tx);
            assertTrue(g.ty >= 0 && g.ty < World.SCREEN_H, "screen-local: " + g.ty);
            assertTrue(sim.world().walkable(World.SPAWN_SX, World.SPAWN_SY, g.tx, g.ty), "never walks into an obstacle");
        }
    }

    @Test
    void deadEnemiesStopMoving() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy g = pinnedGrunt(sim, l.tx + 1, l.ty);
        kill(sim, g);
        int tx = g.tx, ty = g.ty;
        for (int i = 0; i < 20; i++) {
            enemyStep(sim);
        }
        assertEquals(tx, g.tx);
        assertEquals(ty, g.ty);
    }

    // ---- hearts, i-frames, death -----------------------------------------

    @Test
    void startsWithThreeHearts() {
        assertEquals(3, new Sim(1L).link().hearts);
    }

    @Test
    void gruntContactCostsOneHeart() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        grunt(sim, l.tx, l.ty);

        enemyStep(sim);

        assertEquals(2, l.hearts);
    }

    @Test
    void contactIsOneHeartPerTick() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        grunt(sim, l.tx, l.ty);
        grunt(sim, l.tx, l.ty);

        enemyStep(sim);

        assertEquals(2, l.hearts, "i-frames: two Grunts on one tile cost one Heart");
    }

    @Test
    void iFramesExpire() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        grunt(sim, l.tx, l.ty);

        enemyStep(sim);
        assertEquals(2, l.hearts);

        idle(sim, Sim.I_FRAME_DURATION * 0.5f);
        enemyStep(sim);
        assertEquals(2, l.hearts, "still invulnerable");

        idle(sim, Sim.I_FRAME_DURATION);
        enemyStep(sim);
        assertEquals(1, l.hearts);
    }

    @Test
    void deathGoesToGameOverThenRespawnsAtSpawn() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        grunt(sim, l.tx, l.ty);
        for (int i = 0; i < 3; i++) {
            enemyStep(sim);
            idle(sim, Sim.I_FRAME_DURATION);
        }
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());
        assertEquals(0, l.hearts);
    }

    @Test
    void gameOverIgnoresInputUntilRespawn() {
        Sim sim = arenaAtSpawn();
        Link l = sim.link();
        grunt(sim, l.tx, l.ty);
        for (int i = 0; i < 3; i++) {
            enemyStep(sim);
            idle(sim, Sim.I_FRAME_DURATION);
        }
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());
        int sx = l.sx, sy = l.sy, tx = l.tx, ty = l.ty;
        for (int i = 0; i < 50; i++) {
            sim.tick(Sim.STEP_INTERVAL, Link.Dir.RIGHT);
        }
        assertEquals(sx, l.sx);
        assertEquals(tx, l.tx);

        sim.respawn();

        assertEquals(Sim.Phase.PLAYING, sim.phase());
        assertEquals(World.MAX_HEARTS, l.hearts);
        assertEquals(World.SPAWN_SX, l.sx);
        assertEquals(World.SPAWN_SY, l.sy);
        assertEquals(World.SPAWN_TX, l.tx);
        assertEquals(World.SPAWN_TY, l.ty);
    }

    @Test
    void enemiesFromTheGeneratorAreLiveOnTheirScreen() {
        Sim sim = new Sim(5L);
        int[] screen = firstGruntScreen(sim.world());
        assertNotNull(screen, "a generated world has Grunts");
        Link l = sim.link();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        List<Enemy> live = sim.enemies();
        assertEquals(sim.world().enemies(l.sx, l.sy).size(), live.size());
        assertTrue(live.stream().allMatch(e -> e.alive));
    }

    // ---- spawn clouds ------------------------------------------------------

    @Test
    void enemiesEnterTheScreenAsSpawningClouds() {
        Sim sim = new Sim(5L);
        int[] screen = firstGruntScreen(sim.world());
        Link l = sim.link();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(0.01f, null);
        assertTrue(sim.enemies().stream().allMatch(Sim::spawning),
                "every enemy starts as a cloud");

        // a cloud cannot hurt Link, even standing on it
        Enemy cloud = sim.enemies().get(0);
        l.tx = cloud.tx;
        l.ty = cloud.ty;
        l.hearts = World.MAX_HEARTS;
        enemyStep(sim);
        assertEquals(World.MAX_HEARTS, l.hearts, "a cloud does not do contact damage");

        // ...and does not move
        int tx = cloud.tx, ty = cloud.ty;
        idle(sim, 0.5f);
        assertEquals(tx, cloud.tx);
        assertEquals(ty, cloud.ty);
        assertTrue(Sim.spawning(cloud));

        // after the 2s are up it is a live enemy again: it moves (chases Link)
        idle(sim, Sim.ENEMY_SPAWN_DURATION);
        assertFalse(Sim.spawning(cloud));
        l.tx = tx - 3;
        l.ty = ty;
        boolean moved = false;
        for (int i = 0; i < 20 && !moved; i++) {
            enemyStep(sim);
            moved = cloud.tx != tx || cloud.ty != ty;
        }
        assertTrue(moved, "a materialised enemy moves again");
    }

    @Test
    void enemiesRespawnInNewPlacesOnEveryEntry() {
        Sim sim = new Sim(5L);
        int[] screen = firstGruntScreen(sim.world());
        Link l = sim.link();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(0.01f, null);
        List<String> first = positions(sim);
        assertTrue(sim.world().enemies(l.sx, l.sy).size() >= 3,
                "screen has enough enemies for the layout to move");

        int neighbour = screen[0] > 0 ? screen[0] - 1 : screen[0] + 1;
        boolean differs = false;
        for (int visit = 0; visit < 8 && !differs; visit++) {
            l.sx = neighbour;
            sim.tick(0.01f, null);
            l.sx = screen[0];
            sim.tick(0.01f, null);
            if (!positions(sim).equals(first)) {
                differs = true;
            }
        }
        assertTrue(differs, "re-entering the screen re-rolls the enemy layout");
    }

    private static List<String> positions(Sim sim) {
        return sim.enemies().stream().map(e -> e.kind + "@" + e.tx + "," + e.ty).sorted().toList();
    }

    @Test
    void killedEnemiesRespawnWhenLinkLeavesAndReturns() {
        Sim sim = new Sim(5L);
        int[] screen = firstGruntScreen(sim.world());
        Link l = sim.link();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        materialize(sim);
        Enemy target = sim.enemies().stream().filter(e -> e.kind == EnemyKind.GRUNT).findFirst().orElseThrow();

        kill(sim, target);

        // staying on the screen keeps it dead
        int dead = sim.enemies().size() - 1;
        idle(sim, Sim.ENEMY_STEP_INTERVAL * 20);
        assertEquals(dead, sim.enemies().stream().filter(e -> e.alive).count());

        // leaving and returning respawns the placed tiles
        l.sx = screen[0] + 1;
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        assertTrue(sim.enemies().stream().noneMatch(e -> e == target));
        l.sx = screen[0];
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        assertEquals(sim.world().enemies(l.sx, l.sy).size(), sim.enemies().size());
        assertTrue(sim.enemies().stream().allMatch(e -> e.alive));
    }

    @Test
    void deathResetsKilledEnemies() {
        Sim sim = new Sim(5L);
        int[] screen = firstGruntScreen(sim.world());
        Link l = sim.link();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        materialize(sim);
        int placed = sim.enemies().size();
        Enemy target = sim.enemies().stream().filter(e -> e.kind == EnemyKind.GRUNT).findFirst().orElseThrow();
        kill(sim, target);
        assertEquals(placed - 1, sim.enemies().stream().filter(e -> e.alive).count());

        grunt(sim, l.tx, l.ty);
        for (int i = 0; i < 3; i++) {
            enemyStep(sim);
            idle(sim, Sim.I_FRAME_DURATION);
        }
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());

        sim.respawn();
        l.sx = screen[0];
        l.sy = screen[1];
        sim.tick(Sim.ENEMY_SPAWN_DURATION, null);
        assertTrue(sim.enemies().stream().anyMatch(e -> e.alive && e.kind == EnemyKind.GRUNT),
                "non-persistent state: enemy spawns reset on death");
    }

    @Test
    void seedReproducesCombat() {
        Sim a = new Sim(11L);
        Sim b = new Sim(11L);
        int[] screen = firstGruntScreen(a.world());
        for (Sim s : new Sim[] {a, b}) {
            Link l = s.link();
            l.sx = screen[0];
            l.sy = screen[1];
        }
        a.tick(0.01f, null);
        b.tick(0.01f, null);
        for (int i = 0; i < 300; i++) {
            Link.Dir d = new Link.Dir[] {Link.Dir.UP, Link.Dir.RIGHT, Link.Dir.DOWN, Link.Dir.LEFT}[i % 4];
            a.tick(0.02f, d);
            b.tick(0.02f, d);
            if (i % 7 == 0) {
                a.swing();
                b.swing();
            }
        }
        assertEquals(a.link().hearts, b.link().hearts);
        assertEquals(a.enemies().size(), b.enemies().size());
        for (int i = 0; i < a.enemies().size(); i++) {
            assertEquals(a.enemies().get(i).tx, b.enemies().get(i).tx);
            assertEquals(a.enemies().get(i).ty, b.enemies().get(i).ty);
        }
    }
}
