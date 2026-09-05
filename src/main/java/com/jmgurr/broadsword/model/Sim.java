package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The headless, tick-driven simulation. Takes (seed, input) and advances game
 * state on tick; the render layer only polls input and draws this state.
 * No libgdx types may be used here.
 */
public final class Sim {
    // --- tunables -----------------------------------------------------------
    public static final float STEP_INTERVAL = 0.12f; // ~8 tiles/sec
    /** Enemy step clock: one tile per step (~4 tiles/sec). */
    public static final float ENEMY_STEP_INTERVAL = 0.25f;
    /** Minimum time between sword swings. */
    public static final float SWORD_COOLDOWN = 0.4f;
    /** How long the blade stays out (the vulnerable "swinging" window). */
    public static final float SWORD_SWING_DURATION = 0.2f;
    /** Invulnerability after contact damage. */
    public static final float I_FRAME_DURATION = 1.0f;
    /** Sword hits to kill a Grunt. */
    public static final int GRUNT_HP = 2;
    /** Manhattan distance at which a Grunt switches from patrol to chase. */
    public static final int GRUNT_AGGRO_TILES = 8;

    public enum Phase {
        PLAYING, GAME_OVER
    }

    private final World world;
    private final Link link;
    private Consumer<SaveState> saveSink = s -> {
    };

    private float stepTimer = 0;
    private boolean interpolating = false;
    private float interpProgress = 1;
    private Link.Dir interpolatingDir = null; // dir of the in-flight step

    private Phase phase = Phase.PLAYING;
    private float swordTimer = 0; // time until the next swing is allowed
    private float swingTimer = 0; // blade still out while > 0
    private float invulnTimer = 0; // i-frames remaining
    private float enemyTimer = 0;

    /** Live enemies of the screen Link currently occupies. */
    private final List<Enemy> enemies = new ArrayList<>();
    private int enemyScreenKey = Integer.MIN_VALUE;

    /** A new game: fresh world from the seed, Link at spawn. */
    public Sim(long seed) {
        this.world = WorldGenerator.generate(seed);
        this.link = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
        placeScreenEnemies();
    }

    /** Continue: re-derive the saved world from its seed, resume at the saved position. */
    public Sim(SaveState save) {
        this.world = WorldGenerator.generate(save.seed());
        this.link = new Link(save.sx(), save.sy(), save.tx(), save.ty());
        this.link.facing = save.facing();
        placeScreenEnemies();
    }

    /** Snapshot of the current persistent state (seed + position). */
    public SaveState saveState() {
        return new SaveState(world.seed(), link.sx, link.sy, link.tx, link.ty, link.facing);
    }

    /**
     * Where saves go: called on every screen transition and on major events
     * (item acquired, piece collected, dungeon entry). The render layer writes
     * the file; tests collect the snapshots.
     */
    public void setSaveSink(Consumer<SaveState> sink) {
        this.saveSink = sink;
    }

    /** Autosave for a major event. */
    public void autosave() {
        saveSink.accept(saveState());
    }

    /**
     * Advance the simulation by delta seconds, applying the desired direction
     * (null for no input). A step is attempted once the step interval has
     * elapsed and no slide animation is in flight.
     */
    public void tick(float delta, Link.Dir desired) {
        tick(delta, desired, false);
    }

    public void tick(float delta, Link.Dir desired, boolean swing) {
        if (phase != Phase.PLAYING) {
            return; // game-over: the renderer shows the overlay and calls respawn()
        }
        if (desired != null && !interpolating && stepTimer >= STEP_INTERVAL) {
            int psx = link.sx, psy = link.sy;
            if (link.step(world, desired)) {
                if (link.sx != psx || link.sy != psy) {
                    // crossed a screen edge: no slide animation across the seam,
                    // and the run autosaves
                    interpolating = false;
                    autosave();
                } else {
                    interpolating = true;
                    interpolatingDir = desired;
                    interpProgress = 0;
                }
                stepTimer = 0;
            }
        }
        stepTimer += delta;
        swordTimer = Math.max(0, swordTimer - delta);
        swingTimer = Math.max(0, swingTimer - delta);
        invulnTimer = Math.max(0, invulnTimer - delta);
        if (interpolating) {
            interpProgress = Math.min(1, interpProgress + delta / STEP_INTERVAL);
            if (interpProgress >= 1) {
                interpolating = false;
            }
        }
        if (swing) {
            trySwing();
        }
        if (enemyScreenKey != screenKey(link.sx, link.sy)) {
            placeScreenEnemies(); // leaving and returning respawns the placed tiles
            enemyTimer = 0;
        }
        enemyTimer += delta;
        if (enemyTimer >= ENEMY_STEP_INTERVAL) {
            enemyTimer = 0;
            stepEnemies();
        }
    }

    /**
     * Swing the sword at the tile Link faces. Ignored while the cooldown has
     * not expired. Deals 1 damage; survivors are stunned one step and knocked
     * back one tile unless the target tile is blocked.
     */
    public void swing() {
        trySwing();
    }

    private void trySwing() {
        if (phase != Phase.PLAYING || swordTimer > 0) {
            return;
        }
        swordTimer = SWORD_COOLDOWN;
        swingTimer = SWORD_SWING_DURATION;
        int hx = link.tx + link.facing.dx;
        int hy = link.ty + link.facing.dy;
        for (Enemy e : enemies) {
            if (e.alive && e.tx == hx && e.ty == hy) {
                hit(e);
            }
        }
    }

