package com.jmgurr.broadsword;

import com.jmgurr.broadsword.model.Sim;

/**
 * The run-level state machine shared by the title screen, gameplay, and the
 * game-over and victory overlays: TITLE to PLAYING to GAME_OVER or VICTORY
 * and back to TITLE. A run can also be quit mid-play. This is the only place
 * that decides which transitions are legal.
 */
public enum GameState {
    TITLE,
    PLAYING,
    GAME_OVER,
    VICTORY;

    /** The only legal moves on this machine. */
    public boolean canTransitionTo(GameState to) {
        return switch (this) {
            case TITLE -> to == PLAYING;
            case PLAYING -> to == GAME_OVER || to == VICTORY || to == TITLE;
            // respawn resumes the same run; a won run only ever goes back to the title
            case GAME_OVER -> to == PLAYING || to == TITLE;
            case VICTORY -> to == TITLE;
        };
    }

    /** The state that matches the sim's phase while a run is on screen. */
    public static GameState of(Sim.Phase phase) {
        return switch (phase) {
            case PLAYING -> PLAYING;
            case GAME_OVER -> GAME_OVER;
            case VICTORY -> VICTORY;
        };
    }
}
