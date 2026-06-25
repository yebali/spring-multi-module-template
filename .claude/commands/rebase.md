Rebase the current branch onto a remote branch.

**All output and responses MUST be in Korean (한국어).**

Rebase the current branch onto the latest state of a remote (`origin`) branch.

## Target branch

`$ARGUMENTS` — the remote branch name to rebase onto (`main` / `dev`, etc.). **Defaults to `main` if empty.** The remote is always `origin`.

## Steps

1. **Determine the target branch**: If `$ARGUMENTS` is empty, use `main`; otherwise use its value as the target branch.
2. **Auto-stash**: Check for tracked changes with `git status --porcelain`. If there are changes, stash them with `git stash push` (do not use `-u` — untracked files do not block a rebase, so leave them alone). Remember whether you stashed.
3. **fetch**: `git fetch origin` — fetch **all branches** from the remote (`origin`). If the fetch fails, report the error in Korean and — if you stashed, restore with `git stash pop` first — then exit. Even after a successful fetch, if `origin/<branch>` does not exist, likewise report + restore, then exit.
4. **rebase**: `git rebase origin/<branch>`.
5. **On conflicts**: Do not auto-resolve conflicts. Report the list of conflicting files in Korean and instruct the user to resolve them themselves and then run `git rebase --continue` (or `git rebase --abort`). If you stashed, state that they must run `git stash pop` themselves after the rebase completes/aborts. Do not call `git rebase --abort` on your own.
6. **On rebase success + stashed**: Restore the changes with `git stash pop`. If a conflict arises during the pop, report the conflicting files and **skip** steps 7 and 8. Instruct the user to run `./gradlew ktlintFormat clean build` manually after resolving the conflicts.
7. **Auto-apply ktlintFormat**: Run `./gradlew ktlintFormat` from the root. Check whether files were modified with `git status --porcelain`. If there are modifications, report the list of changed files in Korean and — **do not commit automatically** — instruct the user to review and run `git add ... && git commit` themselves (git-conventions Git #9: no commit without explicit user instruction).
8. **clean build verification**: Run `./gradlew clean build` from the root. On success, proceed to step 9. On failure, report the failed task name and the core error message (top 20 lines) in Korean, then exit — do not try to fix the failure automatically.
9. **Report the result (in Korean)**: Number of rebased commits, the new base (short SHA of `origin/<branch>`), whether the stash was restored, whether ktlintFormat made automatic fixes (number of files), and the clean build result.

## Notes

- Rebase rewrites history. If the current branch has already been pushed to the remote, also note in the result report that a subsequent `git push --force-with-lease` will be needed.
- The case where the current branch is the same as the target branch behaves close to a remote-based fast-forward and is allowed as-is — it is harmless.
- `./gradlew clean build` cleans all modules (the `include` targets in `settings.gradle.kts` — currently `projectA` / `ProjectB`) and then runs compile, test, and ktlintCheck. It can take a long time, so also note that the user has to be patient and wait — also mention that `./gradlew check` can be used as a substitute if only a quick verification is wanted.
- Files modified by ktlintFormat are either a cleanup of the style of the changes brought in by the rebase, or the exposure of a latent violation unrelated to the rebase. The user judges which of the two it is via `git diff`.