    private void hit(Enemy e) {
        e.hp -= 1;
        if (e.hp <= 0) {
            e.alive = false;
            return;
        }
        e.stunned = true;
        // knockback: one tile directly away from the blow, if the tile is clear
        int kx = e.tx + link.facing.dx;
        int ky = e.ty + link.facing.dy;
        if (world.walkable(link.sx, link.sy, kx, ky) && !liveEnemyAt(kx, ky)) {
            e.tx = kx;
            e.ty = ky;
        }
    }

    private boolean liveEnemyAt(int tx, int ty) {
        for (Enemy e : enemies) {
            if (e.alive && e.tx == tx && e.ty == ty) {
                return true;
            }
        }
        return false;
    }

    private void stepEnemies() {
        boolean hitNow = false;
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            if (e.kind == EnemyKind.GRUNT) {
                stepGrunt(e);
            }
            // Octorock AI lands with T5 (stationary for now)
            if (e.alive && e.tx == link.tx && e.ty == link.ty) {
                hitNow = true; // contact: 1 Heart per tick, not per enemy
            }
        }
        if (hitNow && invulnTimer <= 0) {
            link.hearts -= 1;
            invulnTimer = I_FRAME_DURATION;
            if (link.hearts <= 0) {
                link.hearts = 0;
                phase = Phase.GAME_OVER;
            }
        }
    }

    /** V1 HP default for every species (tunable per kind when they diverge). */
    static int enemyHp(EnemyKind kind) {
        return GRUNT_HP;
    }

    /** Grunt: chase within aggro radius, random walk beyond it; blocked by terrain and enemies. */
    private void stepGrunt(Enemy e) {
        if (e.stunned) {
            e.stunned = false;
            return;
        }
        int dist = Math.abs(e.tx - link.tx) + Math.abs(e.ty - link.ty);
        if (dist <= GRUNT_AGGRO_TILES) {
            // chase: close on the axis with the bigger gap (x wins ties)
            int dx = Integer.signum(link.tx - e.tx);
            int dy = Integer.signum(link.ty - e.ty);
            boolean xFirst = Math.abs(link.tx - e.tx) >= Math.abs(link.ty - e.ty);
            if (dx != 0 && tryEnemyMove(e, xFirst ? dx : 0, xFirst ? 0 : dy)) {
                return;
            }
            if (dy != 0) {
                tryEnemyMove(e, 0, dy);
            }
            return;
        }
        // patrol: random direction, stay put if blocked
        Link.Dir[] dirs = Link.Dir.values();
        Link.Dir d = dirs[e.rng.nextInt(4)];
        tryEnemyMove(e, d.dx, d.dy);
    }

    private boolean tryEnemyMove(Enemy e, int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return false;
        }
        int nx = e.tx + dx, ny = e.ty + dy;
        if (!world.walkable(link.sx, link.sy, nx, ny) || liveEnemyAtOther(e, nx, ny)) {
            return false;
        }
        e.tx = nx;
        e.ty = ny;
        return true;
    }

    private boolean liveEnemyAtOther(Enemy self, int tx, int ty) {
        for (Enemy o : enemies) {
            if (o != self && o.alive && o.tx == tx && o.ty == ty) {
                return true;
            }
        }
        return false;
    }

    /** (Re)place the generator's enemies for Link's current screen; all alive. */
    private void placeScreenEnemies() {
        enemies.clear();
        List<EnemySpawn> placed = world.enemies(link.sx, link.sy);
        for (int slot = 0; slot < placed.size(); slot++) {
            EnemySpawn s = placed.get(slot);
            long wanderSeed = world.usedSeed() * 1000003L + screenKey(link.sx, link.sy) * 31337L + slot;
            enemies.add(new Enemy(s.kind(), s.tx(), s.ty(), enemyHp(s.kind()), wanderSeed));
        }
        enemyScreenKey = screenKey(link.sx, link.sy);
    }

    private static int screenKey(int sx, int sy) {
        return sy * World.WORLD_W + sx;
    }

    /**
     * Respawn after game over: full Hearts at the overworld spawn; all
     * non-persistent state (enemy spawns) resets.
     */
    public void respawn() {
        if (phase != Phase.GAME_OVER) {
            return;
        }
        link.hearts = World.MAX_HEARTS;
        link.sx = World.SPAWN_SX;
        link.sy = World.SPAWN_SY;
        link.tx = World.SPAWN_TX;
        link.ty = World.SPAWN_TY;
        invulnTimer = 0;
        swordTimer = 0;
        swingTimer = 0;
        enemyTimer = 0;
        stepTimer = 0;
        interpolating = false;
        interpProgress = 1;
        phase = Phase.PLAYING;
        placeScreenEnemies();
    }

    public World world() {
        return world;
    }

    public Link link() {
        return link;
    }

    public Phase phase() {
        return phase;
    }

    /** True while the blade is out (shield is down while swinging). */
    public boolean swinging() {
        return swingTimer > 0;
    }

    /** Live enemies on Link's current screen; tests place and inspect enemies here. */
    public List<Enemy> enemies() {
        return enemies;
    }

    /** True while Link cannot be hurt (i-frames active). */
    public boolean invulnerable() {
        return invulnTimer > 0;
    }

    /** True while a within-screen slide animation is in flight. */
    public boolean interpolating() {
        return interpolating;
    }

    /** Progress of the in-flight step, 0..1. */
    public float interpProgress() {
        return interpProgress;
    }

    /** Direction of the in-flight step, or null when none. */
    public Link.Dir interpolatingDir() {
        return interpolatingDir;
    }
}
