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
The spell. Sends a flame two tiles in front of Link; lights a Dark screen permanently, burns an enemy, or burns down a Flammable tree.
_Avoid_: fire, torch

**Sword**:
Link's melee weapon; a swing hits the tile Link faces.
_Avoid_: blade, weapon (ambiguous with spell)

**Shield**:
Link's basic defense; deflects Projectiles automatically while Link is not swinging.
_Avoid_: block, guard

**Projectile**:
A fast object fired by an enemy at Link; deflected by the shield.
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

**Dark screen**:
A rare challenge room (dungeon only) rendered blank and black: no obstacles or textures, just vague outlines of the tiles, enemies, and Link. Lit permanently for the run by the Light spell.
_Avoid_: cave, fog

**Flammable**:
An attribute of some trees: the Light spell burns them down, removing the obstacle.

**Ghost**:
The ethereal enemy that exists only in the Cemetery. Phases through all obstacles and through Link; contact deals damage. Spawns at intervals while Link lingers on a Cemetery screen; despawns off-screen. Cannot be killed except by a key item (see Enemy AI).
_Avoid_: spirit, wraith, spectre

**Secret tree**:
A Flammable tree that hides a secret; burning it down reveals Secret stairs or nothing (the tree itself was the key location blocking a passage).
_Avoid_: hidden tree, chest tree

**Secret stairs**:
A hidden transport revealed by burning a Secret tree; in V1 they lead to the Old woman's Cave.
_Avoid_: door (a door is visible and permanent), portal

**Cave**:
A small room not part of the overworld grid. Two kinds: secret caves, reached by burning a Secret tree (V1: the Old woman's), and formation caves, entered through a hole in a large rock formation (Rockfield/Mountain). In V1 they hold nothing; later they hold unique merchants and items.
_Avoid_: hut, grotto, dungeon (a dungeon is a full boss map)

**Old woman**:
The resident of the Cave. In V1 she does nothing; placeholder for a future NPC.
_Avoid_: witch, sage

**Game over**:
The state following Link's death; a screen that ends the run.
