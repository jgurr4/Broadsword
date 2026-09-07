package com.jmgurr.broadsword.model;

/**
 * A cave: a side room reached from one overworld tile. {@code entry} is the
 * overworld screen holding the entrance tile; {@code room} is the off-grid
 * interior whose STAIRS tile leads back out. A cave is one screen of dark floor
 * walled in on all four sides, and it never holds enemies.
 *
 * <p>Cave state is non-persistent: the interior is re-derived from the seed,
 * only "Link is inside cave X" goes into a save.
 */
public record Cave(ScreenPos entry, Screen room, int entryTx, int entryTy) {

    public boolean atEntry(int sx, int sy, int tx, int ty) {
        return entry.sx() == sx && entry.sy() == sy && entry.tx() == tx && entry.ty() == ty;
    }
}
