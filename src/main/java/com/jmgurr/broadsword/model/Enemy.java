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
    /**
     * Float position, in tiles, for ethereal movers: it drifts on a smooth
     * clock and ignores the tick grid. Walkers keep these equal to tx/ty.
     */
    public double fx, fy;
    /**
     * True while this enemy moves in float space and phases through terrain
     * and Link (Ghosts). Solid checks and tile occupancy do not apply to it.
     */
    public boolean ethereal = false;
    /** Test hook: this ethereal enemy's float position sat on a solid tile. */
    public boolean solidPass = false;
    /** Tile the current slide started from, and its progress 1..0 (0 = arrived). */
    public int fromTx, fromTy;
    public float interp = 0;
    public int hp;
    public boolean alive = true;
    /** True after a sword hit; costs this enemy its next step. */
    public boolean stunned = false;
    /** Octorock/Hydra head: seconds until the next Fireball may be fired. */
    public float fireTimer = 0;
    /** Hydra head only: initial fire timer in seconds, staggered across heads. */
    public float initialFireTimer = 0;
    /** Boss (T11): fixed at its tile; no knockback, no stun, never moves. */
    public boolean stationary = false;
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
        this.fx = tx;
        this.fy = ty;
        this.fromTx = tx;
        this.fromTy = ty;
        this.hp = hp;
        this.rng = new Random(wanderSeed);
        this.stationary = kind == EnemyKind.HYDRA_HEAD || kind == EnemyKind.HYDRA_BODY;
    }
}
