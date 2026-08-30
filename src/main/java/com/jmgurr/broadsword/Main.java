package com.jmgurr.broadsword;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class Main {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Broadsword");
        // logical 240x150 (16x10 tiles @ 15px) scaled 3x
        cfg.setWindowedMode(GameConfig.VIEW_W * GameConfig.SCALE, GameConfig.VIEW_H * GameConfig.SCALE);
        cfg.setResizable(false);
        cfg.setForegroundFPS(60);
        new Lwjgl3Application(new BroadswordGame(), cfg);
    }
}
