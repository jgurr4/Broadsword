package com.jmgurr.broadsword.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Mutable per-dungeon progress: keys held, locks opened, loot taken. Lives on
 * the Sim across dungeon exits and deaths (the keep side of the keep-vs-reset
 * table); enemies respawn and dark resets are handled elsewhere.
 */
public final class DungeonRun {

    private int keys;
    private int items;
    private final Set<Integer> openedLocks = new HashSet<>();
    private final Set<Integer> takenLoot = new HashSet<>();

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

    /** Restore persisted state (load); replaces everything. */
    public void restore(int keys, int items, Iterable<Integer> opened, Iterable<Integer> taken) {
        this.keys = keys;
        this.items = items;
        openedLocks.clear();
        takenLoot.clear();
        for (int i : opened) openedLocks.add(i);
        for (int i : taken) takenLoot.add(i);
    }
}
