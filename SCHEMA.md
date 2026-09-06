# Wiki Schema

## Identity
- **Path:** /home/jared/repos/broadsword
- **Domain:** Document the broadsword Java source code and explain what each file, class and method does, written for Java beginners.
- **Source types:** Java source files under `src/main/java/`, JUnit tests under `src/test/java/`, Gradle build files, `README.md`, `CONTEXT.md`, `docs/`
- **Created:** 2026-09-05

## Page Frontmatter
Every wiki page must start with:
---
title: <page title>
category: <one of the Index Categories below>
summary: <one-line description — becomes this page's index entry>
tags: [tag1, tag2]
sources: [source-slug1]
created: YYYY-MM-DD
updated: YYYY-MM-DD
---

`category` and `summary` drive index generation (see **Index Generation** below);
`category` must match one of the wiki's Index Categories. `created` is set once when the
page is first written and never changes; `updated` bumps on every edit.

## Cross-References
- **link_style:** markdown
- **link_style_rules:** config/link-style.md
- See `config/link-style.md` for the exact emit and parse rules. Every wiki skill
  reads that file to decide how to write new cross-references and how to scan
  existing ones.

## Concept Identity

The slug **is** the concept's identity — there is no separate id. A concept is the
page at `wiki/pages/<slug>.md`; everything that links to it uses the link form defined in
`config/link-style.md`. This only works if the link graph is trustworthy, so two
rules hold everywhere links are written:

1. **Links are verified, never invented.** Before writing any link, the slug must
   resolve to an existing `wiki/pages/<slug>.md` **or** to a page being created in the
   same operation. List the existing page set first (`ls wiki/pages/`); never emit a
   link to a slug you have not confirmed. A link that resolves to nothing is a
   hallucinated link — the failure this discipline exists to prevent.

2. **Homonyms get qualified slugs.** When a new concept collides with an existing slug
   for a *different* sense, qualify both with a discriminator rather than overloading
   one page (`screen-game` vs `screen-tile`, `link-java` vs `link-network`).
   Pick the narrowest discriminator that disambiguates. `wiki-lint` warns when slugs
   share a base token and look like an unintended collision.

### Slug conventions for this wiki

Pages live flat in `wiki/pages/`; prefixes group them.

| Prefix | Example | Category | Use for |
|---|---|---|---|
| `file-` | `file-sim-java` | Files | One page per `.java` file (or tightly related file group) |
| `cls-` | `cls-enemy` | Classes | One page per public class/enum/interface, its fields and methods |
| `java-` | `java-enum` | Concepts | Beginner Java / game-dev concepts used by the code |
| `flow-` | `flow-game-boot` | Flows | End-to-end paths through the running program |

Cite code locations as inline paths, e.g. `src/main/java/com/jmgurr/broadsword/model/Sim.java`.
Do not paste whole methods into wiki pages — describe *what* and *why*, link to the code for *how*.

## Citations

Cite every non-common-knowledge factual claim. "Common knowledge" = uncontroversial,
undergraduate-level facts in this wiki's domain. Granularity is paragraph or claim,
never per-sentence. If you cannot produce a citation in one of the forms below,
find one, weaken the claim, or drop it.

For this wiki the primary sources are the code files themselves, copied into `raw/` at
ingest time (see **Source snapshots** below), which makes line ranges stable.

Format: Markdown footnotes. Two citation kinds, three valid targets.

**Quote citation** (preferred):
```
The simulation advances the world one step at a time.[^1]

[^1]: [[src-sim-java](pages/src-sim-java.md)] §Sim.step L120-138 — "for (Enemy e : enemies) { e.update(dt); }"
```

**Synthesis citation** (when no single quote captures the claim):
```
Enemy movement is resolved entirely inside the model package, with no rendering calls.[^2]

[^2]: [[src-sim-java](pages/src-sim-java.md)] §Sim [synthesis] L88-210 — the update, move and
      collide sections together show movement staying inside the model layer
```

`L120-138` / `L88-210` are line ranges in the raw source file. For a quote they mark the
lines the quote is taken from; for a synthesis they mark the block being summarized.

Three rules for every footnote:

