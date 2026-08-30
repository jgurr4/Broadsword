package com.jmgurr.broadsword;

import com.jmgurr.broadsword.model.Link;
import com.jmgurr.broadsword.model.Tile;
import com.jmgurr.broadsword.model.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LinkTest {
    private final World w = new World(1L);

    private static Link.Dir opposite(Link.Dir d) {
        return switch (d) {
            case UP -> Link.Dir.DOWN;
            case DOWN -> Link.Dir.UP;
            case LEFT -> Link.Dir.RIGHT;
            case RIGHT -> Link.Dir.LEFT;
        };
    }

    @Test
    void spawnIsWalkable() {
        assertTrue(w.walkable(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY));
    }

    @Test
    void stepForwardAndBack() {
        Link l = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
        int tx0 = l.tx, ty0 = l.ty;
        // find a walkable direction from spawn, step, then step back
        for (Link.Dir d : Link.Dir.values()) {
            if (w.walkable(l.sx, l.sy, tx0 + d.dx, ty0 + d.dy)) {
                assertTrue(l.step(w, d));
                assertEquals(tx0 + d.dx, l.tx);
                assertEquals(ty0 + d.dy, l.ty);
                Link.Dir back = opposite(d);
                assertTrue(l.step(w, back));
                assertEquals(tx0, l.tx);
                assertEquals(ty0, l.ty);
                return;
            }
        }
        fail("no walkable direction from spawn");
    }

    @Test
    void blockedByObstacle() {
        Link l = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
        w.screen(l.sx, l.sy).set(l.tx, l.ty + 1, Tile.ROCK);
        assertFalse(l.step(w, Link.Dir.UP));
        assertEquals(l.tx, l.tx);
    }

    @Test
    void edgeCrossingMovesBetweenScreens() {
        // place Link at the left edge of its spawn screen, clear a walkable tile on the right side of the screen to its left
        int sx = World.SPAWN_SX;
        int sy = World.SPAWN_SY;
        w.screen(sx, sy).set(0, sy, Tile.GRASS);
        w.screen(sx - 1, sy).set(World.SCREEN_W - 1, sy, Tile.GRASS);
        Link l = new Link(sx, sy, 0, sy);
        assertTrue(l.step(w, Link.Dir.LEFT));
        assertEquals(sx - 1, l.sx);
        assertEquals(sy, l.sy);
        assertEquals(World.SCREEN_W - 1, l.tx);
        assertEquals(sy, l.ty);
    }

    @Test
    void worldBorderBlocks() {
        World border = new World(2L);
        border.screen(0, 0).set(0, 0, Tile.GRASS);
        border.screen(0, 0).set(0, World.SCREEN_H - 1, Tile.GRASS);
        // west and south edges of the world
        Link l = new Link(0, 0, 0, 0);
        assertFalse(l.step(border, Link.Dir.LEFT));
        assertFalse(l.step(border, Link.Dir.DOWN));
        assertEquals(0, l.sx);
        assertEquals(0, l.sy);
        assertEquals(0, l.tx);
        assertEquals(0, l.ty);
        // north edge of the world (top screen row, top tile row)
        border.screen(0, World.WORLD_H - 1).set(0, World.SCREEN_H - 1, Tile.GRASS);
        Link l2 = new Link(0, World.WORLD_H - 1, 0, World.SCREEN_H - 1);
        assertFalse(l2.step(border, Link.Dir.UP));
        assertEquals(World.SCREEN_H - 1, l2.ty);
    }

    @Test
    void seedReproducesWorld() {
        World a = new World(7L);
        World b = new World(7L);
        for (int y = 0; y < World.WORLD_H; y++) {
            for (int x = 0; x < World.WORLD_W; x++) {
                for (int ty = 0; ty < World.SCREEN_H; ty++) {
                    for (int tx = 0; tx < World.SCREEN_W; tx++) {
                        assertEquals(a.screen(x, y).get(tx, ty), b.screen(x, y).get(tx, ty));
                    }
                }
            }
        }
    }
}
