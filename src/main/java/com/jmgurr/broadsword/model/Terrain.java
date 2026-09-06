package com.jmgurr.broadsword.model;

/**
 * Anything Link's steps are validated against: the overworld, or one off-grid
 * Cave. Coordinates are (screen, tile); a single-screen terrain ignores the
 * screen.
 */
public interface Terrain {
    boolean walkable(int sx, int sy, int tx, int ty);
}
