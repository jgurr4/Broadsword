package com.jmgurr.broadsword.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Save round-trip through the headless Sim seam: play → save → fresh Sim → identical state. */
class SaveStateTest {

    private static Sim playedSim(long seed) {
        Sim sim = new Sim(seed);
        World w = sim.world();
        // clear an eastbound lane and walk a few tiles, including over the seam
        for (int sx = World.SPAWN_SX; sx <= World.SPAWN_SX + 1; sx++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                w.screen(sx, World.SPAWN_SY).set(x, World.SPAWN_TY, Tile.GRASS);
            }
        }
        Link l = sim.link();
        for (int i = 0; i < World.SCREEN_W; i++) {
            for (int f = 0; f < 100; f++) {
                sim.tick(0.01f, Link.Dir.RIGHT);
                if (l.tx == World.SCREEN_W - 1 || (l.sx == World.SPAWN_SX + 1 && l.tx == i + 1)) break;
            }
        }
        return sim;
    }

    @Test
    void saveFormatRoundTrips() {
        SaveState save = new SaveState(12345L, 21, 5, 3, 7, Link.Dir.LEFT);
        Optional<SaveState> parsed = SaveState.parse(save.format());
        assertTrue(parsed.isPresent());
        assertEquals(save, parsed.get());
    }

    @Test
    void playSaveRestore_stateIdentical() {
        Sim played = playedSim(7L);
        SaveState save = played.saveState();

        Sim restored = new Sim(save);
        assertEquals(played.world().seed(), restored.world().seed());
        // same seed re-derives the identical world
        for (int sy = 0; sy < World.WORLD_H; sy++) {
            for (int sx = 0; sx < World.WORLD_W; sx++) {
                assertSame(played.world().archetype(sx, sy), restored.world().archetype(sx, sy));
                for (int ty = 0; ty < World.SCREEN_H; ty++) {
                    for (int tx = 0; tx < World.SCREEN_W; tx++) {
                        assertEquals(played.world().screen(sx, sy).get(tx, ty),
                                restored.world().screen(sx, sy).get(tx, ty));
                    }
                }
            }
        }
        // Link resumes exactly where he was saved
        Link a = played.link(), b = restored.link();
        assertEquals(a.sx, b.sx);
        assertEquals(a.sy, b.sy);
        assertEquals(a.tx, b.tx);
        assertEquals(a.ty, b.ty);
        assertEquals(a.facing, b.facing);
    }

    @Test
    void continueWalksOnFromSavedPosition() {
        Sim played = playedSim(7L);
        Link a = played.link();
        assertTrue(a.sx > World.SPAWN_SX, "played sim should have crossed the seam");

        Sim restored = new Sim(played.saveState());
        assertNotEquals(World.SPAWN_SX, restored.link().sx);
        assertNotEquals(World.SPAWN_TX, restored.link().tx);
    }

    @Test
    void corruptSavesParseEmpty() {
        for (String bad : List.of("", "not a save", "version=1\nseed=abc",
                "version=99\nseed=1\nlink=0,0,0,0\nfacing=UP",
                "version=1\nseed=1\nlink=999,0,0,0\nfacing=UP",
                "version=1\nseed=1\nlink=0,0,0,0\nfacing=SIDWAYS",
                "version=1\nseed=1\nlink=0,0")) {
            assertEquals(Optional.empty(), SaveState.parse(bad), "should reject: " + bad);
        }
    }

    @Test
    void autosaveFiresOnScreenTransition() {
        Sim sim = new Sim(42L);
        World w = sim.world();
        for (int sx = World.SPAWN_SX; sx <= World.SPAWN_SX + 1; sx++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                w.screen(sx, World.SPAWN_SY).set(x, World.SPAWN_TY, Tile.GRASS);
            }
        }
        List<SaveState> saved = new ArrayList<>();
        sim.setSaveSink(saved::add);

        // within-screen steps save nothing
        for (int i = 0; i < 5; i++) {
            for (int f = 0; f < 100; f++) {
                sim.tick(0.01f, Link.Dir.RIGHT);
                if (sim.link().tx == World.SPAWN_TX + 5) break;
            }
        }
        assertEquals(0, saved.size());

        // crossing the seam saves
        Link l = sim.link();
        for (int f = 0; l.sx == World.SPAWN_SX && f < 3000; f++) {
            sim.tick(0.01f, Link.Dir.RIGHT);
        }
        assertEquals(1, saved.size());
        assertEquals(42L, saved.get(0).seed());
        assertEquals(l.sx, saved.get(0).sx());
        assertEquals(l.sy, saved.get(0).sy());
        assertEquals(l.tx, saved.get(0).tx());
        assertEquals(l.ty, saved.get(0).ty());
    }

    @Test
    void majorEventAutosaves() {
        Sim sim = new Sim(3L);
        List<SaveState> saved = new ArrayList<>();
        sim.setSaveSink(saved::add);
        sim.autosave();
        assertEquals(1, saved.size());
        assertEquals(3L, saved.get(0).seed());
    }

    @Test
    void newGameStartsAtSpawnWithGivenSeed() {
        Sim fresh = new Sim(99L);
        assertEquals(World.SPAWN_SX, fresh.link().sx);
        assertEquals(World.SPAWN_SY, fresh.link().sy);
        assertEquals(World.SPAWN_TX, fresh.link().tx);
        assertEquals(World.SPAWN_TY, fresh.link().ty);
        assertEquals(99L, fresh.world().seed());
    }
}
