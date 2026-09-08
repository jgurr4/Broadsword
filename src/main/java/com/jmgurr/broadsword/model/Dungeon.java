package com.jmgurr.broadsword.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An authored multi-screen dungeon parsed from the text tile-map format:
 *
 * <pre>
 * # dungeon: hydra
 * [legend]
 * (comment lines; ignored)
 *
 * [s01] doors: E=s02 S=s05(locked) dark: true
 * ################
 * #..............#   exactly World.SCREEN_H rows of World.SCREEN_W chars
 * ...
 * </pre>
 *
 * Grid chars: '#' wall, '.' floor, 'd' door, 'L' locked door, 'E' overworld
 * exit, 'k' key, 'i' item, 'B' shoveable block, 'g' grunt, 'o' octorock,
 * 'H' hydra (boss tile; T11 spawns it). Door connectivity comes
 * from the header ({@code doors: <DIR>=<id>[(locked)] ...}); the door tile is
 * wherever the grid shows 'd'/'L' on that edge. Doors are two-way: every
 * declared door must have a matching door on the target's opposite edge.
 *
 * <p>A line {@code trigger: B@(x,y) -> i@(x,y)} under a screen header binds a
 * block's first push to revealing the hidden item at the target tile. The item
 * is invisible and untouchable until that first push.
 */
public final class Dungeon {

    /** Classpath resource for the authored hydra dungeon. */
    public static final String HYDRA_RESOURCE = "/dungeons/hydra.txt";

    private static Dungeon hydraCache;

    private final String name;
    private final List<DungeonScreen> screens;
    private final int entryIndex;

    private Dungeon(String name, List<DungeonScreen> screens, int entryIndex) {
        this.name = name;
        this.screens = screens;
        this.entryIndex = entryIndex;
    }

    public String name() {
        return name;
    }

    public List<DungeonScreen> screens() {
        return screens;
    }

    public DungeonScreen screen(int index) {
        return screens.get(index);
    }

