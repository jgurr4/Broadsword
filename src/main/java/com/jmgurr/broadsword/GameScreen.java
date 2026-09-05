package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import com.jmgurr.broadsword.model.Link;
import com.jmgurr.broadsword.model.Sim;
import com.jmgurr.broadsword.model.Tile;
import com.jmgurr.broadsword.model.World;

public class GameScreen implements Screen {
    private final BroadswordGame game;
    private final Sim sim;
    private final Texture tiles;
    private final Texture ui;
    private final Texture sprites;
    private final TextureRegion[] tileRegions;

    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private Link.Dir desired = null;

    public GameScreen(BroadswordGame game, Sim sim) {
        this.game = game;
        this.sim = sim;
        sim.setSaveSink(SaveFiles::write);
        // anchor the save at spawn right away: closing before the first
        // transition must never leave the previous run's save behind
        sim.autosave();
        this.tiles = game.tiles();
        this.ui = game.ui();
        this.sprites = game.sprites();
        // tile strip cells match the Tile enum ordinals
        this.tileRegions = new TextureRegion[] {
            TextureGen.region(tiles, 0),
            TextureGen.region(tiles, 1),
            TextureGen.region(tiles, 2),
            TextureGen.region(tiles, 3),
            TextureGen.region(tiles, 4),
            TextureGen.region(tiles, 5),
            TextureGen.region(tiles, 6),
            TextureGen.region(tiles, 7)
        };
    }

    @Override
    public void render(float delta) {
        desired = readInput();
        boolean swing = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        if (sim.phase() == Sim.Phase.GAME_OVER && Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            sim.respawn();
        }
        sim.tick(Math.min(delta, 0.1f), desired, swing);
        draw();
    }

    /** Draw the UI cell at (x, y) in a given color, tinting the region white base. */
    private void drawUiCell(SpriteBatch b, int cell, float x, float y, com.badlogic.gdx.graphics.Color tint) {
        b.setColor(tint);
        b.draw(TextureGen.region(ui, cell), x, y);
        b.setColor(1, 1, 1, 1);
    }

