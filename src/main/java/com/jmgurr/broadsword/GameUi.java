package com.jmgurr.broadsword;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.jmgurr.broadsword.model.Sim;
import com.jmgurr.broadsword.model.World;

/**
 * The shared UI components every state screen draws through, so the HUD and the
 * overlays look the same everywhere: Hearts top left, Magic pips top right, and
 * one centered overlay for the states that sit on top of a run.
 */
final class GameUi {
    /** Cell stride for one HUD icon: 15px art, 1px gap. */
    private static final int ICON_STRIDE = GameConfig.TILE - 4;
    private static final int HUD_TOP = GameConfig.LOGICAL_H - GameConfig.TILE - 1;
    private static final GlyphLayout layout = new GlyphLayout();
    private static final Color DIM = new Color(0.32f, 0.32f, 0.36f, 1f);

    private GameUi() {
    }

    /** The run HUD, drawn last on every gameplay screen (overworld, cave, dungeon). */
    static void hud(SpriteBatch batch, Texture ui, Sim sim) {
        for (int i = 0; i < World.MAX_HEARTS; i++) {
            float remain = sim.link().hearts - i;
            batch.setColor(remain >= 0.5f ? Color.WHITE : DIM);
            batch.draw(TextureGen.region(ui, remain >= 1f ? 0 : remain >= 0.5f ? 3 : 0),
                    2 + i * ICON_STRIDE, HUD_TOP);
        }
        for (int i = 0; i < GameConfig.MAX_MAGIC; i++) {
            // right-aligned, so the pip order reads left to right as 1..MAX_MAGIC
            int slot = GameConfig.MAX_MAGIC - 1 - i;
            batch.setColor(i < sim.magic() ? Color.WHITE : DIM);
            batch.draw(TextureGen.region(ui, 1),
                    GameConfig.LOGICAL_W - 2 - (slot + 1) * ICON_STRIDE, HUD_TOP);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** One centered overlay: a dark scrim plus a title line and smaller body lines. */
    static void overlay(SpriteBatch batch, BitmapFont font, Texture ui, String title, String... lines) {
        batch.setColor(0f, 0f, 0.06f, 0.82f);
        batch.draw(TextureGen.region(ui, TextureGen.UI_SOLID), 0, 0,
                GameConfig.LOGICAL_W, GameConfig.LOGICAL_H);
        batch.setColor(1, 1, 1, 1);
        float y = GameConfig.LOGICAL_H / 2f + 16;
        font.getData().setScale(1f);
        centeredText(batch, font, title, y);
        font.getData().setScale(0.5f);
        y -= 4;
        for (String line : lines) {
            centeredText(batch, font, line, y);
            y -= 9;
        }
        font.getData().setScale(1f);
    }

    static void centeredText(SpriteBatch batch, BitmapFont font, String text, float y) {
        layout.setText(font, text);
        font.draw(batch, layout, (GameConfig.LOGICAL_W - layout.width) / 2f, y);
    }
}
