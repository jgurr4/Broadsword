package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.ArrayList;
import java.util.List;

/** Procedural placeholder art: builds the tile/UI/sprite sheets from pixels. */
public final class TextureGen {
    private TextureGen() {
    }

    public static Texture tiles() {
        int w = 6 * GameConfig.TILE;
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
                    case 1 -> pm.setColor(lx < 2 || ly < 2 || lx >= 13 || ly >= 13 ? new Color(0.3f, 0.3f, 0.32f, 1) : new Color(0.55f, 0.55f, 0.58f, 1));
                    case 2 -> pm.setColor(ly >= 11 ? new Color(0.4f, 0.28f, 0.15f, 1) : n % 3 == 0 ? new Color(0.1f, 0.4f, 0.15f, 1) : new Color(0.15f, 0.45f, 0.2f, 1));
                    case 3 -> pm.setColor(n == 0 ? new Color(0.25f, 0.45f, 0.8f, 1) : new Color(0.2f, 0.4f, 0.75f, 1));
                    case 4 -> pm.setColor(n % 2 == 0 ? new Color(0.2f, 0.18f, 0.22f, 1) : new Color(0.18f, 0.16f, 0.2f, 1));
                    default -> pm.setColor((ly >= 3 && lx >= 4 && lx <= 10) ? new Color(0.5f, 0.3f, 0.15f, 1) : new Color(0.12f, 0.1f, 0.14f, 1));
                }
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

    public static Texture ui() {
        int w = 3 * GameConfig.TILE;
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
                    default -> {
                        int dx = Math.abs(lx - 7);
                        int dy = Math.abs(ly - 7);
                        c = (dy <= dx && dx + dy <= 8) ? new Color(0.95f, 0.8f, 0.2f, 1) : Color.CLEAR;
                    }
                }
                pm.setColor(c);
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

    public static Texture sprites() {
        Pixmap pm = new Pixmap(GameConfig.TILE, GameConfig.TILE, Pixmap.Format.RGBA8888);
        for (int x = 0; x < GameConfig.TILE; x++) {
            for (int y = 0; y < GameConfig.TILE; y++) {
                int lx = x, ly = y;
                Color c = Color.CLEAR;
                if (ly <= 4) {
                    c = new Color(0.2f, 0.4f, 0.9f, 1); // hat
                } else if (ly <= 6) {
                    c = new Color(0.9f, 0.75f, 0.55f, 1); // face
                } else if (ly <= 12 && lx >= 4 && lx <= 10) {
                    c = new Color(0.2f, 0.7f, 0.3f, 1); // tunic
                } else if (ly >= 13 && lx >= 5 && lx <= 9) {
                    c = new Color(0.3f, 0.25f, 0.5f, 1); // legs
                }
                pm.setColor(c);
                pm.drawPixel(x, y);
            }
        }
        return new Texture(pm);
    }

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
