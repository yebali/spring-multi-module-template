---
name: convention-audit
description: Use when the user wants to scan the ENTIRE Kotlin codebase — every module, every production file, NOT just changed/modified code — against the project's convention rule set and report/fix violations — the code ↔ convention axis. Detects @Service/@Component miscategorization (service-conventions §12 table), comment-policy violations (`/* */` block comments inside methods, self-evident label KDoc), multiple top-level types per file, util misplacement, and machine-detectable violations (FQN, `!!`, `@Query`/`@Modifying`, class-level `@Transactional`, `@ResponseStatus`, Bean Validation, `@Suppress("UNCHECKED_CAST")`). EVERY run also performs a mandatory duplication & extraction review. Triggers on "convention violation detection", "convention audit", "code convention audit", "rule violation check", "@Service @Component check", "duplicate code review", "shared method extraction review", or the convention-audit skill. NOT for reviewing a diff or changed code (use code-review), NOT for docs↔skills drift (use sync-conventions), NOT for docs↔code drift (use sync-docs).
---

# Convention Audit — Code ↔ Convention Drift Detection & Fix

**All output and responses MUST be in Korean (한국어).**

Scan already-committed production Kotlin code against the project's convention rule set, report violations, and fix the clear ones. This skill operates on the **code ↔ convention** axis — distinct from `sync-conventions` (docs ↔ skills) and `sync-docs` (docs ↔ code).

## Scope

- **Target — the ENTIRE codebase**: every production `*.kt` file in every module. **Discover the module list dynamically** from `settings.gradle.kts` (`include(...)` entries — currently `projectA`, `ProjectB`) — never hardcode it. This is NOT a changed-code / diff review — it never limits itself to modified files, a single module, or a path argument. Exclude only `build/`, kapt-generated sources, and `*Test.kt`.
- **No modes, no options**: the audit always runs end to end — detect, report, AND fix. Every detected 🔴 clear violation is always fixed.
- **Invariant**: the Phase 2 rule scan, the Phase 3 duplication & extraction review, and the Phase 4 fix ALWAYS run over the whole codebase. The run never stops at reporting.

## Tooling — dispatch scan agents as `Explore` subagents

Phase 2 / Phase 3 scan-partition agents MUST be dispatched with **`subagent_type: Explore`**. The `Explore` agent carries **`Read` / `Glob` / `Grep` / `Bash`** as first-class tools. A `general-purpose` agent does NOT — there `Grep` / `Glob` are *deferred* tools, which is the historical root cause of partition agents failing with "Grep/Glob unavailable" and **silently skipping whole domains**.

- Discover files → **Glob**. Search patterns (annotations, `!!`, `@Query`, …) → **Grep**. Read contents → **Read**.
- If a search tool is somehow unavailable, **Bash `find` / `grep` is an allowed fallback** — coverage beats tool purity.
- Scan-partition agents are read-only (detect & report; no fixes). The Phase 4 fix is applied by the main orchestrator.

## Maximize Parallelism

This skill fans out across **as many parallel agents as possible**. Phases 2 and 3 partition the work and dispatch every partition as a separate agent **in one single message**.

**Scope partitioning** — split the audit target into the smallest independently-auditable units:
- One partition per module (`projectA`, `ProjectB`).
- For a large module, split further into one partition per top-level domain package under `com/yebali/template/` (plus `config` / `util`).

Discover the partition list dynamically by enumerating every module (from `settings.gradle.kts`) and every domain package. The union of all partitions MUST cover the entire codebase.

## Phase 1 — Collect the rule surface (dynamic)

Do NOT hardcode the check list. On every run, read the canonical sources and build the check list:

- `CLAUDE.md` — any project rules it states.
- `.claude/skills/code-style-conventions/SKILL.md` — comment/KDoc policy, one-top-level-type-per-file + exceptions, util placement, single-use intermediate, `@Suppress` ban.
- `.claude/skills/service-conventions/SKILL.md` §12 — the `@Service` / `@Component` category table; plus `@Transactional`, exception, CRUD ordering rules.
- `.claude/skills/controller-conventions/SKILL.md` — `@ResponseStatus`, Bean Validation, `required=false`, Entity-import bans.
- `.claude/skills/repository-conventions/SKILL.md` — `@Query`/`@Modifying` ban, `@Transactional`/`@Repository` on `Custom*RepositoryImpl` ban, JSONB annotations.

When rules are added or changed, Phase 1 picks them up automatically.

## Phase 2 — Rule scan (massively parallel)

Dispatch **one agent per scope partition** (each `subagent_type: Explore`), all in a single message. Each partition agent receives the Phase 1 rule map and runs the full check set on its partition:

