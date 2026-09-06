---
title: Overview
tags: [overview, synthesis]
sources: []
updated: 2026-09-05
---

# Broadsword source, explained for Java beginners — Overview

> Evolving synthesis of everything in the wiki. Updated by wiki-ingest when sources shift the understanding.

## Current Understanding

1 of 29 Java files documented. The program boots in two clearly separated layers: a
nine-line JVM entry point that only configures a desktop window, and a libGDX `Game`
subclass that owns everything game-shaped. `Main` never mentions the world, the player or
combat, so the project's structure is already visible from its first file: display setup,
then game object, then simulation model.

## Open Questions

- Why is the window non-resizable (`setResizable(false)`) — a deliberate constraint so the
  viewport only has to be configured once, or just a preference?
- The window is 2160×1350 against a 240×150 logical world (a 9× scale). What drives that
  ratio, and does it hold on a 1080p screen?

## Key Entities / Concepts

- [[file-main-java](pages/file-main-java.md)] — the entry point; window config then handover to `Lwjgl3Application`.
- [[java-main-method](pages/java-main-method.md)] — why `public static void main(String[] args)` is the start, and why a game loop never lives there.
