package com.jmgurr.broadsword.model;

/** The smallest addressable cell within a screen. */
public enum Tile {
    GRASS(true),
    ROCK(false),
    TREE(false),
    WATER(false);

    public final boolean walkable;

    Tile(boolean walkable) {
        this.walkable = walkable;
    }
}
