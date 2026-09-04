package com.jmgurr.broadsword;

import com.jmgurr.broadsword.model.Link;
import com.jmgurr.broadsword.model.Sim;
import com.jmgurr.broadsword.model.Tile;
import com.jmgurr.broadsword.model.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the game through the headless Sim seam: no window, no libgdx. */
class SimTest {
    /** Frame-sized ticks until Link moves, or the budget runs out. */
    private static void step(Sim sim, Link.Dir d) {
        int sx = sim.link().sx, sy = sim.link().sy;
        int tx = sim.link().tx, ty = sim.link().ty;
        for (int i = 0; i < 100; i++) {
            sim.tick(0.01f, d);
            if (sim.link().sx != sx || sim.link().sy != sy || sim.link().tx != tx || sim.link().ty != ty) {
                return;
            }
        }
    }

    @Test
    void spawnAtWorldSpawn() {
        Sim sim = new Sim(1L);
        assertEquals(World.SPAWN_SX, sim.link().sx);
        assertEquals(World.SPAWN_SY, sim.link().sy);
        assertEquals(World.SPAWN_TX, sim.link().tx);
        assertEquals(World.SPAWN_TY, sim.link().ty);
    }

    @Test
    void noInputNoMovement() {
        Sim sim = new Sim(1L);
        for (int i = 0; i < 100; i++) {
            sim.tick(0.016f, null);
        }
        assertEquals(World.SPAWN_TX, sim.link().tx);
        assertEquals(World.SPAWN_TY, sim.link().ty);
    }

    @Test
    void stepsAlongOpenGround() {
        Sim sim = new Sim(1L);
        World w = sim.world();
        // clear a lane east of spawn so obstacles can't interfere
        for (int x = World.SPAWN_TX; x < World.SCREEN_W; x++) {
            w.screen(World.SPAWN_SX, World.SPAWN_SY).set(x, World.SPAWN_TY, Tile.GRASS);
        }
        int tx0 = sim.link().tx;
        step(sim, Link.Dir.RIGHT);
        assertEquals(tx0 + 1, sim.link().tx);
        assertEquals(Link.Dir.RIGHT, sim.link().facing);
    }

    @Test
    void obstacleBlocksStep() {
        Sim sim = new Sim(1L);
        sim.world().screen(World.SPAWN_SX, World.SPAWN_SY).set(World.SPAWN_TX, World.SPAWN_TY - 1, Tile.ROCK);
        step(sim, Link.Dir.UP);
        assertEquals(World.SPAWN_TY, sim.link().ty);
    }

    @Test
    void screenTransitionThroughSim() {
        Sim sim = new Sim(42L);
        World w = sim.world();
        int sx = World.SPAWN_SX, sy = World.SPAWN_SY;
        // clear an eastbound lane across the seam, then walk until the crossing
        for (int x = 0; x < World.SCREEN_W; x++) {
            w.screen(sx, sy).set(x, World.SPAWN_TY, Tile.GRASS);
            w.screen(sx + 1, sy).set(x, World.SPAWN_TY, Tile.GRASS);
        }
        Link l = sim.link();
        for (int i = 0; l.sx == sx && i < 100; i++) {
            step(sim, Link.Dir.RIGHT);
        }
        assertEquals(sx + 1, l.sx);
        assertEquals(0, l.tx);
        assertEquals(World.SPAWN_TY, l.ty);
        // crossing the seam never animates a slide
        assertFalse(sim.interpolating());
    }

    @Test
    void worldBorderBlocksThroughSim() {
        Sim sim = new Sim(2L);
        sim.world().screen(0, 0).set(0, 0, Tile.GRASS);
        Link l = sim.link();
        // teleport to the northwest corner via the public link state
        l.sx = 0;
        l.sy = 0;
        l.tx = 0;
        l.ty = 0;
        step(sim, Link.Dir.UP);
        step(sim, Link.Dir.LEFT);
        assertEquals(0, l.sx);
        assertEquals(0, l.sy);
        assertEquals(0, l.tx);
        assertEquals(0, l.ty);
    }

    @Test
    void interpolationStartsAndCompletes() {
        Sim sim = new Sim(1L);
        World w = sim.world();
        w.screen(World.SPAWN_SX, World.SPAWN_SY).set(World.SPAWN_TX + 1, World.SPAWN_TY, Tile.GRASS);
        step(sim, Link.Dir.RIGHT);
        assertTrue(sim.interpolating());
        assertTrue(sim.interpProgress() > 0 && sim.interpProgress() < 1);
        assertEquals(Link.Dir.RIGHT, sim.interpolatingDir());
        // after a full interval the slide is done
        sim.tick(Sim.STEP_INTERVAL, null);
        assertFalse(sim.interpolating());
        assertEquals(1f, sim.interpProgress(), 0.001f);
    }

    @Test
    void seedReproducesSimulation() {
        Sim a = new Sim(9L);
        Sim b = new Sim(9L);
        Link.Dir[] script = {Link.Dir.UP, Link.Dir.RIGHT, Link.Dir.DOWN, Link.Dir.LEFT};
        for (int i = 0; i < 40; i++) {
            Link.Dir d = script[i % script.length];
            for (int f = 0; f < 20; f++) {
                a.tick(0.01f, d);
                b.tick(0.01f, d);
            }
        }
        assertEquals(a.link().sx, b.link().sx);
        assertEquals(a.link().sy, b.link().sy);
        assertEquals(a.link().tx, b.link().tx);
        assertEquals(a.link().ty, b.link().ty);
    }
}
