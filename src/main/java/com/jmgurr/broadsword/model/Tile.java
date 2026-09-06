package com.jmgurr.broadsword.model;

/** The smallest addressable cell within a screen. */
public enum Tile {
    GRASS(true),
    DIRT(true),
    SAND(true),
    ROCK(false),
    TREE(false),
    TOMBSTONE(false),
    WATER(false),
    /** The dungeon entrance: walkable, and the only tile of its kind in the world. */
    ENTRANCE(true),
    /** A tree the Light spell burns down; everything else treats it as a tree. */
    FLAMMABLE_TREE(false),
    /** Secret stairs revealed by burning the Secret tree; walkable, leads to the Cave. */
    STAIRS(true);

    public final boolean walkable;

    Tile(boolean walkable) {
        this.walkable = walkable;
    }
}
