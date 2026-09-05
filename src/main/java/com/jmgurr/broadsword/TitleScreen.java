package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.jmgurr.broadsword.model.SaveState;

import java.util.Optional;

/** Title: New game always, Continue only when a readable save exists. */
public class TitleScreen implements Screen {
    private final BroadswordGame game;
    private final BitmapFont font = new BitmapFont();
    private final Optional<SaveState> continueSave;

    public TitleScreen(BroadswordGame game) {
        this.game = game;
        // read once at show: corrupt or missing saves parse empty, hiding Continue
        this.continueSave = SaveFiles.read();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.07f, 0.07f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            game.newGame();
            return;
        }
        if (continueSave.isPresent() && Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            game.continueGame(continueSave.get());
            return;
        }
        game.batch().setProjectionMatrix(game.viewport().getCamera().combined);
        game.batch().begin();
        font.draw(game.batch(), "BROADSWORD", 80, GameConfig.LOGICAL_H - 30);
        font.draw(game.batch(), "N - New game", 80, GameConfig.LOGICAL_H - 60);
        if (continueSave.isPresent()) {
            font.draw(game.batch(), "C - Continue", 80, GameConfig.LOGICAL_H - 75);
        }
        game.batch().end();
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
        font.dispose();
    }
}
