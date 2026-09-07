package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The persistent state of a run: seed + Link's position + the progress a death
 * must not hand back (Magic spent, the Secret revealed, dungeon keys, opened
 * doors and taken loot). The world itself re-derives from the seed; killed
 * enemies respawn per the save model.
 *
 * Text format, one key=value per line, version 5. Older saves load with the
 * dungeon fields defaulted (v1: no magic/secret; v2: cave was a boolean; v3:
 * no Flute; v4: no dungeon progress). No libgdx types: parsing is testable
 * headlessly; file I/O lives in the render layer.
 */
public record SaveState(long seed, int sx, int sy, int tx, int ty, Link.Dir facing,
        int magic, boolean secretRevealed, int caveKey, boolean fluteTaken,
        int dungeonKeys, boolean inDungeon, Set<Integer> openedLocks, Set<Integer> takenLoot) {

    /** {@code caveKey} values: -1 on the overworld; v2's "in the secret cave" marker. */
    public static final int NO_CAVE = -1;
    public static final int CAVE_SECRET_V2 = -2;
    public static final int VERSION = 5;
    static final int VERSION_V1 = 1;
    static final int VERSION_V2 = 2;
    static final int VERSION_V3 = 3;
    static final int VERSION_V4 = 4;

    /** A new run: full Magic, the Secret still hidden, no Flute, Link on the overworld. */
    public SaveState(long seed, int sx, int sy, int tx, int ty, Link.Dir facing) {
        this(seed, sx, sy, tx, ty, facing, World.MAX_MAGIC, false, NO_CAVE, false,
                0, false, Set.of(), Set.of());
    }

    /** A save without dungeon progress. */
    public SaveState(long seed, int sx, int sy, int tx, int ty, Link.Dir facing,
            int magic, boolean secretRevealed, int caveKey, boolean fluteTaken) {
        this(seed, sx, sy, tx, ty, facing, magic, secretRevealed, caveKey, fluteTaken,
                0, false, Set.of(), Set.of());
    }

    public String format() {
        return "version=" + VERSION + "\n"
                + "seed=" + seed + "\n"
                + "link=" + sx + "," + sy + "," + tx + "," + ty + "\n"
                + "facing=" + facing.name() + "\n"
                + "magic=" + magic + "\n"
                + "secret=" + (secretRevealed ? 1 : 0) + "\n"
                + "cave=" + caveKey + "\n"
                + "flute=" + (fluteTaken ? 1 : 0) + "\n"
                + "dungeon=" + (inDungeon ? 1 : 0) + "\n"
                + "keys=" + dungeonKeys + "\n"
                + "open=" + join(openedLocks) + "\n"
                + "loot=" + join(takenLoot) + "\n";
    }

    /** Parse a save file. Any deviation (bad version, bad numbers, out-of-world position) is corrupt. */
    public static Optional<SaveState> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Integer version = null;
        Long seed = null;
        int[] link = null;
        Link.Dir facing = null;
        Integer magic = null;
        Boolean secret = null;
        Integer cave = null;
        Boolean flute = null;
        Boolean dungeon = null;
        Integer keys = null;
        Set<Integer> open = null;
        Set<Integer> loot = null;
        for (String line : text.split("\\R")) {
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "version" -> version = Integer.parseInt(value);
                    case "seed" -> seed = Long.parseLong(value);
                    case "link" -> {
                        String[] parts = value.split(",");
                        if (parts.length != 4) {
                            return Optional.empty();
                        }
                        link = new int[4];
                        for (int i = 0; i < 4; i++) {
                            link[i] = Integer.parseInt(parts[i].trim());
                        }
                    }
                    case "facing" -> facing = Link.Dir.valueOf(value);
                    case "magic" -> magic = Integer.parseInt(value);
                    case "secret" -> secret = flag(value);
                    case "cave" -> cave = Integer.parseInt(value);
                    case "flute" -> flute = flag(value);
                    case "dungeon" -> dungeon = flag(value);
                    case "keys" -> keys = Integer.parseInt(value);
                    case "open" -> open = idList(value);
                    case "loot" -> loot = idList(value);
                    default -> {
                    }
                }
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        if (version == null || seed == null || link == null || facing == null) {
            return Optional.empty();
        }
        boolean known = version == VERSION || version == VERSION_V4 || version == VERSION_V3
                || version == VERSION_V2 || version == VERSION_V1;
        if (!known || !World.inWorld(link[0], link[1])
                || link[2] < 0 || link[2] >= World.SCREEN_W
                || link[3] < 0 || link[3] >= World.SCREEN_H) {
            return Optional.empty();
        }
        // V1 saves predate Magic and the Secret: a fresh run at the saved spot.
        int magicLeft = magic == null ? World.MAX_MAGIC : magic;
        if (magicLeft < 0 || magicLeft > World.MAX_MAGIC) {
            return Optional.empty();
        }
        // v2 stored a boolean: in a cave meant in the secret cave; v3 stores its key.
        int caveAt = cave == null ? NO_CAVE
                : version == VERSION_V2 ? (cave == 1 ? CAVE_SECRET_V2 : NO_CAVE)
                : cave;
        if (caveAt != NO_CAVE && caveAt != CAVE_SECRET_V2 && (caveAt < 0 || caveAt >= caveKeySpace())) {
            return Optional.empty();
        }
        if (keys != null && keys < 0) {
            return Optional.empty();
        }
        // v4 and older saves predate the dungeon: no keys, not inside, nothing taken.
        return Optional.of(new SaveState(seed, link[0], link[1], link[2], link[3], facing,
                magicLeft, secret != null && secret, caveAt, flute != null && flute,
                keys == null ? 0 : keys, dungeon != null && dungeon,
                open == null ? Set.of() : open, loot == null ? Set.of() : loot));
    }

    private static String join(Set<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        for (int i : new TreeSet<>(ids)) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i);
        }
        return sb.toString();
    }

    private static Set<Integer> idList(String value) {
        Set<Integer> out = new TreeSet<>();
        if (value.isEmpty()) return Set.of();
        List<String> parts = new ArrayList<>(List.of(value.split(",")));
        for (String p : parts) {
            int id = Integer.parseInt(p.trim());
            if (id < 0) throw new IllegalArgumentException(value);
            out.add(id);
        }
        return out;
    }

    private static int caveKeySpace() {
        return World.WORLD_W * World.SCREEN_W * World.WORLD_H * World.SCREEN_H;
    }

    private static Boolean flag(String value) {
        return switch (value) {
            case "0" -> Boolean.FALSE;
            case "1" -> Boolean.TRUE;
            default -> throw new IllegalArgumentException(value);
        };
    }
}
