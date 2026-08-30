package com.jmgurr.broadsword.model;

import java.util.Random;

/** The overworld: a 40x10 grid of screens (400 screens). */
public class World {
    public static final int WORLD_W = 40;
    public static final int WORLD_H = 10;
    public static final int SCREEN_W = 16;
    public static final int SCREEN_H = 10;

    public static final int SPAWN_SX = 20;
    public static final int SPAWN_SY = 5;
    public static final int SPAWN_TX = 8;
    public static final int SPAWN_TY = 5;

    private final Screen[][] screens = new Screen[WORLD_W][WORLD_H];

    public World(long seed) {
        Random rng = new Random(seed);
        for (int y = 0; y < WORLD_H; y++) {
            for (int x = 0; x < WORLD_W; x++) {
                screens[x][y] = new Screen(rng);
            }
        }
        clearSpawn();
    }

    /** Spawn area must be walkable no matter what the generator scattered. */
    private void clearSpawn() {
        Screen s = screens[SPAWN_SX][SPAWN_SY];
        for (int y = SPAWN_TY - 1; y <= SPAWN_TY + 1; y++) {
            for (int x = SPAWN_TX - 1; x <= SPAWN_TX + 1; x++) {
                s.set(x, y, Tile.GRASS);
            }
        }
    }

    public boolean inBounds(int sx, int sy) {
        return sx >= 0 && sx < WORLD_W && sy >= 0 && sy < WORLD_H;
    }

    public Screen screen(int sx, int sy) {
        return screens[sx][sy];
    }

    public boolean walkable(int sx, int sy, int tx, int ty) {
        if (!inBounds(sx, sy) || tx < 0 || tx >= SCREEN_W || ty < 0 || ty >= SCREEN_H) {
            return false;
        }
        return screens[sx][sy].get(tx, ty).walkable;
    }
}
