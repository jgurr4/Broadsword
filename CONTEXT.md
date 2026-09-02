# Broadsword

A 2D Zelda-inspired adventure game. Every map is auto-generated; players who can read maps and solve puzzles get further.

## Language

**Overworld**:
The main playable area: a 40×10 grid of screens (400 screens).
_Avoid_: world, map (ambiguous with dungeon map)

**Dungeon**:
A separate map of screens, entered from the overworld, containing the boss.
_Avoid_: cave, underground

**Screen**:
One viewport of the game: a 16×10 tile grid that occupies the whole screen. Link moves from screen to screen across the overworld.
_Avoid_: room, level, page

**Tile**:
The smallest addressable cell within a screen. Positions are always tile coordinates.
_Avoid_: cell, square

**Obstacle**:
A tile Link cannot enter: water, rock, tree, bush, etc.
_Avoid_: wall (implies screen borders only), block

**Tier**:
A screen's enemy difficulty level (1–4), assigned from its distance from spawn. Only the enemy layer scales with tier; terrain does not.
_Avoid_: level, depth, zone

**Archetype**:
A named screen shape drawn from a small fixed vocabulary — the readable grammar of the world. V1 roster: Meadow, Forest, Lake, River, Rockfield, Path, Clearing, Mountain, Shore. Each has a fixed, repeatable signature.
_Avoid_: tileset, biome, template

**Designed screen**:
A hand-authored screen (or connected set of screens) placed at a random position in the generated world. Its enemies are author-placed; the secret pass skips it.
_Avoid_: custom map, handcrafted room

**Landmark**:
A unique, one-per-world place at a fixed offset from the dungeon entrance (same relative position on every world). V1: Big Lake, Rock Mountain, Ruined Shrine, Cemetery.
_Avoid_: POI, feature, decoration

**Link**:
The hero the human controls. Moves tile by tile; carries a sword and a shield; has three Hearts and a Magic meter.
_Avoid_: player (player = the human), character, hero

**Heart**:
One unit of Link's health. Link dies when all three are gone.
_Avoid_: HP, life

**Magic**:
A meter that depletes when Link casts a Spell.
_Avoid_: mana, MP

**Spell**:
An ability that consumes Magic. V1 has exactly one: Light.
_Avoid_: attack, power

**Light**:
The spell. Sends a flame two tiles in front of Link; lights a Dark screen (until the dungeon resets), burns an enemy, or burns down a Flammable tree.
_Avoid_: fire, torch

**Sword**:
Link's melee weapon; a swing hits the tile Link faces.
_Avoid_: blade, weapon (ambiguous with spell)

**Shield**:
Link's basic defense. While he is **not** swinging the sword and is **facing** an incoming Projectile, the V1 shield **destroys it on impact** — it does not reflect or deflect. A Projectile still hits if he is swinging when it makes contact, or if it arrives from a side he is not facing.
_Avoid_: block, guard

**Magic Shield**:
A post-V1 shield power-up that, unlike the V1 shield, **reflects** a blocked Projectile back toward the attacker (sent back in the opposite direction) instead of destroying it. Not part of V1.
_Avoid_: reflective shield, mirror shield

**Projectile**:
A fast object fired by an enemy at Link. The V1 shield destroys it on impact (if Link is not swinging and is facing it); the later Magic Shield reflects it.
_Avoid_: bullet, arrow

**Fireball**:
The Projectile a Hydra head fires.
_Avoid_: ball

**Cemetery**:
The one-per-world landmark of tombstones and crosses (2×2 screens, fixed offset from the dungeon entrance). Only home of the Ghost.
_Avoid_: graveyard, graveyard (use Cemetery)

**Enemy**:
A hostile actor that occupies tiles in a screen and damages Link. V1 types: Grunt (melee) and Octorock (ranged).
_Avoid_: monster, mob

**Grunt**:
The melee enemy: moves toward Link; touching it costs a Heart.
_Avoid_: chaser, walker

**Octorock**:
The ranged enemy: holds distance and fires Projectiles at Link.
_Avoid_: shooter, slinger

**Boss**:
The enemy at the far end of the dungeon; defeating it awards the Triforce piece. The V1 boss is the Hydra.
_Avoid_: final enemy, king

**Hydra**:
The dungeon boss: stationary, with three heads that fire Fireballs at Link.
_Avoid_: dragon, serpent

**Triforce piece**:
The artifact awarded by defeating a dungeon's boss; collecting every piece wins the game. V1: one piece (the Hydra's); the finished game: up to 10, one per dungeon.
_Avoid_: artifact (generic), trophy

