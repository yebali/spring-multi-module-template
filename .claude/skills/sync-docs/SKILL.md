---
name: sync-docs
description: Use when the user asks to sync, audit, or verify project documentation against the codebase — detects drift between markdown docs and actual source (versions, entities, packages, env vars, conventions). Triggers on phrases like "documentation sync", "docs check", "sync docs", "whether the docs match the code", or when the user mentions outdated/stale documentation. Discovers the document set and module scopes dynamically from the filesystem — does not assume a fixed list of files or modules.
---

# Sync Codebase with Project Documentation

**All output and responses MUST be in Korean (한국어).**

Audit the project's markdown documentation against the current codebase state and apply corrective updates. The set of documents and module scopes is **discovered dynamically** at run time — never assume a hardcoded list. If docs or modules are added, removed, or renamed, this skill still works without modification.

## Operating Principle

You are reconciling two sources of truth:

- **Code** — the authoritative description of what the system is today.
- **Docs** — human-maintained claims about the system. Prone to drift whenever code changes and docs don't.

Your job is to find every claim in the docs that the code contradicts, then correct the docs (never the code). Do not invent new documentation for undocumented features — only correct existing claims and note demonstrable gaps.

---

## Phase 1 — Discover documentation scopes

Do NOT start from a remembered list. Derive the work partition from the filesystem each time.

**Enumerate scopes, not individual files.** A *scope* is a directory that owns a coherent set of docs plus the code those docs describe:

- **Root scope** — repo root `CLAUDE.md`, any top-level `README*.md` / `AGENTS.md`, and all files under `docs/**/*.md` (if present). This scope describes cross-cutting concerns and verifies against repo-wide locations (`gradle/libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, `buildSrc/`).
- **Module scopes** — one scope per module directory (discovered from `settings.gradle.kts` `include(...)` entries — currently `projectA`, `ProjectB`) that contains a `docs/` subdirectory. Discover via Glob: any `*/docs` that is not the root `docs/`. Each module scope owns `{module}/docs/**/*.md` and verifies against code under `{module}/src/**`.

Determine the module list dynamically by reading `settings.gradle.kts` (the `include("...")` calls) rather than hardcoding it.

Exclude `.claude/**`, `.git/**`, `build/**`, `.gradle/**`, `node_modules/**`, and any `**/skills/**` or `**/commands/**`.

For each scope, before dispatch, list the actual `.md` files it contains and skim their openings to build a short claim map: what versions, class names, packages, tables, enum values, env vars, and cross-links each file asserts. This map is what Phase 2 verifies.

> This template may currently have no per-module `docs/` — in that case only the root scope (CLAUDE.md, README.md) is audited. When new docs are added they are automatically included as a scope.

## Phase 2 — Detect drift (one agent per scope, in parallel)

**Dispatch one `Explore` agent per scope**, all in a single message so they run concurrently. The number of agents equals the number of scopes discovered in Phase 1.

Each agent's brief:

> Audit the `{scope}` documentation against code in `{code-root}`. Doc root: `{docs-root}`. Report only drift — mismatches, contradictions, and missing coverage that the code makes obvious. Use `file:line` references. Do not list things that are in sync.

Per-agent verification checklist (apply whatever is relevant):
- Referenced files, classes, packages still exist at the stated path (`com.yebali.template.*`).
- Version numbers and image tags match the authoritative build files (`gradle/libs.versions.toml`).
- Enum membership lists are complete and current.
- Environment variable / property names match `application*.yml` and code usage.
- Cross-references between docs (including cross-scope links) resolve.
- Two docs describing the same topic are not contradicting each other.
- Any *code* that violates a rule the doc states should also be surfaced (for user decision — code fixes are out of scope).

Collect and merge all agents' findings before Phase 2B.

## Phase 2B — Structural health check (docs vs. docs)

Run after Phase 2; a single `Explore` agent covers it (inherently cross-scope).

### 1. Oversized documents (split candidates)
Flag a `.md` as a split candidate when: file exceeds ~600 lines, OR covers 4+ unrelated top-level topics, OR a single H2 section exceeds ~250 lines and is self-coherent. For each, propose target filenames, which sections move, cross-references to redirect, and a stub to leave behind. **Do NOT auto-split** — record in `items to confirm`.

### 2. Cross-document duplication
Detect text blocks (paragraph, table, list, code block) that appear verbatim or near-verbatim (>80% similar) in 2+ files. For each cluster, identify the canonical owner by topic. Default mapping (apply unless a stronger reason exists):
- Code style / naming / file organization → `.claude/skills/code-style-conventions/SKILL.md` (or `docs/CODE_CONVENTIONS.md` if it exists)
- Layered architecture / command-result / enum / exception rules → `service-conventions` (or `docs/IMPLEMENTATION_PATTERNS.md`)
- JSONB / cascade / queries → `repository-conventions` (or `docs/DATABASE_PATTERNS.md`)
- Test infrastructure / helpers / @Nested conventions → `test-conventions`
- Setup / build / Docker / ports → `README.md` (or `docs/SETUP.md`)
- System overview / tech stack → root `CLAUDE.md` (or `docs/ARCHITECTURE.md`)

In every non-canonical copy, replace the duplicated block with a cross-reference. **Auto-fix verbatim/near-verbatim duplicates (🟡)**; if copies diverged, reconcile into one best version in the canonical file then link.

### 3. Inefficient / redundant content within a single doc
Flag and simplify: paragraphs restating an adjacent table/code block; 2+ examples for the same rule (keep the clearest); orphan/aspirational sections with no code reference; overlong mixed tables; dead anchors. **Auto-remove obvious redundancy (🟡)**; ambiguous table reshapes → surface (⚪).

Collect Phase 2B findings and merge with Phase 2 before Phase 3.

## Phase 3 — Apply every fix (no confirmation gate)

**Do not ask the user which items to apply.** Every item from Phase 2 (drift) and Phase 2B (structural health) is applied automatically in the same run — the invocation itself is the approval. The only exceptions are items explicitly marked "surface to user" (document splits, ambiguous table reshapes) — those go into `items to confirm`.

Classify internally for ordering/reporting:
- 🔴 **Severe** — doc describes code that no longer exists, or behavior opposite to current code. Apply first.
- 🟠 **Missing content** — new entities/codes exist in code but absent from docs expected to cover them. Apply next.
- 🟡 **Stale details** — renamed classes, moved files, outdated examples, small factual errors. Apply.
- ⚪ **Judgment calls** — ambiguous/cosmetic. Apply the most-likely-correct interpretation; if truly undecidable, leave untouched and record in `items to confirm`.

Edit rules: preserve each document's existing structure/tone/formatting; don't remove sections unless the referenced code is also gone; mirror established heading/ordering style; update all affected docs in the same pass. For efficiency dispatch one worker agent per scope in parallel to apply that scope's fixes.

## Phase 4 — Report

```
## Documentation sync result

### Modified files
- **<file path>**: <reason for change> — <summary of changes>

### Items to confirm
- <items left because they could not be auto-applied, or items requiring a code change rather than a doc change>
```

---

## Non-negotiable rules

- Do **not** remove documentation unless the referenced code has been deleted.
- Do **not** add speculative documentation — only document what exists in the codebase right now.
- Preserve each document's existing structure and formatting style.
- Version numbers must match the authoritative build files (`gradle/libs.versions.toml`) exactly.
- Never edit code as part of this skill — if a doc-vs-code mismatch can only be resolved by a code change, surface it to the user and stop.