- **Check A — @Service / @Component** (service-conventions §12): map every `@Service` / `@Component` class to the categories; flag annotation↔category and suffix↔category mismatch. Clear mismatch → 🔴; gray zones → 🟡.
- **Check B — Comment policy** (code-style-conventions §1): `/* */` block comments inside method bodies; self-evident label KDoc; line-by-line WHAT-restating comments. Also flag WHAT-restatement *sentences* inside an otherwise-valid WHY KDoc — the fix trims that one sentence, it does NOT delete the whole KDoc.
- **Check C — File organization** (code-style-conventions §2): files with 2+ top-level `class` / `object` / `interface` / `enum class`; skip the allowed exceptions (sealed hierarchy, `@Entity`+`@IdClass`, `@Configuration`+`@ConfigurationProperties`, cohesive util file, `@SpringBootApplication`+`main`).
- **Check D — Machine-detectable violations**: FQN, `!!`, `@Query` / `@Modifying`, class-level `@Transactional` on a Service/Controller AND any `@Transactional` on a `Custom*RepositoryImpl` (repository-conventions Repo #7), `@ResponseStatus`, Bean Validation annotations (`jakarta.validation.*`), `@Repository`/`@Component` on a `Custom*RepositoryImpl` (Repo #8), `@Suppress("UNCHECKED_CAST")`, generic `IllegalArgumentException`/`IllegalStateException` thrown for domain errors (service-conventions Svc #13), single-expression trailing-lambda calls needlessly split across 3 lines while the receiver/args fit on one (code-style-conventions §6 — exempt when the argument itself is genuinely multi-line).

Each partition agent also collects, for Phase 3, the **method/util inventory** of its partition (signatures, short body summaries, util-like helpers).

To push parallelism further, additionally split each partition by check group (separate agents for Check A / B / C / D per partition), all dispatched in the same single message.

### Partition completeness — verify, never skip silently

After the parallel agents return, **verify every partition produced a valid structured report**. A partition whose agent returned empty / an error / "cannot audit / tool unavailable" / no file inventory is a **failed partition** — **re-dispatch it** (as `Explore`; instruct Bash `find`/`grep` fallback) before Phase 4. The audit MUST NOT report while any partition is unaccounted for.

## Phase 3 — Duplication & extraction review (ALWAYS runs, cannot be skipped)

**Every audit run MUST include this phase.** It implements the "review existing code for duplicates" duty of code-style-conventions §3.

**NOT a duplication candidate — `Command`/`Result`/`Request`/`Response` inner DTOs.** Same-shape inner data classes across these classes are **intentional duplication** (controller-conventions §4.1 / service-conventions §2) — each class declares its own. Do NOT report identical inner DTOs as a 🔁 extraction candidate.

**Also NOT a candidate — QueryDSL query skeletons.** Repeated `selectFrom…where…fetch` chains are intentionally not extracted (repository-conventions Repo #9).

1. **Local duplication scan (parallel)** — one agent per scope partition; each scans for intra-partition duplicate / near-duplicate methods and extraction candidates.
2. **Cross-partition consolidation** — merge every partition's method/util inventory and find logic duplicated *across* domains. Runs after the parallel agents return.
3. **Classify** each candidate by usage span: single-domain / multi-domain within one module / across modules.
4. **Placement proposal** — per code-style-conventions §3: multi-domain within a module → `{module}/.../util/`. (This template has no shared module, so cross-module duplication is only reported as a candidate for either keeping it in each module or creating a new shared module.)
5. **Human-confirmation gate** — duplication / extraction is a semantic judgment. The tool **reports candidates**; an extract or move is applied only for items a human approves.

## Phase 4 — Report & fix

### Coverage verification (before reporting)
Count production `*.kt` files per module (`Glob`), and confirm the union of all partitions' inspected-file lists equals that total. If any file is unaccounted for, scan it before reporting.

### Report (Korean output)
- Group violations by check + severity (🔴 clear / 🟡 gray zone). Each item: `file:line`, violated rule, rationale, recommended fix.
- Put Phase 3 duplication / extraction candidates in their own section — per candidate: all duplicate locations, usage-span classification, placement proposal.
- End with a summary table — including an **inspected-files / total-files** row proving full coverage.

### Fix (always — every run)
- Apply fixes for ALL 🔴 clear rule violations. No report-only mode.
- Do NOT auto-fix 🟡 gray zones or Phase 3 duplication candidates — those require human approval.
- After fixing, run `./gradlew ktlintCheck` on the affected modules. For fixes affecting compilation (`@Component` ↔ `@Service`, etc.), also run the module `build`.

## Phase 5 — Documentation sync (final step, every run)

The audit changes **code** (annotations, comments, signatures, file splits), which can make docs go stale. So the final step is to re-align documentation:

- After Phase 4's fixes are applied and verified, **invoke the `sync-docs` skill**. It reconciles `CLAUDE.md`, `README.md`, and any `docs/**` against the current code.
- This makes a single `convention-audit` run leave **both code and docs consistent**.
- If this run added or changed a convention *rule* (not just applied existing ones), additionally recommend running `sync-conventions` (docs↔skills, read-only). Only when rules themselves changed.

## Output Format

Headings in English, body text in Korean.

```
## Convention Audit result — entire project (all modules)

### 🔴 Clear violations (N)
**1. {check} — {title}**
- Location: `file:line`
- Rule: {skill §N / Svc #N / ...}
- Why: {why it is a violation}
- Fix: {recommended fix}

### 🟡 Gray zone / needs human judgment (N)
(same format — not auto-fixed)

### 🔁 Duplication / extraction candidates (N)
**1. {title}**
- Locations: `file:line`, ...
- Span: single-domain / within-module / across-modules
- Proposal: {target location}

### 📊 Summary
| Category | Count |
|----------|-------|
| 🔴 Clear violations | N |
| 🟡 Gray zone | N |
| 🔁 Duplication candidates | N |
| Inspected files / total | N / N (full coverage confirmed) |

**Fixes applied**: {count of 🔴 clear violations fixed}

### Phase 5 — documentation sync result
{summary of docs modified by `sync-docs`, or "no drift"}
```

## Self-check

Before reporting, confirm:
- Phase 2 / Phase 3 scan agents were dispatched with `subagent_type: Explore`.
- **Every partition returned a valid structured report**; any failed / empty partition was re-dispatched and covered.
- Coverage was verified — inspected-files count equals the total production `*.kt` count; the Summary table shows it.
- Phase 3 duplication & extraction review was performed (mandatory).
- Phase 5 documentation sync (`sync-docs`) was invoked after Phase 4.
- Phase 2 and Phase 3 agents were dispatched in a single message for true parallelism.
- Gray-zone and duplication candidates were NOT auto-fixed.
- The module list and check list came dynamically from `settings.gradle.kts` and Phase 1 (no hardcoding).
