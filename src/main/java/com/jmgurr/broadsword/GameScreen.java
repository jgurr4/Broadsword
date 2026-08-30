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
import com.jmgurr.broadsword.model.Tile;
import com.jmgurr.broadsword.model.World;

public class GameScreen implements Screen {
    private final BroadswordGame game;
    private final World world;
    private final Link link;
    private final Texture tiles;
    private final Texture ui;
    private final Texture sprites;
    private final TextureRegion[] tileRegions;

    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private float stepTimer = 0;
    private boolean interpolating = false;
    private float interpProgress = 1;
    private Link.Dir interpolatingDir = null; // dir of the in-flight step
    private Link.Dir desired = null;

    public GameScreen(BroadswordGame game) {
        this.game = game;
        this.world = new World(GameConfig.SEED);
        this.link = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
        this.tiles = game.tiles();
        this.ui = game.ui();
        this.sprites = game.sprites();
        // tile strip cells: 0 GRASS, 1 ROCK, 2 TREE, 3 WATER
        this.tileRegions = new TextureRegion[] {
            TextureGen.region(tiles, 0),
            TextureGen.region(tiles, 1),
            TextureGen.region(tiles, 2),
            TextureGen.region(tiles, 3)
        };
    }

    @Override
    public void render(float delta) {
        update(Math.min(delta, 0.1f));
        draw();
    }

    private void update(float delta) {
        desired = readInput();
        if (desired != null && !interpolating && stepTimer >= GameConfig.STEP_INTERVAL) {
            int psx = link.sx, psy = link.sy;
            if (link.step(world, desired)) {
                if (link.sx != psx || link.sy != psy) {
                    // crossed a screen edge: no slide animation across the seam
                    interpolating = false;
                } else {
                    interpolating = true;
                    interpolatingDir = desired;
                    interpProgress = 0;
                }
                stepTimer = 0;
            }
        }
        stepTimer += delta;
        if (interpolating) {
            interpProgress = Math.min(1, interpProgress + delta / GameConfig.STEP_INTERVAL);
            if (interpProgress >= 1) {
                interpolating = false;
            }
        }
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
        SpriteBatch b = game.batch();
        b.setProjectionMatrix(game.viewport().getCamera().combined);
        b.begin();
        float linkPxX, linkPxY;
        if (interpolating) {
            // interpolation only happens within one screen: screen-local start + progress
            Link.Dir d = interpolatingDir;
            float fx = link.tx - d.dx + d.dx * interpProgress;
            float fy = link.ty - d.dy + d.dy * interpProgress;
            linkPxX = fx * GameConfig.TILE;
            linkPxY = (World.SCREEN_H - 1 - fy) * GameConfig.TILE;
        } else {
            linkPxX = link.tx * GameConfig.TILE;
            linkPxY = (World.SCREEN_H - 1 - link.ty) * GameConfig.TILE;
        }
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                Tile t = world.screen(link.sx, link.sy).get(x, y);
                int cell = switch (t) {
                    case GRASS -> 0;
                    case ROCK -> 1;
                    case TREE -> 2;
                    case WATER -> 3;
                };
                b.draw(tileRegions[cell], x * GameConfig.TILE, (World.SCREEN_H - 1 - y) * GameConfig.TILE);
            }
        }
        b.draw(sprites, linkPxX, linkPxY, GameConfig.TILE, GameConfig.TILE);
        // HUD: hearts top-left, magic below (both inset from the top edge)
        for (int i = 0; i < GameConfig.MAX_HEARTS; i++) {
            b.draw(TextureGen.region(ui, 0), 3 + i * 12, GameConfig.LOGICAL_H - 19);
        }
        for (int i = 0; i < GameConfig.MAX_MAGIC; i++) {
            b.draw(TextureGen.region(ui, 1), 3 + i * 12, GameConfig.LOGICAL_H - 38);
        }
        // dev readout: screen:tiles and the direction the input layer sees
        String dbg = String.format("%d:%d %d:%d %s", link.sx, link.sy, link.tx, link.ty,
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
