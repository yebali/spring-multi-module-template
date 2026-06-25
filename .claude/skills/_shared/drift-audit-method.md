# Drift-Audit Method — Shared Machinery

Common operating machinery for the project's drift-audit skills (`convention-audit` = code↔convention, `sync-conventions` = docs↔skills, `sync-docs` = docs↔code). A skill links here for the parts that are identical across them; everything skill-specific (severity legend meanings, canonical-owner mappings, apply policy, phase structure, axis-specific checks) stays inline in that skill.

## Korean-output mandate

**All output and responses MUST be in Korean (한국어).** (Headings may stay in English; body text is Korean.)

## Dynamic partition derivation

Derive the work partition **dynamically each run** — never start from a remembered or hardcoded list. Re-enumerate the in-scope set (files / docs / scopes / rule surface) from the filesystem (Glob / Grep / git) on every invocation, so newly added items are automatically covered and removed items drop out. A partition list copied from a previous run is invalid.

## Parallel dispatch via Explore

Fan out across **as many parallel agents as possible**: dispatch **one `Explore` agent per partition / drift dimension / scope**, and send them **all in ONE single message** so they run concurrently. Never process independent partitions sequentially.

Use `subagent_type: Explore` — NOT `general-purpose`. `Explore` carries `Read` / `Glob` / `Grep` / `Bash` as first-class tools; on a freshly-spawned `general-purpose` agent those are *deferred* tools that cannot be called without first running `ToolSearch`. Dispatching scan partitions as `general-purpose` is the historical root cause of partition agents failing with "Grep/Glob unavailable", returning an empty report, and **silently skipping whole partitions** — leaving the audit with partial coverage. (If a search tool is somehow still unavailable to an `Explore` agent, Bash `find` / `grep` is an allowed fallback — a partition must NEVER fail to enumerate its work.)

## Report skeleton

Open the report with a Korean `## …result` heading (output is Korean — see Korean-output mandate above), then group findings into severity-tagged sections (highest severity first), and close with a summary. The concrete severity glyphs and their meanings, the exact section titles, and any summary table differ per skill and are defined inline in each skill — use the skill's own legend, not a generic one.

```
## <scope> result

### <🔴 highest-severity section>
- Each item: location (`file:line`), what/why it is drift, recommended action

### <next-severity section>
- …

### Summary / items to confirm
- Aggregates or items that require human judgment
```
