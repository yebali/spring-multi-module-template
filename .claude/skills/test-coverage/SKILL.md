---
name: test-coverage
description: Use when auditing or ensuring the COMPLETENESS and regression-value of a Kotlin Spring Boot test SUITE in this spring-multi-module-template repo (projectA / ProjectB) — not how to write one test, but whether the right tests exist and carry signal. Covers per-layer coverage rules (Service public-method A-1 / private-method A-2, Controller parameter matrix B-1 / param-binding B-2) and the four test quality criteria (core-logic coverage, no-duplicate-verification, convention compliance, E2E API coverage — incl. C-1 trivial-CRUD avoidance, C-2 must-have standalone tests, C-3 regression-signal self-check). Triggers on "test coverage audit", "write missing tests for @Service / @RestController / *Repository", "is coverage sufficient", or any mention of A-1 / A-2 / B-1 / B-2 / C-1 / C-2 / C-3 / Criterion 1-4. For HOW to write/structure a test (SpringBootTestSupport, @Nested, helpers, assertions) use test-conventions — these two are peers and usually apply together.
---

# Test Coverage & Quality Criteria

Companion to `test-conventions` (which owns the *writing mechanics* — `SpringBootTestSupport`, `@Nested`, helpers, assertions). This skill owns the **suite-completeness and regression-value** judgments: per-layer coverage rules and the quality criteria for deciding whether a test is worth writing.

> When you write tests you usually need BOTH skills: `test-conventions` for how, `test-coverage` for what-must-exist. Repo-wide rules that also apply to tests are cited as `Test #0a–#0d` (defined in `test-conventions` §0).

---

## Controller coverage

### B-1. Parameter Variation Coverage (required)

Each endpoint tests the following parameter variations **at least once each**. This is a stronger requirement than Criterion 4 (E2E API Coverage)'s "one test per endpoint" — it satisfies the *endpoint × variation* matrix.

| Variation | Verification |
|------|------|
| All required values filled | 200 OK + response schema |
| Optional value omitted (Kotlin nullable / default) | 200 OK + default behavior |
| Required value missing | 400 Bad Request + error message |
| Format mismatch (UUID as string, negative page, etc.) | 400 |
| Insufficient permission (caller without rights) | 403 (when authorization exists) |
| Resource absent | 404 (or 403 if the permission check runs first) |

Pagination endpoints additionally verify **at least one sort key** and **at least one page-size boundary (0 / 1 / max)**.

### B-2. Param-binding coverage (required)

- **Typed `@RequestParam` / `toCommand` binding MUST be exercised through real HTTP**, even when the filter SQL is already covered at the repository layer. The String→type conversion (`ZonedDateTime?`, `Long?`, enum, range objects, etc.) + the `toCommand` wiring is a controller-only failure surface that repository tests (which pass typed objects directly) cannot catch. A fixture that can't *distinguish* the filtered-in from the filtered-out row does NOT count as coverage — send a discriminating dataset and assert only the in-filter rows survive.
- A `PUT`/`DELETE`/command endpoint that returns **`Unit`** has an empty body — its only HTTP contract is `status().isOk`; do NOT assert `jsonPath("$.field")` on it (there is no body). The 200 + request-deserialization is the contract; the state effect belongs to the Service test.

---

## Service coverage

### A-1. Public Method Coverage Rule (required)

Every `public fun` of a Service class has **at least one success-path test** and **one failure-path test per domain exception it throws**.

**Exception**: *internal Service-to-Service* methods like `get*Entity()` / `list*Entities()` may be covered indirectly by the cross-domain caller's test that uses them — when a standalone test would verify the same branches as the caller's test and thus be redundant.

Checklist:
- [ ] Every public method called by a Controller has a corresponding `@Nested` group
- [ ] Each group covers *success* + *each throw scenario (per domain exception)*
- [ ] Every exception thrown by a `check*` private method is reachable through a combination of public-method inputs

### A-2. Private Method Testing Strategy (required)

Private methods are **never called directly** — reflection, `@VisibleForTesting`, and package-private exposure are all prohibited. Reach private branches by combining public-method inputs (`Command` fields, ID existence, permission level, state enum, etc.) along the execution path.

Rationale:
- Testing private methods directly means a refactor (rename / inline / moving a boundary) breaks the test, making it impossible to distinguish a *real regression* from an *internal structure change*.
- A private branch unreachable from the public surface is **dead code** — a candidate for *removal*, not testing.

See `test-conventions` §2.5 A-2 for a worked example.

---

## Quality Criteria

Use these 4 criteria when writing new tests or reviewing existing tests.

### Criterion 1: Core Logic Coverage

Every domain's critical business logic MUST have dedicated tests:

| Domain | Required Test Coverage |
|--------|-----------------------|
| CRUD operations | Create, Read (single + page), Update, Delete |
| Permission checks | each permission level (allow / deny) for each operation |
| State transitions | Status changes (e.g., `DRAFT → PUBLISHED → ARCHIVED`) |
| Edge cases | Soft delete re-access, empty results, boundary values |
| Cross-domain operations | Link/unlink with cross-domain permission validation |
| **Service public method coverage** | → A-1 |

