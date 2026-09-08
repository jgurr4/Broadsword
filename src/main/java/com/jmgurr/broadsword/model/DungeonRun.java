package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable per-dungeon progress: keys held, locks opened, loot taken, shoveable
 * block positions and triggered loot. Lives on the Sim across dungeon exits and
 * deaths (the keep side of the keep-vs-reset table); enemies respawn and dark
 * resets are handled elsewhere.
 */
public final class DungeonRun {

    private int keys;
    private int items;
    private final Set<Integer> openedLocks = new HashSet<>();
    private final Set<Integer> takenLoot = new HashSet<>();
    /** screen index -> live block tiles, in authored order until first pushed. */
    private final Map<Integer, List<ScreenPos>> blocks = new HashMap<>();
    private final Set<Integer> triggered = new HashSet<>();

    public int keys() {
        return keys;
    }

    /** Keys still held; collected loot stays collected. */
    public int itemsTaken() {
        return items;
    }

    public boolean isOpen(int lockId) {
        return lockId >= 0 && openedLocks.contains(lockId);
    }

    public void openLock(int lockId) {
        openedLocks.add(lockId);
    }

    public boolean taken(int lootId) {
        return takenLoot.contains(lootId);
    }

    /** Pick up a key: contact pickup, counted, any key opens any lock. */
    public void takeKey(int lootId) {
        if (takenLoot.add(lootId)) keys++;
    }

    /** Pick up an item (loot persists across exit/re-entry). */
    public void takeItem(int lootId) {
        if (takenLoot.add(lootId)) items++;
    }

    /**
     * Spend one key on a lock.
     *
     * @return true if a key was available and the lock is now open
     */
    public boolean spendKey(int lockId) {
        if (keys <= 0 || isOpen(lockId)) return false;
        keys--;
        openLock(lockId);
        return true;
    }

    /** Opened lock ids, sorted. */
    public Set<Integer> openedLocks() {
        return Set.copyOf(openedLocks);
    }

    public Set<Integer> takenLoot() {
        return Set.copyOf(takenLoot);
    }

    // ---- T10: shoveable blocks -------------------------------------------

    /** Live block tiles for a screen; seeded from the authored layout on first ask. */
    public List<ScreenPos> blocks(int screenIndex, DungeonScreen s) {
        return blocks.computeIfAbsent(screenIndex,
                i -> new ArrayList<>(s.blocks()));
    }

    /** Move a block (and Link onto its old tile) after a legal push. */
    public void pushBlock(int screenIndex, DungeonScreen s, ScreenPos from, ScreenPos to) {
        List<ScreenPos> list = blocks(screenIndex, s);
        int at = list.indexOf(from);
        if (at < 0) return;
        list.set(at, to);
        Lootable loot = s.triggers().get(from);
        if (loot != null) triggered.add(loot.id()); // first push reveals it, once
    }

    /** True when the hidden loot at this tile has been revealed by a push. */
    public boolean revealed(int lootId) {
        return triggered.contains(lootId);
    }

    /** Restore persisted state (load); replaces everything. Blocks are not persisted. */
    public void restore(int keys, int items, Iterable<Integer> opened, Iterable<Integer> taken) {
        this.keys = keys;
        this.items = items;
        openedLocks.clear();
        takenLoot.clear();
        for (int i : opened) openedLocks.add(i);
        for (int i : taken) takenLoot.add(i);
    }
}
