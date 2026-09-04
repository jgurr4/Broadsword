package com.jmgurr.broadsword.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Generates the overworld as a pure function of a seed: the archetype map,
 * tier bands by distance from spawn, rivers and shorelines that continue
 * across screen borders, the four fixed-offset landmarks, one dungeon
 * entrance, and tier-scaled enemy placements.
 *
 * <p>Connectivity is constructed rather than hoped for. Every screen keeps a
 * walkable plus-shaped lane (row {@link World#LANE_Y}, column
 * {@link World#LANE_X}) that reaches all four of its edges, and every tile
 * write goes through {@link #put}, which refuses to dam a lane tile with an
 * obstacle. So each screen's four edge midpoints are walkable and mutually
 * connected, and screen-to-screen movement links the whole grid. The
 * validation pass still checks the tiles it is given, and a world that fails
 * regenerates from seed+1, bounded by {@link #MAX_ATTEMPTS}.
 *
 * <p>All generation tunables live at the top of this class.
 */
public final class WorldGenerator {

    // --- tunables -------------------------------------------------------------

    /** Archetype draw weights for interior screens (spec ticket #4). */
    private static final Map<Archetype, Integer> WEIGHT = Map.of(
            Archetype.MEADOW, 22,
            Archetype.FOREST, 22,
            Archetype.LAKE, 10,
            Archetype.ROCKFIELD, 12,
            Archetype.PATH, 10,
            Archetype.CLEARING, 8,
            Archetype.MOUNTAIN, 6);
    /** Chance an interior screen column carries the river; dry gap between rivers. */
    private static final double RIVER_COL_CHANCE = 0.06;
    private static final int RIVER_GAP = 3;
    /** Screen-local west column of the channel, and its width. Every river screen agrees. */
    private static final int RIVER_STRIP_X = 4, RIVER_STRIP_MIN = 2, RIVER_STRIP_MAX = 3;
    /** Chance a river screen gets a second crossing besides the lane bridge. */
    private static final double RIVER_EXTRA_CROSSING = 0.5;
    /** Shore ocean strip depth, in tiles. One roll per world, so the coast is continuous. */
    private static final int OCEAN_DEPTH_MIN = 2, OCEAN_DEPTH_MAX = 3;
    /** Trees: meadow 0-2, forest 10-18, shore 0-2. */
    private static final int MEADOW_TREES_MAX = 2, FOREST_TREES_MIN = 10, FOREST_TREES_MAX = 18,
            SHORE_TREES_MAX = 2;
    /** Rockfield 6-12 rocks; mountain 10-15 boulders plus 1-2 edge walls of <=4. */
    private static final int ROCKFIELD_MIN = 6, ROCKFIELD_MAX = 12;
    private static final int MOUNTAIN_BOULDERS_MIN = 10, MOUNTAIN_BOULDERS_MAX = 15;
    private static final int MOUNTAIN_WALLS_MIN = 1, MOUNTAIN_WALLS_MAX = 2, MOUNTAIN_WALL_LEN_MAX = 4;
    /** Lake blob size, in tiles. */
    private static final int LAKE_MIN = 5, LAKE_MAX = 8;
    /** Path corridor width. */
    private static final int PATH_WIDTH = 4;
    /** Max validation failures before generation gives up. */
    static final int MAX_ATTEMPTS = 100;

    /** Manhattan band upper bound, 60% tier, 40% tier. */
    private static final int[][] TIER_BANDS = { { 5, 1, 1 }, { 10, 2, 1 }, { 15, 3, 2 },
            { Integer.MAX_VALUE, 4, 3 } };
    /** Uniform enemy count range per tier (index = tier - 1). */
    private static final int[][] TIER_COUNT = { { 0, 2 }, { 0, 3 }, { 1, 3 }, { 1, 4 } };
    /** Fixed landmark offsets from the dungeon entrance, in screens. */
    private static final Map<Landmark, int[]> LANDMARK_OFFSET = offsets();

    private static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
    private static final int[] DDX = { 0, 1, 0, -1 };
    private static final int[] DDY = { -1, 0, 1, 0 };

    private WorldGenerator() {
    }

    private static Map<Landmark, int[]> offsets() {
        Map<Landmark, int[]> m = new EnumMap<>(Landmark.class);
        m.put(Landmark.BIG_LAKE, new int[] { -5, 2 });
        m.put(Landmark.ROCK_MOUNTAIN, new int[] { 6, -3 });
        m.put(Landmark.RUINED_SHRINE, new int[] { 3, 4 });
        m.put(Landmark.CEMETERY, new int[] { -8, 3 });
        return m;
    }

    /** Generate the world for a seed; on a validation failure, retry with seed+1, bounded. */
    public static World generate(long seed) {
        return generate(seed, WorldGenerator::valid);
    }

    /** Generation with the post-condition injected, so the retry loop is testable. */
    public static World generate(long seed, Predicate<World> validator) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            World w = build(seed, seed + attempt, attempt, new Random(seed + attempt));
            if (validator.test(w)) {
                return w;
            }
        }
        throw new IllegalStateException("no valid world for seed " + seed + " in " + MAX_ATTEMPTS + " attempts");
    }

    // --- passes ---------------------------------------------------------------

    private static World build(long seed, long usedSeed, int attempt, Random rng) {
        int[] riverStrip = riverStrips(rng); // 0 on dry columns, 2-3 where the river runs
        int oceanDepth = range(rng, OCEAN_DEPTH_MIN, OCEAN_DEPTH_MAX);
        Archetype[][] archetypes = drawArchetypes(rng, riverStrip);
        int[][] tiers = rollTiers(rng);

        ScreenPos entrance = rollEntrance(rng);
        Map<Landmark, ScreenPos> landmarks = new EnumMap<>(Landmark.class);
        if (entrance != null) {
            for (Map.Entry<Landmark, int[]> e : LANDMARK_OFFSET.entrySet()) {
                landmarks.put(e.getKey(), new ScreenPos(entrance.sx() + e.getValue()[0],
                        entrance.sy() + e.getValue()[1], 0, 0));
            }
        }

        Screen[][] screens = new Screen[World.WORLD_W][World.WORLD_H];
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                screens[sx][sy] = render(rng, sx, sy, archetypes[sx][sy], riverStrip[sx], oceanDepth, landmarks);
            }
        }
        repairBoundaries(screens);
        if (entrance != null) {
            screens[entrance.sx()][entrance.sy()].set(entrance.tx(), entrance.ty(), Tile.ENTRANCE);
        }

        @SuppressWarnings("unchecked")
        List<EnemySpawn>[] enemies = new List[World.WORLD_W * World.WORLD_H];
        if (entrance != null) {
            placeEnemies(rng, screens, tiers, landmarks, entrance, enemies);
        }
        return new World(seed, usedSeed, attempt, screens, archetypes, tiers,
                entrance == null ? new ScreenPos(-1, -1, -1, -1) : entrance, landmarks, enemies);
    }

    /**
     * The river is a full-height channel: a river screen column puts water on
     * the same screen-local columns of every screen in that column, so the
     * channel continues across every border it crosses and drains into the sea
     * at the north/south world edge. Rivers are at least RIVER_GAP columns
     * apart, so no river screen has a river directly east or west of it.
     *
     * <p>ponytail: channels run straight north-south only; add meandering
     * columns if straight rivers read too artificial.
     */
    private static int[] riverStrips(Random rng) {
        int[] strip = new int[World.WORLD_W];
        for (int x = 1; x < World.WORLD_W - 1; x++) {
            if (rng.nextDouble() < RIVER_COL_CHANCE) {
                strip[x] = range(rng, RIVER_STRIP_MIN, RIVER_STRIP_MAX);
                x += RIVER_GAP - 1;
            }
        }
        return strip;
    }

    /**
     * The archetype map. The border ring is Shore (the world is an island, so
     * the coastline wraps every corner for free), river columns are River,
     * everything else is a weighted draw.
     */
    private static Archetype[][] drawArchetypes(Random rng, int[] riverStrip) {
        Archetype[][] a = new Archetype[World.WORLD_W][World.WORLD_H];
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                if (isBorder(sx, sy)) {
                    a[sx][sy] = Archetype.SHORE;
                } else if (riverStrip[sx] > 0) {
                    a[sx][sy] = Archetype.RIVER;
                } else {
                    a[sx][sy] = draw(rng, Set.of());
                }
            }
        }
        repairNeighbourConstraint(a, rng);
        return a;
    }

    /** Weighted archetype draw; Shore and River are structural, never drawn. */
    private static Archetype draw(Random rng, Set<Archetype> extraExcluded) {
        Set<Archetype> excluded = EnumSet.of(Archetype.SHORE, Archetype.RIVER);
        excluded.addAll(extraExcluded);
        int total = 0;
        for (Map.Entry<Archetype, Integer> e : WEIGHT.entrySet()) {
            if (!excluded.contains(e.getKey())) {
                total += e.getValue();
            }
        }
        int roll = rng.nextInt(total);
        for (Archetype a : Archetype.values()) {
            Integer w = WEIGHT.get(a);
            if (w == null || excluded.contains(a)) {
                continue;
            }
            if (roll < w) {
                return a;
            }
            roll -= w;
        }
        throw new AssertionError("archetype pool exhausted");
    }

    /**
     * No screen may share its archetype with all four neighbours. River screens
     * can never violate it (RIVER_GAP keeps their east/west neighbours dry) and
     * Shore is border-only (so it never has four neighbours at all).
     */
    private static void repairNeighbourConstraint(Archetype[][] a, Random rng) {
        Set<Archetype> structural = EnumSet.of(Archetype.RIVER, Archetype.SHORE);
        for (int pass = 0; pass < 20; pass++) {
            boolean dirty = false;
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    if (degree(sx, sy) < 4 || structural.contains(a[sx][sy])) {
                        continue;
                    }
                    if (sameAsAllNeighbours(a, sx, sy)) {
                        a[sx][sy] = draw(rng, structural);
                        dirty = true;
                    }
                }
            }
            if (!dirty) {
                return;
            }
        }
    }

    private static boolean sameAsAllNeighbours(Archetype[][] a, int sx, int sy) {
        for (int d = 0; d < 4; d++) {
            if (a[sx + DDX[d]][sy + DDY[d]] != a[sx][sy]) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameAsAllNeighbours(World w, int sx, int sy) {
        for (int d = 0; d < 4; d++) {
            if (w.archetype(sx + DDX[d], sy + DDY[d]) != w.archetype(sx, sy)) {
                return false;
            }
        }
        return true;
    }

    private static int degree(int sx, int sy) {
        int n = 0;
        for (int d = 0; d < 4; d++) {
            if (World.inWorld(sx + DDX[d], sy + DDY[d])) {
                n++;
            }
        }
        return n;
    }

    private static boolean isBorder(int sx, int sy) {
        return sx == 0 || sy == 0 || sx == World.WORLD_W - 1 || sy == World.WORLD_H - 1;
    }

    private static int[][] rollTiers(Random rng) {
        int[][] tiers = new int[World.WORLD_W][World.WORLD_H];
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                int dist = World.distanceFromSpawn(sx, sy);
                for (int[] band : TIER_BANDS) {
                    if (dist <= band[0]) {
                        tiers[sx][sy] = rng.nextInt(100) < 60 ? band[1] : band[2];
                        break;
                    }
                }
            }
        }
        return tiers;
    }

    /**
     * The dungeon entrance screen: rolled until every 2x2 landmark area falls
     * inside the world, off the spawn screen, and off the entrance itself. The
     * entrance tile sits on the lane crossing, so it is always reachable.
     * Deterministic per seed, so a conflict re-roll is reproducible.
     */
    private static ScreenPos rollEntrance(Random rng) {
        for (int tryNo = 0; tryNo < 5000; tryNo++) {
            int sx = rng.nextInt(World.WORLD_W);
            int sy = rng.nextInt(World.WORLD_H);
            if (sx == World.SPAWN_SX && sy == World.SPAWN_SY) {
                continue;
            }
            Map<Landmark, ScreenPos> spots = new EnumMap<>(Landmark.class);
            for (Map.Entry<Landmark, int[]> e : LANDMARK_OFFSET.entrySet()) {
                spots.put(e.getKey(), new ScreenPos(sx + e.getValue()[0], sy + e.getValue()[1], 0, 0));
            }
            if (landmarksFit(spots, sx, sy)) {
                return new ScreenPos(sx, sy, World.LANE_X, World.LANE_Y);
            }
        }
        return null;
    }

    /** Every 2x2 landmark area fits inside the world and misses spawn and entrance. */
    private static boolean landmarksFit(Map<Landmark, ScreenPos> spots, int entranceSx, int entranceSy) {
        for (ScreenPos p : spots.values()) {
            if (p.sx() < 0 || p.sy() < 0 || p.sx() + 2 > World.WORLD_W || p.sy() + 2 > World.WORLD_H) {
                return false;
            }
            if (covers(p, World.SPAWN_SX, World.SPAWN_SY) || covers(p, entranceSx, entranceSy)) {
                return false;
            }
        }
        return true;
    }

    /** The landmark whose 2x2 area covers this screen, or null. */
    private static Landmark landmarkAt(Map<Landmark, ScreenPos> landmarks, int sx, int sy) {
        for (Map.Entry<Landmark, ScreenPos> e : landmarks.entrySet()) {
            if (covers(e.getValue(), sx, sy)) {
                return e.getKey();
            }
        }
        return null;
    }

    private static boolean covers(ScreenPos area, int sx, int sy) {
        return sx >= area.sx() && sx < area.sx() + 2 && sy >= area.sy() && sy < area.sy() + 2;
    }

    // --- screen rendering ------------------------------------------------------

    private static Screen render(Random rng, int sx, int sy, Archetype a, int riverStrip, int oceanDepth,
            Map<Landmark, ScreenPos> landmarks) {
        Screen s = new Screen();
        Landmark lm = landmarkAt(landmarks, sx, sy);
        if (lm != null) {
            renderLandmark(lm, s, rng);
        } else {
            switch (a) {
                case MEADOW -> scatter(s, rng, Tile.TREE, rng.nextInt(MEADOW_TREES_MAX + 1), Tile.GRASS);
                case FOREST -> forest(s, rng);
                case LAKE -> lake(s, rng);
                case RIVER -> river(s, rng, riverStrip);
                case ROCKFIELD -> scatter(s, rng, Tile.ROCK, range(rng, ROCKFIELD_MIN, ROCKFIELD_MAX), Tile.GRASS);
                case PATH -> path(s, rng);
                case CLEARING -> clearing(s, rng);
                case MOUNTAIN -> mountain(s, rng);
                case SHORE -> shore(s, rng, sx, sy, oceanDepth, riverStrip);
            }
        }
        return s;
    }

    /** Forest signature: 10-18 trees with a narrow winding gap through them. */
    private static void forest(Screen s, Random rng) {
        scatter(s, rng, Tile.TREE, range(rng, FOREST_TREES_MIN, FOREST_TREES_MAX), Tile.GRASS);
        carveWindingGap(s, rng, 2);
    }

    /** Lake signature: a 5-8 tile blob, kept off the screen edges. The lanes are its shore path. */
    private static void lake(Screen s, Random rng) {
        put(s, pick(rng, 2, World.SCREEN_W - 3, World.LANE_X), pick(rng, 2, World.SCREEN_H - 3, World.LANE_Y),
                Tile.WATER);
        int size = range(rng, LAKE_MIN, LAKE_MAX);
        for (int guard = 0; waterCount(s) < size && guard < 1000; guard++) {
            int x = rng.nextInt(World.SCREEN_W), y = 1 + rng.nextInt(World.SCREEN_H - 2);
            for (int d = 0; d < 4; d++) {
                int nx = x + DDX[d], ny = y + DDY[d];
                if (nx >= 0 && nx < World.SCREEN_W && ny >= 0 && ny < World.SCREEN_H
                        && s.get(nx, ny) == Tile.WATER) {
                    put(s, x, y, Tile.WATER);
                    break;
                }
            }
        }
    }

    /**
     * River signature: a 2-3 tile channel on full-height columns at the same
     * screen-local offset in every river screen, so it lines up across borders.
     * The lane row never accepts water, so it reads as the bridge (1st
     * crossing); a 2nd crossing is sometimes carved near a horizontal edge.
     */
    private static void river(Screen s, Random rng, int stripWidth) {
        for (int x = RIVER_STRIP_X; x < RIVER_STRIP_X + stripWidth; x++) {
            for (int y = 0; y < World.SCREEN_H; y++) {
                put(s, x, y, Tile.WATER);
            }
        }
        if (rng.nextDouble() < RIVER_EXTRA_CROSSING) {
            int y = rng.nextBoolean() ? 1 : World.SCREEN_H - 2;
            for (int x = RIVER_STRIP_X; x < RIVER_STRIP_X + stripWidth; x++) {
                open(s, x, y);
            }
        }
    }

    /** Path signature: a clear 4-tile corridor across the screen, dense on both sides. */
    private static void path(Screen s, Random rng) {
        boolean horizontal = rng.nextBoolean();
        int span = (horizontal ? World.SCREEN_H : World.SCREEN_W) - PATH_WIDTH;
        int start = 1 + rng.nextInt(span - 1);
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                boolean corridor = horizontal
                        ? y >= start && y < start + PATH_WIDTH
                        : x >= start && x < start + PATH_WIDTH;
                if (corridor) {
                    s.set(x, y, Tile.DIRT);
                } else {
                    put(s, x, y, rng.nextInt(3) == 0 ? Tile.ROCK : Tile.TREE);
                }
            }
        }
    }

    /** Clearing signature: open centre, ring of trees at the screen edges. */
    private static void clearing(Screen s, Random rng) {
        for (int x = 0; x < World.SCREEN_W; x++) {
            for (int y = 0; y < World.SCREEN_H; y++) {
                if (x == 0 || y == 0 || x == World.SCREEN_W - 1 || y == World.SCREEN_H - 1) {
                    put(s, x, y, Tile.TREE);
                }
            }
        }
    }

    /** Mountain signature: boulder clusters plus 1-2 wall segments on screen edges only. */
    private static void mountain(Screen s, Random rng) {
        scatter(s, rng, Tile.ROCK, range(rng, MOUNTAIN_BOULDERS_MIN, MOUNTAIN_BOULDERS_MAX), Tile.GRASS);
        int walls = range(rng, MOUNTAIN_WALLS_MIN, MOUNTAIN_WALLS_MAX);
        for (int i = 0; i < walls; i++) {
            int edge = rng.nextInt(4);
            int len = 1 + rng.nextInt(MOUNTAIN_WALL_LEN_MAX);
            boolean horizontal = edge == NORTH || edge == SOUTH;
            int start = rng.nextInt((horizontal ? World.SCREEN_W : World.SCREEN_H) - len + 1);
            for (int j = 0; j < len; j++) {
                int x = horizontal ? start + j : (edge == EAST ? World.SCREEN_W - 1 : 0);
                int y = horizontal ? (edge == NORTH ? 0 : World.SCREEN_H - 1) : start + j;
                put(s, x, y, Tile.ROCK);
            }
        }
    }

    /**
     * Shore signature: sand, ocean on the world-border edges, 0-2 trees. On a
     * river column the channel is carved through the sand too, so the river
     * drains into the sea instead of dead-ending at the coastline.
     */
    private static void shore(Screen s, Random rng, int sx, int sy, int depth, int riverStrip) {
        for (int i = 0; i < World.SCREEN_W * World.SCREEN_H; i++) {
            s.set(i % World.SCREEN_W, i / World.SCREEN_W, Tile.SAND);
        }
        if (sx == 0) {
            sea(s, 0, 0, depth, World.SCREEN_H);
        }
        if (sx == World.WORLD_W - 1) {
            sea(s, World.SCREEN_W - depth, 0, depth, World.SCREEN_H);
        }
        if (sy == 0) {
            sea(s, 0, 0, World.SCREEN_W, depth);
        }
        if (sy == World.WORLD_H - 1) {
            sea(s, 0, World.SCREEN_H - depth, World.SCREEN_W, depth);
        }
        if (riverStrip > 0) {
            for (int x = RIVER_STRIP_X; x < RIVER_STRIP_X + riverStrip; x++) {
                for (int y = 0; y < World.SCREEN_H; y++) {
                    put(s, x, y, Tile.WATER);
                }
            }
        }
        scatter(s, rng, Tile.TREE, rng.nextInt(SHORE_TREES_MAX + 1), Tile.SAND);
    }

    /** Ocean writes bypass lane protection: a sea strip must not keep a sand bridge to the horizon. */
    private static void sea(Screen s, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int tx = x + i, ty = y + j;
                if (tx >= 0 && tx < World.SCREEN_W && ty >= 0 && ty < World.SCREEN_H) {
                    s.set(tx, ty, Tile.WATER);
                }
            }
        }
    }

    /**
     * Make every screen border visible from both sides: a tile walkable on only one
     * side of a border is closed with rock on its side, so the player never walks
     * toward an edge that an unseen neighbouring screen blocks. Borders keep at
     * least one opening because every screen keeps its lane cross walkable and the
     * lane tiles of two neighbours always agree.
     */
    private static void repairBoundaries(Screen[][] screens) {
        // corner tiles belong to two borders, so one pass can break what another
        // just fixed; repairs only ever close tiles, so this converges fast
        for (int pass = 0; pass < 10; pass++) {
            boolean dirty = false;
            for (int sy = 0; sy < World.WORLD_H; sy++) {
                for (int sx = 0; sx + 1 < World.WORLD_W; sx++) {
                    dirty |= repairBorder(screens[sx][sy], screens[sx + 1][sy], true);
                }
            }
            for (int sy = 0; sy + 1 < World.WORLD_H; sy++) {
                for (int sx = 0; sx < World.WORLD_W; sx++) {
                    dirty |= repairBorder(screens[sx][sy], screens[sx][sy + 1], false);
                }
            }
            if (!dirty) {
                return;
            }
        }
        throw new IllegalStateException("border repair did not converge");
    }

    private static boolean repairBorder(Screen a, Screen b, boolean vertical) {
        int n = vertical ? World.SCREEN_H : World.SCREEN_W;
        boolean dirty = false;
        for (int i = 0; i < n; i++) {
            int ax = vertical ? World.SCREEN_W - 1 : i, ay = vertical ? i : World.SCREEN_H - 1;
            int bx = vertical ? 0 : i, by = vertical ? i : 0;
            boolean wa = a.get(ax, ay).walkable, wb = b.get(bx, by).walkable;
            if (wa && !wb) {
                a.set(ax, ay, Tile.ROCK);
                dirty = true;
            } else if (!wa && wb) {
                b.set(bx, by, Tile.ROCK);
                dirty = true;
            }
        }
        return dirty;
    }

    private static void renderLandmark(Landmark lm, Screen s, Random rng) {
        switch (lm) {
            case BIG_LAKE -> {
                // solid water; the lanes read as the causeway across the lake
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        put(s, x, y, Tile.WATER);
                    }
                }
            }
            case ROCK_MOUNTAIN -> {
                mountain(s, rng);
                carveWindingGap(s, rng, 3);
            }
            case RUINED_SHRINE -> {
                // ring of rock walls with broken gaps
                for (int x = 0; x < World.SCREEN_W; x++) {
                    put(s, x, 0, Tile.ROCK);
                    put(s, x, World.SCREEN_H - 1, Tile.ROCK);
                }
                for (int y = 0; y < World.SCREEN_H; y++) {
                    put(s, 0, y, Tile.ROCK);
                    put(s, World.SCREEN_W - 1, y, Tile.ROCK);
                }
                for (int i = 0; i < 3; i++) {
                    open(s, rng.nextInt(World.SCREEN_W), rng.nextBoolean() ? 0 : World.SCREEN_H - 1);
                }
            }
            case CEMETERY -> {
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        boolean aisle = (x + 1) % 4 == 0 || (y + 1) % 3 == 0;
                        put(s, x, y, aisle ? Tile.DIRT : Tile.TOMBSTONE);
                    }
                }
            }
        }
    }

    /** A walkable corridor meandering west edge to east edge; `turnChance` in 1-in-n. */
    private static void carveWindingGap(Screen s, Random rng, int turnChance) {
        int y = rng.nextInt(World.SCREEN_H);
        for (int x = 0; x < World.SCREEN_W; x++) {
            open(s, x, y);
            if (rng.nextInt(turnChance) != 0 || x + 1 >= World.SCREEN_W) {
                continue;
            }
            int ny = clamp(y + (rng.nextBoolean() ? 1 : -1), 0, World.SCREEN_H - 1);
            for (int my = Math.min(y, ny); my <= Math.max(y, ny); my++) {
                open(s, x + 1, my);
            }
            y = ny;
        }
    }

    // --- helpers ----------------------------------------------------------------

    /** Write a tile, never damming a lane tile with an obstacle. */
    private static void put(Screen s, int x, int y, Tile t) {
        if (x < 0 || y < 0 || x >= World.SCREEN_W || y >= World.SCREEN_H) {
            return;
        }
        if (!t.walkable && isLane(x, y)) {
            return;
        }
        s.set(x, y, t);
    }

    /** Make a tile walkable, keeping the sand texture where it belongs. */
    private static void open(Screen s, int x, int y) {
        Tile t = s.get(x, y);
        if (!t.walkable) {
            s.set(x, y, t == Tile.SAND ? Tile.SAND : Tile.GRASS);
        }
    }

    private static boolean isLane(int x, int y) {
        return x == World.LANE_X || y == World.LANE_Y;
    }

    private static int waterCount(Screen s) {
        int n = 0;
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                if (s.get(x, y) == Tile.WATER) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int range(Random rng, int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A random value in [min,max] that is never `avoid`. */
    private static int pick(Random rng, int min, int max, int avoid) {
        int v;
        do {
            v = range(rng, min, max);
        } while (v == avoid && max > min);
        return v;
    }

    /** Scatter `count` tiles of t onto tiles currently equal to `on`. */
    private static void scatter(Screen s, Random rng, Tile t, int count, Tile on) {
        for (int i = 0, guard = 0; i < count && guard < count * 40 + 40; guard++) {
            int x = rng.nextInt(World.SCREEN_W), y = rng.nextInt(World.SCREEN_H);
            if (s.get(x, y) == on) {
                put(s, x, y, t);
                if (s.get(x, y) == t) { // lanes refuse obstacles: only count real writes
                    i++;
                }
            }
        }
    }

    // --- enemies ------------------------------------------------------------------

    private static void placeEnemies(Random rng, Screen[][] screens, int[][] tiers,
            Map<Landmark, ScreenPos> landmarks, ScreenPos entrance, List<EnemySpawn>[] out) {
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                if ((sx == World.SPAWN_SX && sy == World.SPAWN_SY) || (sx == entrance.sx() && sy == entrance.sy())
                        || landmarkAt(landmarks, sx, sy) == Landmark.CEMETERY) {
                    continue; // spawn and the entrance area stay safe; the Cemetery is Ghost-only
                }
                int tier = tiers[sx][sy];
                int count = range(rng, TIER_COUNT[tier - 1][0], TIER_COUNT[tier - 1][1]);
                if (count == 0) {
                    continue;
                }
                List<EnemySpawn> list = new ArrayList<>(count);
                for (int guard = 0; list.size() < count && guard < count * 40 + 40; guard++) {
                    int tx = rng.nextInt(World.SCREEN_W), ty = rng.nextInt(World.SCREEN_H);
                    if (!screens[sx][sy].get(tx, ty).walkable || occupied(list, tx, ty)) {
                        continue;
                    }
                    // tier 1 is Grunt-only; tier 2+ mixes in Octorocks
                    EnemyKind kind = tier >= 2 && rng.nextBoolean() ? EnemyKind.OCTOROCK : EnemyKind.GRUNT;
                    list.add(new EnemySpawn(kind, tx, ty));
                }
                out[sy * World.WORLD_W + sx] = list;
            }
        }
    }

    private static boolean occupied(List<EnemySpawn> list, int tx, int ty) {
        for (EnemySpawn e : list) {
            if (e.tx() == tx && e.ty() == ty) {
                return true;
            }
        }
        return false;
    }

    // --- validation -----------------------------------------------------------------

    /** Post-conditions every generated world must satisfy. */
    static boolean valid(World w) {
        return archetypeConstraintHolds(w) && screenBordersMatch(w) && entranceIsPlaced(w) && spawnIsWalkable(w)
                && allScreensReachable(w) && enemiesAreLegal(w);
    }

    static boolean archetypeConstraintHolds(World w) {
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                if (degree(sx, sy) == 4 && sameAsAllNeighbours(w, sx, sy)) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean entranceIsPlaced(World w) {
        ScreenPos e = w.dungeonEntrance();
        if (!World.inWorld(e.sx(), e.sy()) || (e.sx() == World.SPAWN_SX && e.sy() == World.SPAWN_SY)) {
            return false;
        }
        if (w.screen(e.sx(), e.sy()).get(e.tx(), e.ty()) != Tile.ENTRANCE) {
            return false;
        }
        int count = 0;
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                Screen s = w.screen(sx, sy);
                for (int y = 0; y < World.SCREEN_H; y++) {
                    for (int x = 0; x < World.SCREEN_W; x++) {
                        if (s.get(x, y) == Tile.ENTRANCE) {
                            count++;
                        }
                    }
                }
            }
        }
        return count == 1;
    }

    static boolean spawnIsWalkable(World w) {
        return w.walkable(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
    }

    /**
     * Tile-accurate flood fill from spawn, crossing screen borders the way Link
     * does. Every screen must be reached.
     */
    static boolean allScreensReachable(World w) {
        int tw = World.WORLD_W * World.SCREEN_W;
        int th = World.WORLD_H * World.SCREEN_H;
        boolean[] seen = new boolean[tw * th];
        boolean[] screenSeen = new boolean[World.WORLD_W * World.WORLD_H];
        if (!spawnIsWalkable(w)) {
            return false;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        int start = (World.SPAWN_SY * World.SCREEN_H + World.SPAWN_TY) * tw
                + World.SPAWN_SX * World.SCREEN_W + World.SPAWN_TX;
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int wx = idx % tw, wy = idx / tw;
            screenSeen[(wy / World.SCREEN_H) * World.WORLD_W + wx / World.SCREEN_W] = true;
            for (int d = 0; d < 4; d++) {
                int nx = wx + DDX[d], ny = wy + DDY[d];
                if (nx < 0 || ny < 0 || nx >= tw || ny >= th) {
                    continue; // the world is an island: no wrapping at the edge
                }
                int next = ny * tw + nx;
                if (!seen[next] && w.walkable(nx / World.SCREEN_W, ny / World.SCREEN_H, nx % World.SCREEN_W,
                        ny % World.SCREEN_H)) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        for (boolean b : screenSeen) {
            if (!b) {
                return false;
            }
        }
        return true;
    }

    /** Every border tile pair is walkable on both sides or on neither. */
    static boolean screenBordersMatch(World w) {
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx + 1 < World.WORLD_W; sx++) {
                for (int y = 0; y < World.SCREEN_H; y++) {
                    if (w.walkable(sx, sy, World.SCREEN_W - 1, y) != w.walkable(sx + 1, sy, 0, y)) {
                        return false;
                    }
                }
            }
        }
        for (int sy = 0; sy + 1 < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                for (int x = 0; x < World.SCREEN_W; x++) {
                    if (w.walkable(sx, sy, x, World.SCREEN_H - 1) != w.walkable(sx, sy + 1, x, 0)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    static boolean enemiesAreLegal(World w) {
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                for (EnemySpawn e : w.enemies(sx, sy)) {
                    if (!w.walkable(sx, sy, e.tx(), e.ty()) || w.isEntrance(sx, sy, e.tx(), e.ty())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
