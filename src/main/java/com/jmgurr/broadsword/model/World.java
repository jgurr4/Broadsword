package com.jmgurr.broadsword.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The overworld: a 40x15 grid of screens (600 screens), fully derived from one seed. */
public class World implements Terrain {
    public static final int WORLD_W = 40;
    public static final int WORLD_H = 15;
    public static final int SCREEN_W = 16;
    public static final int SCREEN_H = 10;

    /** Link's starting (and maximum, in V1) Hearts. */
    public static final int MAX_HEARTS = 3;

    /** Light casts per run. Nothing refills Magic in V1. */
    public static final int MAX_MAGIC = 4;

    /** Cave entry tile (below it sits the exit stairs, in the wall). */
    public static final int CAVE_ENTRY_TX = 8, CAVE_ENTRY_TY = 1;

    /** Rock-formation cave room: a 4x3 interior, Link enters at ENTRY, exit stairs one step south. */
    public static final int ROCK_CAVE_X0 = 6, ROCK_CAVE_X1 = 9, ROCK_CAVE_Y0 = 3, ROCK_CAVE_Y1 = 5;
    public static final int ROCK_CAVE_ENTRY_TX = 8, ROCK_CAVE_ENTRY_TY = 3;

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
    private final ScreenPos secretTree;
    private final Map<Landmark, ScreenPos> landmarks;
    private final List<EnemySpawn>[] enemiesByScreen;
    /** All caves of this world, keyed by the packed global tile of their overworld entrance. */
    private final Map<Integer, Cave> caves;

    @SuppressWarnings("unchecked")
    World(long seed, long usedSeed, int attempts, Screen[][] screens, Archetype[][] archetypes, int[][] tiers,
            ScreenPos entrance, Map<Landmark, ScreenPos> landmarks, List<EnemySpawn>[] enemiesByScreen,
            ScreenPos secretTree, Map<Integer, Cave> caves) {
        this.seed = seed;
        this.usedSeed = usedSeed;
        this.attempts = attempts;
        this.screens = screens;
        this.archetypes = archetypes;
        this.tiers = tiers;
        this.entrance = entrance;
        this.secretTree = secretTree;
        this.landmarks = Collections.unmodifiableMap(new EnumMap<>(landmarks));
        this.enemiesByScreen = enemiesByScreen;
        this.caves = caves;
        // The Old woman's Cave sits behind the Secret tree in every world; its entrance
        // tile only becomes walkable when the tree burns.
        if (secretTree != null && secretTree.sx() >= 0) {
            caves.putIfAbsent(packCave(secretTree.sx(), secretTree.sy(), secretTree.tx(), secretTree.ty()),
                    new Cave(secretTree, buildCave(), CAVE_ENTRY_TX, CAVE_ENTRY_TY, null));
        }
    }

    /** The Old woman's Cave: a walled off-grid room with the return stairs in the south wall. */
    private static Screen buildCave() {
        Screen s = new Screen();
        for (int x = 0; x < World.SCREEN_W; x++) {
            s.set(x, 0, Tile.ROCK);
            s.set(x, World.SCREEN_H - 1, Tile.ROCK);
        }
        for (int y = 0; y < World.SCREEN_H; y++) {
            s.set(0, y, Tile.ROCK);
            s.set(World.SCREEN_W - 1, y, Tile.ROCK);
        }
        s.set(World.CAVE_ENTRY_TX, World.SCREEN_H - 1, Tile.STAIRS);
        return s;
    }

    /** A rock-formation cave room: solid rock with a 4x3 grass interior and exit stairs inside it. */
    static Screen buildRockCaveRoom() {
        Screen s = new Screen();
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                s.set(x, y, Tile.ROCK);
            }
        }
        for (int y = ROCK_CAVE_Y0; y <= ROCK_CAVE_Y1; y++) {
            for (int x = ROCK_CAVE_X0; x <= ROCK_CAVE_X1; x++) {
                s.set(x, y, Tile.GRASS);
            }
        }
        s.set(ROCK_CAVE_ENTRY_TX, ROCK_CAVE_ENTRY_TY + 1, Tile.STAIRS);
        return s;
    }

    /** Key for the caves map: the packed global tile of the cave's overworld entrance. */
    public static int packCave(int sx, int sy, int tx, int ty) {
        return ((sy * WORLD_W + sx) * SCREEN_W + tx) * SCREEN_H + ty;
    }

    public static int caveKeySx(int key) {
        return (key / (SCREEN_W * SCREEN_H)) % WORLD_W;
    }

    public static int caveKeySy(int key) {
        return key / (SCREEN_W * SCREEN_H * WORLD_W);
    }

    public static int caveKeyTx(int key) {
        return (key / SCREEN_H) % SCREEN_W;
    }

    public static int caveKeyTy(int key) {
        return key % SCREEN_H;
    }

    /** The cave whose overworld entrance is this tile, or null. */
    public Cave caveAt(int sx, int sy, int tx, int ty) {
        return caves.get(packCave(sx, sy, tx, ty));
    }

    /** The caves map (live; the generator fills it before validation). */
    public Map<Integer, Cave> caves() {
        return caves;
    }

    /** The Old woman's Cave screen (same layout in every world; holds nothing in V1). */
    public Screen cave() {
        if (secretTree == null || secretTree.sx() < 0) {
            return null;
        }
        Cave c = caves.get(packCave(secretTree.sx(), secretTree.sy(), secretTree.tx(), secretTree.ty()));
        return c == null ? null : c.room();
    }

    /** The Cave walked as terrain (the stairs tile is walkable, the rock is not). */
    public Terrain caveTerrain() {
        Screen room = cave();
        return (sx, sy, tx, ty) -> tx >= 0 && tx < SCREEN_W && ty >= 0 && ty < SCREEN_H
                && room.get(tx, ty).walkable;
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

    @Override
    public boolean walkable(int sx, int sy, int tx, int ty) {
        if (!inWorld(sx, sy) || tx < 0 || tx >= SCREEN_W || ty < 0 || ty >= SCREEN_H) {
            return false;
        }
        return screens[sx][sy].get(tx, ty).walkable;
    }

    /** The one Secret tree's tile (once burned, this tile holds the Secret stairs). */
    public ScreenPos secretTree() {
        return secretTree;
    }

    public boolean isSecretTree(int sx, int sy, int tx, int ty) {
        return secretTree.sx() == sx && secretTree.sy() == sy && secretTree.tx() == tx && secretTree.ty() == ty;
    }

    /** Turn the Secret tree's tile into the Secret stairs (the tree is already gone). */
    public void revealSecretStairs() {
        screens[secretTree.sx()][secretTree.sy()].set(secretTree.tx(), secretTree.ty(), Tile.STAIRS);
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
