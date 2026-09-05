package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T5 rules, driven through the headless Sim seam: Octorock AI (range,
 * retreat, line-of-sight firing, rate), the Fireball projectile, and the V1
 * shield (destroys only while not swinging and facing the source).
 */
class RangedCombatTest {

    // ---- helpers ----------------------------------------------------------

    /** Sim with Link parked at a tile of a cleared 16x10 arena. */
    private static Sim arena(Sim sim, int tx, int ty) {
        World w = sim.world();
        int sx = World.SPAWN_SX, sy = World.SPAWN_SY;
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
        sim.projectiles().clear();
        return sim;
    }

    private static Enemy octorock(Sim sim, int tx, int ty) {
        Enemy e = new Enemy(EnemyKind.OCTOROCK, tx, ty, 99L);
        sim.enemies().add(e);
        return e;
    }

    /** Hand-place a Fireball one tile from Link, travelling toward him. */
    private static Projectile fireballAt(Sim sim, int tx, int ty, int dx, int dy) {
        Projectile p = new Projectile(tx, ty, dx, dy);
        sim.projectiles().add(p);
        return p;
    }

    /** Advance exactly one enemy step (and the projectile clock). */
    private static void enemyStep(Sim sim) {
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
    }

    /** Advance exactly one projectile step. */
    private static void projectileStep(Sim sim) {
        sim.tick(Sim.PROJECTILE_STEP_INTERVAL, null);
    }

    private static int liveProjectiles(Sim sim) {
        return (int) sim.projectiles().stream().filter(p -> p.alive).count();
    }

    // ---- octorock: hp, range, retreat -------------------------------------

    @Test
    void octorockDiesInTwoSwordHits() {
        Sim sim = arena(new Sim(1L), 5, 5);
        Link l = sim.link();
        l.facing = Link.Dir.RIGHT;
        Enemy o = octorock(sim, l.tx + 1, l.ty);
        assertEquals(Sim.OCTOROCK_HP, o.hp);

        sim.swing();
        assertTrue(o.alive, "one hit is not enough");
        idle(sim, Sim.SWORD_COOLDOWN);
        l.tx = o.tx - 1;
        l.ty = o.ty;
        sim.swing();
        assertFalse(o.alive);
    }

    @Test
    void octorockClosesWhenTooFar() {
        Sim sim = arena(new Sim(1L), 2, 5);
        Enemy o = octorock(sim, 14, 5); // 12 tiles away

        enemyStep(sim);

        assertEquals(13, o.tx, "closes one tile while beyond the hold range");
    }

    @Test
    void octorockHoldsAtPreferredRange() {
        Sim sim = arena(new Sim(1L), 2, 5);
        Enemy o = octorock(sim, 2 + Sim.OCTOROCK_HOLD_TILES, 5); // exactly at range
        sim.link().facing = Link.Dir.UP; // no shot interference bookkeeping needed

        enemyStep(sim);

        assertEquals(2 + Sim.OCTOROCK_HOLD_TILES, o.tx, "holds at the preferred range");
    }

    @Test
    void octorockRetreatsWhenCornered() {
        Sim sim = arena(new Sim(1L), 5, 5);
        Enemy o = octorock(sim, 5, 5 + Sim.OCTOROCK_RETREAT_TILES); // 3 tiles away, same column

        enemyStep(sim);

        assertEquals(5 + Sim.OCTOROCK_RETREAT_TILES + 1, o.ty, "backs away one tile at <=3 tiles");
    }

    @Test
    void retreatFallsBackToTheOpenAxis()
    {
        Sim sim = arena(new Sim(1L), 5, 5);
        Enemy o = octorock(sim, 6, 7); // below-right of Link: |dy| > |dx|, retreat prefers y (down)
        sim.world().screen(World.SPAWN_SX, World.SPAWN_SY).set(6, 8, Tile.ROCK); // straight retreat blocked

        enemyStep(sim);

        assertEquals(7, o.tx, "blocked retreat falls back to the x axis, still away from Link");
        assertEquals(7, o.ty);
    }

