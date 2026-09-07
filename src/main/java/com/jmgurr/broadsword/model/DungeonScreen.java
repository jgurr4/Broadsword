package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One authored dungeon screen: an all-walled tile grid (except authored doors)
 * plus the entities placed on it. Static data; mutable run state (keys held,
 * doors opened, loot taken) lives in {@link DungeonRun}.
 */
public final class DungeonScreen {

    /** A door on this edge leading to another screen. Both sides of a locked
     * pair share a lockId so opening one opens both. */
    public record Door(int target, boolean locked, int lockId) {
        Door withLockId(int id) {
            return new Door(target, locked, id);
        }
    }

    private final String id;
    private final Screen grid;
    private final boolean dark;
    private final Map<Link.Dir, Door> doors;
    private final ScreenPos exit; // DUNGEON_EXIT tile, or null
    private final ScreenPos boss; // hydra tile (T11 spawns it), or null
    private final List<Lootable> keys;
    private final List<Lootable> items;
    private final List<EnemySpawn> enemies;
    private final List<ScreenPos> blocks; // stored for T10; inert in T9

    DungeonScreen(String id, Screen grid, boolean dark, Map<Link.Dir, Door> doors,
                  ScreenPos exit, ScreenPos boss, List<Lootable> keys, List<Lootable> items,
                  List<EnemySpawn> enemies, List<ScreenPos> blocks) {
        this.id = id;
        this.grid = grid;
        this.dark = dark;
        this.doors = doors;
        this.exit = exit;
        this.boss = boss;
        this.keys = keys;
        this.items = items;
        this.enemies = enemies;
        this.blocks = blocks;
    }

    static DungeonScreen of(String id, Screen grid, boolean dark, ScreenPos exit, ScreenPos boss,
                            List<Lootable> keys, List<Lootable> items,
                            List<EnemySpawn> enemies, List<ScreenPos> blocks) {
        return new DungeonScreen(id, grid, dark, Map.of(), exit, boss, keys, items, enemies, blocks);
    }

    DungeonScreen withDoors(Map<Link.Dir, Door> doors) {
        return new DungeonScreen(id, grid, dark, Map.copyOf(doors), exit, boss, keys, items, enemies, blocks);
    }

    /** Assign globally-unique ids to this screen's loot (parse pass 1). */
    DungeonScreen withLootIds(int[] nextId) {
        List<Lootable> k = new ArrayList<>();
        for (Lootable l : keys) k.add(new Lootable(nextId[0]++, l.tx(), l.ty()));
        List<Lootable> i = new ArrayList<>();
        for (Lootable l : items) i.add(new Lootable(nextId[0]++, l.tx(), l.ty()));
        return new DungeonScreen(id, grid, dark, doors, exit, boss, List.copyOf(k), List.copyOf(i), enemies, blocks);
    }

    public String id() {
        return id;
    }

    public Screen grid() {
        return grid;
    }

    /** Authored dark screen; lighting is T10, flag stored now. */
    public boolean dark() {
        return dark;
    }

    public Map<Link.Dir, Door> doors() {
        return doors;
    }

    public Door dir(Link.Dir d) {
        return doors.get(d);
    }

    /** The overworld exit tile, or null when this screen has none. */
    public ScreenPos exitTile() {
        return exit;
    }

    /** Boss tile (hydra); T11 spawns the boss, T9 only guarantees the room. */
    public ScreenPos bossTile() {
        return boss;
    }

    public List<Lootable> keys() {
        return keys;
    }

    public List<Lootable> items() {
        return items;
    }

    /** Shoveable block positions (T10 behaviour); parsed and stored in T9. */
    public List<ScreenPos> blocks() {
        return blocks;
    }

    public List<EnemySpawn> enemies() {
        return enemies;
    }

    /** Where the door for {@code d} sits: the DOOR tile on that edge. */
    public ScreenPos doorTile(Link.Dir d) {
        for (int i = 0; i < World.SCREEN_W; i++) {
            for (int j = 0; j < World.SCREEN_H; j++) {
                int tx = switch (d) {
                    case UP, DOWN -> i;
                    case LEFT -> 0;
                    case RIGHT -> World.SCREEN_W - 1;
                };
                int ty = switch (d) {
                    case UP -> 0;
                    case DOWN -> World.SCREEN_H - 1;
                    case LEFT, RIGHT -> j;
                };
                if (grid.get(tx, ty) == Tile.DOOR) return new ScreenPos(0, 0, tx, ty);
            }
        }
        return null;
    }

    /** The door whose tile sits at (tx, ty), or null. */
    public Map.Entry<Link.Dir, Door> doorAt(int tx, int ty) {
        for (Map.Entry<Link.Dir, Door> e : doors.entrySet()) {
            ScreenPos p = doorTile(e.getKey());
            if (p != null && p.tx() == tx && p.ty() == ty) return e;
        }
        return null;
    }

    /**
     * Tile Link stands on after entering through the overworld exit: one step
     * inwards from the exit tile, facing inwards.
     */
    public Link.Dir exitInward() {
        return switch (edgeOf(exit)) {
            case LEFT -> Link.Dir.RIGHT;
            case RIGHT -> Link.Dir.LEFT;
            case UP -> Link.Dir.DOWN;
            case DOWN -> Link.Dir.UP;
        };
    }

    /** The edge a boundary tile sits on. */
    static Link.Dir edgeOf(ScreenPos p) {
        if (p.tx() == 0) return Link.Dir.LEFT;
        if (p.tx() == World.SCREEN_W - 1) return Link.Dir.RIGHT;
        if (p.ty() == 0) return Link.Dir.UP;
        return Link.Dir.DOWN;
    }
}
