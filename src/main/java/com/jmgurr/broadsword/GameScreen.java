package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
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

    private float stepTimer = 0;
    private float prevWorldX, prevWorldY; // world tile coords, float
    private boolean interpolating = false;
    private float interpProgress = 1;
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
                    prevWorldX = link.worldTileX() - desired.dx;
                    prevWorldY = link.worldTileY() - desired.dy;
                    interpolating = true;
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
        b.begin();
        float linkPxX, linkPxY;
        if (interpolating) {
            float tx = link.worldTileX(), ty = link.worldTileY();
            float fx = prevWorldX + (tx - prevWorldX) * interpProgress;
            float fy = prevWorldY + (ty - prevWorldY) * interpProgress;
            linkPxX = fx * GameConfig.TILE;
            linkPxY = (World.WORLD_H * World.SCREEN_H - 1 - fy) * GameConfig.TILE;
        } else {
            linkPxX = link.worldTileX() * GameConfig.TILE;
            linkPxY = (World.WORLD_H * World.SCREEN_H - 1 - link.worldTileY()) * GameConfig.TILE;
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
        // HUD: hearts top-left, magic below
        for (int i = 0; i < 3; i++) {
            b.draw(TextureGen.region(ui, 0), 3 + i * 12, GameConfig.VIEW_H - 15);
        }
        for (int i = 0; i < GameConfig.MIN_MAGIC; i++) {
            b.draw(TextureGen.region(ui, 1), 3 + i * 12, GameConfig.VIEW_H - 26);
        }
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
