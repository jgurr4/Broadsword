package com.jmgurr.broadsword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The run state machine: only the intended routes exist, in both directions. */
class GameStateTest {

    @Test
    void allowedTransitions() {
        assertTrue(GameState.TITLE.canTransitionTo(GameState.PLAYING));
        assertTrue(GameState.PLAYING.canTransitionTo(GameState.GAME_OVER));
        assertTrue(GameState.PLAYING.canTransitionTo(GameState.VICTORY));
        assertTrue(GameState.PLAYING.canTransitionTo(GameState.TITLE));
        assertTrue(GameState.GAME_OVER.canTransitionTo(GameState.PLAYING)); // respawn
        assertTrue(GameState.GAME_OVER.canTransitionTo(GameState.TITLE));
        assertTrue(GameState.VICTORY.canTransitionTo(GameState.TITLE));
    }

    @Test
    void seedEntryParsesOnlyDigits() {
        assertEquals(12345L, TitleScreen.parseSeed("12345").orElseThrow());
        assertTrue(TitleScreen.parseSeed("").isEmpty(), "empty means random");
        assertTrue(TitleScreen.parseSeed("12x").isEmpty());
        assertTrue(TitleScreen.parseSeed("99999999999999999999").isEmpty(), "overflows long");
    }

    @Test
    void forbiddenTransitions() {
        for (GameState to : GameState.values()) {
            assertFalse(GameState.TITLE.canTransitionTo(to) && to != GameState.PLAYING);
            assertFalse(GameState.VICTORY.canTransitionTo(GameState.PLAYING),
                    "a won run never resumes");
            assertFalse(GameState.VICTORY.canTransitionTo(GameState.GAME_OVER));
            assertFalse(GameState.GAME_OVER.canTransitionTo(GameState.VICTORY));
        }
    }
}
