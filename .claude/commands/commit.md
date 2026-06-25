Commit the changed code

**All output and responses MUST be in Korean (한국어).**

Commit the currently changed code following the rules of the `git-conventions` skill. The detailed rules (commit message format, staging, attribution prohibition, etc.) live in `git-conventions` as the source of truth; this command is the entry point.

## $ARGUMENTS

Optional issue ticket number. If provided, include it in the commit subject as `[PROJ-XXX]` (e.g. `PROJ-123`). If empty, use the ticket-less `<type>: <description>` format — do not invent a ticket.

## Steps

1. **Check changes**: Identify what changed with `git status` / `git diff`.
2. **Split into logical units**: Break commits up by concern so they are easy to review.
   - Different features/bugfixes go in separate commits
   - Refactoring and feature changes go in separate commits
   - Test code goes together with the feature it covers
   - If there is a migration driven by an Entity change, include it together with that Entity change
3. **staging**: Stage only explicit paths per commit (`git add <path>` — `git add -A` / `git add .` forbidden, git-conventions Git #8).
4. **Write the message**: Write it per `git-conventions` §2–§3. The `Co-Authored-By: Claude` / `Generated with Claude Code` / `🤖` footer is forbidden (Git #7).
5. **Commit**: Run only when the user has explicitly requested a commit (Git #9). Do not use `git commit --amend`; always create a new commit.
6. **Post-commit summary** (in Korean, without example code):
   - Overview of the changed code
   - Changed/added APIs
   - Changed features and business logic
   - Things to check at deployment time (migrations / environment variables / infrastructure)
   - Migration queries when Entities changed
   - Other caveats
