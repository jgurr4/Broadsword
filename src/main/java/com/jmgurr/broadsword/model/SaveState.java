package com.jmgurr.broadsword.model;

import java.util.Optional;

/**
 * The persistent state of a run: seed + Link's position. The world itself
 * re-derives from the seed; nothing else persists in V1 so far (killed
 * enemies and uncollected pickups respawn per the save model).
 *
 * Text format, one key=value per line. No libgdx types: parsing is testable
 * headlessly; file I/O lives in the render layer.
 */
public record SaveState(long seed, int sx, int sy, int tx, int ty, Link.Dir facing) {
    public static final int VERSION = 1;

    public String format() {
        return "version=" + VERSION + "\n"
                + "seed=" + seed + "\n"
                + "link=" + sx + "," + sy + "," + tx + "," + ty + "\n"
                + "facing=" + facing.name() + "\n";
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
                    default -> {
                    }
                }
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        if (version == null || version != VERSION || seed == null || link == null || facing == null) {
            return Optional.empty();
        }
        if (!World.inWorld(link[0], link[1])
                || link[2] < 0 || link[2] >= World.SCREEN_W
                || link[3] < 0 || link[3] >= World.SCREEN_H) {
            return Optional.empty();
        }
        return Optional.of(new SaveState(seed, link[0], link[1], link[2], link[3], facing));
    }
}
