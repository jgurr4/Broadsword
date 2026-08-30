package com.jmgurr.broadsword;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScalingViewport;

public class BroadswordGame extends Game {
    private SpriteBatch batch;
    private final ScalingViewport viewport = new ScalingViewport(Scaling.fit, GameConfig.VIEW_W, GameConfig.VIEW_H);
    private Texture tiles;
    private Texture ui;
    private Texture sprites;

    @Override
    public void create() {
        // The 1.14 viewport centers its projection on camera.position; the
        // camera is y-up by default. Position it at the world center.
        viewport.getCamera().position.set(GameConfig.VIEW_W / 2f, GameConfig.VIEW_H / 2f, 0);
        batch = new SpriteBatch();
        tiles = TextureGen.tiles();
        ui = TextureGen.ui();
        sprites = TextureGen.sprites();
        setScreen(new GameScreen(this));
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        tiles.dispose();
        ui.dispose();
        sprites.dispose();
    }

    public SpriteBatch batch() {
        return batch;
    }

    public ScalingViewport viewport() {
        return viewport;
    }

    public Texture tiles() {
        return tiles;
    }

    public Texture ui() {
        return ui;
    }

    public Texture sprites() {
        return sprites;
    }
}
