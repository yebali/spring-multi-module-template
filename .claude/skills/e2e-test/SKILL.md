---
name: e2e-test
description: Use when verifying that the CURRENT BRANCH's code changes actually work end-to-end against real local servers (module `bootRun`) + Docker PostgreSQL + real HTTP calls in this spring-multi-module-template repo. The skill identifies what changed in this branch (vs `origin/main`, including uncommitted working-tree edits), derives the matching e2e scenarios for the changed REST endpoints / domain flows on the fly, executes them with real HTTP, and reports HTTP + DB verification per scenario. Triggered by `/e2e-test`, or when the user says "spin up a real server and test", "e2e test", "integration test (real server)", "verify by calling the API locally", "verify against a real server", "e2e-verify the changes in this branch". For unit/integration tests at the SpringBootTest level use `test-conventions`; invoke this skill only when a **real server process + real DB + real HTTP** is required. NOT a fixed scenario catalog — every run derives scenarios from the diff.
---

# e2e-test (real server + current-branch-change-based e2e verification)

A foundation for e2e-verifying **the behavior of code changed on the current branch** with a real server (`bootRun`) + real DB (Docker PostgreSQL) + real HTTP calls.

**All output and responses MUST be in Korean (한국어).**

Core intent:
- §1–§2 are the scenario-independent **common lifecycle** (infrastructure → module bootRun + health polling).
- §3 is the heart of this skill: from the **current branch diff** (vs `origin/main`, including the working tree), automatically extract the changed controller endpoints / service public methods and present a list of verification scenarios.
- §4 is a guide for deriving the e2e calls that match each change on the spot (no fixed catalog).
- §5 is execution + HTTP / DB verification. §6 cleanup, §7 reporting, §8 failure handling.

This skill is **not a cumulative catalog** — every run derives scenarios anew from the current branch state.

---

## 0. Quick Reference (dynamic discovery)

| Item | Value |
|---|---|
| Module list | Discovered dynamically from the `include(...)` in `settings.gradle.kts` (currently `projectA`, `ProjectB`) |
| Server port | Read from each module's `server.port` in `application*.yml`. **If unset, defaults to 8080** — running multiple modules at once collides, so assign a distinct port per module via `--args='--server.port=NNNN'` |
| Actuator port | `management.server.port` (currently both modules use `8081` — this also needs overriding when running concurrently) |
| DB | Docker PostgreSQL — per `application.yml`: `jdbc:postgresql://localhost:5432/postgres`, user `postgres`, password `testpassword` |
| Base branch | `origin/main` (the baseline for identifying changes) |
| Auth | The current template has no auth layer — no token / auth header needed. If auth is added, augment this section with a sign-in procedure |

`$ARGUMENTS` (passed by the command):
- If empty, automatically derive **all changed endpoints on the current branch**
- If a keyword is given, only the endpoints / scenarios matching that keyword (judged by changed file path or endpoint URL substring)

> **Note**: This template still has almost no actual REST endpoints / domain logic. If the changes do not touch a controller/service, report "no e2e-relevant changes" in §3 and stop.

---

## 1. Infrastructure (PostgreSQL) check / startup

```bash
docker ps --format '{{.Names}}' | grep -E '^postgresql$'
```

If absent, start it (credentials per `application.yml`):

```bash
docker run -d --name postgresql -p 5432:5432 \
  -e POSTGRES_PASSWORD=testpassword postgres:18
```

If an existing container is stopped, just run `docker start postgresql`.

ready polling:
```bash
until docker exec postgresql pg_isready -U postgres > /dev/null 2>&1; do sleep 1; done
```

> It is common for the user to already have the infrastructure running — check with `docker ps` and, if present, use it as is (do not start a duplicate).

---

## 2. Background module startup + health polling

Decide **which modules to start** from the change-identification result in `§3` — if changes are confined to one module, you may start only that module. Running multiple modules at once collides on ports, so assign a distinct `server.port` / `management.server.port` per module.

Run `bootRun` each in its own background Bash. Redirect logs to `/tmp/e2e-test-logs/<module>.log`.

```bash
mkdir -p /tmp/e2e-test-logs

# e.g. projectA — server 8080 / actuator 18081
./gradlew :projectA:bootRun \
  --args='--spring.profiles.active=local --server.port=8080 --management.server.port=18081' \
  > /tmp/e2e-test-logs/projectA.log 2>&1  # run_in_background

# e.g. ProjectB — server 8090 / actuator 18091 (avoid collision when running concurrently)
./gradlew :ProjectB:bootRun \
  --args='--spring.profiles.active=local --server.port=8090 --management.server.port=18091' \
  > /tmp/e2e-test-logs/ProjectB.log 2>&1  # run_in_background
```

### 2.1 actuator/health UP polling

```bash
# match the actuator port of the started module
for svc in "projectA:18081" "ProjectB:18091"; do
  name="${svc%:*}"; port="${svc##*:}"
  until curl -fsS "http://localhost:${port}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do sleep 2; done
  echo "${name} UP (:${port})"
done
```

Notify the user one line at a time as each module comes UP.

---

## 3. Change identification (the heart of this skill)

When `/e2e-test` is invoked, the very first step is to identify **the code changed on the current branch relative to origin/main**.

### 3.1 Collect the diff

```bash
git fetch origin main --quiet
git diff --name-only origin/main...HEAD          # committed-but-not-merged
git diff --name-only HEAD                          # unstaged
git diff --name-only --cached                      # staged
git ls-files --others --exclude-standard           # untracked
```

The union of all four = "the set of changed files on the current branch".

### 3.2 Classify the changes

