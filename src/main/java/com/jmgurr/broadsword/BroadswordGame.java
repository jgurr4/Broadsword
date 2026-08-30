package com.jmgurr.broadsword;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class BroadswordGame extends Game {
    private SpriteBatch batch;
    private Texture tiles;
    private Texture ui;
    private Texture sprites;

    @Override
    public void create() {
        batch = new SpriteBatch();
        tiles = TextureGen.tiles();
        ui = TextureGen.ui();
        sprites = TextureGen.sprites();
        setScreen(new GameScreen(this));
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
