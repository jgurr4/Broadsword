package com.jmgurr.broadsword.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T9: dungeon behavior on the Sim seam — entry/exit, doors, keys, loot, death, save. */
class DungeonTest {

    // ---- fixtures ---------------------------------------------------------

    /** a: E exit west, E door east, S door south; key at (9,3), octorock at (8,6). */
    private static final String SIMPLE = """
            # dungeon: test
            [a] doors: E=b S=c
            ################
            #..............#
            #..............#
            #.........k....#
            #..............#
            E..............d
            #.......o......#
            #..............#
            #..............#
            ########d#######
            [b] doors: W=a E=d(locked)
            ################
            #..............#
            #..............#
            #..............#
            #..............#
            d..............L
            #..............#
            #..............#
            #..............#
            ################
            [d] doors: W=b(locked)
            ################
            #..............#
            #.......i......#
            #..............#
            #..............#
            L..............#
            #..............#
            #..............#
            #..............#
            ################
            [c] doors: N=a
            ########d#######
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            #..............#
            ################
            """;

    private static Dungeon simple() {
        return Dungeon.parse(SIMPLE);
    }

    /** Enter the dungeon and clear the respawned enemies unless a test wants them. */
    private static void enter(Sim sim) {
        sim.enterDungeon();
        sim.enemies().clear();
    }

    private static Sim simWith(long seed, Dungeon dungeon) {
        Sim sim = new Sim(seed);
        sim.setDungeon(dungeon);
        return sim;
    }

    /** One full tile step: hold the direction until Link's tile or screen changes. */
    private static void step(Sim sim, Link.Dir d) {
        for (int i = 0; i < 100; i++) {
            int tx = sim.link().tx, ty = sim.link().ty, sx = sim.link().sx, sy = sim.link().sy;
            sim.tick(Sim.STEP_INTERVAL, d);
            if (sim.link().tx != tx || sim.link().ty != ty || sim.link().sx != sx || sim.link().sy != sy) {
                return;
            }
        }
        fail("Link never stepped " + d + " from " + sim.link().tx + "," + sim.link().ty + " on "
                + (sim.inDungeon() ? sim.dungeonScreen().id() : "overworld"));
    }

    /** Walk in bounds (never through a door) until Link stands on the loot char. */
    private static void walkOnto(Sim sim, char ch) {
        ScreenPos p = null;
        outer:
        for (int y = 0; y < World.SCREEN_H; y++) {
            for (int x = 0; x < World.SCREEN_W; x++) {
                for (Lootable l : sim.dungeonScreen().keys()) {
                    if (l.tx() == x && l.ty() == y) p = new ScreenPos(0, 0, x, y);
                }
                for (Lootable l : sim.dungeonScreen().items()) {
                    if (l.tx() == x && l.ty() == y) p = new ScreenPos(0, 0, x, y);
                }
                if (p != null) break outer;
            }
        }
        assertNotNull(p, "a " + ch + " is authored on this screen");
        Link l = sim.link();
        for (int i = 0; i < 200 && (l.tx != p.tx() || l.ty != p.ty()); i++) {
            if (l.tx < p.tx()) step(sim, Link.Dir.RIGHT);
            else if (l.tx > p.tx()) step(sim, Link.Dir.LEFT);
            else if (l.ty < p.ty()) step(sim, Link.Dir.DOWN);
            else if (l.ty > p.ty()) step(sim, Link.Dir.UP);
        }
        assertEquals(p.tx(), l.tx);
        assertEquals(p.ty(), l.ty);
    }

    /** Park Link on a walkable overworld tile beside the entrance. */
    private static Link.Dir parkBesideEntrance(Sim sim) {
        World w = sim.world();
        ScreenPos en = w.dungeonEntrance();
        for (Link.Dir d : Link.Dir.values()) {
            int px = en.tx() + d.dx, py = en.ty() + d.dy;
            if (w.walkable(en.sx(), en.sy(), px, py)) {
                Link l = sim.link();
                l.sx = en.sx();
                l.sy = en.sy();
                l.tx = px;
                l.ty = py;
                return d;
            }
        }
        throw new AssertionError("entrance has no walkable neighbour");
    }

