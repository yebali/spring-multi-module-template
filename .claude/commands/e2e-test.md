Verify **the code changed on the current branch** end-to-end with a real server (`bootRun`) + a real DB (Docker PostgreSQL) + real HTTP calls. All e2e verification in this repo goes through this command.

**All output and responses MUST be in Korean (한국어).**

The `e2e-test` skill is the single source of truth and this command is the entry point — do not restate the phases.

## Scope

`$ARGUMENTS` — **single-diff mode** (no fixed scenario catalog; every run derives scenarios anew from the current branch state):

- **empty** → automatically derive **all changed endpoints** of the current branch (vs `origin/main`, including the working tree).
- **keyword/description** → only the changed endpoints / scenarios matching that keyword (judged by changed file path or endpoint URL substring).

> If the changes do not touch a controller/service, report "no e2e-relevant changes" in §3 and exit.

## Procedure

Follow the sections of the `e2e-test` skill exactly:

- **Common lifecycle §1–§2** — verify/start PostgreSQL infrastructure → background `bootRun` of only the changed modules (`projectA`/`ProjectB`) on distinct `--server.port` / `--management.server.port` → poll for actuator health UP. (This template has no authentication layer, so there is no token/auth-header step. If authentication is added, augment the sign-in procedure in skill §0/§3.)
- **§3 change identification** (core) — union of the 4 kinds of `origin/main` diff (committed/staged/unstaged/untracked) → classify → extract endpoints → present a candidate scenario table.
- **§4 scenario generation** — derive the call shape, prerequisite data, verification points, and happy/failure paths for each changed endpoint.
- **§5 execution + verification** — real HTTP calls → verify HTTP status / response body / (optional) DB state.

**Wrap-up** — **§7** result report table + one-line summary → **§6** cleanup (kill the launched server/management port processes; keep the PostgreSQL container running).

## Notes

- Since ports collide when several modules are started simultaneously, specify a different `--server.port` / `--management.server.port` for each module (skill §2).
- Since each module takes time to start, notify the user with a short status (e.g. "projectA UP") each time one comes UP.
- If a code bug surfaces during scenario execution, do not fix it automatically; state it in the report (surface it to the user).
- When you discover a new failure pattern, add one line to the failure table in skill **§8**.
