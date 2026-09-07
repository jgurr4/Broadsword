package com.jmgurr.broadsword.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import com.jmgurr.broadsword.model.Tile;

/** T9: the authored dungeon file — parsing, validation, door symmetry. */
class DungeonParseTest {

    @Test
    void hydraLoads() {
        Dungeon d = Dungeon.loadHydra();
        assertEquals(12, d.screens().size());
        assertEquals("s01", d.entry().id());
        assertNotNull(d.entry().exitTile());
        assertEquals(Tile.DUNGEON_EXIT, d.entry().grid().get(d.entry().exitTile().tx(),
                d.entry().exitTile().ty()));
        assertEquals("s12", d.screens().get(11).id());
        assertNotNull(d.screens().get(11).bossTile(), "the hydra room has its boss tile (T11 spawns it)");
    }

    @Test
    void hydraHasEveryFeature() {
        Dungeon d = Dungeon.loadHydra();
        int locks = 0, keys = 0, items = 0, enemyRooms = 0, dark = 0;
        for (DungeonScreen s : d.screens()) {
            if (s.dark()) dark++;
            for (DungeonScreen.Door door : s.doors().values()) {
                if (door.locked()) locks++;
            }
            keys += s.keys().size();
            items += s.items().size();
            if (!s.enemies().isEmpty()) enemyRooms++;
        }
        assertTrue(locks >= 2, "at least one locked pair (both sides declared)");
        assertEquals(locks / 2, locks - locks / 2, "locked doors come in symmetric pairs");
        assertTrue(keys >= 2, "at least two keys");
        assertEquals(1, items, "exactly one reward item");
        assertTrue(enemyRooms >= 4, "enemies in at least four rooms");
        assertTrue(dark >= 1, "dark screens parse (flag for T10)");
    }

    @Test
    void lockedDoorsPairUp() {
        Dungeon d = Dungeon.loadHydra();
        int locked = 0;
        for (DungeonScreen s : d.screens()) {
            for (Map.Entry<Link.Dir, DungeonScreen.Door> e : s.doors().entrySet()) {
                if (!e.getValue().locked()) continue;
                locked++;
                DungeonScreen target = d.screen(e.getValue().target());
                DungeonScreen.Door back = target.doors().get(Sim.opposite(e.getKey()));
                assertNotNull(back, "return door declared");
                assertTrue(back.locked(), "locked on both sides");
                assertEquals(e.getValue().lockId(), back.lockId(), "same lock on both sides");
            }
        }
        assertEquals(4, locked, "two locked pairs: the reward vault and the boss door");
    }

    @Test
    void gridMatchesDeclaredDoors() {
        Dungeon d = Dungeon.loadHydra();
        for (DungeonScreen s : d.screens()) {
            for (Link.Dir dir : Link.Dir.values()) {
                boolean declared = s.doors().containsKey(dir);
                ScreenPos tile = s.doorTile(dir);
                if (tile != null) {
                    assertTrue(declared, s.id() + " has an " + dir + " door tile but no declaration");
                }
                if (declared) {
                    assertNotNull(tile, s.id() + " declares " + dir + " but has no door tile");
                }
            }
            // the exit tile sits on the boundary
            ScreenPos e = s.exitTile();
            if (e != null) {
                assertTrue(e.tx() == 0 || e.tx() == World.SCREEN_W - 1 || e.ty() == 0
                        || e.ty() == World.SCREEN_H - 1, s.id() + " exit is on the boundary");
            }
        }
    }

    @Test
    void everyBoundaryDoorIsDeclared() {
        for (DungeonScreen s : Dungeon.loadHydra().screens()) {
            for (int tx = 0; tx < World.SCREEN_W; tx++) {
                bound(s, tx, 0, Link.Dir.UP);
                bound(s, tx, World.SCREEN_H - 1, Link.Dir.DOWN);
            }
            for (int ty = 0; ty < World.SCREEN_H; ty++) {
                bound(s, 0, ty, Link.Dir.LEFT);
                bound(s, World.SCREEN_W - 1, ty, Link.Dir.RIGHT);
            }
        }
    }

    /** A boundary tile is either solid wall or a declared door: never an undeclared gap. */
    private static void bound(DungeonScreen s, int tx, int ty, Link.Dir edge) {
        Tile t = s.grid().get(tx, ty);
        if (t.walkable) {
            if (t == Tile.DUNGEON_EXIT) {
                assertEquals(s.exitTile().tx(), tx);
                assertEquals(s.exitTile().ty(), ty);
                return; // the overworld exit sits on the boundary too
            }
            assertEquals(Tile.DOOR, t, s.id() + " boundary walkable tile is a door");
            assertTrue(s.doors().containsKey(edge), s.id() + " boundary door at " + edge + " is declared");
        }
    }

    @Test
    void rejectsAsymmetricDoors() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Dungeon.parse("# dungeon: bad\n" + "[s01] doors: E=s02\n" + grid("E..............d") + "\n"
                        + "[s02] doors: \n" + grid("################")));
        assertTrue(e.getMessage().contains("s02"));
    }

    @Test
    void rejectsMissingDoorTile() {
        assertThrows(IllegalStateException.class,
                () -> Dungeon.parse("# dungeon: bad\n" + "[s01] doors: E=s02\n" + grid("E..............#") + "\n"
                        + "[s02] doors: W=s01\n" + grid("d..............#")));
    }

    @Test
    void rejectsTwoExits() {
        assertThrows(IllegalStateException.class,
                () -> Dungeon.parse("# dungeon: bad\n" + "[s01] doors: \n" + grid("E..............#") + "\n"
                        + "[s02] doors: \n" + grid("E..............#")));
    }

    @Test
    void rejectsBadGrid() {
        assertThrows(IllegalStateException.class,
                () -> Dungeon.parse("# dungeon: bad\n" + "[s01] doors: \n" + "################\n"));
    }

    /** One screen with the given first row, floor elsewhere; walls on the other edges. */
    private static String grid(String row0) {
        StringBuilder sb = new StringBuilder();
        sb.append(row0).append('\n');
        for (int y = 1; y < World.SCREEN_H; y++) {
            sb.append(y == World.SCREEN_H - 1 ? "################" : "#..............#").append('\n');
        }
        return sb.toString();
    }
}
