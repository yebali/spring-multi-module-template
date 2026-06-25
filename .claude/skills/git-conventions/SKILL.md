---
name: git-conventions
description: Use when creating commits, choosing branch names, drafting commit messages, or staging changes in this spring-multi-module-template repo. Covers commit subject format (`<type>: <description>`, optional `[PROJ-XXX]` ticket prefix, no module/scope prefix), commit type vocabulary (`feat`/`fix`/`refactor`/`test`/`chore`/`docs`/`style`), commit body structure (Why / What / Verification 3-block recommendation), branch naming (`feature/<kebab-case>`, `bugfix/<kebab-case>`, `hotfix/<kebab-case>`; no ticket id in branch name), Claude attribution prohibition (no `Co-Authored-By: Claude`, no `Generated with Claude Code`, no `🤖` footer), staging discipline (`git add <specific paths>` only — `git add -A`/`git add .` forbidden), and the policy that Claude never auto-runs `git commit` without explicit user instruction. Triggers on edits or invocations involving `git commit` / `git checkout -b` / `git branch -m` / `git add`; mention of "commit", "branch", "commit message", "PR title", commit drafting, branch creation, or staging selection.
---

# Git Workflow Conventions

Single source of truth (operational checklist) for commit messages and branch naming. Apply BEFORE running `git commit` or `git checkout -b`.

> **Rule-number notation**: reference rules here as `Git #N`.

---

## 1. Hard Rules (never violate when committing or branching)

| # | Rule | Where it applies |
|---|------|-----------------|
| Git #1 | Subject format: `<type>: <description>`. If the project uses an issue tracker, an optional ticket prefix is allowed: `[PROJ-XXX] <type>: <description>` | every commit subject |
| Git #2 | Type vocabulary: `feat`, `fix`, `refactor`, `test`, `chore`, `docs`, `style` (use only these) | every commit subject |
| Git #3 | Module/scope prefix is forbidden: `feat(projectA): ...`, `ProjectB: ...` are NOT allowed. Subject stays scope-less; mention module in body if needed | every commit subject |
| Git #4 | Subject is present tense, no trailing period, ~50 chars recommended. Korean or English both OK | every commit subject |
| Git #5 | Branch format: `feature/<kebab-case>`, `bugfix/<kebab-case>`, `hotfix/<kebab-case>`. Use only these three prefixes | `git checkout -b`, `git branch -m` |
| Git #6 | **Branch name MUST NOT contain a ticket id.** Use `feature/replace-patchmapping-with-putmapping`, NOT `feature/PROJ-328-...`. Ticket id (if any) belongs in commit subject and PR title only | `git checkout -b`, `git branch -m` |
| Git #7 | Claude attribution is forbidden: NO `Co-Authored-By: Claude ...`, NO `Generated with Claude Code`, NO `🤖` footer. Strip these from any boilerplate before committing | every commit message |
| Git #8 | Stage with explicit paths: `git add path/to/file.kt path/to/another.kt`. `git add -A` and `git add .` are forbidden (risk of staging secrets / untracked plans / build artifacts) | every staging step |
| Git #9 | Claude never auto-runs `git commit` without an explicit user instruction. Drafting a message in conversation is fine; running the command is gated on a direct ask | `git commit` invocation |

> **Ticket id (optional)**: this template does not mandate an issue tracker. Use the plain `<type>: <description>` form by default. Only when the user supplies a ticket id (or asks for one) add the `[PROJ-XXX]` prefix. Never fabricate a ticket id.

---

## 2. Commit Subject

```text
# ✅ Good — without ticket (default in this repo)
feat: upgrade dependencies to latest versions
fix: add missing PostgreSQL compatibility mode to the H2 test profile
refactor: unify @PatchMapping to @PutMapping
test: clean up convention compliance in ArticleServiceTest

# ✅ Good — with optional ticket prefix
[PROJ-328] refactor: unify @PatchMapping to @PutMapping
[PROJ-314] feat: add article query filter

# ❌ Bad — module/scope prefix
feat(projectA): Add endpoint
ProjectB: fix login bug

# ❌ Bad — past tense / trailing period
fix: Resolved the bug.
feat: Added new endpoint.
```

**Korean vs English**: Both acceptable. Match the surrounding context — domain code commits are usually Korean, infra/config commits are often English.

