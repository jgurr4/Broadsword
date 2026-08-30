package com.jmgurr.broadsword;

/** Constants; tunables live at the top of their system. */
public final class GameConfig {
    public static final long SEED = 42L;
    public static final int TILE = 15;
    public static final int SCALE = 3;
    public static final int VIEW_W = 16 * TILE; // 240 logical px
    public static final int VIEW_H = 10 * TILE; // 150 logical px
    public static final float STEP_INTERVAL = 0.12f; // ~8 tiles/sec
    public static final float LINK_SPEED = 10f; // tiles/sec, render interpolation
    public static final int MIN_MAGIC = 4;

    private GameConfig() {
    }
}
