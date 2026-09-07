package com.jmgurr.broadsword.model;

/**
 * A collectible entity placed by the dungeon parser. The id is assigned
 * globally across the dungeon in parse order so taken loot survives
 * exit/re-entry and save/load.
 */
public record Lootable(int id, int tx, int ty) {
}