    private Link.Dir readInput() {
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            return Link.Dir.UP;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            return Link.Dir.DOWN;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            return Link.Dir.LEFT;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            return Link.Dir.RIGHT;
        }
        return null;
    }

    private void draw() {
        Link link = sim.link();
        SpriteBatch b = game.batch();
        b.setProjectionMatrix(game.viewport().getCamera().combined);
        b.begin();
        float linkPxX, linkPxY;
        if (sim.interpolating()) {
            // interpolation only happens within one screen: screen-local start + progress
            Link.Dir d = sim.interpolatingDir();
            float fx = link.tx - d.dx + d.dx * sim.interpProgress();
            float fy = link.ty - d.dy + d.dy * sim.interpProgress();
            linkPxX = fx * GameConfig.TILE;
            linkPxY = (World.SCREEN_H - 1 - fy) * GameConfig.TILE;
        } else {
            linkPxX = link.tx * GameConfig.TILE;
            linkPxY = (World.SCREEN_H - 1 - link.ty) * GameConfig.TILE;
        }
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                Tile t = sim.world().screen(link.sx, link.sy).get(x, y);
                int cell = t.ordinal();
                b.draw(tileRegions[cell], x * GameConfig.TILE, (World.SCREEN_H - 1 - y) * GameConfig.TILE);
            }
        }
        // enemies: every live enemy of the current screen, in a distinct colour
        com.badlogic.gdx.graphics.Color flash =
                sim.invulnerable() && ((System.nanoTime() / 80_000_000L) & 1) == 0
                        ? new com.badlogic.gdx.graphics.Color(1, 0.4f, 0.4f, 1)
                        : com.badlogic.gdx.graphics.Color.WHITE;
        b.setColor(flash);
        for (com.jmgurr.broadsword.model.Enemy e : sim.enemies()) {
            if (!e.alive) {
                continue;
            }
            float ex = e.tx * GameConfig.TILE;
            float ey = (World.SCREEN_H - 1 - e.ty) * GameConfig.TILE;
            if (Sim.spawning(e)) {
                // pulsing cloud: the enemy materialises after ENEMY_SPAWN_DURATION
                float pulse = 0.5f + 0.5f * (e.spawning / Sim.ENEMY_SPAWN_DURATION);
                b.setColor(1, 1, 1, 0.55f + 0.45f * pulse);
                b.draw(TextureGen.region(sprites, 8), ex, ey);
                b.setColor(flash);
            } else {
                int cell = e.kind == com.jmgurr.broadsword.model.EnemyKind.OCTOROCK ? 3 : 1;
                b.draw(TextureGen.region(sprites, cell), ex, ey);
            }
        }
        b.setColor(1, 1, 1, 1);
        for (com.jmgurr.broadsword.model.Projectile p : sim.projectiles()) {
            if (p.alive) {
                b.draw(TextureGen.region(sprites, 4), p.tx * GameConfig.TILE, (World.SCREEN_H - 1 - p.ty) * GameConfig.TILE);
            }
        }
        // Link: a distinct sprite per facing direction (side profile flipped L/R)
        Link.Dir f = link.facing;
        switch (f) {
            case UP -> b.draw(TextureGen.region(sprites, 9), linkPxX, linkPxY, GameConfig.TILE, GameConfig.TILE);
            case DOWN -> b.draw(TextureGen.region(sprites, 0), linkPxX, linkPxY, GameConfig.TILE, GameConfig.TILE);
            case LEFT -> b.draw(TextureGen.region(sprites, 5), linkPxX, linkPxY, GameConfig.TILE, GameConfig.TILE);
            case RIGHT -> b.draw(TextureGen.region(sprites, 5), linkPxX + GameConfig.TILE, linkPxY,
                    -GameConfig.TILE, GameConfig.TILE); // negative width flips horizontally
        }
        // shield: carried on the edge Link faces; tucked away while swinging
        if (!sim.swinging()) {
            b.draw(TextureGen.region(sprites, 7),
                    linkPxX + f.dx * 4, linkPxY + f.dy * 4);
        }
        // sword: the blade out in front of Link while swinging, oriented along
        // the swing so up/down stabs read as stabs (negative size flips)
        if (sim.swinging()) {
            float w = GameConfig.TILE * 0.6f;
            float h = GameConfig.TILE * 1.6f;
            switch (f) {
                case RIGHT -> b.draw(TextureGen.region(sprites, 2), linkPxX + GameConfig.TILE * 0.4f, linkPxY, h, GameConfig.TILE);
                case LEFT -> b.draw(TextureGen.region(sprites, 2), linkPxX + GameConfig.TILE * 0.6f, linkPxY, -h, GameConfig.TILE);
                // pixel y grows up-screen: UP extends with +h, DOWN with -h
                case UP -> b.draw(TextureGen.region(sprites, 6), linkPxX + (GameConfig.TILE - w) / 2, linkPxY - GameConfig.TILE * 0.3f, w, h);
                case DOWN -> b.draw(TextureGen.region(sprites, 6), linkPxX + (GameConfig.TILE - w) / 2, linkPxY + GameConfig.TILE * 0.3f, w, -h);
            }
        }
        // HUD: hearts top-left (filled vs. lost), magic below (both inset from the top edge)
        for (int i = 0; i < World.MAX_HEARTS; i++) {
            drawUiCell(b, 0, 3 + i * 12, GameConfig.LOGICAL_H - 19,
                    i < link.hearts ? com.badlogic.gdx.graphics.Color.WHITE : new com.badlogic.gdx.graphics.Color(0.3f, 0.3f, 0.3f, 1f));
        }
        for (int i = 0; i < GameConfig.MAX_MAGIC; i++) {
            b.draw(TextureGen.region(ui, 1), 3 + i * 12, GameConfig.LOGICAL_H - 38);
        }
        if (sim.phase() == Sim.Phase.GAME_OVER) {
            layout.setText(font, "GAME OVER");
            font.draw(b, "GAME OVER", (GameConfig.LOGICAL_W - layout.width) / 2, GameConfig.LOGICAL_H / 2f + 6);
            layout.setText(font, "press R to respawn");
            font.draw(b, "press R to respawn",
                    (GameConfig.LOGICAL_W - layout.width) / 2, GameConfig.LOGICAL_H / 2f - 10);
        }
        // dev readout: what the screen is (a landmark overrides its archetype),
        // screen:tiles and the direction the input layer sees
        World world = sim.world();
        com.jmgurr.broadsword.model.Landmark lm = world.landmarkAt(link.sx, link.sy);
        String dbg = String.format("%s %d:%d %d:%d %s",
                lm != null ? lm.name() : world.archetype(link.sx, link.sy).name(),
                link.sx, link.sy, link.tx, link.ty,
                desired == null ? "-" : desired.name());
        layout.setText(font, dbg);
        // y-up camera: y is the text baseline; place by ascent so the glyph tops
        // sit 2px below the top edge, 4px in from the right edge
        float textY = GameConfig.LOGICAL_H - font.getAscent() - 2;
        font.draw(b, dbg, GameConfig.LOGICAL_W - layout.width - 4, textY);
        b.end();
    }

    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void resize(int width, int height) {
        game.viewport().update(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
    }
}
