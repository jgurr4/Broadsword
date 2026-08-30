package com.jmgurr.broadsword;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

public class Main {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Broadsword");
        cfg.setWindowedMode(GameConfig.VIEW_W * GameConfig.SCALE, GameConfig.VIEW_H * GameConfig.SCALE);
        cfg.setResizable(false);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new BroadswordGame(), cfg);
    }
}
