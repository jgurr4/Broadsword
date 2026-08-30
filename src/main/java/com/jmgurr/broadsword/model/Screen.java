package com.jmgurr.broadsword.model;

import java.util.Random;

/** One viewport: a 16x10 tile grid that occupies the whole screen. */
public class Screen {
    private final Tile[] tiles = new Tile[World.SCREEN_W * World.SCREEN_H];

    /** Step 1 placeholder generation: grass with scattered obstacles. */
    public Screen(Random rng) {
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = Tile.GRASS;
        }
        int obstacles = 6 + rng.nextInt(7); // 6-12
        for (int i = 0; i < obstacles; i++) {
            int x = rng.nextInt(World.SCREEN_W);
            int y = rng.nextInt(World.SCREEN_H);
            Tile t = switch (rng.nextInt(3)) {
                case 0 -> Tile.ROCK;
                case 1 -> Tile.TREE;
                default -> Tile.WATER;
            };
            tiles[y * World.SCREEN_W + x] = t;
        }
    }

    public Tile get(int x, int y) {
        return tiles[y * World.SCREEN_W + x];
    }

    public void set(int x, int y, Tile t) {
        tiles[y * World.SCREEN_W + x] = t;
    }
}
