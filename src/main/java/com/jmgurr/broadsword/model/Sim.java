package com.jmgurr.broadsword.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    /** Enemy step clock: one tile per step (~2 tiles/sec). */
    public static final float ENEMY_STEP_INTERVAL = 0.5f;
    /** Minimum time between sword swings. */
    public static final float SWORD_COOLDOWN = 0.4f;
    /** How long the blade stays out (the vulnerable "swinging" window). */
    public static final float SWORD_SWING_DURATION = 0.2f;
    /** Invulnerability after contact damage. */
    public static final float I_FRAME_DURATION = 1.0f;
    /** Hearts lost per enemy hit (contact or projectile). */
    public static final float ENEMY_DAMAGE = 0.5f;
    /** Sword hits to kill a Grunt. */
    public static final int GRUNT_HP = 2;
    /** Sword hits to kill an Octorock. */
    public static final int OCTOROCK_HP = 2;
    /** Sword hits to fell one Hydra head (tunable). */
    public static final int HYDRA_HEAD_HP = 4;
    /** Hydra head: seconds between Fireballs with all three heads up (tunable). */
    public static final float HYDRA_FIRE_INTERVAL = 1.5f;
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
    /** Light: tiles straight ahead of Link, obstacles and enemies included. */
    public static final int LIGHT_RANGE = 2;
    /** Seconds the Light beam stays visible. */
    public static final float LIGHT_FX_DURATION = 0.25f;
    /** Ghosts: seconds Link must linger on a Cemetery screen before the first one drifts out. */
    public static final float GHOST_FIRST_DELAY = 1.5f;
    /** Ghosts: seconds between arrivals at the start of the ramp. */
    public static final float GHOST_INTERVAL_START = 3.0f;
    /** Ghosts: seconds of lingering over which the interval narrows to GHOST_INTERVAL_MIN. */
    public static final float GHOST_RAMP_SECONDS = 30f;
    /** Ghosts: the interval the ramp converges on. */
    public static final float GHOST_INTERVAL_MIN = 1.5f;
    /** Ghosts: how many may haunt one screen at once. */
    public static final int GHOST_CAP = 6;
    /** Ghosts: drift speed in tiles per second - slower than Link, faster than a Grunt. */
    public static final float GHOST_SPEED = 1.5f;
    /** Ghosts: closer than this (in tiles) and the touch drains a Heart. */
    public static final float GHOST_TOUCH_RADIUS = 0.5f;

    public enum Phase {
        PLAYING, GAME_OVER, VICTORY
    }

    private final World world;
    /** Authored dungeon data (V1: the hydra). Run state lives in dungeonRun. */
    private Dungeon dungeon;
    private final Link link;
    private Consumer<SaveState> saveSink = s -> {
    };

    private float stepTimer = 0;
    private boolean interpolating = false;
    private float interpProgress = 1;
    private Link.Dir interpolatingDir = null; // dir of the in-flight step

    private Phase phase = Phase.PLAYING;
    private int magic = World.MAX_MAGIC; // Light casts left; a death never refills it
    private boolean secretRevealed = false;
    /** The cave Link currently stands in, or null on the overworld. */
    private Cave cave = null;
    private float lightFxTimer = 0; // Light beam still visible while > 0
    private Link.Dir lightFxFacing = Link.Dir.UP;
    private float swordTimer = 0; // time until the next swing is allowed
    private float swingTimer = 0; // blade still out while > 0
    private float invulnTimer = 0; // i-frames remaining
    private float enemyTimer = 0;
    /**
     * Fire charges left: one free cast per screen entered, no Magic involved.
     * Refilled whenever Link arrives on a new screen (or re-enters a cave).
     */
    private boolean fireReady = true;

    /** True once Link has picked up this world's Flute. Survives death and reload. */
    private boolean fluteTaken = false;
    /** The Flute has been played since the current visit began. */
    private boolean fluteUsedThisVisit = false;
    /** Seconds Link has lingered on this screen: drives the Ghost spawn ramp. */
    private float ghostRampElapsed = 0;
    /** Countdown to the next Ghost. */
    private float ghostTimer = 0;
    private Random visitRng = new Random();

    /** Live enemies of the screen Link currently occupies. */
    private final List<Enemy> enemies = new ArrayList<>();
    /** In-flight projectiles on the current screen. */
    private final List<Projectile> projectiles = new ArrayList<>();
    private int enemyScreenKey = Integer.MIN_VALUE;
    private float projectileTimer = 0;

    // ---- T9: Dungeon Core (authored multi-screen dungeon) ----
    /** Keys held, locks opened and loot taken; persists across exits and deaths. */
    private final DungeonRun dungeonRun = new DungeonRun();
    private boolean inDungeon;
    private boolean diedInDungeon;
    /**
     * Dungeon screens lit by Light during the current visit; cleared on every
     * entry and exit (dark screens reset on re-entry per the keep-vs-reset table).
     */
    private final java.util.Set<Integer> litScreens = new java.util.HashSet<>();

    /** A new game: fresh world from the seed, Link at spawn. */
    public Sim(long seed) {
        this.world = WorldGenerator.generate(seed);
        this.dungeon = Dungeon.loadHydra();
        this.link = new Link(World.SPAWN_SX, World.SPAWN_SY, World.SPAWN_TX, World.SPAWN_TY);
        newScreenVisit();
        placeScreenEnemies();
    }

    /** Continue: re-derive the saved world from its seed, resume at the saved position. */
    public Sim(SaveState save) {
        this.world = WorldGenerator.generate(save.seed());
        this.dungeon = Dungeon.loadHydra();
        this.link = new Link(save.sx(), save.sy(), save.tx(), save.ty());
        this.link.facing = save.facing();
        this.magic = save.magic();
        int itemsHeld = 0;
        for (int id : save.takenLoot()) {
            if (this.dungeon.isItemId(id)) itemsHeld++;
        }
        this.dungeonRun.restore(save.dungeonKeys(), itemsHeld, save.openedLocks(), save.takenLoot(),
                save.bossDefeated());
        this.inDungeon = save.inDungeon();
        if (save.secretRevealed()) {
            world.revealSecretStairs();
            this.secretRevealed = true;
        }
        // A v2 save in the Cave can only mean the secret cave; v3 stores its key.
        if (save.caveKey() == SaveState.CAVE_SECRET_V2) {
            this.cave = world.caveAt(world.secretTree().sx(), world.secretTree().sy(),
                    world.secretTree().tx(), world.secretTree().ty());
        } else if (save.caveKey() >= 0) {
            this.cave = world.caves().get(save.caveKey());
        }
        this.fluteTaken = save.fluteTaken();
        newScreenVisit();
        placeScreenEnemies();
    }

    /** Snapshot of the current persistent state (seed + position + run progress). */
    public SaveState saveState() {
        return new SaveState(world.seed(), link.sx, link.sy, link.tx, link.ty, link.facing,
                magic, secretRevealed, cave == null ? SaveState.NO_CAVE : World.packCave(
                        cave.entry().sx(), cave.entry().sy(), cave.entry().tx(), cave.entry().ty()), fluteTaken,
                dungeonRun.keys(), inDungeon, dungeonRun.openedLocks(), dungeonRun.takenLoot(),
                dungeonRun.bossDefeated());
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
        if (phase == Phase.GAME_OVER) {
            return; // game-over: the renderer shows the overlay and calls respawn()
        }
        if (phase == Phase.VICTORY) {
            return; // victory: the renderer shows the overlay and takes over
        }
        if (desired != null && !interpolating && stepTimer >= STEP_INTERVAL) {
            int psx = link.sx, psy = link.sy;
            boolean moved = inDungeon ? stepDungeon(desired) : link.step(terrain(), desired);
            if (moved) {
                if (link.sx != psx || link.sy != psy) {
                    // crossed a screen edge: no slide animation across the seam,
                    // the fire is ready again, the Ghost ramp and the Flute
                    // start over, and the run autosaves
                    interpolating = false;
                    fireReady = true;
                    newScreenVisit();
                    if (inDungeon) placeScreenEnemies(); // dungeon screens respawn their enemies
                    autosave();
                } else {
                    interpolating = true;
                    interpolatingDir = desired;
                    interpProgress = 0;
                }
                stepTimer = 0;
                takeFluteIfStandingOnIt();
                useStairsIfStandingOnThem();
                enterDungeonIfStandingOnEntrance();
                takeDungeonLootIfStandingOnIt();
            }
        }
        stepTimer += delta;
        swordTimer = Math.max(0, swordTimer - delta);
        swingTimer = Math.max(0, swingTimer - delta);
        invulnTimer = Math.max(0, invulnTimer - delta);
        lightFxTimer = Math.max(0, lightFxTimer - delta);
        if (interpolating) {
            interpProgress = Math.min(1, interpProgress + delta / STEP_INTERVAL);
            if (interpProgress >= 1) {
                interpolating = false;
            }
        }
        if (swing) {
            swing();
        }
        if (!inCave() && !inDungeon && enemyScreenKey != screenKey(link.sx, link.sy)) {
            placeScreenEnemies(); // leaving and returning respawns the tiles, jittered, in clouds
            enemyTimer = 0;
            projectileTimer = 0;
        }
        for (Enemy e : enemies) {
            if (e.interp > 0) {
                e.interp = Math.max(0, e.interp - delta / ENEMY_STEP_INTERVAL);
            }
        }
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            if (e.spawning > 0) {
                e.spawning = Math.max(0, e.spawning - delta);
            } else if (e.kind == EnemyKind.HYDRA_HEAD) {
                // fires down a clear cardinal lane; no cloud, no wander
                if (e.fireTimer <= 0 && (e.tx == link.tx || e.ty == link.ty)
                        && lineClear(e.tx, e.ty, link.tx, link.ty)) {
                    fireballFrom(e);
                } else {
                    e.fireTimer = Math.max(0, e.fireTimer - delta);
                }
            } else if (e.kind == EnemyKind.OCTOROCK) {
                e.fireTimer = Math.max(0, e.fireTimer - delta);
            }
        }
        // projectiles first: a fireball fired on this tick starts moving next tick
        projectileTimer += delta;
        while (projectileTimer >= PROJECTILE_STEP_INTERVAL) {
            projectileTimer -= PROJECTILE_STEP_INTERVAL;
            stepProjectiles();
        }
        enemyTimer += delta;
        if (enemyTimer >= ENEMY_STEP_INTERVAL) {
            enemyTimer = 0;
            stepEnemies();
        }
        stepGhosts(delta);
    }

    // --- Ghosts and the Flute -------------------------------------------------

    /**
     * The one screen visit's bookkeeping: called whenever Link arrives on a
     * screen he was not on a moment ago. The Flute gets its use back and the
     * Ghost ramp starts from zero, which is the ticket's "resets on re-entry".
     */
    private void newScreenVisit() {
        long visitKey = screenKey(link.sx, link.sy) * 2L + (inCave() ? 1 : 0);
        fluteUsedThisVisit = false;
        ghostRampElapsed = 0;
        ghostTimer = GHOST_FIRST_DELAY;
        enemies.removeIf(e -> e.kind == EnemyKind.GHOST);
        visitRng = new Random(world.usedSeed() * 1000003L + visitKey * 7919L + entryCounter * 104729L);
    }

    /** Ghosts drift on their own smooth clock: they never take the tile grid. */
    private void stepGhosts(float delta) {
        if (inCave() || inDungeon) {
            return; // caves and dungeons hold nothing ethereal, Ghosts included
        }
        if (!world.isCemetery(link.sx, link.sy) || fluteUsedThisVisit) {
            return; // Ghosts haunt the Cemetery only, and a played tune ends the visit's spawning
        }
        ghostRampElapsed += delta;
        ghostTimer = Math.max(0, ghostTimer - delta);
        if (ghostTimer <= 0 && liveGhosts() < GHOST_CAP) {
            spawnGhost();
            ghostTimer = currentGhostInterval();
        }
        boolean touch = false;
        for (Enemy e : enemies) {
            if (!e.alive || !e.ethereal) {
                continue;
            }
            double dx = link.tx - e.fx, dy = link.ty - e.fy;
            double len = Math.hypot(dx, dy);
            if (len > 1e-6) {
                e.fx += dx / len * GHOST_SPEED * delta;
                e.fy += dy / len * GHOST_SPEED * delta;
            }
            e.tx = (int) Math.round(e.fx);
            e.ty = (int) Math.round(e.fy);
            if (e.tx < 0 || e.tx >= World.SCREEN_W || e.ty < 0 || e.ty >= World.SCREEN_H) {
                e.alive = false; // drifted off the screen: despawned
                continue;
            }
            if (!world.walkable(link.sx, link.sy, e.tx, e.ty)) {
                e.solidPass = true; // proof it is phasing, not walking
            }
            if (Math.hypot(e.fx - link.tx, e.fy - link.ty) < GHOST_TOUCH_RADIUS) {
                touch = true;
            }
        }
        enemies.removeIf(e -> !e.alive && e.ethereal);
        if (touch) {
            damageLink(); // a Ghost's touch is worth 1 Heart, i-frames and all
        }
    }

    /** The ramp: GHOST_INTERVAL_START narrowing to GHOST_INTERVAL_MIN over GHOST_RAMP_SECONDS. */
    private float currentGhostInterval() {
        float t = Math.min(1, ghostRampElapsed / GHOST_RAMP_SECONDS);
        return GHOST_INTERVAL_START + (GHOST_INTERVAL_MIN - GHOST_INTERVAL_START) * t;
    }

    private int liveGhosts() {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.alive && e.ethereal) {
                n++;
            }
        }
        return n;
    }

    /** One Ghost drifts out of the tombstones, never on Link's own tile. */
    private void spawnGhost() {
        for (int guard = 0; guard < 60; guard++) {
            int tx = visitRng.nextInt(World.SCREEN_W);
            int ty = visitRng.nextInt(World.SCREEN_H);
            if (tx == link.tx && ty == link.ty) {
                continue;
            }
            Enemy g = new Enemy(EnemyKind.GHOST, tx, ty, enemyHp(EnemyKind.GHOST),
                    (world.usedSeed() ^ screenKey(link.sx, link.sy) * 31337L) * 2654435761L + guard);
            g.ethereal = true;
            enemies.add(g);
            return;
        }
    }

    /**
     * The one per-world Flute, picked up by walking onto its Cemetery tile.
     * True once Link holds it; it never goes back.
     */
    public boolean hasFlute() {
        return fluteTaken;
    }

    /**
     * Play the Flute: one tune per screen visit, for free. It dispels every
     * Ghost on the screen and stops the spawning while Link stays here. False
     * when he has no Flute or has already played this visit.
     */
    public boolean playFlute() {
        if (phase != Phase.PLAYING || !fluteTaken || fluteUsedThisVisit) {
            return false;
        }
        fluteUsedThisVisit = true;
        for (Enemy e : enemies) {
            if (e.ethereal) {
                e.alive = false; // dispelled: the tune does not kill, it clears
            }
        }
        enemies.removeIf(e -> !e.alive && e.ethereal);
        ghostTimer = Float.MAX_VALUE; // no Ghost returns while Link lingers here
        return true;
    }

    /** True while the Flute can still be played on this screen visit. */
    public boolean fluteReady() {
        return fluteTaken && !fluteUsedThisVisit;
    }

    /**
     * Swing the sword at the tile Link faces. Ignored while the cooldown has
     * not expired. Deals 1 damage; survivors are stunned one step and knocked
     * back one tile unless the target tile is blocked.
     */
    public void swing() {
        if (swordTimer > 0) {
            return; // cooldown not expired
        }
        hitWith(link.facing, true);
    }

    // --- Light spell ---------------------------------------------------------

    /** Magic casts left. Spent Magic never comes back: no refill on death or reload. */
    public int magic() {
        return magic;
    }

    /** True while the Light beam is visible (renderer draws it along the saved facing). */
    public boolean lightVisible() {
        return lightFxTimer > 0;
    }

    public Link.Dir lightFxFacing() {
        return lightFxFacing;
    }

    /**
     * Cast Light: a beam straight ahead of Link for {@link #LIGHT_RANGE} tiles.
     * It costs one fire charge, never Magic: Link has exactly one charge and it
     * refills the moment he enters a new screen. It passes through everything:
     * enemies in the beam take 1 damage and one tile of knockback, flammable
     * trees burn away, and burning the Secret tree reveals the stairs. Returns
     * false (no charge spent) when this screen's charge is already used.
     */
    public boolean castLight() {
        if (phase != Phase.PLAYING || !fireReady) {
            return false;
        }
        fireReady = false;
        lightFxTimer = LIGHT_FX_DURATION;
        lightFxFacing = link.facing;
        if (inDungeon) {
            lightDungeonScreen(); // a Dark screen is lit for the rest of the visit
        }
        if (!inCave()) {
            for (int i = 1; i <= LIGHT_RANGE; i++) {
                int tx = link.tx + link.facing.dx * i;
                int ty = link.ty + link.facing.dy * i;
                if (tx < 0 || tx >= World.SCREEN_W || ty < 0 || ty >= World.SCREEN_H) {
                    continue; // the beam leaves the screen; the next screen is not simulated
                }
                Tile tile = inDungeon ? dungeonScreen().grid().get(tx, ty)
                        : world.screen(link.sx, link.sy).get(tx, ty);
                if (tile == Tile.FLAMMABLE_TREE) {
                    burn(link.sx, link.sy, tx, ty);
                    autosave(); // a burned tree is persistent world state
                }
                for (Enemy e : enemies) {
                    // the closed list: Ghosts are not on it, and phase straight through;
                    // the Hydra stands on the list but takes nothing from Light (T11)
                    if (e.alive && e.spawning <= 0 && !e.ethereal && !e.stationary
                            && e.tx == tx && e.ty == ty) {
                        hit(e, link.facing); // knocked back along the beam
                    }
                }
            }
        }
        return true;
    }

    /** True while this screen's fire charge is still unused. */
    public boolean fireReady() {
        return fireReady;
    }

    /** Burn one flammable tree; the Secret tree leaves stairs in its place. */
    private void burn(int sx, int sy, int tx, int ty) {
        if (world.isSecretTree(sx, sy, tx, ty)) {
            world.revealSecretStairs();
            secretRevealed = true;
        } else {
            world.screen(sx, sy).set(tx, ty, Tile.GRASS);
        }
    }

    /** The world Link currently walks on: the overworld, or the current cave room. */
    private Terrain terrain() {
        if (inDungeon) {
            DungeonScreen s = dungeonScreen();
            return (sx, sy, tx, ty) -> tx >= 0 && tx < World.SCREEN_W && ty >= 0 && ty < World.SCREEN_H
                    && dungeonTileWalkable(s, tx, ty);
        }
        return inCave() ? caveRoomTerrain() : world;
    }

    // ---- T9: Dungeon Core -------------------------------------------------

    public boolean inDungeon() {
        return inDungeon;
    }

    /** Index of the dungeon screen Link occupies (link.sx doubles as it). */
    public int dungeonScreenIndex() {
        return link.sx;
    }

    /** Tests swap in a small authored dungeon; the overworld entrance still leads to it. */
    void setDungeon(Dungeon d) {
        this.dungeon = d;
    }

    /** Enter through the overworld entrance without walking onto it (tests). */
    void enterDungeon() {
        enterDungeonFromOverworld();
    }

    /** Step out of the dungeon onto the overworld, beside the entrance (tests). */
    void leaveDungeon() {
        leaveDungeonToOverworld();
    }

    public Dungeon dungeon() {
        return dungeon;
    }

    /** Keys held, locks opened and loot taken; persists across exits and deaths. */
    public DungeonRun dungeonRun() {
        return dungeonRun;
    }

    /** The screen Link occupies inside the dungeon (link.sx is the screen index). */
    public DungeonScreen dungeonScreen() {
        return dungeon.screen(link.sx);
    }

    private boolean dungeonTileWalkable(DungeonScreen s, int tx, int ty) {
        Tile t = s.grid().get(tx, ty);
        if (t == Tile.DOOR) {
            Map.Entry<Link.Dir, DungeonScreen.Door> at = s.doorAt(tx, ty);
            if (at != null && at.getValue().locked() && !dungeonRun.isOpen(at.getValue().lockId()))
                return false;
        } else if (!t.walkable) {
            return false;
        }
        if (!dungeonRun.bossDefeated() && s.blockedByHydra(tx, ty)) {
            return false; // the Hydra stands on floor tiles like a wall
        }
        return !blockOccupies(tx, ty) && !enemyOccupies(tx, ty);
    }

    /** A shoveable block occupies its tile like a wall: nothing walks through. */
    private boolean blockOccupies(int tx, int ty) {
        return dungeonRun.blocks(dungeonScreenIndex(), dungeonScreen()).stream()
                .anyMatch(p -> p.tx() == tx && p.ty() == ty);
    }

    /** Undismissed loot on the tile (keys, chests, revealed hidden loot): a block never covers it. */
    private boolean lootAt(int tx, int ty) {
        DungeonScreen s = dungeonScreen();
        for (Lootable l : s.keys()) {
            if (!dungeonRun.taken(l.id()) && l.tx() == tx && l.ty() == ty) return true;
        }
        for (Lootable l : s.items()) {
            if (!dungeonRun.taken(l.id()) && l.tx() == tx && l.ty() == ty) return true;
        }
        for (Lootable l : s.hiddenLoot()) {
            // hidden or revealed, loot is never shoveled under a block
            if (!dungeonRun.taken(l.id()) && l.tx() == tx && l.ty() == ty) return true;
        }
        return false;
    }

    /** Walkable tile inside the current dungeon screen (out of bounds is wall). */
    public boolean isDungeonWalkable(int tx, int ty) {
        return tx >= 0 && tx < World.SCREEN_W && ty >= 0 && ty < World.SCREEN_H
                && dungeonTileWalkable(dungeonScreen(), tx, ty);
    }

    /**
     * One dungeon step. Passage exists only at authored doors: walking off a
     * screen edge through an open door arrives at the target screen's return
     * door; stepping off the overworld-exit tile returns to the overworld;
     * bumping a locked door spends a key (any key opens any lock, consumed).
     */
    private boolean stepDungeon(Link.Dir d) {
        DungeonScreen s = dungeonScreen();
        link.facing = d;
        int nx = link.tx + d.dx, ny = link.ty + d.dy;
        if (nx >= 0 && nx < World.SCREEN_W && ny >= 0 && ny < World.SCREEN_H) {
            if (blockOccupies(nx, ny)) {
                // a shove: the block moves 1 tile when the far tile is clear,
                // and Link takes the tile the block just left
                int fx = nx + d.dx, fy = ny + d.dy;
                Tile far = (fx >= 0 && fx < World.SCREEN_W && fy >= 0 && fy < World.SCREEN_H)
                        ? s.grid().get(fx, fy) : Tile.DUNGEON_WALL;
                if (!far.walkable || far == Tile.DOOR || far == Tile.DUNGEON_EXIT
                        || blockOccupies(fx, fy) || lootAt(fx, fy)) {
                    return false; // blocked: the block stays put, so does Link
                }
                dungeonRun.pushBlock(dungeonScreenIndex(), s,
                        new ScreenPos(0, 0, nx, ny), new ScreenPos(0, 0, fx, fy));
                link.tx = nx;
                link.ty = ny;
                return true;
            }
            Tile t = s.grid().get(nx, ny);
            if (t == Tile.DOOR) {
                Map.Entry<Link.Dir, DungeonScreen.Door> at = s.doorAt(nx, ny);
                if (at != null && at.getValue().locked() && !dungeonRun.isOpen(at.getValue().lockId())) {
                    if (dungeonRun.spendKey(at.getValue().lockId())) autosave();
                    return false; // the door opens under the next step
                }
            }
            if (!dungeonTileWalkable(s, nx, ny)) return false;
            link.tx = nx;
            link.ty = ny;
            return true;
        }
        // off-screen: only an authored door leads out
        DungeonScreen.Door door = s.dir(d);
        if (door != null) {
            if (door.locked() && !dungeonRun.isOpen(door.lockId())) {
                if (dungeonRun.spendKey(door.lockId())) autosave();
                return false;
            }
            ScreenPos arrive = dungeon.screen(door.target()).doorTile(opposite(d));
            if (arrive == null) return false; // parse validates this pairing
            link.sx = door.target();
            link.tx = arrive.tx();
            link.ty = arrive.ty();
            return true;
        }
        if (s.grid().get(link.tx, link.ty) == Tile.DUNGEON_EXIT) {
            leaveDungeonToOverworld();
            return false; // leaveDungeon already placed Link and saved
        }
        return false;
    }

    /** Live (non-spawning) enemies occupying a tile; a block never lands on one. */
    private boolean enemyOccupies(int tx, int ty) {
        for (Enemy e : enemies) {
            if (e.alive && e.spawning <= 0 && e.tx == tx && e.ty == ty) return true;
        }
        return false;
    }

    /** Walking onto the overworld entrance tile enters the dungeon. */
    private void enterDungeonIfStandingOnEntrance() {
        if (inDungeon || inCave() || phase != Phase.PLAYING) return;
        if (!world.isEntrance(link.sx, link.sy, link.tx, link.ty)) return;
        enterDungeonFromOverworld();
    }

    /**
     * The one entry path: arrive at the entrance screen's inner tile. Dark
     * screens are dark again (the visit's lighting is per-visit).
     */
    private void enterDungeonFromOverworld() {
        DungeonScreen entry = dungeon.entry();
        ScreenPos e = entry.exitTile();
        Link.Dir inward = entry.exitInward();
        inDungeon = true;
        litScreens.clear(); // dark screens reset on re-entry
        link.sx = dungeon.indexOf(entry.id());
        link.sy = 0;
        link.tx = e.tx() + inward.dx;
        link.ty = e.ty() + inward.dy;
        link.facing = inward;
        link.hearts = World.MAX_HEARTS; // like every entrance, arriving restores Hearts
        interpolating = false;
        fireReady = true;
        newScreenVisit();
        placeScreenEnemies();
        autosave();
    }

    /** Stepping off the exit tile: back to the overworld, beside the entrance. */
    private void leaveDungeonToOverworld() {
        inDungeon = false;
        litScreens.clear(); // the visit is over; its lighting went with it
        ScreenPos en = world.dungeonEntrance();
        // any walkable neighbour of the entrance, never the entrance tile itself
        int ax = en.tx(), ay = en.ty();
        outer:
        for (int[] d : new int[][]{
                { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
        }) {
            int px = en.tx() + d[0], py = en.ty() + d[1];
            if (world.walkable(en.sx(), en.sy(), px, py)) {
                ax = px;
                ay = py;
                break outer;
            }
        }
        link.sx = en.sx();
        link.sy = en.sy();
        link.tx = ax;
        link.ty = ay;
        link.hearts = World.MAX_HEARTS;
        interpolating = false;
        fireReady = true;
        newScreenVisit();
        placeScreenEnemies();
        autosave();
    }

    /** Keys and items are contact pickups; taken loot never reappears. */
    private void takeDungeonLootIfStandingOnIt() {
        if (!inDungeon) return;
        DungeonScreen s = dungeonScreen();
        for (Lootable k : s.keys()) {
            if (k.tx() == link.tx && k.ty() == link.ty && !dungeonRun.taken(k.id())) {
                dungeonRun.takeKey(k.id());
                autosave();
            }
        }
        for (Lootable i : s.items()) {
            if (i.tx() == link.tx && i.ty() == link.ty && !dungeonRun.taken(i.id())) {
                dungeonRun.takeItem(i.id());
                autosave();
            }
        }
        // loot revealed by a block trigger: invisible until that first push
        for (Lootable h : s.hiddenLoot()) {
            if (dungeonRun.revealed(h.id()) && h.tx() == link.tx && h.ty() == link.ty
                    && !dungeonRun.taken(h.id())) {
                dungeonRun.takeItem(h.id());
                autosave();
            }
        }
    }

    // ---- T10: dark screens and shoveable blocks -----------------------------

    /** True when this dungeon screen is obscured: authored dark, not yet lit this visit. */
    public boolean screenIsDark() {
        return inDungeon && dungeonScreen().dark() && !litScreens.contains(dungeonScreenIndex());
    }

    /**
     * Light the current dungeon screen for the rest of this visit. Called by
     * {@link #castLight()}; a no-op on a screen that is not dark.
     */
    private void lightDungeonScreen() {
        if (inDungeon && dungeonScreen().dark()) litScreens.add(dungeonScreenIndex());
    }

    static Link.Dir opposite(Link.Dir d) {
        return switch (d) {
            case UP -> Link.Dir.DOWN;
            case DOWN -> Link.Dir.UP;
            case LEFT -> Link.Dir.RIGHT;
            case RIGHT -> Link.Dir.LEFT;
        };
    }

    private Terrain caveRoomTerrain() {
        Screen room = cave.room();
        return (sx, sy, tx, ty) -> tx >= 0 && tx < World.SCREEN_W && ty >= 0 && ty < World.SCREEN_H
                && room.get(tx, ty).walkable;
    }

    /** True while Link stands in any cave. */
    public boolean inCave() {
        return cave != null;
    }

    /** The cave Link stands in, or null. */
    public Cave currentCave() {
        return cave;
    }

    public boolean secretRevealed() {
        return secretRevealed;
    }

    /**
     * The Flute pickup: its tile is walkable, and stepping onto it is all it
     * takes. Persistent: a death never hands the Flute back.
     */
    private void takeFluteIfStandingOnIt() {
        if (inDungeon) {
            return; // dungeon screens reuse the index space, never the overworld's Flute
        }
        if (!fluteTaken && world.isFlute(link.sx, link.sy, link.tx, link.ty)) {
            fluteTaken = true;
            autosave(); // an item acquired is a major event
        }
    }

    /** The stairs toggle: standing on a STAIRS tile after a step moves Link through it. */
    private void useStairsIfStandingOnThem() {
        if (inDungeon) {
            return; // dungeon screens reuse the index space, never the overworld's stairs
        }
        if (inCave()) {
            if (cave.room().get(link.tx, link.ty) == Tile.STAIRS) {
                leaveCave();
            }
        } else {
            Cave c = world.caveAt(link.sx, link.sy, link.tx, link.ty);
            if (c != null && world.screen(link.sx, link.sy).get(link.tx, link.ty) == Tile.STAIRS) {
                enterCave(c);
            }
        }
    }

    /**
     * Into a cave room: Link appears at its entry tile, facing back toward the
     * exit stairs so one step out returns him the way he came.
     */
    private void enterCave(Cave c) {
        cave = c;
        link.tx = c.entryTx();
        link.ty = c.entryTy();
        link.facing = Link.Dir.DOWN; // the exit stairs are south of the entry tile in every room
        interpolating = false;
        fireReady = true; // entering the cave counts as entering a new screen
        newScreenVisit();
        placeScreenEnemies(); // returning re-places the overworld screen
        autosave();
    }

    /** Back onto a walkable tile beside the cave's stairs, never on the stairs themselves. */
    private void leaveCave() {
        ScreenPos e = cave.entry();
        cave = null;
        int sx = e.sx(), sy = e.sy();
        int stx = e.tx(), sty = e.ty();
        int bdx = -link.facing.dx, bdy = -link.facing.dy; // back the way Link came, then any side
        for (int[] d : new int[][] { { bdx, bdy }, { 0, -1 }, { 0, 1 }, { 1, 0 }, { -1, 0 } }) {
            int tx = stx + d[0], ty = sty + d[1];
            if (world.walkable(sx, sy, tx, ty)) {
                link.sx = sx;
                link.sy = sy;
                link.tx = tx;
                link.ty = ty;
                interpolating = false;
                fireReady = true; // back on the overworld: the fire is ready again
                newScreenVisit();
                placeScreenEnemies();
                autosave();
                return;
            }
        }
    }

    /** One swing's worth of damage along Link's facing, plus the blade-out window. */
    private void hitWith(Link.Dir blow, boolean bladeOut) {
        if (phase != Phase.PLAYING) {
            return;
        }
        if (bladeOut) {
            swordTimer = SWORD_COOLDOWN;
            swingTimer = SWORD_SWING_DURATION;
        }
        int hx = link.tx + blow.dx;
        int hy = link.ty + blow.dy;
        for (Enemy e : enemies) {
            // the sword passes through a Ghost: it is not on the closed list;
            // the Hydra body is scenery: only the heads take the blade
            if (e.alive && e.spawning <= 0 && !e.ethereal && e.kind != EnemyKind.HYDRA_BODY
                    && e.tx == hx && e.ty == hy) {
                hit(e, blow);
            }
        }
    }

    /** 1 damage away from the blow ({@code blow} = the direction it travels). */
    private void hit(Enemy e, Link.Dir blow) {
        if (e.stationary) {
            e.hp -= 1; // a boss part never stuns or slides
            if (e.hp <= 0) {
                e.alive = false;
            }
            return;
        }
        e.hp -= 1;
        if (e.hp <= 0) {
            e.alive = false;
            return;
        }
        e.stunned = true;
        // knockback: one tile directly away from the blow, if the tile is clear
        int kx = e.tx + blow.dx;
        int ky = e.ty + blow.dy;
        if (terrain().walkable(link.sx, link.sy, kx, ky) && !liveEnemyAt(kx, ky)) {
            e.fromTx = e.tx;
            e.fromTy = e.ty;
            e.interp = 1;
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
            if (!e.alive || e.spawning > 0 || e.stationary) {
                continue; // a cloud and a boss part neither move nor crowd Link
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
        checkHydraDown();
    }

    // ---- T11: the Hydra boss and the Triforce -----------------------------

    /**
     * Spawn the Hydra: an invulnerable three-tile body and three stationary
     * heads with staggered fire timers. No spawn clouds: the boss is simply
     * there when Link walks in.
     */
    private void placeHydra(DungeonScreen s) {
        for (ScreenPos b : s.hydraBodyTiles()) {
            enemies.add(new Enemy(EnemyKind.HYDRA_BODY, b.tx(), b.ty(),
                    enemyHp(EnemyKind.HYDRA_BODY), 0L));
        }
        float stagger = hydraIntervalAt(3) / 3f; // heads fire in a round-robin
        int slot = 0;
        for (ScreenPos h : s.hydraHeadTiles()) {
            Enemy head = new Enemy(EnemyKind.HYDRA_HEAD, h.tx(), h.ty(),
                    enemyHp(EnemyKind.HYDRA_HEAD), 0L);
            head.fireTimer = stagger * (slot++ + 1);
            enemies.add(head);
        }
    }

    /** Live Hydra heads on the boss screen. */
    private int liveHeads() {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.alive && e.kind == EnemyKind.HYDRA_HEAD) n++;
        }
        return n;
    }

    /**
     * The Hydra's fire clock escalates with the heads left: with n of 3 up,
     * each head fires every HYDRA_FIRE_INTERVAL * n / 3 seconds.
     */
    private float hydraIntervalAt(int heads) {
        return HYDRA_FIRE_INTERVAL * heads / 3f;
    }

    /** One Fireball aimed at Link along the head's clear cardinal lane. */
    private void fireballFrom(Enemy e) {
        int fdx = Integer.signum(link.tx - e.tx), fdy = Integer.signum(link.ty - e.ty);
        Projectile p = new Projectile(e.tx + fdx, e.ty + fdy, fdx, fdy);
        e.fireTimer = hydraIntervalAt(liveHeads());
        if (p.tx == link.tx && p.ty == link.ty) {
            hitLinkByProjectile(p); // point-blank: hit on the firing tile
        } else {
            projectiles.add(p);
        }
    }

    /**
     * All heads down: the body sinks, the Triforce is claimed (the autosave
     * marks the dungeon cleared) and the run is won. Idempotent.
     */
    private void checkHydraDown() {
        if (phase != Phase.PLAYING || dungeonRun.bossDefeated() || !inDungeon) return;
        if (dungeonScreen().bossTile() == null || liveHeads() > 0) return;
        boolean bodyWasHere = enemies.stream().anyMatch(e -> e.kind == EnemyKind.HYDRA_BODY);
        if (!bodyWasHere) return;
        dungeonRun.markBossDefeated();
        enemies.removeIf(e -> e.kind == EnemyKind.HYDRA_BODY); // the body sinks with the heads
        autosave(); // a piece collected autosaves before the victory screen shows
        phase = Phase.VICTORY;
    }

    /** True when the run is won: the renderer shows the victory overlay. */
    public boolean won() {
        return phase == Phase.VICTORY;
    }

    /**
     * Take {@link #ENEMY_DAMAGE} Hearts, obeying i-frames; 0 Hearts ends the
     * run. Caves are always safe: they hold no enemies.
     */
    private void damageLink() {
        if (invulnTimer > 0 || (inCave() && world.isSecretTree(cave.entry().sx(), cave.entry().sy(),
                cave.entry().tx(), cave.entry().ty()))) {
            return;
        }
        link.hearts -= ENEMY_DAMAGE;
        invulnTimer = I_FRAME_DURATION;
        if (link.hearts <= 0) {
            link.hearts = 0;
            phase = Phase.GAME_OVER;
            diedInDungeon = inDungeon; // dying inside respawns at the dungeon entrance
        }
    }

    /** V1 HP default per species (tunable per kind when they diverge). */
    static int enemyHp(EnemyKind kind) {
        return switch (kind) {
            case OCTOROCK -> OCTOROCK_HP;
            case GHOST -> 1; // nothing damages a Ghost; the Flute dispels it outright
            case HYDRA_HEAD -> HYDRA_HEAD_HP;
            case HYDRA_BODY -> Integer.MAX_VALUE; // invulnerable scenery
            default -> GRUNT_HP;
        };
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
            if (!terrain().walkable(link.sx, link.sy, x, y)) {
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
            } else if (!terrain().walkable(link.sx, link.sy, p.tx, p.ty)) {
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
        if (!terrain().walkable(link.sx, link.sy, nx, ny) || liveEnemyAtOther(e, nx, ny)) {
            return false;
        }
        // remember where the slide starts so the renderer can glide the sprite
        e.fromTx = e.tx;
        e.fromTy = e.ty;
        e.interp = 1;
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
    void placeScreenEnemies() { // package-visible: tests place after a teleported screen change
        enemies.clear();
        projectiles.clear();
        if (inCave()) {
            // caves are empty: nothing to place
            enemyScreenKey = Integer.MIN_VALUE; // leaving the cave re-places the overworld screen
            return;
        }
        if (inDungeon) {
            int key = screenKey(link.sx, link.sy);
            if (dungeonRun.bossDefeated()) {
                enemyScreenKey = ~key; // a cleared dungeon stays cleared: nothing respawns
                return;
            }
            // authored enemies, exact tiles, spawning clouds; respawn every visit
            for (int slot = 0; slot < dungeonScreen().enemies().size(); slot++) {
                EnemySpawn sp = dungeonScreen().enemies().get(slot);
                if (dungeonScreen().blockedByHydra(sp.tx(), sp.ty())) {
                    continue; // never bury an authored enemy inside the boss
                }
                long wanderSeed = world.usedSeed() * 1000003L + key * 31L + slot;
                Enemy e = new Enemy(sp.kind(), sp.tx(), sp.ty(), enemyHp(sp.kind()), wanderSeed);
                e.spawning = ENEMY_SPAWN_DURATION;
                enemies.add(e);
            }
            if (dungeonScreen().bossTile() != null) {
                placeHydra(dungeonScreen());
            }
            enemyScreenKey = ~key; // never equals an overworld key; re-placed per dungeon screen
            return;
        }
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
        // after the Hydra fell the dungeon is cleared: death there respawns on the overworld
        boolean inD = diedInDungeon && !dungeonRun.bossDefeated();
        diedInDungeon = false;
        if (inD) {
            // dying inside the dungeon respawns at the dungeon entrance; run progress persists
            DungeonScreen entry = dungeon.entry();
            ScreenPos e = entry.exitTile();
            Link.Dir inward = entry.exitInward();
            inDungeon = true;
            litScreens.clear(); // respawn starts a new visit: dark screens are dark again
            link.sx = dungeon.indexOf(entry.id());
            link.sy = 0;
            link.tx = e.tx() + inward.dx;
            link.ty = e.ty() + inward.dy;
            link.facing = inward;
        } else {
            link.sx = World.SPAWN_SX;
            link.sy = World.SPAWN_SY;
            link.tx = World.SPAWN_TX;
            link.ty = World.SPAWN_TY;
        }
        cave = null;
        invulnTimer = 0;
        swordTimer = 0;
        swingTimer = 0;
        enemyTimer = 0;
        stepTimer = 0;
        projectileTimer = 0;
        interpolating = false;
        interpProgress = 1;
        fireReady = true; // respawn is a new screen
        newScreenVisit();
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
