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
    ENTRANCE(true);

    public final boolean walkable;

    Tile(boolean walkable) {
        this.walkable = walkable;
    }
}
