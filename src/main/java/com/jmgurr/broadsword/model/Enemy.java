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
    public int hp;
    public boolean alive = true;
    /** True after a sword hit; costs this enemy its next step. */
    public boolean stunned = false;
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
        this.hp = hp;
        this.rng = new Random(wanderSeed);
    }
}