---

## 3. Commit Body

Body is **optional**. Single-purpose changes (typo fix, one-line refactor) don't need a body.

When the change has a non-obvious "Why" or a non-trivial "What", structure the body as **3 blocks**:

1. **Why** — 1–2 sentences on the motivation. The constraint, deadline, or design tradeoff that prompted the change.
2. **What** — bullet list of the concrete file/symbol-level changes. Include affected modules.
3. **Verification** — bullet list of tests added/run, manual verification, or grep checks. Skip if the change is doc-only.

```text
refactor: unify @PatchMapping to @PutMapping

Establish as a convention that PATCH is not used, and convert all existing
endpoints to PUT in one pass. The URL structure is preserved, so there is no
migration impact on external clients.

- ArticleController.updateStatus: PATCH → PUT
- Sync the canonical convention (controller-conventions/SKILL.md)

Verification:
- :projectA:ktlintCheck PASS
- :projectA:test all PASS
```

Wrap subject ≤ 72 chars where reasonable; body lines ≤ 72 chars.

---

## 4. Branch Naming

| Prefix | When | Example |
|--------|------|---------|
| `feature/` | New capability, refactor, doc update | `feature/replace-patchmapping-with-putmapping` |
| `bugfix/` | Non-critical bug fix targeting `main` | `bugfix/null-author-on-create` |
| `hotfix/` | Critical production fix | `hotfix/article-create-500` |

**Naming**:
- kebab-case (lowercase, hyphen-separated)
- Describe the **what** in 3–6 words
- **Do NOT include a ticket id** — `feature/PROJ-328-...` is wrong, `feature/replace-patchmapping-...` is right

**If you create the wrong branch name**: rename locally before push.

```bash
# Local rename — safe, no remote impact, history preserved
git branch -m feature/PROJ-328-foo feature/foo
```

After push, renaming is more expensive. Get the name right before the first push.

PR target: `main`. This repo uses a simple `main`-based flow — no long-lived develop / release branches.

---

## 5. Pre-Commit Workflow

1. **Check working tree**: `git status --short` — confirm what's modified vs untracked. Look for accidental files (build artifacts, `plans/*.md`, `.env` files).
2. **Stage explicit paths only**: `git add path/a path/b ...`. Never `-A` or `.`. If you need many files, list them; don't shortcut.
3. **Verify staged set**: `git status --short` again — staged files should match intent.
4. **Draft message**: subject (Git #1–#4) + optional body (§3). Use HEREDOC for multi-line bodies to preserve formatting:
   ```bash
   git commit -m "$(cat <<'EOF'
   type: subject

   Why...
   What...
   Verification:
   - ...
   EOF
   )"
   ```
5. **Commit only on explicit user instruction** (Git #9).
6. **Pre-commit hooks**: ktlint hooks may run. If a hook fails, **fix the underlying issue and create a NEW commit**. Do NOT amend or use `--no-verify`.
7. **After commit**: `git status --short` to confirm clean tree, `git log --oneline -3` to confirm subject formatting.

---

## 6. Pre-Write Checklist

Before running `git commit`:

- [ ] Subject matches `<type>: ...` (or `[PROJ-XXX] <type>: ...` if a ticket was supplied) (Git #1)
- [ ] Type is one of `feat`/`fix`/`refactor`/`test`/`chore`/`docs`/`style` (Git #2)
- [ ] No module/scope prefix like `feat(projectA):` (Git #3)
- [ ] Subject is present tense, no trailing period (Git #4)
- [ ] Body (if present) follows the Why/What/Verification 3-block (§3)
- [ ] No `Co-Authored-By: Claude` / `Generated with Claude Code` / `🤖` (Git #7)

Before running `git checkout -b` or `git branch -m`:

- [ ] Prefix is `feature/` / `bugfix/` / `hotfix/` (Git #5)
- [ ] Branch name does NOT contain a ticket id (Git #6)
- [ ] Branch name is kebab-case, 3–6 words

Before running `git add`:

- [ ] Explicit paths listed, not `-A` / `.` (Git #8)
- [ ] No `.env`, credentials, build artifacts, or unrelated `plans/*.md` accidentally included
- [ ] User has explicitly asked for the commit (Git #9)