    // ---- entry and exit ---------------------------------------------------

    @Test
    void steppingOnEntranceEntersAtEntryInwardTile() {
        Sim sim = simWith(1L, simple());
        Link.Dir home = parkBesideEntrance(sim); // standing beside E, on the overworld
        World w = sim.world();
        ScreenPos en = w.dungeonEntrance();
        assertTrue(w.isEntrance(en.sx(), en.sy(), en.tx(), en.ty()), "the entrance sits on the overworld");
        sim.link().hearts = Sim.ENEMY_DAMAGE; // one hit kills // entry restores hearts like every other entrance

        step(sim, Sim.opposite(home)); // step onto the entrance tile

        assertTrue(sim.inDungeon());
        assertEquals("a", sim.dungeonScreen().id());
        DungeonScreen entry = sim.dungeon().entry();
        ScreenPos e = entry.exitTile();
        Link.Dir in = entry.exitInward();
        assertEquals(e.tx() + in.dx, sim.link().tx);
        assertEquals(e.ty() + in.dy, sim.link().ty);
        assertEquals(in, sim.link().facing);
        assertEquals(World.MAX_HEARTS, sim.link().hearts, "entering restores Hearts");
    }

    @Test
    void enteringDoesNotReEnterOnAContinuousHold() {
        Sim sim = simWith(1L, simple());
        Link.Dir home = parkBesideEntrance(sim);
        step(sim, Sim.opposite(home));
        assertTrue(sim.inDungeon());
        for (int i = 0; i < 40; i++) sim.tick(Sim.STEP_INTERVAL, Sim.opposite(home));
        assertTrue(sim.inDungeon(), "the inward arrival never re-triggers the entrance");
    }

    @Test
    void steppingOffExitTileReturnsBesideWorldEntrance() {
        Sim sim = simWith(1L, simple());
        sim.enterDungeon();
        Link l = sim.link();
        DungeonScreen entry = sim.dungeonScreen();
        ScreenPos e = entry.exitTile();
        assertEquals(e.tx() + 1, l.tx, "arrived one tile inward from the exit");
        assertEquals(e.ty(), l.ty);

        step(sim, Link.Dir.LEFT); // onto the E tile
        assertTrue(sim.inDungeon(), "standing on the exit does not eject");
        sim.link().hearts = Sim.ENEMY_DAMAGE; // one hit kills
        step(sim, Link.Dir.LEFT); // and off the world again

        assertFalse(sim.inDungeon());
        ScreenPos en = sim.world().dungeonEntrance();
        assertEquals(en.sx(), l.sx);
        assertEquals(en.sy(), l.sy);
        assertTrue(sim.world().walkable(l.sx, l.sy, l.tx, l.ty), "back on solid overworld ground");
        assertEquals(World.MAX_HEARTS, l.hearts, "leaving restores Hearts like the Cave");
    }

    @Test
    void entryRoomHasDoorsAndBossRoomIsLockedSingleDoor() {
        Dungeon d = Dungeon.loadHydra();
        assertEquals("s01", d.entry().id());
        assertFalse(d.entry().doors().isEmpty(), "entry room connects onward");
        DungeonScreen boss = d.screens().get(d.screens().size() - 1);
        assertEquals("s12", boss.id());
        assertNotNull(boss.bossTile());
        assertEquals(1, boss.doors().size(), "the boss room has exactly one door");
        assertTrue(boss.doors().values().iterator().next().locked(), "and it is locked");
    }

    // ---- inter-room movement ----------------------------------------------

