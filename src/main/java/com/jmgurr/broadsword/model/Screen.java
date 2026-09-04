package com.jmgurr.broadsword.model;

import java.util.Arrays;

/** One viewport: a 16x10 tile grid that occupies the whole screen. */
public class Screen {
    private final Tile[] tiles = new Tile[World.SCREEN_W * World.SCREEN_H];

    public Screen() {
        Arrays.fill(tiles, Tile.GRASS);
    }

    public Tile get(int x, int y) {
        return tiles[y * World.SCREEN_W + x];
    }

    public void set(int x, int y, Tile t) {
        tiles[y * World.SCREEN_W + x] = t;
    }
}
