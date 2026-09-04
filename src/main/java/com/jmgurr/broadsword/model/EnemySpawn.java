package com.jmgurr.broadsword.model;

/** An enemy placed by the generator on a screen-local tile. */
public record EnemySpawn(EnemyKind kind, int tx, int ty) {
}
