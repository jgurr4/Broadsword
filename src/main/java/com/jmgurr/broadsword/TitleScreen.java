package com.jmgurr.broadsword;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.jmgurr.broadsword.model.SaveState;
import com.jmgurr.broadsword.model.World;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Title: New game, Continue when a readable save exists, and an optional seed.
 * Typed digits build the seed; N or Enter starts with it (empty seed = random).
 */
public class TitleScreen implements Screen {
    private static final int MAX_SEED_DIGITS = 9;

    private final BroadswordGame game;
    private final BitmapFont font = new BitmapFont();
    private final Optional<SaveState> continueSave;
    private final StringBuilder seed = new StringBuilder();

    public TitleScreen(BroadswordGame game) {
        this.game = game;
        // read once at show: corrupt or missing saves parse empty, hiding Continue
        this.continueSave = SaveFiles.read();
    }

    /** One typed digit this frame (top row or numpad), or nothing. */
    private void pollSeedKey() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && !seed.isEmpty()) {
            seed.setLength(seed.length() - 1);
            return;
        }
        for (int base : new int[] { Input.Keys.NUM_0, Input.Keys.NUMPAD_0 }) {
            for (int d = 0; d <= 9; d++) {
                if (Gdx.input.isKeyJustPressed(base + d)) {
                    if (seed.length() < MAX_SEED_DIGITS && !(seed.isEmpty() && d == 0)) {
                        seed.append((char) ('0' + d));
                    }
                    return;
                }
            }
        }
    }

    /**
     * The typed seed, or empty for a random one. Digits only, leading zeros
     * stripped; anything else (including a number too large) is not a seed.
     */
    static OptionalLong parseSeed(String typed) {
        String s = typed.strip();
        if (s.isEmpty() || !s.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(s));
        } catch (NumberFormatException tooBig) {
            return OptionalLong.empty();
        }
    }

    private void startNewGame() {
        game.newGame(parseSeed(seed.toString()).orElse(World.randomSeed()));
    }

    @Override
    public void render(float delta) {
        pollSeedKey();
        if (continueSave.isPresent() && Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            game.continueGame(continueSave.get());
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.N) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            startNewGame();
            return;
        }
        Gdx.gl.glClearColor(0.07f, 0.07f, 0.1f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        var b = game.batch();
        b.setProjectionMatrix(game.viewport().getCamera().combined);
        b.begin();
        font.getData().setScale(1.4f);
        GameUi.centeredText(b, font, "BROADSWORD", GameConfig.LOGICAL_H - 44);
        font.getData().setScale(0.5f);
        GameUi.centeredText(b, font, "press N or ENTER - new game", GameConfig.LOGICAL_H - 68);
        if (continueSave.isPresent()) {
            GameUi.centeredText(b, font, "press C - continue", GameConfig.LOGICAL_H - 80);
        }
        GameUi.centeredText(b, font, "seed: " + (seed.isEmpty() ? "(random)" : seed),
                GameConfig.LOGICAL_H - 96);
        GameUi.centeredText(b, font, "type digits, backspace to fix", GameConfig.LOGICAL_H - 108);
        font.getData().setScale(1f);
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
        font.dispose();
    }
}
