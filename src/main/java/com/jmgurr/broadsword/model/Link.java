package com.jmgurr.broadsword.model;

/** The hero. Position is always a tile coordinate; no physics. */
public class Link {
    public enum Dir {
        UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0);

        public final int dx;
        public final int dy;

        Dir(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }
    }

    public int sx, sy; // screen coords in the overworld
    public int tx, ty; // tile coords within the screen
    public Dir facing = Dir.DOWN;

    public Link(int sx, int sy, int tx, int ty) {
        this.sx = sx;
        this.sy = sy;
        this.tx = tx;
        this.ty = ty;
    }

    public int worldTileX() {
        return sx * World.SCREEN_W + tx;
    }

    public int worldTileY() {
        return sy * World.SCREEN_H + ty;
    }

    /**
     * Attempt one tile step. Crossing a screen edge moves Link to the opposite
     * edge of the adjacent screen; the world border and obstacles block.
     *
     * @return true if Link moved
     */
    public boolean step(World w, Dir d) {
        facing = d;
        int nsx = sx, nsy = sy;
        int nx = tx + d.dx, ny = ty + d.dy;
        if (nx < 0) {
            if (nsx == 0) {
                return false;
            }
            nsx--;
            nx = World.SCREEN_W - 1;
        } else if (nx >= World.SCREEN_W) {
            if (nsx == World.WORLD_W - 1) {
                return false;
            }
            nsx++;
            nx = 0;
        }
        if (ny < 0) {
            if (nsy == 0) {
                return false;
            }
            nsy--;
            ny = World.SCREEN_H - 1;
        } else if (ny >= World.SCREEN_H) {
            if (nsy == World.WORLD_H - 1) {
                return false;
            }
            nsy++;
            ny = 0;
        }
        if (!w.walkable(nsx, nsy, nx, ny)) {
            return false;
        }
        sx = nsx;
        sy = nsy;
        tx = nx;
        ty = ny;
        return true;
    }
}
