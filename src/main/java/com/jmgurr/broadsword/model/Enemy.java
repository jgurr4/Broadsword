package com.jmgurr.broadsword.model;

import java.util.Random;

/**
 * A live enemy on Link's current screen. Movement is tile-to-tile on the
 * enemy step clock, screen-local always. Killed enemies stay dead until Link
 * leaves and re-enters the screen; respawn is owned by Sim.
 */
public final class Enemy {
    public final EnemyKind kind;
    public int tx, ty;
    /** Tile the current slide started from, and its progress 1..0 (0 = arrived). */
    public int fromTx, fromTy;
    public float interp = 0;
    public int hp;
    public boolean alive = true;
    /** True after a sword hit; costs this enemy its next step. */
    public boolean stunned = false;
    /** Octorock: seconds until the next Fireball may be fired. */
    public float fireTimer = 0;
    /** Seconds until this enemy materialises; while > 0 it is a harmless cloud. */
    public float spawning = 0;
    /** Deterministic per-enemy wander: seeded from world seed + screen + slot. */
    final Random rng;

    /** Test/ad-hoc enemy with default V1 HP and an explicit wander seed. */
    public Enemy(EnemyKind kind, int tx, int ty, long wanderSeed) {
        this(kind, tx, ty, Sim.enemyHp(kind), wanderSeed);
    }

    Enemy(EnemyKind kind, int tx, int ty, int hp, long wanderSeed) {
        this.kind = kind;
        this.tx = tx;
        this.ty = ty;
        this.fromTx = tx;
        this.fromTy = ty;
        this.hp = hp;
        this.rng = new Random(wanderSeed);
    }
}
