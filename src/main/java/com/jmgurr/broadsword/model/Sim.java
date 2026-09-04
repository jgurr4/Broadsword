package com.jmgurr.broadsword.model;

/**
 * The headless, tick-driven simulation. Takes (seed, input) and advances game
 * state on tick; the render layer only polls input and draws this state.
 * No libgdx types may be used here.
 */
public final class Sim {
    public static final float STEP_INTERVAL = 0.12f; // ~8 tiles/sec

    private final World world;
    private final Link link;

    private float stepTimer = 0;
    private boolean interpolating = false;
    private float interpProgress = 1;
    private Link.Dir interpolatingDir = null; // dir of the in-flight step

    public Sim(long seed) {
        this.world = WorldGenerator.generate(seed);
        this.link = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
    }

    /**
     * Advance the simulation by delta seconds, applying the desired direction
     * (null for no input). A step is attempted once the step interval has
     * elapsed and no slide animation is in flight.
     */
    public void tick(float delta, Link.Dir desired) {
        if (desired != null && !interpolating && stepTimer >= STEP_INTERVAL) {
            int psx = link.sx, psy = link.sy;
            if (link.step(world, desired)) {
                if (link.sx != psx || link.sy != psy) {
                    // crossed a screen edge: no slide animation across the seam
                    interpolating = false;
                } else {
                    interpolating = true;
                    interpolatingDir = desired;
                    interpProgress = 0;
                }
                stepTimer = 0;
            }
        }
        stepTimer += delta;
        if (interpolating) {
            interpProgress = Math.min(1, interpProgress + delta / STEP_INTERVAL);
            if (interpProgress >= 1) {
                interpolating = false;
            }
        }
    }

    public World world() {
        return world;
    }

    public Link link() {
        return link;
    }

    /** True while a within-screen slide animation is in flight. */
    public boolean interpolating() {
        return interpolating;
    }

    /** Progress of the in-flight step, 0..1. */
    public float interpProgress() {
        return interpProgress;
    }

    /** Direction of the in-flight step, or null when none. */
    public Link.Dir interpolatingDir() {
        return interpolatingDir;
    }
}
