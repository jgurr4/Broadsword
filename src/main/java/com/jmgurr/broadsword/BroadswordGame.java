package com.jmgurr.broadsword;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.jmgurr.broadsword.model.SaveState;
import com.jmgurr.broadsword.model.Sim;
import com.jmgurr.broadsword.model.World;

public class BroadswordGame extends Game {
    private SpriteBatch batch;
    private final ScalingViewport viewport = new ScalingViewport(Scaling.fit, GameConfig.LOGICAL_W, GameConfig.LOGICAL_H);
    private Texture tiles;
    private Texture ui;
    private Texture sprites;

    @Override
    public void create() {
        // The 1.14 viewport centers its projection on camera.position; the
        // camera is y-up by default. Position it at the world center.
        viewport.getCamera().position.set(GameConfig.LOGICAL_W / 2f, GameConfig.LOGICAL_H / 2f, 0);
        batch = new SpriteBatch();
        tiles = TextureGen.tiles();
        ui = TextureGen.ui();
        sprites = TextureGen.sprites();
        setScreen(new TitleScreen(this));
    }

    /** New game: fresh seed, spawn position; the new run overwrites the save immediately. */
    public void newGame() {
        setScreen(new GameScreen(this, new Sim(World.randomSeed())));
    }

    /** Continue: re-derive the saved world and resume at the saved position. */
    public void continueGame(SaveState save) {
        setScreen(new GameScreen(this, new Sim(save)));
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