1. **The cited target is one of three forms:**
   - A slug reference to a source-type wiki page (preferred for sources ingested via `wiki-ingest`)
   - `raw/<file>` or `assets/<file>` — a path to a local file (for drive-by citations
     where a synthesis page isn't worth creating)
   - `<URL>` — a live URL or other ephemeral source (no local copy required)

   Never cite Files, Classes, Concepts or Flows pages — those are syntheses, not sources.

2. **A locator is present.** Always a semantic locator: `§<class>.<method>`, `§<section>`,
   `p.<n>`, `[HH:MM:SS]` for transcripts, URL anchor for web, or `(YYYY-MM-DD)` for dated posts.

   **Plus a line-range when the source is text-addressable.** Java files are text, so
   append a line-range token after the semantic locator:

   - `L<start>-<end>` — a range, e.g. `L142-145`
   - `L<n>` — a single line, e.g. `L142`
   - `L142-145,L201-203` — disjoint ranges

   The line range refers to lines in the **raw source file** resolved from the target.

   A line-range is **required** for text-addressable sources and applies to BOTH citation
   kinds. **Exempt** (semantic locator only, no `L…`): PDFs, transcripts, and live URLs
   with no local cached copy.

3. **Either a verbatim quote, or the `[synthesis]` tag plus a description** of what the
   cited range supports. No third option.

**Drive-by citation examples:**
```
[^3]: raw/build.gradle L12-20 — "implementation 'com.badlogicgames.gdx:gdx:1.12.1'"
[^4]: https://github.com/libgdx/libgdx/wiki (2026-09-01) — "libGDX is a cross-platform Java game development framework"
```

## Source snapshots

`raw/` is immutable, and line-range citations only hold if the text under them does not
move. Because `src/` changes under git while `raw/` must not, each ingest snapshots the
source file it reads:

- Copy the file to `raw/<path-under-src>` (e.g. `raw/main/java/com/jmgurr/broadsword/model/Sim.java`)
  **only when it is not already present**, and record the git commit the snapshot came from
  in the source page's frontmatter (`commit: <sha>`).
- If the snapshot exists but differs from the working tree, do not overwrite it silently:
  create a versioned snapshot `raw/main/java/.../Sim@<sha>.java`, update the source page's
  `**Source:**` to it, and flag the Classes/Files pages that cite the older snapshot as
  candidates for `wiki-update`.

## Cross-Model Review

`wiki-audit strong` runs a second-opinion pass with a different-provider model and
stamps the audited page with an optional `review:` frontmatter block:
```
review:
  model: codex          # gemini | claude-sonnet
  provider: openai      # google | anthropic
  date: YYYY-MM-DD
  status: clean         # or: disputed
  findings: 2           # present only when status: disputed
```
- `status: clean` — the reviewer surfaced no disagreement with the normal audit.
- `status: disputed` — the reviewer flagged overreach or a contradiction the normal
  audit missed; `findings:` carries the count. The detail lives in the (local-only)
  audit report.
- `provider: anthropic` (the `claude-sonnet` fallback) means no different-provider CLI
  was available, so the check is same-provider and weaker.

This block is optional and is added only by `wiki-audit strong`. Pages never need it to
be valid.

## Contradiction Check

`wiki-ingest` runs a cheap contradiction check on the pages each ingest touches, before it
commits. It is a **gate, not an annotation**: every page that lands in git is clean.

- **Scope — touched neighbors only.** The check compares the pages an ingest wrote or
  edited against (a) themselves and (b) the pages that ingest already read (the Classes /
  Concepts pages it updated and the neighbor pages from its backlink audit). It does NOT
  re-read the whole wiki — a conflict with a distant, untouched page is left to the
  periodic `wiki-lint` sweep.
- **Blocking vs. soft.** A **blocking** contradiction is a real factual conflict on the
  same entity under the same scope — incompatible line ranges, method signatures, counts,
  names, or mutually-exclusive claims. A **soft** tension (differing emphasis, claims that
  hold under different scope) is not a conflict.
- **The transient blocker flag.** When a blocking contradiction is found, a single line is
  written to the affected page's frontmatter and the ingest stops before committing:
  ```yaml
  contradiction-check: failed — <one-line reason naming the counterpart slug or "internal">
  ```
  The machine-readable token is the literal `contradiction-check: failed`. It exists ONLY
  while the conflict is unresolved; resolving the conflict **removes the line**. A committed
  page never carries it — there is no `passed` stamp, no severity history, nothing. Absence
  of the flag is the only "clean" state.
- **Soft tensions are surfaced, not recorded** — mentioned in the ingest summary so you can
  act if you wish, but never persisted and never blocking.

This flag is also what the **Pre-commit Gate** below scans staged files for.

## Pre-commit Gate

`bin/hooks/pre-commit` (installed via `git config core.hooksPath bin/hooks`) runs **two**
deterministic gates before every commit in this repo — no LLM:

1. **`bin/check-contradictions.py`** — scans the **staged** content of `wiki/pages/*.md`,
   frontmatter only, and **blocks the commit** if any page still carries a
   `contradiction-check: failed` flag. Backstop to the skill-level hold in `wiki-ingest`
   step 7b; on a healthy wiki it never fires. Resolve the contradiction and remove the
   `contradiction-check:` line, then re-stage.
2. **`bin/lint-mechanical.py --staged`** — scans the staged pages for **structural**
   problems and **blocks the commit** on any: missing required frontmatter, a broken
   link, or a slug collision (a bare slug clashing with a qualified one). Fix the page
   and re-stage.

Both gates look only at `wiki/pages/`, so ordinary code commits pass through untouched.

- **Fresh clone:** `core.hooksPath` is repo-local config and is not cloned — re-run
  `git config core.hooksPath bin/hooks` once after cloning.
- **Override** an intentional commit with `git commit --no-verify`.

## Operation Log & Commit Convention
Operations: init, ingest, query, update, lint, audit, merge, split

**The git history is the operation log.** After an operation, the skill suggests a commit
message and commits on your confirmation (skills never auto-commit). Render the human log
on demand with `python bin/render-log.py`.

No existing subject convention was found in this repo, so the default applies: **Conventional
Commits**, choosing the type by operation:

| Operation        | Type                                  |
|------------------|---------------------------------------|
| init             | `chore`                               |
| ingest           | `docs`                                |
| update           | `docs`                                |
| query (saved)    | `docs`                                |
| lint             | `fix` if fixes applied, else `chore`  |
| audit            | `fix` if fixes applied, else `chore`  |
| merge / split    | `refactor`                            |

**Always append a `Wiki-Op:` trailer** — it is what `render-log.py` keys on, decoupling the
log from the subject convention. Which pages changed is read from the commit diff, so no
`Pages:` trailer is needed.
```
docs: document Sim simulation step

Wiki-Op: ingest
```

To keep wiki history separable from code history, wiki commits stage only `wiki/`, `raw/`,
`SCHEMA.md` and `bin/`.

## Index Generation
`wiki/index.md` is a generated, gitignored artifact — never hand-edit it. It is rebuilt
from page frontmatter by `bin/generate-index.py`:
- Run `python bin/generate-index.py` (or `python3`) **before reading the index**, and
  **after** any operation that adds, removes, renames, or re-categorizes a page.
- The generator groups pages by their `category` frontmatter, in the order categories are
  listed under **Index Categories** below; within a category it lists pages newest-first
  by `created`. Each entry is `- [[slug](pages/slug.md)] — summary _(created)_`.
- Pages whose filename matches `audit-*.md` are excluded (gitignored local-only
  artifacts). A page with an unrecognized or missing `category` lands in an
  `Uncategorized` section.

## Index Categories
Files
Classes
Concepts
Flows

## Conventions
- raw/ is immutable — skills never modify it (see **Source snapshots** for how code is versioned there)
- operation log: each op is a commit (see Operation Log & Commit Convention), rendered with bin/render-log.py
- index.md is GENERATED by bin/generate-index.py and is gitignored — never hand-edit it; set page frontmatter (category, summary) and regenerate instead
- All pages live flat in wiki/pages/ — no subdirectories
- overview.md reflects the current synthesis across all sources
- Cross-reference and citation slug-targets follow `config/link-style.md` — every skill reads it before writing or scanning links
- contradiction check: ingest gates on blocking contradictions in touched pages via a transient `contradiction-check: failed` flag, removed before commit — committed pages are always clean (see Contradiction Check)
- pre-commit gate: bin/hooks/pre-commit (via core.hooksPath) runs bin/check-contradictions.py then bin/lint-mechanical.py --staged; re-run `git config core.hooksPath bin/hooks` after a fresh clone
- README boundary: wiki pages must not duplicate README content. Extract structural signals; link to the README for operational content (setup, contributing, running). When ingesting any README, also evaluate it for gaps and suggest edits.
- Beginner-audience rule: every Classes and Files page must define each Java idiom it relies on (generics, enums, interfaces, lambdas, `final`, `static`, etc.) or link to a Concepts page for it. Assume the reader knows another language, not Java.
