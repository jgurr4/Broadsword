---
title: Main.java — the program's entry point
category: Files
summary: The JVM entry point; configures a libGDX desktop window and hands control to the game object.
tags: [entry-point, libgdx, desktop-launcher]
sources: [main-java]
commit: 9c85cd6
created: 2026-09-05
updated: 2026-09-05
---

# Main.java — the program's entry point

**Source:** `src/main/java/com/jmgurr/broadsword/Main.java` (snapshot: `raw/main/java/com/jmgurr/broadsword/Main.java`, git `9c85cd6`)
**Date ingested:** 2026-09-05
**Type:** code (Java, 15 lines)

## Summary

`Main` is the smallest file in the project and the only one with a special job: the JVM
looks for a class with a `public static void main(String[] args)` method and calls it when
you launch the program. Nothing in the build config names `Main` as the entry point for the
code itself — the launcher is told the class name and finds the method by convention. See
[[java-main-method](pages/java-main-method.md)] for why the signature has to read exactly
that way.

The method body is pure setup. It creates an `Lwjgl3ApplicationConfiguration` — libGDX's
holder for "what should the desktop window look like" — sets a title, a window size,
whether the user may drag the window edges, and a target frame rate, then constructs an
`Lwjgl3Application`, passing it a freshly built `BroadswordGame` and that configuration.
That last line is where the program stops being `Main`'s business: the constructor starts
libGDX's own loop, which calls back into the game object many times per second. `main`
never returns to anything useful and contains no loop of its own.

Two facts worth internalising before reading any other file. First, this file touches no
game rules — no world, no player, no combat. Everything game-shaped lives behind
`BroadswordGame`. Second, the window size it sets is a *display* concern, deliberately
separate from the game's internal drawing resolution, so the same tiny world can fill any
window.

## Key Takeaways

- `main` is found by convention, not registration: `public static void main(String[] args)` is the
  exact signature the JVM requires.[^1]
- The file's only libGDX imports are the two desktop-launcher classes; no game code is
  imported at all beyond `BroadswordGame` and `GameConfig`, which sit in the same
  package.[^2]
- Window title, size, resizability and frame rate are set as four independent calls on the
  configuration object before the application exists.[^3]
- `new Lwjgl3Application(new BroadswordGame(), cfg)` both starts the frame loop and keeps
  the process alive; the game object is handed over as a constructor argument.[^4]
- The window is 2160×1350 and non-resizable, and that size is *not* the resolution the game
  draws at — the logical world is 240×150 pixels and the viewport scales it.[^5]
- Because `GameConfig` holds these numbers, changing the window size means editing one
  constant rather than hunting for a literal.[^6]

## What each line does

| Line | Code (abridged) | Purpose |
|---|---|---|
| 1 | `package com.jmgurr.broadsword;` | Declares the package; must match the directory path under `src/main/java/`. |
| 3-4 | `import ...Lwjgl3Application;` | The desktop backend (LWJGL 3 = the library that talks to OpenGL/windowing on desktop). |
| 7 | `public static void main(String[] args)` | JVM entry point. `public`/`static` requirements are explained in [[java-main-method](pages/java-main-method.md)]. |
| 8 | `new Lwjgl3ApplicationConfiguration()` | Empty configuration object; the setters below fill it in. |
| 9 | `cfg.setTitle("Broadsword")` | OS window title. |
| 10 | `cfg.setWindowedMode(WINDOW_W, WINDOW_H)` | Opens in windowed (not fullscreen) mode at 2160×1350. |
| 11 | `cfg.setResizable(false)` | Disables drag-resize, so `resize()` in the game is called once rather than on every drag. |
| 12 | `cfg.setForegroundFPS(60)` | Caps the loop at 60 frames per second when the window is focused. |
| 13 | `new Lwjgl3Application(new BroadswordGame(), cfg)` | Start the loop; from here libGDX drives the program. |

The object created on line 8 is never disposed. That is normal here: the application object
that consumes it owns the process lifetime, and line 13 blocks until the window closes.

## Beginner notes on this file

- **`static`** — `main` must be `static` because the JVM has no `Main` object to call a
  method on; it needs a method that belongs to the class itself. See
  [[java-main-method](pages/java-main-method.md)].
- **`String[] args`** — command-line arguments, one array element per space-separated
  argument. This program ignores the array entirely, which is legal and common.
- **No `import` for `BroadswordGame` or `GameConfig`** — classes in the same package see
  each other without an import statement.
- **Two `GameConfig` constants are the only game-side values used here** — the file stays
  unaware of anything else in the project, which is why you can read it first.

## Relation to Other Wiki Pages

This is the first page in the wiki, so nothing backlinks here yet. `BroadswordGame`,
`GameConfig`, `TextureGen`, `TitleScreen` and `GameScreen` are all named by this file but
have no pages of their own; each is a candidate for a follow-up ingest, in the order they
are reached at runtime.

[^1]: `raw/main/java/com/jmgurr/broadsword/Main.java` §main L7 — "public static void main(String[] args) {"
[^2]: `raw/main/java/com/jmgurr/broadsword/Main.java` §imports L3-4 — "import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;"
[^3]: `raw/main/java/com/jmgurr/broadsword/Main.java` §main L9-12 — "cfg.setTitle(\"Broadsword\");"
[^4]: `raw/main/java/com/jmgurr/broadsword/Main.java` §main L13 — "new Lwjgl3Application(new BroadswordGame(), cfg);"
[^5]: `raw/main/java/com/jmgurr/broadsword/GameConfig.java` §window L11-14 — "// physical window resolution — independent of the logical resolution;" and "// the viewport scales the logical world to fit the window"
[^6]: `raw/main/java/com/jmgurr/broadsword/GameConfig.java` §class [synthesis] L4-20 — a `final` class whose only contents are `public static final` constants plus a private constructor that stops anyone instantiating it
