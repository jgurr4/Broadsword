package com.jmgurr.broadsword;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScalingViewport;
import com.jmgurr.broadsword.model.SaveState;
import com.jmgurr.broadsword.model.Sim;

public class BroadswordGame extends Game {
    /** The run-level state machine shared by title, gameplay, and the overlays. */
    private GameState state = GameState.TITLE;
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

    /** New game on the given seed (typed at the title, or random); overwrites the save. */
    public void newGame(long seed) {
        goTo(GameState.PLAYING);
        setScreen(new GameScreen(this, new Sim(seed)));
    }

    /** Continue: re-derive the saved world and resume at the saved position. */
    public void continueGame(SaveState save) {
        goTo(GameState.PLAYING);
        setScreen(new GameScreen(this, new Sim(save)));
    }

    /** Move the run state machine; staying put is not a transition, an illegal move throws. */
    void goTo(GameState to) {
        if (state == to) {
            return;
        }
        if (!state.canTransitionTo(to)) {
            throw new IllegalStateException(state + " -> " + to);
        }
        state = to;
    }

    /** Leave the run for the title screen; the next game starts from TITLE again. */
    void toTitle() {
        goTo(GameState.TITLE);
        setScreen(new TitleScreen(this));
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
