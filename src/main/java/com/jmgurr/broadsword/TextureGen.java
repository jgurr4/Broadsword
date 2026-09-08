package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.jmgurr.broadsword.model.Tile;

import java.util.ArrayList;
import java.util.List;

/** Procedural placeholder art: builds the tile/UI/sprite sheets from pixels. */
public final class TextureGen {
    private TextureGen() {
    }

    /**
     * Tile strip, one cell per Tile ordinal:
     * GRASS DIRT SAND ROCK TREE TOMBSTONE WATER ENTRANCE FLAMMABLE_TREE STAIRS CAVE_FLOOR
     * DUNGEON_WALL DUNGEON_FLOOR DOOR LOCKED_DOOR DUNGEON_EXIT.
     */
    public static Texture tiles() {
        int w = Tile.values().length * GameConfig.TILE;
        Pixmap pm = new Pixmap(w, GameConfig.TILE, Pixmap.Format.RGBA8888);
        pm.setColor(Color.BLACK);
        pm.fill();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < GameConfig.TILE; y++) {
                int cell = x / GameConfig.TILE;
                int lx = x % GameConfig.TILE;
                int ly = y % GameConfig.TILE;
                int n = (x * 7 + y * 13) % 5;
                switch (cell) {
                    case 0 -> pm.setColor(n % 2 == 0 ? new Color(0.29f, 0.6f, 0.24f, 1) : new Color(0.25f, 0.55f, 0.22f, 1));
                    case 1 -> pm.setColor(n % 2 == 0 ? new Color(0.55f, 0.42f, 0.26f, 1) : new Color(0.5f, 0.38f, 0.23f, 1));
                    case 2 -> pm.setColor(n % 2 == 0 ? new Color(0.85f, 0.78f, 0.55f, 1) : new Color(0.8f, 0.73f, 0.5f, 1));
                    case 3 -> pm.setColor(lx < 2 || ly < 2 || lx >= 13 || ly >= 13 ? new Color(0.3f, 0.3f, 0.32f, 1) : new Color(0.55f, 0.55f, 0.58f, 1));
                    case 4 -> pm.setColor(ly >= 11 ? new Color(0.4f, 0.28f, 0.15f, 1) : n % 3 == 0 ? new Color(0.1f, 0.4f, 0.15f, 1) : new Color(0.15f, 0.45f, 0.2f, 1));
                    case 5 -> pm.setColor(ly >= 3 && lx >= 4 && lx <= 10 && (ly >= 5 || Math.abs(lx - 7) + ly <= 7) ? new Color(0.6f, 0.6f, 0.65f, 1) : new Color(0.25f, 0.5f, 0.2f, 1));
                    case 6 -> pm.setColor(n == 0 ? new Color(0.25f, 0.45f, 0.8f, 1) : new Color(0.2f, 0.4f, 0.75f, 1));
                    case 7 -> pm.setColor((ly >= 3 && lx >= 4 && lx <= 10) ? new Color(0.5f, 0.3f, 0.15f, 1) : new Color(0.12f, 0.1f, 0.14f, 1));
                    // same tree, dry autumn colours: reads as "this one could burn"
                    case 8 -> pm.setColor(ly >= 11 ? new Color(0.4f, 0.28f, 0.15f, 1) : n % 3 == 0 ? new Color(0.5f, 0.34f, 0.12f, 1) : new Color(0.62f, 0.45f, 0.16f, 1));
                    case 9 -> pm.setColor(ly >= 8 && lx >= 3 && lx <= 12
                            ? (lx + ly) % 4 < 2 ? new Color(0.75f, 0.7f, 0.6f, 1) : new Color(0.2f, 0.17f, 0.2f, 1)
                            : new Color(0.3f, 0.3f, 0.32f, 1));
                    // cave floor: near-black, faint speckle so the space still reads
                    case 10 -> pm.setColor(n == 0 ? new Color(0.08f, 0.08f, 0.11f, 1)
                            : new Color(0.05f, 0.05f, 0.075f, 1));
                    // dungeon wall: dark blue-grey masonry with bevelled courses
                    case 11 -> pm.setColor(ly % 8 < 2 || lx % 8 < 2
                            ? new Color(0.16f, 0.16f, 0.24f, 1)
                            : (lx + ly) % 6 == 0 ? new Color(0.34f, 0.34f, 0.46f, 1)
                                    : new Color(0.26f, 0.26f, 0.36f, 1));
                    // dungeon floor: grey stone slab, lighter than the wall
                    case 12 -> pm.setColor(lx == 0 || ly == 0 || lx == 15 || ly == 15
                            ? new Color(0.28f, 0.27f, 0.33f, 1)
                            : n == 0 ? new Color(0.46f, 0.45f, 0.52f, 1)
                                    : new Color(0.4f, 0.39f, 0.46f, 1));
                    // door: a brown arched door in a stone frame
                    case 13 -> pm.setColor((lx < 2 || lx >= 14 || ly < 2) ? new Color(0.32f, 0.3f, 0.36f, 1)
                            : lx >= 4 && lx <= 11 ? new Color(0.5f, 0.32f, 0.14f, 1)
                                    : new Color(0.42f, 0.27f, 0.12f, 1));
                    // locked door: same arch, barred over
                    case 14 -> pm.setColor((lx < 2 || lx >= 14 || ly < 2) ? new Color(0.32f, 0.3f, 0.36f, 1)
                            : lx >= 4 && lx <= 11
                                    ? (ly % 5 < 2 || lx == 4 || lx == 11
                                            ? new Color(0.55f, 0.55f, 0.6f, 1) : new Color(0.45f, 0.3f, 0.14f, 1))
                                    : new Color(0.42f, 0.27f, 0.12f, 1));
                    // dungeon exit: the door arch glows white
                    default -> pm.setColor((lx < 2 || lx >= 14 || ly < 2) ? new Color(0.32f, 0.3f, 0.36f, 1)
                            : lx >= 4 && lx <= 11 && (lx + ly) % 3 != 0
                                    ? new Color(0.92f, 0.92f, 0.98f, 1)
                                    : new Color(0.7f, 0.7f, 0.8f, 1));
                }
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

    public static Texture ui() {
        int w = 4 * GameConfig.TILE;
        Pixmap pm = new Pixmap(w, GameConfig.TILE, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < GameConfig.TILE; y++) {
                int cell = x / GameConfig.TILE;
                int lx = x % GameConfig.TILE;
                int ly = y % GameConfig.TILE;
                Color c = Color.CLEAR;
                switch (cell) {
                    case 0 -> c = heart(lx, ly) ? new Color(0.85f, 0.15f, 0.15f, 1) : Color.CLEAR;
                    case 1 -> c = (lx >= 4 && lx <= 10 && ly >= 4 && ly <= 10) ? new Color(0.2f, 0.5f, 0.95f, 1) : Color.CLEAR;
                    case 2 -> {
                        int dx = Math.abs(lx - 7);
                        int dy = Math.abs(ly - 7);
                        c = (dy <= dx && dx + dy <= 8) ? new Color(0.95f, 0.8f, 0.2f, 1) : Color.CLEAR;
                    }
                    case 3 -> {
                        // half Heart: the left half of the full Heart cell
                        if (lx >= 7) break;
                        c = heart(lx, ly) ? new Color(0.85f, 0.15f, 0.15f, 1) : Color.CLEAR;
                    }
                }
                pm.setColor(c);
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

    /**
     * Sprite strip: cell 0 Link front/back, cell 1 Grunt, cell 2 sword blade
     * (horizontal), cell 3 Octorock, cell 4 Fireball, cell 5 Link side profile
     * (facing left; flip for right), cell 6 sword blade (vertical), cell 7
     * shield, cell 8 spawn cloud, cell 9 Link from behind (facing up).
     */
    public static Texture sprites() {
        int w = SPRITE_CELLS * GameConfig.TILE;
        Pixmap pm = new Pixmap(w, GameConfig.TILE, Pixmap.Format.RGBA8888);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < GameConfig.TILE; y++) {
                int cell = x / GameConfig.TILE;
                int lx = x % GameConfig.TILE, ly = y;
                Color c = Color.CLEAR;
                switch (cell) {
                    case 0 -> {
                        if (ly <= 4) {
                            c = new Color(0.2f, 0.4f, 0.9f, 1); // hat
                        } else if (ly <= 6) {
                            c = new Color(0.9f, 0.75f, 0.55f, 1); // face
                        } else if (ly <= 12 && lx >= 4 && lx <= 10) {
                            c = new Color(0.2f, 0.7f, 0.3f, 1); // tunic
                        } else if (ly >= 13 && lx >= 5 && lx <= 9) {
                            c = new Color(0.3f, 0.25f, 0.5f, 1); // legs
                        }
                    }
                    case 1 -> {
                        // Grunt: a dark blob with two eyes
                        if (lx >= 2 && lx <= 12 && ly >= 3 && ly <= 12) {
                            c = new Color(0.55f, 0.25f, 0.2f, 1);
                        }
                        if ((lx == 5 || lx == 9) && ly >= 5 && ly <= 6) {
                            c = new Color(1f, 0.9f, 0.3f, 1);
                        }
                    }
                    case 2 -> {
                        // sword: a pale blade pointing right
                        if (lx >= 2 && lx <= 12 && ly >= 6 && ly <= 8) {
                            c = new Color(0.9f, 0.9f, 0.95f, 1);
                        }
                    }
                    case 3 -> {
                        // Octorock: an orange squid blob with two eyes
                        if (lx >= 2 && lx <= 12 && ly >= 4 && ly <= 12) {
                            c = new Color(0.9f, 0.5f, 0.15f, 1);
                        }
                        if ((lx == 5 || lx == 9) && ly >= 6 && ly <= 7) {
                            c = new Color(0.1f, 0.1f, 0.4f, 1);
                        }
                    }
                    case 4 -> {
                        // Fireball: an orange-yellow dot
                        if (lx >= 4 && lx <= 10 && ly >= 4 && ly <= 10) {
                            c = new Color(1f, 0.7f, 0.1f, 1);
                        }
                    }
                    case 5 -> {
                        // Link side profile, facing left: hat, face with eye on
                        // the left edge, tunic, legs mid-stride
                        if (ly <= 4) {
                            c = new Color(0.2f, 0.4f, 0.9f, 1); // hat
                        } else if (ly <= 6) {
                            c = lx >= 4 && lx <= 10 ? new Color(0.9f, 0.75f, 0.55f, 1) : Color.CLEAR;
                            if (lx == 5 && ly == 6) {
                                c = new Color(0.1f, 0.1f, 0.3f, 1); // eye
                            }
                        } else if (ly <= 12 && lx >= 5 && lx <= 10) {
                            c = new Color(0.2f, 0.7f, 0.3f, 1); // tunic
                        } else if (ly >= 13 && lx >= 5 && lx <= 7) {
                            c = new Color(0.3f, 0.25f, 0.5f, 1); // leading leg
                        } else if (ly >= 14 && lx >= 8 && lx <= 10) {
                            c = new Color(0.25f, 0.2f, 0.45f, 1); // trailing leg
                        }
                    }
                    case 6 -> {
                        // sword: a pale blade pointing up
                        if (lx >= 6 && lx <= 8 && ly >= 2 && ly <= 12) {
                            c = new Color(0.9f, 0.9f, 0.95f, 1);
                        }
                    }
                    case 7 -> {
                        // shield: a blue rounded shield with a pale boss
                        boolean body = lx >= 3 && lx <= 11 && ly >= 3 && ly <= 12
                                && !(ly >= 11 && (lx <= 4 || lx >= 10));
                        if (body) {
                            c = new Color(0.15f, 0.35f, 0.8f, 1);
                            if (lx >= 6 && lx <= 8 && ly >= 6 && ly <= 8) {
                                c = new Color(0.9f, 0.9f, 0.95f, 1);
                            } else if (lx == 3 || lx == 11 || ly == 3 || (ly == 12 && lx > 4 && lx < 10)) {
                                c = new Color(0.5f, 0.52f, 0.6f, 1); // rim
                            }
                        }
                    }
                    case 8 -> {
                        // spawn cloud: two overlapping grey puffs
                        int dx1 = Math.max(0, Math.abs(lx - 5) + Math.abs(ly - 8) - 3);
                        int dx2 = Math.max(0, Math.abs(lx - 10) + Math.abs(ly - 7) - 3);
                        if (dx1 == 0 || dx2 == 0) {
                            c = ((lx + ly) % 3 == 0) ? new Color(0.75f, 0.75f, 0.8f, 0.9f)
                                    : new Color(0.62f, 0.62f, 0.7f, 0.85f);
                        }
                    }
                    case 10 -> {
                        // Ghost: a pale translucent hooded wisp
                        boolean head = lx >= 4 && lx <= 10 && ly >= 2 && ly <= 8;
                        boolean skirt = lx >= 3 && lx <= 11 && ly >= 9 && ly <= 13
                                && (lx + ly) % 4 != 0; // ragged hem
                        if (head || skirt) {
                            c = new Color(0.85f, 0.88f, 1f, 0.65f);
                        }
                        if ((lx == 6 || lx == 9) && ly >= 4 && ly <= 5) {
                            c = new Color(0.15f, 0.15f, 0.35f, 0.9f); // eyes
                        }
                    }
                    case 12 -> {
                        // Key: a small gold key
                        if (lx >= 3 && lx <= 6 && ly >= 3 && ly <= 6) {
                            c = new Color(0.95f, 0.8f, 0.2f, 1);
                        }
                        if (lx == 4 && ly >= 6 && ly <= 12) {
                            c = new Color(0.95f, 0.8f, 0.2f, 1); // shaft
                        }
                        if (lx >= 4 && lx <= 7 && (ly == 10 || ly == 12)) {
                            c = new Color(0.95f, 0.8f, 0.2f, 1); // bits
                        }
                    }
                    case 13 -> {
                        // Item chest: brown box with a gold band
                        if (lx >= 2 && lx <= 13 && ly >= 5 && ly <= 13) {
                            c = new Color(0.45f, 0.3f, 0.14f, 1);
                        }
                        if (ly == 7 || ly == 8) {
                            c = new Color(0.9f, 0.78f, 0.25f, 1);
                        }
                        if (lx == 7 || lx == 8) {
                            c = new Color(0.9f, 0.78f, 0.25f, 1); // lock strip
                        }
                        if (lx == 2 || lx == 13 || ly == 5 || ly == 13) {
                            c = new Color(0.3f, 0.2f, 0.1f, 1); // edge
                        }
                    }
                    case 14 -> {
                        // Shoveable block: a carved stone cube with bevelled faces
                        if (lx >= 1 && lx <= 13 && ly >= 1 && ly <= 13) {
                            c = new Color(0.5f, 0.48f, 0.55f, 1);
                        }
                        if (lx >= 3 && lx <= 11 && ly >= 3 && ly <= 11) {
                            c = new Color(0.62f, 0.6f, 0.68f, 1); // raised face
                        }
                        if (lx == 1 || lx == 13 || ly == 1 || ly == 13) {
                            c = new Color(0.3f, 0.29f, 0.36f, 1); // edge
                        }
                    }
                    case 15 -> {
                        // Hydra body: a low dark-green scaled mound
                        if (lx >= 1 && lx <= 13 && ly >= 6 && ly <= 13) {
                            c = new Color(0.16f, 0.42f, 0.22f, 1);
                        }
                        if (lx >= 3 && lx <= 11 && ly >= 3 && ly <= 7) {
                            c = new Color(0.2f, 0.52f, 0.28f, 1); // hump
                        }
                        if ((lx + ly) % 4 == 0 && ly >= 6 && ly <= 12 && lx >= 2 && lx <= 12) {
                            c = new Color(0.12f, 0.32f, 0.17f, 1); // scale speckles
                        }
                        if (ly == 13 || lx == 1 || lx == 13) {
                            c = new Color(0.08f, 0.22f, 0.12f, 1); // edge
                        }
                    }
                    case 16 -> {
                        // Hydra head: green neck, open red-eyed jaw facing down
                        if (lx >= 5 && lx <= 9 && ly >= 2 && ly <= 7) {
                            c = new Color(0.2f, 0.5f, 0.26f, 1); // neck
                        }
                        if (lx >= 3 && lx <= 12 && ly >= 7 && ly <= 12) {
                            c = new Color(0.24f, 0.58f, 0.3f, 1); // skull
                        }
                        if ((lx == 5 || lx == 10) && ly >= 8 && ly <= 9) {
                            c = new Color(0.95f, 0.2f, 0.15f, 1); // eyes
                        }
                        if (lx >= 5 && lx <= 10 && (ly == 11 || ly == 12)) {
                            c = new Color(0.85f, 0.85f, 0.6f, 1); // fangs
                        }
                    }
                    case 11 -> {
                        // Flute: a pale gold pipe lying on the ground
                        if (lx >= 3 && lx <= 11 && ly >= 6 && ly <= 8) {
                            c = new Color(0.95f, 0.85f, 0.35f, 1);
                        }
                        if ((lx == 5 || lx == 8) && ly == 7) {
                            c = new Color(0.55f, 0.45f, 0.1f, 1); // holes
                        }
                    }
                    default -> {
                        // Link from behind: hat peak, no face, boots apart
                        if (ly <= 4) {
                            c = lx >= 5 && lx <= 10 ? new Color(0.18f, 0.35f, 0.8f, 1) : Color.CLEAR;
                        } else if (ly <= 6 && lx >= 4 && lx <= 11) {
                            c = new Color(0.75f, 0.6f, 0.45f, 1); // back of head, shaded skin
                        } else if (ly <= 12 && lx >= 4 && lx <= 10) {
                            c = new Color(0.18f, 0.6f, 0.27f, 1); // tunic back
                        } else if (ly >= 13 && lx >= 4 && lx <= 6) {
                            c = new Color(0.3f, 0.25f, 0.5f, 1); // legs
                        } else if (ly >= 13 && lx >= 8 && lx <= 10) {
                            c = new Color(0.25f, 0.2f, 0.45f, 1);
                        }
                    }
                }
                pm.setColor(c);
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

    /** Sprite strip cells: Link, Grunt, sword, Octorock, fireball, Link-left, sword-up, shield, cloud, Link-back, Ghost, Flute, Key, Chest, Block, Hydra body, Hydra head. */
    public static final int SPRITE_CELLS = 17;
    public static final int SPRITE_HYDRA_BODY = 15;
    public static final int SPRITE_HYDRA_HEAD = 16;
    public static final int SPRITE_BLOCK = 14;
    public static final int SPRITE_GHOST = 10;
    public static final int SPRITE_FLUTE = 11;
    public static final int SPRITE_KEY = 12;
    public static final int SPRITE_CHEST = 13;

    private static boolean heart(int x, int y) {
        // classic 5x3 pixel heart, scaled to 3x per pixel
        int[] hx = {1, 1, 2, 2, 3};
        int[] hy = {0, 1, 0, 1, 2};
        int px = x / 3, py = y / 3;
        for (int i = 0; i < hx.length; i++) {
            if (Math.abs(px - hx[i]) <= 0 && py == hy[i]) {
                return true;
            }
        }
        // fill: two bumps + point
        boolean bumpL = (px == 1 || px == 2) && (py == 0 || py == 1);
        boolean bumpR = (px == 3 || px == 4) && (py == 0 || py == 1);
        boolean mid = (px == 2 || px == 3) && py == 2;
        boolean point = px == 2 && py == 3;
        return bumpL || bumpR || mid || point;
    }

    /** Region from a horizontal strip at cell i; Pixmap row 0 is the texture top. */
    public static TextureRegion region(Texture tex, int cell) {
        int h = GameConfig.TILE;
        int y = tex.getHeight() - h; // strip is one row tall; flip Pixmap->GL coords
        return new TextureRegion(tex, cell * h, y, h, h);
    }

    /** All regions for a strip texture, in cell order. */
    public static List<TextureRegion> regions(Texture tex, int count) {
        List<TextureRegion> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(region(tex, i));
        }
        return out;
    }
}