| Category | Matcher | Meaning |
|---|---|---|
| **REST endpoint** | `@*Mapping` changed / new / signature-changed in `**/controller/*.kt` | direct e2e call target |
| **Service public method** | `fun` signature changed / new in `**/service/*.kt` | trace whether reachable via a controller |
| **Entity / Repository / Query** | `**/entity/*.kt` / `**/repository/**/*.kt` | if changed together with the two categories above, that endpoint is affected |
| **DTO** | `**/controller/rest/*.kt` / `**/service/command/*.kt` | request/response structure change — verify that endpoint's contract |
| **Test only** | only `**/src/test/**` changed | not an e2e target — JUnit suffices. Notify of skip |
| **Config / Build** | `*.yml` / `*.gradle*` / `*.properties` / `application*.kt` | not a direct call target but affects behavior — surface to the user and ask which endpoint regression is needed |

### 3.3 Endpoint extraction

For REST endpoint changes, extract from the controller file (Read + grep):
- class-level `@RequestMapping("...")`
- method-level `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping`
- method signature (path variable, request param, request body type)
- which module the controller belongs to (judged by the module directory in the changed file path)

Include not only new endpoints but also endpoints with **signature changes**, **request/response DTO changes**, and **changes to the service methods they call**.

### 3.4 Present the candidate scenario list + user confirmation

Show the identification result to the user as a table:

```
| # | endpoint | change type | what to verify |
|---|---|---|---|
| 1 | POST /api/v1/articles | new controller + service | happy path + missing required value 400 |
| 2 | GET /api/v1/articles?status=... | new query param + repo | whether only matching articles are returned |
```

If a keyword (`$ARGUMENTS`) is given, filter to only the endpoints containing that keyword. After presenting the table, confirm whether to proceed / any missing scenarios via AskUserQuestion or a direct question, then proceed to §4.

---

## 4. Scenario generation guide (no fixed catalog)

For each endpoint, derive the following 3 things:

### 4.1 Call shape
From the controller signature: HTTP method + path (substitute path variables), query param, request body (`@RequestBody` DTO → JSON example; extract the inner data class structure precisely by Reading `controller/rest/*Request.kt` — no guessing), required headers.

### 4.2 Prerequisite data
**Compose the rows needed for endpoint execution via a real endpoint call sequence** (avoid direct DB INSERT — risk of bypassing id sequence / audit hooks). Use `psql` directly only when unavoidable.

### 4.3 Verification points
- HTTP status code
- key fields of the response body (`jq` or python `json.load`)
- (optional) DB state change — `docker exec postgresql psql -U postgres -d postgres -c "<SELECT>"`
- (optional) change in another endpoint's response (e.g. confirm via GET after creation)

### 4.4 happy path / failure path
At least 2 scenarios per changed endpoint:
- **happy path**: valid input → 200 + response schema
- one or more **failure paths**: invalid input / nonexistent resource → 4xx (one of the variation matrix in controller-conventions §2.5 B-1)

### 4.5 Isolation / idempotency
Make unique-constraint fields unique with `$(date +%s%N)` / `uuidgen` so two runs do not break.

---

## 5. Scenario execution + verification

Execute each scenario in order and report the result one line at a time.

```bash
HTTP=$(curl -sS -o /tmp/e2e_resp.json -w "%{http_code}" \
  -X <METHOD> <URL> \
  -H "Content-Type: application/json" \
  -d '<body json>')
echo "HTTP=$HTTP"
cat /tmp/e2e_resp.json | python3 -m json.tool

# DB verification
docker exec postgresql psql -U postgres -d postgres -c "SELECT ... FROM ... WHERE ...;"
```

On failure, check the §8 table + the relevant module log (tail 50 of `/tmp/e2e-test-logs/<module>.log`), and if the cause is a code bug, state it in the report (the skill does not auto-fix — surface it to the user).

---

## 6. cleanup

```bash
# clean up all started server / management ports
for p in 8080 8090 18081 18091; do
  PID=$(lsof -ti :$p)
  if [ -n "$PID" ]; then kill -9 $PID; echo "killed :$p (pid=$PID)"; fi
done
```

Leave the PostgreSQL container **as is** (reuse it on the next run). Only if the user explicitly requests, `docker stop postgresql`. DB data is kept by default (since `ddl-auto: create`, the schema is recreated on restart).

---

## 7. Result report format

```
| # | endpoint | variation | HTTP | key response | DB / follow-up verification | pass |
|---|---|---|---|---|---|---|
| 1 | POST /api/v1/articles | happy path | 200 | id=... | 1 articles row | ✅ |
| 1 | POST /api/v1/articles | missing required value | 400 | message=... | no row change | ✅ |
```

Final line: overall pass status / environment issues / module shutdown + whether the container is kept. If there is a changed endpoint for which no scenario could be created, note it separately.

---

## 8. Failure handling

| Symptom | Cause | Response |
|---|---|---|
| `Web server failed to start. Port 8080 was already in use` | multiple modules started on the same port | use a different `--server.port` / `--management.server.port` per module (§2) |
| actuator health does not respond | `management.server.port` collision or health endpoint not exposed | check the port override + check the actuator exposure setting in `application-local.yml` |
| DB connection failure (`Connection refused` / auth) | PostgreSQL not started or credential mismatch | check `docker ps`, check it matches the url/user/password in `application.yml` |
| module startup cannot claim 8080/8081 | leftover process from a previous session | `lsof -ti :<port> \| xargs kill -9`, then restart |
| 404 on endpoint after startup | no actual controller yet (default template state) | re-confirm in §3 whether there is actually an e2e-relevant change |

For each failure, first check the last 50 lines of the log file (`/tmp/e2e-test-logs/*.log`). When you discover a new failure pattern, add a row to this table.
