package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    /** Sword hits to kill an Octorock. */
    public static final int OCTOROCK_HP = 2;
    /** Manhattan distance at which a Grunt switches from patrol to chase. */
    public static final int GRUNT_AGGRO_TILES = 8;
    /** Octorock: holds distance while the gap is within this... */
    public static final int OCTOROCK_HOLD_TILES = 5;
    /** Octorock: ...and retreats once the gap drops to this. */
    public static final int OCTOROCK_RETREAT_TILES = 3;
    /** Octorock: seconds between Fireball attempts (needs line-of-sight). */
    public static final float OCTOROCK_FIRE_INTERVAL = 1.5f;
    /** Projectile clock: one tile per step (~10 tiles/sec). */
    public static final float PROJECTILE_STEP_INTERVAL = 0.1f;
    /** Seconds an enemy spends as a spawning cloud before it materialises. */
    public static final float ENEMY_SPAWN_DURATION = 2.0f;

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
    /** In-flight projectiles on the current screen. */
    private final List<Projectile> projectiles = new ArrayList<>();
    private int enemyScreenKey = Integer.MIN_VALUE;
    private float projectileTimer = 0;

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
            placeScreenEnemies(); // leaving and returning respawns the tiles, jittered, in clouds
            enemyTimer = 0;
            projectileTimer = 0;
        }
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            if (e.spawning > 0) {
                e.spawning = Math.max(0, e.spawning - delta);
            } else if (e.kind == EnemyKind.OCTOROCK) {
                e.fireTimer = Math.max(0, e.fireTimer - delta);
            }
        }
        enemyTimer += delta;
        if (enemyTimer >= ENEMY_STEP_INTERVAL) {
            enemyTimer = 0;
            stepEnemies();
        }
        projectileTimer += delta;
        while (projectileTimer >= PROJECTILE_STEP_INTERVAL) {
            projectileTimer -= PROJECTILE_STEP_INTERVAL;
            stepProjectiles();
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
            if (e.alive && e.spawning <= 0 && e.tx == hx && e.ty == hy) {
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
            if (!e.alive || e.spawning > 0) {
                continue; // a cloud neither moves nor hurts
            }
            if (e.kind == EnemyKind.GRUNT) {
                stepGrunt(e);
            } else if (e.kind == EnemyKind.OCTOROCK) {
                stepOctorock(e);
            }
            if (e.alive && e.tx == link.tx && e.ty == link.ty) {
                hitNow = true; // contact: 1 Heart per tick, not per enemy
            }
        }
        if (hitNow) {
            damageLink();
        }
    }

    /** Take 1 Heart, obeying i-frames; 0 Hearts ends the run. */
    private void damageLink() {
        if (invulnTimer > 0) {
            return;
        }
        link.hearts -= 1;
        invulnTimer = I_FRAME_DURATION;
        if (link.hearts <= 0) {
            link.hearts = 0;
            phase = Phase.GAME_OVER;
        }
    }

    /** V1 HP default for every species (tunable per kind when they diverge). */
    static int enemyHp(EnemyKind kind) {
        return kind == EnemyKind.OCTOROCK ? OCTOROCK_HP : GRUNT_HP;
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
            stepEnemyAlong(e, Integer.signum(link.tx - e.tx), Integer.signum(link.ty - e.ty),
                    Math.abs(link.tx - e.tx) >= Math.abs(link.ty - e.ty));
            return;
        }
        // patrol: random direction, stay put if blocked
        Link.Dir[] dirs = Link.Dir.values();
        Link.Dir d = dirs[e.rng.nextInt(4)];
        tryEnemyMove(e, d.dx, d.dy);
    }

    /**
     * Octorock: holds ~5 tiles, retreats at <=3, and fires a Fireball down a
     * clear cardinal line of sight at the rate clock. Never wanders: standing
     * still is what makes its range readable.
     */
    private void stepOctorock(Enemy e) {
        if (e.stunned) {
            e.stunned = false;
            return;
        }
        int ddx = link.tx - e.tx, ddy = link.ty - e.ty;
        int dist = Math.abs(ddx) + Math.abs(ddy);
        if (dist <= OCTOROCK_RETREAT_TILES) {
            // retreat: back away from Link, preferred axis first, other axis as fallback
            stepEnemyAlong(e, -Integer.signum(ddx), -Integer.signum(ddy), Math.abs(ddx) >= Math.abs(ddy));
        } else if (dist > OCTOROCK_HOLD_TILES) {
            // close in the Grunt's manner: bigger gap first (x wins ties)
            stepEnemyAlong(e, Integer.signum(ddx), Integer.signum(ddy), Math.abs(ddx) >= Math.abs(ddy));
        }
        if (e.fireTimer <= 0) {
            // aim after moving: recompute the gap, no lead
            ddx = link.tx - e.tx;
            ddy = link.ty - e.ty;
            int fdx = Integer.signum(ddx), fdy = Integer.signum(ddy);
            // aimed, no lead: only down a clear cardinal lane
            if ((ddx == 0) != (ddy == 0) && lineClear(e.tx, e.ty, link.tx, link.ty)) {
                Projectile p = new Projectile(e.tx + fdx, e.ty + fdy, fdx, fdy);
                e.fireTimer = OCTOROCK_FIRE_INTERVAL;
                if (p.tx == link.tx && p.ty == link.ty) {
                    hitLinkByProjectile(p); // point-blank: hit on the firing tile
                } else {
                    projectiles.add(p);
                }
            }
        }
    }

    /** Straight cardinal lane between two tiles, exclusive of the endpoints, obstacle-free. */
    private boolean lineClear(int fromX, int fromY, int toX, int toY) {
        int dx = Integer.signum(toX - fromX), dy = Integer.signum(toY - fromY);
        int x = fromX + dx, y = fromY + dy;
        while (x != toX || y != toY) {
            if (!world.walkable(link.sx, link.sy, x, y)) {
                return false;
            }
            x += dx;
            y += dy;
        }
        return true;
    }

    private void stepProjectiles() {
        for (Projectile p : projectiles) {
            if (!p.alive) {
                continue;
            }
            p.tx += p.dx;
            p.ty += p.dy;
            if (p.tx < 0 || p.tx >= World.SCREEN_W || p.ty < 0 || p.ty >= World.SCREEN_H) {
                p.alive = false; // off the screen
            } else if (!world.walkable(link.sx, link.sy, p.tx, p.ty)) {
                p.alive = false; // into an obstacle
            } else if (p.tx == link.tx && p.ty == link.ty) {
                hitLinkByProjectile(p);
            }
        }
        projectiles.removeIf(p -> !p.alive);
    }

    /**
     * The V1 shield: a Fireball Link is facing is destroyed on impact while he
     * is not swinging. A swinging shield does not stop it, and neither does a
     * projectile arriving from a side he is not facing. No reflect.
     */
    private void hitLinkByProjectile(Projectile p) {
        p.alive = false;
        boolean facingSource = link.facing.dx == -p.dx && link.facing.dy == -p.dy;
        if (facingSource && !swinging()) {
            return;
        }
        damageLink();
    }

    /**
     * Step one tile toward (dx, dy) - each -1, 0 or 1 - preferring the x axis
     * when {@code xFirst}, and falling back to the other axis if that tile is
     * blocked.
     */
    private boolean stepEnemyAlong(Enemy e, int dx, int dy, boolean xFirst) {
        if (xFirst) {
            return tryEnemyMove(e, dx, 0) || tryEnemyMove(e, 0, dy);
        }
        return tryEnemyMove(e, 0, dy) || tryEnemyMove(e, dx, 0);
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

    /**
     * (Re)place the generator's enemies for Link's current screen. The kind
     * and count come from the generator; the tile is re-rolled on every entry
     * around the placed tile, so a screen is never an ambush you can memorise.
     * Every enemy starts as a 2s spawning cloud, and never on Link's tile.
     */
    private void placeScreenEnemies() {
        enemies.clear();
        projectiles.clear();
        List<EnemySpawn> placed = world.enemies(link.sx, link.sy);
        int key = screenKey(link.sx, link.sy);
        // visit counter makes every entry's layout different even on revisit
        entryCounter++;
        long layoutSeed = (world.usedSeed() * 1000003L + key * 31337L + entryCounter * 7919L) >>> 1;
        Random layoutRng = new Random(layoutSeed);
        for (int slot = 0; slot < placed.size(); slot++) {
            EnemySpawn s = placed.get(slot);
            long wanderSeed = world.usedSeed() * 1000003L + key * 31337L + slot;
            int tx = jitteredTile(layoutRng, s.tx(), 0, World.SCREEN_W - 1);
            int ty = jitteredTile(layoutRng, s.ty(), 0, World.SCREEN_H - 1);
            if (tx == link.tx && ty == link.ty) {
                tx = tx + 1 < World.SCREEN_W ? tx + 1 : tx - 1;
            }
            if (!world.walkable(link.sx, link.sy, tx, ty) || liveEnemyAt(tx, ty)) {
                tx = s.tx();
                ty = s.ty();
            }
            Enemy e = new Enemy(s.kind(), tx, ty, enemyHp(s.kind()), wanderSeed);
            e.spawning = ENEMY_SPAWN_DURATION;
            enemies.add(e);
        }
        enemyScreenKey = key;
    }

    private long entryCounter = 0;

    /** Nudge a placed tile by -2..+2, clamped into the screen. */
    private static int jitteredTile(Random rng, int v, int min, int max) {
        int j = v + rng.nextInt(5) - 2;
        return Math.max(min, Math.min(max, j));
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
        projectileTimer = 0;
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

    /** True while this enemy is still a spawning cloud (harmless, immobile). */
    public static boolean spawning(Enemy e) {
        return e.alive && e.spawning > 0;
    }

    /** Live enemies on Link's current screen; tests place and inspect enemies here. */
    public List<Enemy> enemies() {
        return enemies;
    }

    /** In-flight projectiles on Link's current screen. */
    public List<Projectile> projectiles() {
        return projectiles;
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