    @Test
    void walkingThroughDoorChangesScreenAndArrivesAtReturnDoor() {
        Sim sim = simWith(1L, simple());
        sim.enterDungeon();
        sim.enemies().clear();
        // walk east along the door row to the door tile, then through
        Link l = sim.link();
        while (l.ty != 5) step(sim, l.ty < 5 ? Link.Dir.DOWN : Link.Dir.UP);
        while (l.tx < 15) step(sim, Link.Dir.RIGHT);
        step(sim, Link.Dir.RIGHT);
        assertEquals("b", sim.dungeonScreen().id());
        ScreenPos back = sim.dungeonScreen().doorTile(Link.Dir.LEFT);
        assertEquals(back.tx(), l.tx);
        assertEquals(back.ty(), l.ty, "arrived at the return door tile");
        step(sim, Link.Dir.LEFT); // and the return door leads home
        assertEquals("a", sim.dungeonScreen().id());
    }

    @Test
    void southDoorThenNorthReturn() {
        Sim sim = simWith(1L, simple());
        sim.enterDungeon();
        sim.enemies().clear();
        Link l = sim.link();
        while (l.tx != 8) step(sim, l.tx < 8 ? Link.Dir.RIGHT : Link.Dir.LEFT);
        while (l.ty < 9) step(sim, Link.Dir.DOWN);
        step(sim, Link.Dir.DOWN);
        assertEquals("c", sim.dungeonScreen().id());
        ScreenPos back = sim.dungeonScreen().doorTile(Link.Dir.UP);
        assertEquals(back.tx(), l.tx);
        assertEquals(back.ty(), l.ty);
    }

    @Test
    void wallsAndUndeclaredEdgesBlock() {
        Sim sim = simWith(1L, simple());
        sim.enterDungeon();
        sim.enemies().clear();
        Link l = sim.link();
        // walk to the top-left floor corner of room a: north and west are wall
        while (l.tx > 1) step(sim, Link.Dir.LEFT);
        while (l.ty > 1) step(sim, Link.Dir.UP);
        for (int i = 0; i < 20; i++) sim.tick(Sim.STEP_INTERVAL, Link.Dir.UP);
        for (int i = 0; i < 20; i++) sim.tick(Sim.STEP_INTERVAL, Link.Dir.LEFT);
        assertEquals(1, l.tx, "the west wall holds");
        assertEquals(1, l.ty, "the north wall holds");
        // row 1 never meets the east door at (15,5): the east wall holds there too
        while (l.tx < 14) step(sim, Link.Dir.RIGHT);
        for (int i = 0; i < 20; i++) sim.tick(Sim.STEP_INTERVAL, Link.Dir.RIGHT);
        assertEquals(0, sim.dungeonScreenIndex(), "no undeclared east exit in row 1");
        assertEquals(14, l.tx, "the east wall holds");
    }

    // ---- locked doors and keys ---------------------------------------------

    /** Enter room a, walk east through its open door into b, stand beside the locked door. */
    private static void reachLockedDoorB(Sim sim) {
        Link l = sim.link();
        while (l.ty != 5) step(sim, l.ty < 5 ? Link.Dir.DOWN : Link.Dir.UP);
        while (l.tx < 15) step(sim, Link.Dir.RIGHT);
        step(sim, Link.Dir.RIGHT); // through a's east door into b
        assertEquals("b", sim.dungeonScreen().id());
        while (l.tx < 14) step(sim, Link.Dir.RIGHT);
    }