    public DungeonScreen screen(String id) {
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).id().equals(id)) return screens.get(i);
        }
        throw new IllegalArgumentException("no such screen: " + id);
    }

    public int indexOf(String id) {
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /** Screen Link arrives on when entering from the overworld. */
    public DungeonScreen entry() {
        return screens.get(entryIndex);
    }

    public int entryIndex() {
        return entryIndex;
    }

    /** Global loot id space: true when the id belongs to an item (not a key). */
    public boolean isItemId(int lootId) {
        for (DungeonScreen s : screens) {
            for (Lootable l : s.items()) {
                if (l.id() == lootId) return true;
            }
            for (Lootable l : s.hiddenLoot()) {
                if (l.id() == lootId) return true;
            }
        }
        return false;
    }

    /** Number of locked doors (per side) authored into this dungeon. */
    public int lockedDoorCount() {
        int n = 0;
        for (DungeonScreen s : screens) {
            for (DungeonScreen.Door d : s.doors().values()) {
                if (d.locked()) n++;
            }
        }
        return n;
    }

    /** Load the authored hydra dungeon from the classpath. */
    public static synchronized Dungeon loadHydra() {
        if (hydraCache == null) {
            String text = readResource(HYDRA_RESOURCE);
            hydraCache = parse(text);
        }
        return hydraCache;
    }

    private static String readResource(String path) {
        try (InputStream in = Dungeon.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    public static Dungeon parse(String text) {
        String name = "dungeon";
        List<Raw> raws = new ArrayList<>();
        Raw current = null;
        int lineNo = 0;
        List<TriggerDecl> triggers = new ArrayList<>();

        for (String raw : text.split("\n", -1)) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty()) continue;
            if (isGridRow(line) && current != null) {
                // grid rows may start with '#': only a run of pure grid chars is a row
                if (current.rows.size() >= World.SCREEN_H)
                    throw error(name, lineNo, "too many grid rows in " + current.id);
                if (line.length() != World.SCREEN_W)
                    throw error(name, lineNo, "row is " + line.length() + " chars, expected " + World.SCREEN_W);
                current.rows.add(line);
                continue;
            }
            if (line.startsWith("#")) {
                String n = between(line, "# dungeon:", null);
                if (n != null) name = n.split("\\s+")[0];
                continue;
            }
            if (line.startsWith("[")) {
                if (line.startsWith("[legend]")) continue; // every other [...] is a screen header
                int close = line.indexOf(']');
                if (close < 0) throw error(name, lineNo, "unterminated screen header");
                current = new Raw(line.substring(1, close), line.substring(close + 1));
                raws.add(current);
                continue;
            }
            if (line.startsWith("trigger:")) {
                if (current == null) throw error(name, lineNo, "trigger before any screen header");
                triggers.add(parseTrigger(name, lineNo, current.id, line));
                continue;
            }
            if (current == null) throw error(name, lineNo, "grid row before any screen header");
            throw error(name, lineNo, "not a grid row: " + line);
        }

        if (raws.isEmpty()) throw error(name, 0, "no screens");

        // pass 1: grids + entities, with globally unique loot ids in parse order
        List<DungeonScreen> screens = new ArrayList<>();
        int nextLootId = 0;
        for (Raw r : raws) {
            r.triggers.addAll(triggers.stream().filter(t -> t.screenId.equals(r.id)).toList());
            for (TriggerDecl t : r.triggers) {
                if (r.hidden.stream().anyMatch(h -> h.tx() == t.itemX && h.ty() == t.itemY))
                    throw error(name, 0, r.id + ": two triggers target " + t.itemX + "," + t.itemY);
                r.hidden.add(new Lootable(-1, t.itemX, t.itemY));
            }
            DungeonScreen s = buildScreen(name, r);
            int[] id = { nextLootId };
            screens.add(s.withLootIds(id));
            nextLootId = id[0];
        }

        int entry = -1;
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).exitTile() != null) {
                if (entry >= 0) throw error(name, 0, "more than one overworld exit (E)");
                entry = i;
            }
        }
        if (entry < 0) throw error(name, 0, "no overworld exit (E)");

        // pass 2: resolve doors, assign shared lock ids per pair, validate
        Map<Link.Dir, DungeonScreen.Door>[] doors = new Map[screens.size()];
        Map<String, Integer> lockIds = new HashMap<>();
        int nextLock = 0;
        for (int i = 0; i < screens.size(); i++) {
            doors[i] = new java.util.EnumMap<>(Link.Dir.class);
            for (Map.Entry<Link.Dir, String> e : raws.get(i).rawDoors.entrySet()) {
                int t = indexOfId(screens, e.getValue());
                if (t < 0) throw error(name, 0, screens.get(i).id() + ": unknown door target " + e.getValue());
                boolean locked = raws.get(i).locked.contains(e.getKey());
                doors[i].put(e.getKey(), new DungeonScreen.Door(t, locked, -1));
            }
        }
        for (int i = 0; i < screens.size(); i++) {
            for (Map.Entry<Link.Dir, DungeonScreen.Door> e : doors[i].entrySet()) {
                DungeonScreen.Door d = e.getValue();
                Link.Dir back = opposite(e.getKey());
                DungeonScreen.Door reverse = doors[d.target()].get(back);
                if (reverse == null) {
                    throw error(name, 0, screens.get(i).id() + " " + e.getKey() + " -> "
                            + screens.get(d.target()).id() + " has no return " + back + " door");
                }
                if (reverse.locked() != d.locked()) {
                    throw error(name, 0, "door " + screens.get(i).id() + "<->"
                            + screens.get(d.target()).id() + " disagrees on locked");
                }
                if (screens.get(i).doorTile(e.getKey()) == null) {
                    throw error(name, 0, screens.get(i).id() + " declares " + e.getKey()
                            + " but has no door tile on that edge");
                }
                if (screens.get(d.target()).doorTile(back) == null) {
                    throw error(name, 0, screens.get(d.target()).id() + " declares " + back
                            + " but has no door tile on that edge");
                }
                if (d.locked() && d.lockId() < 0) {
                    String pair = pairKey(i, d.target());
                    Integer id = lockIds.get(pair);
                    if (id == null) {
                        id = nextLock++;
                        lockIds.put(pair, id);
                    }
                    // stamp both sides
                    stampLock(doors, screens, i, e.getKey(), id);
                }
            }
        }

        DungeonScreen[] final_screens = new DungeonScreen[screens.size()];
        for (int i = 0; i < screens.size(); i++) {
            DungeonScreen s = screens.get(i);
            final_screens[i] = s.withDoors(doors[i]);
        }
        return new Dungeon(name, List.of(final_screens), entry);
    }

    private static void stampLock(Map<Link.Dir, DungeonScreen.Door>[] doors, List<DungeonScreen> screens,
                                  int idx, Link.Dir dir, int lockId) {
        DungeonScreen.Door d = doors[idx].get(dir);
        doors[idx].put(dir, d.withLockId(lockId));
        Link.Dir back = opposite(dir);
        DungeonScreen.Door r = doors[d.target()].get(back);
        if (r != null && r.lockId() < 0) {
            doors[d.target()].put(back, r.withLockId(lockId));
        }
    }

    /** Stable key for an unordered screen pair. */
    private static String pairKey(int a, int b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private static int indexOfId(List<DungeonScreen> screens, String id) {
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    private static DungeonScreen buildScreen(String dungeon, Raw r) {
        if (r.rows.size() != World.SCREEN_H) {
            throw error(dungeon, 0, r.id + " has " + r.rows.size() + " grid rows, expected " + World.SCREEN_H);
        }
        Screen grid = new Screen();
        boolean dark = r.dark;
        ScreenPos exit = null;
        ScreenPos boss = null;
        List<Lootable> keys = new ArrayList<>();
        List<Lootable> items = new ArrayList<>();
        List<EnemySpawn> enemies = new ArrayList<>();
        List<ScreenPos> blocks = new ArrayList<>();
        List<Lootable> hidden = new ArrayList<>(r.hidden);
        for (int y = 0; y < World.SCREEN_H; y++) {
            String row = r.rows.get(y);
            for (int x = 0; x < World.SCREEN_W; x++) {
                char c = row.charAt(x);
                Tile t = switch (c) {
                    case '#' -> Tile.DUNGEON_WALL;
                    case 'd' -> Tile.DOOR;
                    case 'L' -> Tile.DOOR; // locked state lives in the header/doors map
                    case 'E' -> Tile.DUNGEON_EXIT;
                    default -> Tile.DUNGEON_FLOOR;
                };
                grid.set(x, y, t);
                switch (c) {
                    case 'E' -> exit = new ScreenPos(0, 0, x, y);
                    case 'H' -> boss = new ScreenPos(0, 0, x, y);
                    case 'k' -> keys.add(new Lootable(-1, x, y));
                    case 'i' -> items.add(new Lootable(-1, x, y));
                    case 'B' -> blocks.add(new ScreenPos(0, 0, x, y));
                    case 'g' -> enemies.add(new EnemySpawn(EnemyKind.GRUNT, x, y));
                    case 'o' -> enemies.add(new EnemySpawn(EnemyKind.OCTOROCK, x, y));
                    default -> { }
                }
            }
        }
        // link each trigger's block tile to its hidden loot (matching by item tile)
        Map<ScreenPos, Lootable> triggers = new java.util.LinkedHashMap<>();
        for (TriggerDecl t : r.triggers) {
            if (!blocks.contains(new ScreenPos(0, 0, t.blockX, t.blockY)))
                throw error(dungeon, 0, r.id + ": trigger names no block at " + t.blockX + "," + t.blockY);
            Lootable loc = hidden.stream().filter(h -> h.tx() == t.itemX && h.ty() == t.itemY).findFirst()
                    .orElseThrow(() -> error(dungeon, 0, r.id + ": trigger target missing"));
            triggers.put(new ScreenPos(0, 0, t.blockX, t.blockY), loc);
        }
        return DungeonScreen.of(r.id, grid, dark, exit, boss, keys, items, hidden, enemies, blocks, triggers);
    }

    /** trigger: B@(x,y) -> i@(x,y) under the screen it belongs to. */
    private static TriggerDecl parseTrigger(String dungeon, int lineNo, String screenId, String line) {
        java.util.regex.Matcher m = TRIGGER.matcher(line);
        if (!m.matches())
            throw error(dungeon, lineNo, "expected trigger: B@(x,y) -> i@(x,y), got: " + line);
        return new TriggerDecl(screenId, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
    }

    private static final java.util.regex.Pattern TRIGGER = java.util.regex.Pattern
            .compile("trigger:\\s*B@\\((\\d+),(\\d+)\\)\\s*->\\s*i@\\((\\d+),(\\d+)\\)");

    private record TriggerDecl(String screenId, int blockX, int blockY, int itemX, int itemY) {
    }

    private static Link.Dir opposite(Link.Dir d) {
        return switch (d) {
            case UP -> Link.Dir.DOWN;
            case DOWN -> Link.Dir.UP;
            case LEFT -> Link.Dir.RIGHT;
            case RIGHT -> Link.Dir.LEFT;
        };
    }

    /** A line of nothing but grid characters (walls may start with '#'). */
    private static boolean isGridRow(String line) {
        if (line.isEmpty()) return false;
        return line.chars().allMatch(c -> "#.dLEkigoHB".indexOf(c) >= 0);
    }

    private static String between(String line, String marker, String def) {
        int i = line.indexOf(marker);
        if (i < 0) return def;
        String rest = line.substring(i + marker.length()).strip();
        return rest.isEmpty() ? def : rest;
    }

    private static IllegalStateException error(String dungeon, int lineNo, String msg) {
        return new IllegalStateException("dungeon '" + dungeon + "' line " + lineNo + ": " + msg);
    }

    /** Parse state for one screen while reading lines. */
    private static final class Raw {
        final String id;
        final List<String> rows = new ArrayList<>();
        final Map<Link.Dir, String> rawDoors = new java.util.EnumMap<>(Link.Dir.class);
        final java.util.EnumSet<Link.Dir> locked = java.util.EnumSet.noneOf(Link.Dir.class);
        final List<ScreenPos> blocks = new ArrayList<>();
        final List<Lootable> hidden = new ArrayList<>();
        final List<TriggerDecl> triggers = new ArrayList<>();
        boolean dark;

        Raw(String id, String headerRest) {
            this.id = id;
            String rest = headerRest.trim();
            int d = rest.indexOf("doors:");
            if (d >= 0) rest = rest.substring(d + "doors:".length());
            if (rest.contains("dark:")) dark = true;
            int k = rest.indexOf("dark:");
            if (k >= 0) rest = rest.substring(0, k);
            for (String tok : rest.split("\\s+")) {
                if (tok.isEmpty() || tok.equalsIgnoreCase("true")) continue;
                boolean isLocked = false;
                int paren = tok.indexOf('(');
                if (paren >= 0) {
                    String flag = tok.substring(paren + 1, tok.lastIndexOf(')'));
                    if (!flag.equals("locked")) throw new IllegalArgumentException("unknown door flag: " + flag);
                    isLocked = true;
                    tok = tok.substring(0, paren);
                }
                int eq = tok.indexOf('=');
                if (eq < 0) throw new IllegalArgumentException("bad door spec: " + tok);
                Link.Dir dir = switch (tok.charAt(0)) {
                    case 'N' -> Link.Dir.UP;
                    case 'S' -> Link.Dir.DOWN;
                    case 'W' -> Link.Dir.LEFT;
                    case 'E' -> Link.Dir.RIGHT;
                    default -> throw new IllegalArgumentException("bad door direction in " + tok);
                };
                rawDoors.put(dir, tok.substring(eq + 1));
                if (isLocked) locked.add(dir);
            }
        }
    }
}
