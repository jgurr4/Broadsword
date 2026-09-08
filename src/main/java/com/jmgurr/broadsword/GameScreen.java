package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import com.jmgurr.broadsword.model.DungeonScreen;
import com.jmgurr.broadsword.model.Link;
import com.jmgurr.broadsword.model.Sim;
import com.jmgurr.broadsword.model.Tile;
import com.jmgurr.broadsword.model.World;

import java.util.Map;

public class GameScreen implements Screen {
    private final BroadswordGame game;
    private final Sim sim;
    private final Texture tiles;
    private final Texture ui;
    private final Texture sprites;
    private final TextureRegion[] tileRegions;
    private final TextureRegion keySprite;
    private final TextureRegion chestSprite;
    private final TextureRegion blockSprite;

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
        this.tileRegions = TextureGen.regions(tiles, Tile.values().length)
                .toArray(new TextureRegion[0]);
        this.keySprite = TextureGen.region(sprites, TextureGen.SPRITE_KEY);
        this.chestSprite = TextureGen.region(sprites, TextureGen.SPRITE_CHEST);
        this.blockSprite = TextureGen.region(sprites, TextureGen.SPRITE_BLOCK);
    }

    @Override
    public void render(float delta) {
        desired = readInput();
        boolean swing = Gdx.input.isKeyJustPressed(Input.Keys.SPACE);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
            sim.castLight();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            sim.playFlute(); // the tune: dispels this screen's Ghosts, once per visit
        }
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
                Tile t;
                if (sim.inDungeon()) {
                    t = sim.dungeonScreen().grid().get(x, y);
                    // an open lock looks like the open door it became
                    if (t == Tile.LOCKED_DOOR) {
                        Map.Entry<Link.Dir, DungeonScreen.Door> d = sim.dungeonScreen().doorAt(x, y);
                        if (d != null && sim.dungeonRun().isOpen(d.getValue().lockId())) {
                            t = Tile.DOOR;
                        }
                    }
                } else {
                    t = sim.inCave() ? sim.currentCave().room().get(x, y)
                            : sim.world().screen(link.sx, link.sy).get(x, y);
                }
                if (sim.inCave() && t == Tile.ROCK) {
                    b.setColor(0.3f, 0.3f, 0.34f, 1f); // cave walls stay dim next to the dark floor
                }
                b.draw(tileRegions[t.ordinal()], x * GameConfig.TILE, (World.SCREEN_H - 1 - y) * GameConfig.TILE);
                b.setColor(1, 1, 1, 1);
            }
        }
        // shoveable blocks at their live positions (pushed blocks stay pushed)
        if (sim.inDungeon()) {
            for (com.jmgurr.broadsword.model.ScreenPos p : sim.dungeonRun()
                    .blocks(sim.dungeonScreenIndex(), sim.dungeonScreen())) {
                b.draw(blockSprite, p.tx() * GameConfig.TILE, (World.SCREEN_H - 1 - p.ty()) * GameConfig.TILE);
            }
        }
        // loot revealed by a block trigger: only visible once revealed
        if (sim.inDungeon()) {
            for (com.jmgurr.broadsword.model.Lootable loot : sim.dungeonScreen().hiddenLoot()) {
                if (sim.dungeonRun().revealed(loot.id()) && !sim.dungeonRun().taken(loot.id())) {
                    b.draw(chestSprite, loot.tx() * GameConfig.TILE,
                            (World.SCREEN_H - 1 - loot.ty()) * GameConfig.TILE);
                }
            }
        }
        // Light beam: two tiles straight ahead of the facing it was cast along
        if (sim.lightVisible()) {
            Link.Dir lf = sim.lightFxFacing();
            for (int i = 1; i <= Sim.LIGHT_RANGE; i++) {
                int lx = link.tx + lf.dx * i, ly = link.ty + lf.dy * i;
                if (lx < 0 || lx >= World.SCREEN_W || ly < 0 || ly >= World.SCREEN_H) {
                    continue;
                }
                b.setColor(1, 1, 0.7f, 0.5f);
                b.draw(TextureGen.region(ui, 2), lx * GameConfig.TILE, (World.SCREEN_H - 1 - ly) * GameConfig.TILE);
                b.setColor(1, 1, 1, 1);
            }
        }
        // enemies: every live enemy of the current screen, in a distinct colour
        com.badlogic.gdx.graphics.Color flash =
                sim.invulnerable() && ((System.nanoTime() / 80_000_000L) & 1) == 0
                        ? new com.badlogic.gdx.graphics.Color(1, 0.4f, 0.4f, 1)
                        : com.badlogic.gdx.graphics.Color.WHITE;
        b.setColor(flash);
        // dungeon loot not yet taken: keys and one-item chests
        if (sim.inDungeon()) {
            for (com.jmgurr.broadsword.model.Lootable loot : sim.dungeonScreen().keys()) {
                if (!sim.dungeonRun().taken(loot.id())) {
                    b.draw(keySprite, loot.tx() * GameConfig.TILE,
                            (World.SCREEN_H - 1 - loot.ty()) * GameConfig.TILE);
                }
            }
            for (com.jmgurr.broadsword.model.Lootable loot : sim.dungeonScreen().items()) {
                if (!sim.dungeonRun().taken(loot.id())) {
                    b.draw(chestSprite, loot.tx() * GameConfig.TILE,
                            (World.SCREEN_H - 1 - loot.ty()) * GameConfig.TILE);
                }
            }
        }
        // the Flute, still on its tile until Link walks onto it
        if (!sim.hasFlute() && sim.world().flute() != null && !sim.inCave()
                && sim.world().flute().sx() == link.sx && sim.world().flute().sy() == link.sy) {
            com.jmgurr.broadsword.model.ScreenPos fp = sim.world().flute();
            b.draw(TextureGen.region(sprites, TextureGen.SPRITE_FLUTE),
                    fp.tx() * GameConfig.TILE, (World.SCREEN_H - 1 - fp.ty()) * GameConfig.TILE);
        }
        for (com.jmgurr.broadsword.model.Enemy e : sim.enemies()) {
            if (!e.alive) {
                continue;
            }
            // glide from the tile the slide started on to the one it arrived at;
            // ethereal movers are already in smooth float space
            float etx = e.ethereal ? (float) e.fx : e.fromTx + (e.tx - e.fromTx) * (1 - e.interp);
            float ety = e.ethereal ? (float) e.fy : e.fromTy + (e.ty - e.fromTy) * (1 - e.interp);
            float ex = etx * GameConfig.TILE;
            float ey = (World.SCREEN_H - 1 - ety) * GameConfig.TILE;
            if (Sim.spawning(e)) {
                // pulsing cloud: the enemy materialises after ENEMY_SPAWN_DURATION
                float pulse = 0.5f + 0.5f * (e.spawning / Sim.ENEMY_SPAWN_DURATION);
                b.setColor(1, 1, 1, 0.55f + 0.45f * pulse);
                b.draw(TextureGen.region(sprites, 8), ex, ey);
                b.setColor(flash);
            } else if (e.ethereal) {
                b.draw(TextureGen.region(sprites, TextureGen.SPRITE_GHOST), ex, ey);
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
            // pixel y grows up-screen, tile ty grows down-screen: UP is +y in pixels
            switch (f) {
                case RIGHT -> b.draw(TextureGen.region(sprites, 2), linkPxX + GameConfig.TILE * 0.4f, linkPxY, h, GameConfig.TILE);
                case LEFT -> b.draw(TextureGen.region(sprites, 2), linkPxX + GameConfig.TILE * 0.6f, linkPxY, -h, GameConfig.TILE);
                // mirror the horizontal reach along the axis: 0.4 tile into Link's
                // tile, tip a full tile past the tile edge
                case UP -> b.draw(TextureGen.region(sprites, 6), linkPxX + (GameConfig.TILE - w) / 2, linkPxY + GameConfig.TILE * 0.4f, w, h);
                case DOWN -> b.draw(TextureGen.region(sprites, 6), linkPxX + (GameConfig.TILE - w) / 2, linkPxY + GameConfig.TILE * 0.6f, w, -h);
            }
        }
        // Dark screen: drawn over the whole room (enemies and Link included) so
        // everything is obscured. The sprites underneath keep moving: the
        // enemies in the dark are fully active.
        if (sim.screenIsDark()) {
            b.setColor(0.02f, 0.02f, 0.04f, 0.94f);
            b.draw(TextureGen.region(ui, 2), 0, 0, GameConfig.LOGICAL_W, GameConfig.LOGICAL_H);
            b.setColor(1, 1, 1, 0.08f); // faint wall outlines so the room still reads
            for (int y = 0; y < World.SCREEN_H; y++) {
                for (int x = 0; x < World.SCREEN_W; x++) {
                    if (!sim.dungeonScreen().grid().get(x, y).walkable) {
                        b.draw(TextureGen.region(ui, 2), x * GameConfig.TILE,
                                (World.SCREEN_H - 1 - y) * GameConfig.TILE);
                    }
                }
            }
            b.setColor(1, 1, 1, 1);
        }
        // HUD: hearts top-left (filled vs. lost), magic below (both inset from the top edge)
        for (int i = 0; i < World.MAX_HEARTS; i++) {
            drawUiCell(b, 0, 3 + i * 12, GameConfig.LOGICAL_H - 19,
                    i < link.hearts ? com.badlogic.gdx.graphics.Color.WHITE : new com.badlogic.gdx.graphics.Color(0.3f, 0.3f, 0.3f, 1f));
        }
        // Magic pips: spent by Spells, which V1 has none of, so they stay bright
        for (int i = 0; i < GameConfig.MAX_MAGIC; i++) {
            drawUiCell(b, 1, 3 + i * 12, GameConfig.LOGICAL_H - 38,
                    i < sim.magic() ? com.badlogic.gdx.graphics.Color.WHITE
                            : new com.badlogic.gdx.graphics.Color(0.3f, 0.3f, 0.3f, 1f));
        }
        // Fire: one charge per screen entered, no Magic spent
        drawUiCell(b, 2, 3, GameConfig.LOGICAL_H - 57,
                sim.fireReady() ? com.badlogic.gdx.graphics.Color.WHITE
                        : new com.badlogic.gdx.graphics.Color(0.3f, 0.3f, 0.3f, 1f));
        // Keys held, inside a dungeon
        if (sim.inDungeon()) {
            layout.setText(font, "x " + sim.dungeonRun().keys());
            font.draw(b, layout, 3, GameConfig.LOGICAL_H - 66);
            b.draw(keySprite, 15, GameConfig.LOGICAL_H - 81);
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
