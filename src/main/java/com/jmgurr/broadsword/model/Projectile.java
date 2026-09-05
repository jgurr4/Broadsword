package com.jmgurr.broadsword.model;

/**
 * A fast object fired by an enemy at Link. Travels tile-to-tile along one
 * cardinal direction on the projectile clock, screen-local always. Dies on
 * impact with Link (blocked or not), an obstacle, or the screen edge.
 */
public final class Projectile {
    public int tx, ty;
    /** Cardinal travel direction, one tile per projectile step. */
    public final int dx, dy;
    public boolean alive = true;

    public Projectile(int tx, int ty, int dx, int dy) {
        this.tx = tx;
        this.ty = ty;
        this.dx = dx;
        this.dy = dy;
    }
}