**Dark screen**:
A rare challenge room (dungeon only) rendered blank and black: no obstacles or textures, just vague outlines of the tiles, enemies, and Link. Lit by the Light spell until the dungeon resets (goes dark again on re-entry).
_Avoid_: cave, fog

**Flammable**:
An attribute of some trees: the Light spell burns them down, removing the obstacle.

**Ghost**:
The ethereal enemy that exists only in the Cemetery. Phases through all obstacles and through Link; contact deals damage. Spawns at intervals while Link lingers on a Cemetery screen; despawns off-screen. Cannot be killed by sword, Light, or any attack; only the Flute dispels them.
_Avoid_: spirit, wraith, spectre

**Flute**:
An item Link activates with a button press; it plays a short tune that dispels every Ghost on the current screen and stops Ghost spawning while Link stays on that screen. One use per screen visit — it resets when Link leaves the screen and re-enters. Consumes no Magic. Effectively Cemetery-only (Ghosts exist only there).
_Avoid_: ocarina, panpipes, horn

**Secret tree**:
A Flammable tree that hides a secret; burning it down reveals Secret stairs or nothing (the tree itself was the key location blocking a passage).
_Avoid_: hidden tree, chest tree

**Secret stairs**:
A hidden transport revealed by burning a Secret tree; in V1 they lead to the Old woman's Cave.
_Avoid_: door (a door is visible and permanent), portal

**Cave**:
A small room not part of the overworld grid. Two kinds: secret caves, reached by burning a Secret tree (V1: the Old woman's), and formation caves, entered through a hole in a large rock formation (Rockfield/Mountain). In V1 they hold nothing; later they hold unique merchants and items.
_Avoid_: hut, grotto, dungeon (a dungeon is a full boss map)

**Door**:
A visible, authored passage between two screens. Every screen is walled on all four sides; a passage exists only where a door is authored. The overworld dungeon entrance is the only visible door in the overworld.
_Avoid_: passage (generic), opening

**Locked door**:
A door that opens only when Link spends a Key on it. Authored in the dungeon map file.
_Avoid_: gate, seal

**Key**:
A consumable picked up on contact that opens a Locked door. Any key opens any locked door (no pairing); held as a count. Consumed when used.
_Avoid_: key item (generic), fob

**Shoveable block**:
A pushable obstacle: Link moves into its tile and the block moves one tile in the direction Link faces; it holds position until a dungeon reset. An authored trigger can make its first push reveal an item.
_Avoid_: block (ambiguous), boulder (a boulder is Mountain-archetype terrain, not pushable)

**Old woman**:
The resident of the secret Cave, reached via Secret stairs; one per world. She sells one coarse location hint per dungeon (a terrain/Tier pointer, never a pinned coordinate) for 100 Ruppees, once per dungeon. In V1 she does nothing; the Ruppee economy is required before she can.
_Avoid_: witch, sage

**Game over**:
The screen following Link's death, before respawn. Death is not the end of a save: Link respawns at spawn, or at the dungeon entrance if death occurred inside a dungeon. Only non-persistent changes reset.
_Avoid_: game end (victory is the other end-state), wipe

**Persistent change**:
A world change that survives death, reset, and reload: items obtained, Ruppees, bosses killed, dungeons beaten, secrets uncovered, Triforce pieces collected. Stored in the save file.
_Avoid_: permanent item (a subset: key items), durable state

**Non-persistent change**:
A world change that reverts on death, reset, or reload: enemy spawns, uncollected pickups, in-progress dungeon state. The world re-derives from the seed plus persistent changes.
_Avoid_: temporary state, run state

**Save file**:
A named world's persistence: its seed plus all persistent changes plus Link's position. Autosaved on every screen transition and on major events (item acquired, piece collected, dungeon entry). Loading resumes at the saved position; starting a new save always begins at spawn with a new seed.
_Avoid_: checkpoint, slot (a slot is the UI around the file)

**New Game+**:
The post-victory mode that continues the same world in the won state, keeping persistent changes. Not in V1; difficulty selection is its post-V1 sibling for new challenge.
_Avoid_: NG+ (in prose), restart

**Ruppee**:
The currency, carried across death (cap 999). Each kill drops at most one: 50% Green (worth 1), 25% Blue (worth 5), 25% nothing; picked up on contact, despawned after 10 seconds or on leaving the screen. Introduced post-V1.
_Avoid_: rupee (spelling), coin, money
