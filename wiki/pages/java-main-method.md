---
title: The main method (Java entry point)
category: Concepts
summary: Why a Java program starts at public static void main(String[] args), and what that buys and costs.
tags: [concept, java-basics, entry-point]
sources: [file-main-java]
created: 2026-09-05
updated: 2026-09-05
---

# The main method (Java entry point)

## Description

A Java class is just a bundle of methods until something calls one of them. By convention
the JVM needs to be told *which class* to start with (a command line, a jar manifest, or a
build plugin), and then it looks inside that class for a method whose declaration is
exactly:

```java
public static void main(String[] args) { }
```

Each keyword earns its place. `public` — the caller is outside the class's package, so the
method must be visible. `static` — it must be callable on the class without an object
existing first, since nothing has constructed anything yet. `void` — the JVM does not read
a return value; the only way to signal failure is to throw or call `System.exit`.
`String[] args` — the command-line arguments, already split on whitespace, with the program
name *not* included; `args[0]` is the first real argument.

Two consequences worth naming for a beginner. Overloading `main` with a different parameter
type is legal Java but the JVM ignores it, so a class with only `main(int x)` compiles
happily and then fails at launch with "Main method ... not found". And because `main` is an
ordinary static method, a program with all its logic in `main` is a program with no
structure — the healthy pattern, which this project follows, is for `main` to build a few
objects, hand control to one of them, and contain no logic itself.

Broadsword's `main` is a textbook example of that pattern: nine lines of configuration, then
`new Lwjgl3Application(new BroadswordGame(), cfg)` transfers control to a library that owns
the process from then on.[^1] Note the difference from a console program, where `main` runs
to completion and the process then exits. Here the process stays alive exactly as long as
that constructor call blocks, i.e. until the window closes.

## Why games never put the game loop in main

A frame loop wants to run at a fixed rate and to be paused when the window is unfocused.
Both are the windowing library's job, so the game supplies *callbacks* instead of a loop:
libGDX calls `create()` once, then `render()` about sixty times a second. The frame cap is
set on the configuration object before the application is created.[^2] A `while (true)`
inside `main` would have to reimplement all of that.

## Appearances in Sources

- [[file-main-java](pages/file-main-java.md)] — the project's single `main`, nine lines of window setup plus one handover call.

## Related Concepts

No other concept pages exist yet. Candidates that this page will want to link once written:
a `Game`/`ApplicationListener` lifecycle page (from `BroadswordGame`), a viewport and
logical-vs-physical-resolution page (from `GameConfig`), and a packages-and-imports page.

[^1]: `raw/main/java/com/jmgurr/broadsword/Main.java` §main L7-13 [synthesis] — the method body sets a title, window mode, resizability and FPS cap, then constructs Lwjgl3Application with a BroadswordGame instance
[^2]: `raw/main/java/com/jmgurr/broadsword/Main.java` §main L12 — "cfg.setForegroundFPS(60);"