    @Test
    void bumpingLockedDoorWithoutKeyKeepsItLocked() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        reachLockedDoorB(sim);
        Link l = sim.link();
        for (int i = 0; i < 20; i++) sim.tick(Sim.STEP_INTERVAL, Link.Dir.RIGHT);
        assertEquals("b", sim.dungeonScreen().id(), "locked doors block");
        assertEquals(14, l.tx, "Link stays on this side of the door");
        assertEquals(0, sim.dungeonRun().keys());
        assertTrue(sim.dungeonRun().openedLocks().isEmpty(), "nothing unlocked");
    }

    @Test
    void keyOpensAnyLockAndIsConsumed() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        assertEquals(1, sim.dungeonRun().keys());
        reachLockedDoorB(sim);
        step(sim, Link.Dir.RIGHT); // bump: the key opens it
        assertEquals("b", sim.dungeonScreen().id(), "the bump itself does not cross");
        assertEquals(0, sim.dungeonRun().keys(), "the key is consumed");
        assertEquals(1, sim.dungeonRun().openedLocks().size());
        step(sim, Link.Dir.RIGHT); // onto the now-open door tile
        step(sim, Link.Dir.RIGHT); // and through
        assertEquals("d", sim.dungeonScreen().id(), "the open lock leads through");
        assertEquals(0, sim.dungeonRun().keys(), "no second key spent");
    }

    @Test
    void openedLockStaysOpenAfterReentry() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        reachLockedDoorB(sim);
        step(sim, Link.Dir.RIGHT); // spend the key
        assertEquals(0, sim.dungeonRun().keys());
        step(sim, Link.Dir.RIGHT); // onto the open door
        step(sim, Link.Dir.RIGHT); // through to d
        assertEquals("d", sim.dungeonScreen().id());
        // back through the open lock to a, then east again with no keys left
        Link l = sim.link();
        step(sim, Link.Dir.LEFT);
        step(sim, Link.Dir.LEFT); // d -> b through the still-open lock
        assertEquals("b", sim.dungeonScreen().id());
        while (l.tx > 1) step(sim, Link.Dir.LEFT);
        step(sim, Link.Dir.LEFT); // onto b's west door tile
        step(sim, Link.Dir.LEFT); // and across into a
        assertEquals("a", sim.dungeonScreen().id());
        while (l.tx < 15) step(sim, Link.Dir.RIGHT);
        step(sim, Link.Dir.RIGHT);
        assertEquals("b", sim.dungeonScreen().id(), "the opened lock stays open");
        while (l.tx < 14) step(sim, Link.Dir.RIGHT);
        step(sim, Link.Dir.RIGHT); // onto the open east door
        step(sim, Link.Dir.RIGHT); // and across
        assertEquals("d", sim.dungeonScreen().id(), "both sides of the pair stay open");
        assertEquals(0, sim.dungeonRun().keys(), "no key spent on the return");
    }

    @Test
    void keysPersistAcrossExitReentryAndReload() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        assertEquals(1, sim.dungeonRun().keys());

        sim.leaveDungeon();
        assertFalse(sim.inDungeon());
        sim.enterDungeon();
        assertEquals(1, sim.dungeonRun().keys(), "keys persist across exit and re-entry");

        Sim back = new Sim(sim.saveState());
        assertTrue(back.inDungeon(), "Link was inside when saved");
        assertEquals(1, back.dungeonRun().keys(), "keys are not reset by saving and reloading");
        back.leaveDungeon();
        back.enterDungeon();
        assertEquals(1, back.dungeonRun().keys());
    }

    @Test
    void takenLootStaysCollected() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        assertEquals(1, sim.dungeonRun().keys());
        sim.leaveDungeon();
        enter(sim);
        Link l = sim.link();
        while (l.ty != 3) step(sim, l.ty < 3 ? Link.Dir.DOWN : Link.Dir.UP);
        while (l.tx != 10) step(sim, l.tx < 10 ? Link.Dir.RIGHT : Link.Dir.LEFT);
        assertEquals(1, sim.dungeonRun().keys(), "the tile gives no second key");
    }

    @Test
    void itemCountsAsRunProgressAndStaysTaken() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        reachLockedDoorB(sim);
        step(sim, Link.Dir.RIGHT); // spend the key on the lock
        step(sim, Link.Dir.RIGHT); // onto the open door
        step(sim, Link.Dir.RIGHT); // through to d
        assertEquals("d", sim.dungeonScreen().id());
        Link l = sim.link();
        while (l.tx > 8) step(sim, Link.Dir.LEFT);
        while (l.ty != 2) step(sim, l.ty < 2 ? Link.Dir.DOWN : Link.Dir.UP);
        while (l.tx != 8) step(sim, l.tx < 8 ? Link.Dir.RIGHT : Link.Dir.LEFT);
        assertEquals("d", sim.dungeonScreen().id());
        assertEquals(1, sim.dungeonRun().itemsTaken(), "the item counts as run progress");

        sim.leaveDungeon();
        enter(sim);
        reachLockedDoorB(sim);
        step(sim, Link.Dir.RIGHT); // the lock is still open, no key to spend
        step(sim, Link.Dir.RIGHT);
        step(sim, Link.Dir.RIGHT);
        assertEquals("d", sim.dungeonScreen().id());
        while (l.tx > 1) step(sim, Link.Dir.LEFT);
        while (l.ty != 2) step(sim, l.ty < 2 ? Link.Dir.DOWN : Link.Dir.UP);
        while (l.tx != 8) step(sim, l.tx < 8 ? Link.Dir.RIGHT : Link.Dir.LEFT);
        assertEquals(1, sim.dungeonRun().itemsTaken(), "items stay collected");
        assertEquals(0, sim.dungeonRun().keys(), "and no key was re-spent");
    }

    // ---- enemies, death, keep-vs-reset --------------------------------------

    @Test
    void dungeonEnemiesSpawnPerVisitAtAuthoredTiles() {
        Sim sim = simWith(1L, simple());
        sim.enterDungeon();
        assertEquals(1, sim.enemies().size(), "the authored Octorock in room a");
        Enemy e = sim.enemies().get(0);
        assertEquals(EnemyKind.OCTOROCK, e.kind);
        assertEquals(8, e.tx, "authored tile, no jitter");
        assertEquals(6, e.ty);
        assertTrue(Sim.spawning(e), "enemies arrive in the spawn cloud");
        sim.leaveDungeon();
        sim.enterDungeon();
        assertEquals(1, sim.enemies().size(), "dungeon enemies respawn per visit");
        assertEquals(8, sim.enemies().get(0).tx);
        assertEquals(6, sim.enemies().get(0).ty);
    }

    @Test
    void deathInDungeonRespawnsAtDungeonEntranceKeepingRunProgress() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        Link l = sim.link();
        l.hearts = Sim.ENEMY_DAMAGE; // one hit kills
        sim.enemies().add(new Enemy(EnemyKind.GRUNT, l.tx, l.ty, 1L));
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null); // one contact kill
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());

        sim.respawn();
        assertTrue(sim.inDungeon(), "dying inside puts Link back inside");
        assertEquals("a", sim.dungeonScreen().id());
        ScreenPos ex = sim.dungeon().entry().exitTile();
        Link.Dir in = sim.dungeon().entry().exitInward();
        assertEquals(ex.tx() + in.dx, l.tx);
        assertEquals(ex.ty() + in.dy, l.ty);
        assertEquals(World.MAX_HEARTS, l.hearts);
        assertEquals(1, sim.dungeonRun().keys(), "a death does not hand back the key");
        assertEquals(1, sim.enemies().size(), "enemies respawn after death");
    }

    @Test
    void overworldDeathKeepsDungeonProgressButLandsOutside() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        sim.leaveDungeon();
        Link l = sim.link();
        l.hearts = Sim.ENEMY_DAMAGE; // one hit kills
        sim.enemies().add(new Enemy(EnemyKind.GRUNT, l.tx, l.ty, 1L));
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());
        sim.respawn();
        assertFalse(sim.inDungeon());
        assertEquals(World.SPAWN_SX, l.sx);
        assertEquals(World.SPAWN_SY, l.sy);
        assertEquals(1, sim.dungeonRun().keys(), "dungeon progress is never reset");
    }

    @Test
    void keepVersusResetTableAcrossDeath() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        Link l = sim.link();
        // open b's locked door from a's side: key spent, lock opened
        reachLockedDoorB(sim);
        step(sim, Link.Dir.RIGHT); // spend the key on b's lock
        // and die inside
        l.hearts = Sim.ENEMY_DAMAGE; // one hit kills
        sim.enemies().clear();
        sim.enemies().add(new Enemy(EnemyKind.GRUNT, l.tx, l.ty, 1L));
        sim.tick(Sim.ENEMY_STEP_INTERVAL, null);
        assertEquals(Sim.Phase.GAME_OVER, sim.phase());

        SaveState before = sim.saveState();
        sim.respawn();
        SaveState after = sim.saveState();
        // keep: seed, keys, opened locks, taken loot, Secret, Magic
        assertEquals(before.seed(), after.seed());
        assertEquals(before.dungeonKeys(), after.dungeonKeys(), "0: the key was spent");
        assertEquals(before.openedLocks(), after.openedLocks(), "the opened lock stays open");
        assertEquals(before.takenLoot(), after.takenLoot(), "taken loot stays taken");
        assertEquals(before.magic(), after.magic(), "Magic does not refill on death");
        assertEquals(before.secretRevealed(), after.secretRevealed(), "the Secret stays revealed");
        // reset: hearts full, enemies respawned
        assertEquals(World.MAX_HEARTS, sim.link().hearts);
        assertTrue(sim.enemies().isEmpty() || sim.enemies().stream().allMatch(Sim::spawning),
                "enemies respawn as clouds, not the killer");
    }

    @Test
    void dungeonScreensNeverHauntWithGhostsOrOverworldTriggers() {
        Sim sim = simWith(7L, Dungeon.loadHydra());
        sim.enterDungeon();
        boolean hadFlute = sim.hasFlute();
        for (int i = 0; i < 600 && sim.inDungeon(); i++) {
            sim.tick(16f, Link.Dir.values()[i % 4]);
        }
        assertTrue(sim.enemies().stream().noneMatch(e -> e.ethereal), "no Ghosts in the dungeon");
        assertEquals(hadFlute, sim.hasFlute(), "the dungeon never takes the overworld Flute");
        assertFalse(sim.inCave(), "the dungeon never falls through the Secret Tree");
    }

    // ---- save format ---------------------------------------------------------

    @Test
    void saveFormatRoundTripsDungeonFields() {
        SaveState s = new SaveState(42L, 1, 2, 3, 4, Link.Dir.UP, World.MAX_MAGIC, true,
                SaveState.NO_CAVE, true, 3, true, Set.of(0, 1), Set.of(2, 4, 7), true);
        Optional<SaveState> back = SaveState.parse(s.format());
        assertEquals(Optional.of(s), back);
    }

    @Test
    void v4SavesLoadWithDungeonDefaults() {
        Optional<SaveState> back = SaveState.parse("""
                version=4
                seed=9
                link=1,2,3,4
                facing=LEFT
                magic=2
                secret=1
                cave=-1
                flute=0
                """);
        assertTrue(back.isPresent());
        assertEquals(0, back.get().dungeonKeys());
        assertFalse(back.get().inDungeon());
        assertEquals(Set.of(), back.get().openedLocks());
        assertEquals(Set.of(), back.get().takenLoot());
    }

    @Test
    void corruptDungeonFieldsAreRejected() {
        assertTrue(SaveState.parse("""
                version=5
                seed=9
                link=1,2,3,4
                facing=LEFT
                keys=-1
                """).isEmpty());
        assertTrue(SaveState.parse("""
                version=5
                seed=9
                link=1,2,3,4
                facing=LEFT
                open=a
                """).isEmpty());
    }

    @Test
    void saveWhileInDungeonResumesInsideIt() {
        Sim sim = simWith(1L, simple());
        enter(sim);
        walkOnto(sim, 'k');
        List<SaveState> saved = new java.util.ArrayList<>();
        sim.setSaveSink(saved::add);
        sim.enterDungeon(); // re-entering from inside autosaves
        assertFalse(saved.isEmpty(), "entering the dungeon autosaves");
        Sim back = new Sim(sim.saveState());
        back.setDungeon(simple()); // reloaded saves load the authored hydra; swap the fixture back in
        assertTrue(back.inDungeon(), "a save made inside resumes inside");
        assertEquals("a", back.dungeonScreen().id());
        assertEquals(sim.link().tx, back.link().tx);
        assertEquals(sim.link().ty, back.link().ty);
        assertEquals(1, back.dungeonRun().keys());
    }

}