### Criterion 2: No Duplicate Verification

Avoid multiple tests verifying the same logic within the same layer:

| Allowed | Not Allowed |
|---------|-------------|
| Same logic tested at different layers (Controller/Service/Repository) | Two test groups in the same class testing identical assertions |
| Similar structure across different domains (e.g. Article vs Comment) | "Method-axis" AND "Role-axis" tests for the same permission matrix |
| Multiple input values in a single test | Separate tests per input value when the code path is identical |

**Detection pattern**: If two `@Nested` groups produce the same pass/fail matrix (same methods × same permission levels), one is redundant.

#### C-1. Avoid Trivial Insert/CRUD Tests (required)

**Do NOT write** the following — they re-verify behavior already guaranteed by JpaRepository / JPA / Spring, so their regression signal is zero:

| Anti-pattern | Reason |
|---------|------|
| `repo.save(x); assertThat(repo.findById(x.id)).isPresent` | re-verifies the JpaRepository contract |
| `entity.field = newValue; repo.save(entity); assertThat(repo.findById...field).isEqualTo(newValue)` | re-verifies JPA dirty checking |
| `repo.deleteById(x.id); assertThat(repo.findById(x.id)).isEmpty` | re-verifies JpaRepository delete |
| asserting only a simple 1:1 field match right after `Service.create()` | if the Service has *no business logic* beyond Command → Entity mapping, the Controller's response verification (B-1) is sufficient |
| Jackson deserialization / Spring DI behavior itself | re-verifies framework behavior |
| `data class` generated `equals`/`hashCode`/`copy`/`toString` | re-verifies Kotlin codegen. **Exception**: test equals ONLY when an `Array` field forces a *manual* override (arrays use identity equals) — then 2 tests suffice (same-content-equal, diff-not-equal), not the full matrix |
| Spring-singleton bean identity (e.g. resolver "returns same instance") | re-verifies the framework's container, not your logic |
| poking entity `var` setters then reading them back (`entity.x = v; assertThat(entity.x)==v`) | re-verifies the language. Test the entity's *named state-change method* instead (see C-2 entity row) |

#### C-2. Areas That MUST Have a Standalone Test (required)

The following can fail silently on regression, so they **must have a standalone test**:

| Area | Test location | Test points |
|------|------------|---------------|
| QueryDSL Custom Repository (`Custom*RepositoryImpl`) | Repository test | one case per nullable-field branch of the Query object + `*ByQuery` pattern → repository-conventions |
| JSONB query utilities | Repository test | type inference (Double/Int/String), sort direction, null handling |
| `Command.init {}` structural validation | Service test (or Command unit test) | domain exception on empty list / negative / format violation |
| domain enum mapping (`*EnumMapper`) | Unit test | bidirectional mapping for **every** enum value via `@EnumSource` (not a hand-picked subset). **Every** `*EnumMapper` has its OWN test file **named after the mapper it tests** — verify the file actually targets that mapper (a test that asserts a *different* mapper is a silent zero-coverage hole). Cover name-*mismatch* arms explicitly — these are exactly what a one-line typo breaks |
| transactional side-effect ordering (`@TransactionalEventListener(phase = AFTER_COMMIT)`) | Service / integration test | **BOTH** required: (1) committed tx → effect published, (2) rolled-back tx → effect NOT published. A commit-only test stays green even if `phase` is wrong — only the rollback test pins AFTER_COMMIT. Also cover the `REQUIRES_NEW` path (effect survives outer rollback) if the listener uses one |
| entity state-change methods (`assign()` / status transition / `delete()` / `reset*()`) | Service / entity test | success post-state + each rejection (named exception). Reach them through public calls, never by setting `var`s (C-1) |

#### C-3. "Regression Signal Strength" Self-Check

Write a new test only when all three of the following answer *yes*:

1. If this test fails, can you assert that *production* behavior is broken?
2. Is this behavior NOT already verified by a *test in another layer*?
3. Does this test avoid re-verifying *framework* (JpaRepository, Spring DI, Jackson) behavior?

→ Write only if (1) yes + (2) yes + (3) yes. If any is no, choose to *strengthen an existing test* or *not write one*.

### Criterion 3: Convention Compliance

- [ ] Writing mechanics (`SpringBootTestSupport` / `@Nested` / no `@DisplayName` / `@Transactional` rules / faker helpers / FK-order cleanup) follow **`test-conventions`**.
- [ ] Repo-wide rules respected: no FQN (Test #0a), **no cross-layer logic re-assertion (Test #0b — see B-1)**, Service-signature change → `*Test.kt` usages updated (Test #0c), exception-message assertions in exact English (Test #0d).

### Criterion 4: E2E API Coverage

Every Controller endpoint has an integration test and satisfies the B-1 matrix 1+ times each. Verification: cross-check the endpoint list ↔ test methods.
