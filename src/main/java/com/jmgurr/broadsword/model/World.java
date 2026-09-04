package com.jmgurr.broadsword.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The overworld: a 40x10 grid of screens (400 screens), fully derived from one seed. */
public class World {
    public static final int WORLD_W = 40;
    public static final int WORLD_H = 10;
    public static final int SCREEN_W = 16;
    public static final int SCREEN_H = 10;

    public static final int SPAWN_SX = 20;
    public static final int SPAWN_SY = 5;
    public static final int SPAWN_TX = 8;
    public static final int SPAWN_TY = 5;

    /** The shared E-W lane every screen carries; also the guaranteed door row. */
    public static final int LANE_Y = 5;
    /** The shared N-S lane every screen carries (rivers may cross it). */
    public static final int LANE_X = 8;

    private final long seed;
    private final long usedSeed;
    private final int attempts;
    private final Screen[][] screens;
    private final Archetype[][] archetypes;
    private final int[][] tiers;
    private final ScreenPos entrance;
    private final Map<Landmark, ScreenPos> landmarks;
    private final List<EnemySpawn>[] enemiesByScreen;

    @SuppressWarnings("unchecked")
    World(long seed, long usedSeed, int attempts, Screen[][] screens, Archetype[][] archetypes, int[][] tiers,
            ScreenPos entrance, Map<Landmark, ScreenPos> landmarks, List<EnemySpawn>[] enemiesByScreen) {
        this.seed = seed;
        this.usedSeed = usedSeed;
        this.attempts = attempts;
        this.screens = screens;
        this.archetypes = archetypes;
        this.tiers = tiers;
        this.entrance = entrance;
        this.landmarks = Collections.unmodifiableMap(new EnumMap<>(landmarks));
        this.enemiesByScreen = enemiesByScreen;
    }

    /** The seed the player entered (or the run's random seed). */
    public long seed() {
        return seed;
    }

    /** The seed actually used: seed + the number of failed validation attempts. */
    public long usedSeed() {
        return usedSeed;
    }

    /** How many regeneration attempts the generator needed (0 = first try was valid). */
    public int generationAttempts() {
        return attempts;
    }

    /** A fresh random seed for a new run (non-negative, so it is typeable at the title screen). */
    public static long randomSeed() {
        return new Random().nextLong() >>> 1;
    }

    public static boolean inWorld(int sx, int sy) {
        return sx >= 0 && sx < WORLD_W && sy >= 0 && sy < WORLD_H;
    }

    public boolean inBounds(int sx, int sy) {
        return inWorld(sx, sy);
    }

    public Screen screen(int sx, int sy) {
        return screens[sx][sy];
    }

    public boolean walkable(int sx, int sy, int tx, int ty) {
        if (!inWorld(sx, sy) || tx < 0 || tx >= SCREEN_W || ty < 0 || ty >= SCREEN_H) {
            return false;
        }
        return screens[sx][sy].get(tx, ty).walkable;
    }

    /** The screen's terrain archetype. */
    public Archetype archetype(int sx, int sy) {
        return archetypes[sx][sy];
    }

    /** Difficulty tier, 1-4, rolled from the screen's distance from spawn. */
    public int tier(int sx, int sy) {
        return tiers[sx][sy];
    }

    /** The single dungeon entrance tile. Entering it does nothing until the dungeon lands. */
    public ScreenPos dungeonEntrance() {
        return entrance;
    }

    public boolean isEntrance(int sx, int sy, int tx, int ty) {
        return entrance.sx() == sx && entrance.sy() == sy && entrance.tx() == tx && entrance.ty() == ty;
    }

    /** Anchor (north-west) screen of each landmark's 2x2 area. */
    public Map<Landmark, ScreenPos> landmarks() {
        return landmarks;
    }

    /** The landmark occupying this screen, or null. */
    public Landmark landmarkAt(int sx, int sy) {
        for (Map.Entry<Landmark, ScreenPos> e : landmarks.entrySet()) {
            ScreenPos a = e.getValue();
            if (sx >= a.sx() && sx < a.sx() + 2 && sy >= a.sy() && sy < a.sy() + 2) {
                return e.getKey();
            }
        }
        return null;
    }

    public boolean isCemetery(int sx, int sy) {
        return landmarkAt(sx, sy) == Landmark.CEMETERY;
    }

    /** Enemies the generator placed on a screen; empty for safe screens. */
    public List<EnemySpawn> enemies(int sx, int sy) {
        List<EnemySpawn> l = enemiesByScreen[sy * WORLD_W + sx];
        return l == null ? List.of() : Collections.unmodifiableList(l);
    }

    /** Manhattan distance in screens from the spawn screen. */
    public static int distanceFromSpawn(int sx, int sy) {
        return Math.abs(sx - SPAWN_SX) + Math.abs(sy - SPAWN_SY);
    }
}
