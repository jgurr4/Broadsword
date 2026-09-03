package com.jmgurr.broadsword;

/** Constants; tunables live at the top of their system. */
public final class GameConfig {
    public static final long SEED = 42L;

    // logical game resolution — derived from the screen's tile geometry
    public static final int TILE = 15;
    public static final int LOGICAL_W = 16 * TILE; // 240 px
    public static final int LOGICAL_H = 10 * TILE; // 150 px

    // physical window resolution — independent of the logical resolution;
    // the viewport scales the logical world to fit the window
    public static final int WINDOW_W = 2160;
    public static final int WINDOW_H = 1350;

    // gameplay tuning
    public static final int MAX_HEARTS = 3;
    public static final int MAX_MAGIC = 4;

    private GameConfig() {
    }
}
