---
name: sync-conventions
description: Use when the user asks to verify, audit, or check drift between project convention documentation (CLAUDE.md and any docs/*.md) and the layered convention skills (`code-style-conventions`, `repository-conventions`, `service-conventions`, `controller-conventions`, `test-conventions`, `git-conventions`) under `.claude/skills/**/SKILL.md`. Detects drift in both directions — rules present in docs but missing from skills, rules present in skills but missing from docs, contradictory wording, broken cross-references, and inconsistent skill-local rule numbering. Triggers on phrases like "convention sync", "compare skills and docs", "skill drift", "sync conventions", "convention audit", "skill check", or whenever the user asks whether the convention skills and convention docs are aligned. Read-only audit — never auto-edits docs or skills, only reports findings for human reconciliation.
---

# Sync Convention Docs with Convention Skills

**All output and responses MUST be in Korean (한국어).**

Audit drift between the project's convention **documentation** (read by humans on GitHub / IDE) and the project's convention **skills** (loaded by Claude on trigger). The two are intentionally separate artifacts for separate audiences, but they MUST stay aligned on the underlying rule set. This skill detects misalignment and reports it for human reconciliation. **It does not auto-edit either side** — docs prose and skill checklists require different phrasings, so the human decides how to fix.

## Operating Principle

You are reconciling two human-maintained representations of the **same** rule set:

- **Docs** (`CLAUDE.md`, and any `docs/*.md` / `README.md` describing conventions) — prose + rationale, addressed to contributors reading on GitHub.
- **Skills** (`.claude/skills/{code-style,repository,service,controller,test,git}-conventions/SKILL.md` and any future convention skill) — operational checklists addressed to Claude, loaded on trigger.

Drift between them is normal; both are touched independently as conventions evolve. Your job is to surface every divergence so the human can decide which side to update. **Both sides are valid sources of truth depending on context** — never auto-fix one to match the other.

This skill complements `sync-docs` (which audits docs vs *code*) by covering the docs ↔ skills axis that `sync-docs` explicitly excludes (`**/skills/**` is in its excludes list).

---

## Phase 1 — Discover the rule surface

Do NOT start from a remembered rule list. Derive it dynamically each run.

### 1.1 Discover convention docs

Enumerate via Glob (treat any new `.md` file matching these globs as in-scope):
- `CLAUDE.md` (root)
- `README.md` (root) — only sections describing conventions / build / workflow
- Any `docs/*.md` (this template may have none today; pick up new ones automatically)
- Any other `.md` whose top-level heading matches `*conventions*`, `*patterns*`, or `*rules*` (case-insensitive)

For each doc, build a **rule map**:
- All explicit rule numbers / bullet rules
- All H2/H3 section headings (topical units)
- All `❌ Bad` / `✅ Good` code-example pairs (the most testable assertions)
- All cross-references to other docs or skills

### 1.2 Discover convention skills

Enumerate via Glob: `.claude/skills/*/SKILL.md`.

Filter to **convention-style skills only** by inspecting frontmatter `description`. Include when the description mentions any of: "convention", "before writing", "checklist", "Rule #". Exclude tooling/meta skills (`sync-docs`, `sync-conventions`, `convention-audit`, `e2e-test`, `commit`, `code-review`, etc.).

For each in-scope skill, build a parallel **rule map**:
- All skill-local rule references (`Style #N`, `Ctrl #N`, `Svc #N`, `Repo #N`, `Test #N`, `Git #N`)
- All H2 (`##`) section headings
- All `❌ Bad` / `✅ Good` code-example pairs
- All cross-references to docs or sibling skills (including forms like `code-style-conventions §4`)
- The pre-write checklist items (final `## ... Checklist` section), each treated as one assertion

Output of Phase 1: two parallel maps (docs-side, skills-side), keyed where possible (rule id, or H2 heading slug), plus an unkeyed bucket.

---

## Phase 2 — Detect drift (parallel agents)

Dispatch **one `Explore` agent per drift dimension**, all in a single message. Each agent receives the Phase 1 maps and returns only its own findings.

### Agent A — Rule coverage

For every rule asserted in CLAUDE.md / docs:
- Which skill (if any) restates it?
- If restated, do the doc statement and skill statement agree on **what is forbidden / required**? (Allow phrasing differences; flag semantic differences.)
- If a rule references a method name, annotation, or class, does the skill use the same identifier?

Report: 🔴 rule in docs but absent from every convention skill · 🟠 rule in skill body but missing from docs · 🟡 rule restated on both sides with semantically different wording.

### Agent B — Section-level coverage

For every H2/H3 section in the convention docs, determine the natural owning skill (use this default mapping):

| Doc section topic | Expected owning skill |
|------------------|----------------------|
| Repository / QueryDSL / `@Query` / `@EntityGraph` / Q-class / JSONB / cascade | `repository-conventions` |
| `@Service` / Command/Result / `@Transactional` / domain exception / enum / CRUD ordering | `service-conventions` |
| `@RestController` / Request/Response / Bean Validation / `@ResponseStatus` / Pagination / REST URL | `controller-conventions` |
| `*Test.kt` / `SpringBootTestSupport` / `@Nested` / helpers | `test-conventions` |
| Comment/KDoc policy / file organization / util placement / single-use intermediate / `@Suppress` | `code-style-conventions` |
| Commit message / branch naming / staging / Claude attribution | `git-conventions` |

Report: 🟠 doc section with no corresponding skill section · 🟡 doc section present in skill but with materially different example.

### Agent C — Cross-reference integrity

For every markdown link / section reference (doc → skill, skill → doc, skill → sibling skill section):
- Target file exists at the linked path
- Referenced section/anchor exists
- Linking text accurately describes the target

Report: 🔴 broken link · 🟡 stale link text.

### Agent D — Description / trigger drift

For each convention skill's frontmatter `description`:
- Does it list code keywords (`@Query`, `@Transactional`, `@ResponseStatus`) that still appear in the skill body?
- Are file glob triggers (`**/repository/**.kt`, etc.) consistent with the directory layout actually present in the codebase (`projectA`/`ProjectB` under `com/yebali/template`)?

Report: 🟡 description keyword no longer in skill body · 🟡 file glob targets a directory that doesn't exist.

### Agent E — Skill-internal consistency

For each convention skill:
- Does the pre-write checklist at the end cover every Hard Rule listed in §1 of that skill?
- Are skill-local rule numbers (`Style #N`, etc.) used consistently within the skill?
- Does the skill cross-reference docs/skills that exist?

Report: 🟡 Hard Rule in §1 with no matching checklist item · 🟡 inconsistent / dangling skill-local rule number.

Collect and merge all five agents' findings before Phase 3.

---

## Phase 3 — Report (no auto-fix)

**Do not edit any file.** This skill is read-only by design. Print findings in this format:

```
## Convention sync check result

### Summary
- Docs checked: <N>
- Skills checked: <M>
- Drift found: 🔴 <severe> / 🟠 <missing> / 🟡 <minor>

### 🔴 Fix immediately
- **[doc:line vs skill:line]** <one-line summary>
  - docs-side statement: "<quote>"
  - skill-side statement: "<quote or 'none'>"
  - Recommended action: <one line on which side to fix and how>

### 🟠 Missing (present on only one side)
- **[doc-only | <file:line>]** <item> — Recommended: add a checklist item to `<owning-skill>`
- **[skill-only | <file:line>]** <item> — Recommended: add prose to <doc> (including rationale)

### 🟡 Phrasing difference / minor drift
- **[<location>]** <brief summary> — human decides, then updates one side

### Cross-reference check
- 🔴 broken links: <count> / 🟡 stale link text: <count> (each item from→to + recommended fix)

### Items to confirm
- <items that cannot be decided automatically and need user judgment>
```

---

## Non-negotiable rules

- **Read-only.** Never edit `docs/*.md`, `CLAUDE.md`, or `.claude/skills/*/SKILL.md` as part of this skill.
- **Both audiences are valid.** Doc prose and skill checklists must phrase the same rule differently — flag *semantic* divergence, not phrasing divergence.
- **Discover dynamically.** Never hardcode the set of docs or skills. Re-enumerate via Glob each run so newly added convention skills are automatically in scope.
- **Stay on the docs ↔ skills axis.** If you find docs that disagree with *code*, that is `sync-docs` territory — mention it in `Items to confirm` and stop.
- **Do not invent rules.** A rule missing from both sides is a coverage gap — record it under `Items to confirm`, do not propose new wording.