    // ---- octorock: firing ---------------------------------------------------

    @Test
    void octorockNeverFiresWithoutLineOfSight() {
        Sim sim = arena(new Sim(1L), 5, 5);
        // diagonal: no cardinal lane. Even at close range, nothing fires.
        Enemy o = octorock(sim, 7, 7);

        for (int i = 0; i < 40; i++) {
            enemyStep(sim);
            o.stunned = false;
        }

        assertEquals(0, sim.projectiles().size());
    }

    @Test
    void octorockNeverFiresThroughAnObstacle() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.world().screen(World.SPAWN_SX, World.SPAWN_SY).set(8, 5, Tile.ROCK);
        Enemy o = octorock(sim, 10, 5); // same row, but a rock sits in the lane

        for (int i = 0; i < 20; i++) {
            sim.link().hearts = World.MAX_HEARTS;
            enemyStep(sim); // the octorock may close to the rock, never past it
        }

        assertEquals(0, sim.projectiles().size(), "cover is real: no firing through an obstacle");
    }

    @Test
    void octorockFiresAlongAClearLaneOneTilePerStep() {
        Sim sim = arena(new Sim(1L), 2, 5);
        sim.link().facing = Link.Dir.UP; // don't block; the shot will travel
        Enemy o = octorock(sim, 2 + Sim.OCTOROCK_HOLD_TILES, 5); // 5 tiles, clear lane

        enemyStep(sim);

        assertEquals(1, liveProjectiles(sim), "fires with clear line of sight");
        Projectile p = sim.projectiles().get(0);
        assertEquals(-1, p.dx, "aims at Link down the lane, no lead");
        assertEquals(0, p.dy);
        assertEquals(5, p.ty);

        int tx = p.tx;
        projectileStep(sim);
        assertEquals(tx - 1, p.tx, "one tile per projectile step");
    }

    @Test
    void fireRateIsAboutTheFireInterval() {
        Sim sim = arena(new Sim(1L), 2, 5);
        sim.link().facing = Link.Dir.LEFT; // block incoming shots quietly
        Enemy o = octorock(sim, 2 + Sim.OCTOROCK_HOLD_TILES, 5);

        enemyStep(sim); // first shot: fireTimer was 0
        assertEquals(1, liveProjectiles(sim));

        for (int i = 0; i < 5; i++) { // 5 * 0.25s < 1.5s: still cooling down
            enemyStep(sim);
            assertEquals(0, liveProjectiles(sim), "no second shot inside the interval");
        }
        assertTrue(o.fireTimer > 0, "fire rate clock still running out");

        enemyStep(sim); // ~1.5s after the first shot
        assertEquals(1, liveProjectiles(sim), "fires again once the interval has passed");
    }

    // ---- fireball: damage ----------------------------------------------------

    @Test
    void fireballDealsOneHeartOnContact() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.link().facing = Link.Dir.UP; // no shield: shot arrives from the left
        fireballAt(sim, 4, 5, 1, 0);

        projectileStep(sim); // onto Link's tile

        assertEquals(2, sim.link().hearts);
        assertEquals(0, liveProjectiles(sim), "the fireball dies on impact");
    }

    @Test
    void fireballDiesOnObstacleAndScreenEdge() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.world().screen(World.SPAWN_SX, World.SPAWN_SY).set(7, 5, Tile.ROCK);
        Projectile rock = fireballAt(sim, 6, 5, 1, 0);
        Projectile edge = fireballAt(sim, 0, 4, -1, 0);

        projectileStep(sim);

        assertFalse(rock.alive, "absorbed by the obstacle");
        assertFalse(edge.alive, "leaves the screen");
        assertEquals(3, sim.link().hearts, "neither touched Link");
    }

    // ---- shield: facing matrix -----------------------------------------------

    @Test
    void shieldDestroysFireballWhenFacingAndNotSwinging() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.link().facing = Link.Dir.LEFT; // shot comes from the left
        Projectile p = fireballAt(sim, 4, 5, 1, 0);

        projectileStep(sim);

        assertEquals(3, sim.link().hearts, "blocked");
        assertFalse(p.alive, "destroyed on impact, no reflect: nothing bounces back");
        assertEquals(0, liveProjectiles(sim));
    }

    @Test
    void swingingShieldDoesNotStopAFacingFireball() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.link().facing = Link.Dir.LEFT;
        sim.swing(); // blade out: shield down
        Projectile p = fireballAt(sim, 4, 5, 1, 0);
        assertTrue(sim.swinging());

        projectileStep(sim);

        assertEquals(2, sim.link().hearts, "swing at the wrong moment: Link takes the hit");
        assertFalse(p.alive);
    }

    @Test
    void shieldFailsWhenNotFacingTheSource() {
        for (Link.Dir facing : new Link.Dir[] {Link.Dir.UP, Link.Dir.DOWN, Link.Dir.RIGHT}) {
            Sim sim = arena(new Sim(1L), 5, 5);
            sim.link().facing = facing; // shot arrives from the left; Link faces elsewhere
            Projectile p = fireballAt(sim, 4, 5, 1, 0);

            projectileStep(sim);

            assertEquals(2, sim.link().hearts, facing + ": side-arriving projectiles still hit");
            assertFalse(p.alive);
        }
    }

    @Test
    void fireballsFollowTheIFrameRules() {
        Sim sim = arena(new Sim(1L), 5, 5);
        sim.link().facing = Link.Dir.UP;
        fireballAt(sim, 4, 5, 1, 0);
        projectileStep(sim);
        assertEquals(2, sim.link().hearts);

        fireballAt(sim, 4, 5, 1, 0); // immediately after, still i-framed
        projectileStep(sim);
        assertEquals(2, sim.link().hearts, "i-frames absorb the second hit");

        sim.tick(Sim.I_FRAME_DURATION, null);
        fireballAt(sim, 4, 5, 1, 0);
        projectileStep(sim);
        assertEquals(1, sim.link().hearts, "after i-frames expire, the next hit lands");
    }

    // ---- generator roster -----------------------------------------------------

    @Test
    void octorocksAppearInTier2PlusOnly() {
        boolean seen = false;
        for (long seed : new long[] {1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L}) {
            World w = WorldGenerator.generate(seed);
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    for (EnemySpawn e : w.enemies(sx, sy)) {
                        if (e.kind() == EnemyKind.OCTOROCK) {
                            assertTrue(w.tier(sx, sy) >= 2,
                                    "seed " + seed + ": Octorock in tier " + w.tier(sx, sy));
                            seen = true;
                        }
                    }
                }
            }
        }
        assertTrue(seen, "tier-2+ rosters actually contain Octorocks");
    }

    // ---- determinism ----------------------------------------------------------

    @Test
    void seedReproducesRangedCombat() {
        Sim a = new Sim(21L);
        Sim b = new Sim(21L);
        for (Sim s : new Sim[] {a, b}) {
            arena(s, 2, 5);
            s.link().facing = Link.Dir.UP;
            octorock(s, 12, 5);
        }
        for (int i = 0; i < 100; i++) {
            a.tick(0.05f, null);
            b.tick(0.05f, null);
        }
        assertEquals(a.link().hearts, b.link().hearts);
        assertEquals(a.projectiles().size(), b.projectiles().size());
        for (int i = 0; i < a.projectiles().size(); i++) {
            assertEquals(a.projectiles().get(i).tx, b.projectiles().get(i).tx);
            assertEquals(a.projectiles().get(i).ty, b.projectiles().get(i).ty);
        }
    }

    private static void idle(Sim sim, float seconds) {
        sim.tick(seconds, null);
    }
}
