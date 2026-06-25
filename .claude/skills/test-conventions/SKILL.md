---
name: test-conventions
description: Use when writing, reviewing, or auditing Kotlin Spring Boot test code in this spring-multi-module-template repo (projectA / ProjectB) — covers `SpringBootTestSupport` infrastructure, `@Nested` grouping (no `@DisplayName`), per-layer `@Transactional` rules, helper conventions (`save*/build*/random*/mock*/ensure*`), Service public-method coverage rule (A-1), private-method testing strategy (A-2), Controller parameter variation matrix (B-1), and quality criteria (Criterion 1-4 incl. C-1 trivial-CRUD avoidance, C-2 must-test areas, C-3 regression-signal self-check). Triggers on edits to `*Test.kt` files, requests to apply test conventions, audit test coverage, write missing tests for `@Service`/`@RestController`/`*Repository` classes, or any mention of A-1 / A-2 / B-1 / C-1 / C-2 / C-3 rules.
---

# Testing Guide

Conventions and best practices for writing integration tests in this template. Tests use SpringMockK (not Mockito) and run against a real PostgreSQL via Testcontainers (singleton container + `@ServiceConnection` in `SpringBootTestSupport`).

> **Rule-number notation**: this skill cites its own rules as `Test #N` and the layer skills by section.
>
> **Peer skill**: this skill owns the test *writing mechanics* (how). For **suite-completeness and regression-value** judgments (what-must-exist — A-1/A-2/B-1/B-2 coverage rules and Criterion 1-4) see `test-coverage`. The two are peers and usually apply together.

## 0. Repo-wide rules that also apply to tests

These come from the cross-cutting / layer skills and are NOT testing-specific, but every test must respect them.

| # | Rule | Test-specific note |
|---|------|--------------------|
| Test #0a | Never use FQN — always use `import` statements | applies to test bodies, helpers, fixtures |
| Test #0b | **Do NOT test the same logic twice in the same layer.** If `Service.foo()` is exhaustively covered in `FooServiceTest`, the Controller test for `POST /foo` must NOT re-assert the same business branches — it tests routing/contract, not business logic | see §2.5 B-1 |
| Test #0c | Read callers / dependents BEFORE modifying any Service / Entity | when a Service signature changes, search `*Test.kt` for usages and update them in the same change |
| Test #0d | Exception `message` fields are user-visible ⇒ English | test assertions on exception messages must match English text |

## 1. Test Infrastructure

**`SpringBootTestSupport`** is the single abstract base for integration tests. Current definition:

```kotlin
@SpringBootTest
@ActiveProfiles("test")
abstract class SpringBootTestSupport
```

For modules that add web-layer (Controller) tests, also annotate with `@AutoConfigureMockMvc` and expose `mockMvc` via `@Autowired`. When external clients are introduced, declare them in the base with SpringMockK's `@MockkBean` / `@SpykBean`.

**Declare all test dependencies and helpers ONLY in `SpringBootTestSupport` — do not re-declare them in subclass test classes.**

```kotlin
// ✅ Good — use inherited dependencies and helpers as-is
class ArticleServiceTest : SpringBootTestSupport() {
    @Test
    fun `create article success`() {
        val result = articleService.createArticle(buildCreateCommand())
        ...
    }
}

// ❌ Bad — re-declaring an inherited dependency
class ArticleServiceTest : SpringBootTestSupport() {
    @Autowired lateinit var articleService: ArticleService   // should be exposed in the base
}
```

## 2. Test Structure

**@Nested Organization**: ALWAYS use `@Nested` to group related test cases logically.
- Each `@Nested` group focuses on ONE domain feature (e.g., create, read, update, delete, exception handling).
- **DO NOT use `@DisplayName`** (causes Kotlin compiler issues).
- Use English PascalCase or Korean (with backticks) for inner class names.
- Test method names follow patterns: `should do X when Y` / `cannot do X with condition Y` / `verify X logic`.

```kotlin
// ✅ Good - Logical grouping with @Nested
class ArticleServiceTest : SpringBootTestSupport() {
    @Nested
    inner class Create {
        @Test
        fun `create article success`() { ... }

        @Test
        fun `cannot create article with blank title`() { ... }
    }

    @Nested
    inner class Read {
        @Test
        fun `get article success`() { ... }
    }
}

// ❌ Bad - @DisplayName
class ArticleServiceTest : SpringBootTestSupport() {
    @DisplayName("Create Article")  // ❌ Causes compiler errors!
    @Test
    fun `create article`() { ... }
}
```

**Setup/Teardown**: Use `init()` for `@BeforeEach` and `cleanup()` for `@AfterEach` consistently.

**@Transactional Usage Rules**:
- **Repository tests**: Use `@Transactional` at class level (required for bulk DML and `@MapsId` parent persistence).
- **Service tests**: Do NOT use `@Transactional` (verify actual transaction behavior).
- **Controller tests**: Do NOT use `@Transactional` (integration test purpose).

> **Repository *impl* classes do NOT get `@Transactional` (repository-conventions Repo #7 — the boundary is owned by the Service), but Repository *test* classes do — this is not a contradiction.** In production the calling Service provides the boundary, whereas tests call the repo directly, so the *test class* provides the boundary. Since the repo impl has no `@Transactional`, when a test directly calls a Custom repo's bulk DML (`queryFactory.delete(...).execute()`), it throws `TransactionRequiredException` without a class-level `@Transactional`.

```kotlin
// ✅ Good - Repository test uses @Transactional
@Transactional
class CustomCommentRepositoryImplTest : SpringBootTestSupport() {
    @Test
    fun `should delete all Comments by Article ID`() {
        commentRepository.deleteAllByArticleId(article.id)
    }
}

// ✅ Good - Service test does NOT use @Transactional
class ArticleServiceTest : SpringBootTestSupport() {
    @AfterEach
    fun cleanup() { articleRepository.deleteAll() }

    @Test
    fun `should create an Article`() {
        val result = articleService.createArticle(command)
    }
}
```

**Imports**: ALWAYS use import statements instead of FQN — mandatory in test code as well.

## 2.5 Test Responsibilities by Layer

Each layer has a clear test responsibility. Verify only that layer's responsibility.

### Controller Test - "HTTP Contract Verification"

| Verification Target | Description | Example |
|---------------------|-------------|---------|
| HTTP status codes | Correct status code returned | 200, 400, 401, 403, 404, 409 |
| Request validation | `Command.init{}` behavior (via deserialization) | blank strings, out-of-range values → 400 |
| Error response | error message structure | `jsonPath("$.message")` etc. |
| Response structure | JSON response schema | `jsonPath("$.id")`, `jsonPath("$.title")` |
| Auth/Authorization | permission check | 401 without auth, 403 without permission |

**What NOT to test**: Detailed business logic, DB state verification.

#### B-1. Parameter Variation Coverage (required)

Each endpoint tests the following parameter variations **at least once each** — satisfying the *endpoint × variation* matrix.

| Variation | Verification |
|-----------|--------------|
| All required values filled | 200 OK + response schema |
| Optional value omitted (Kotlin nullable / default) | 200 OK + default behavior |
| Required value missing | 400 Bad Request |
| Format mismatch (UUID as string, negative page, etc.) | 400 |
| Insufficient permission | 403 |
| Missing resource | 404 (or 403 if the permission check runs first) |

Pagination endpoints additionally verify **at least one sort key** and **at least one page-size boundary (0 / 1 / max)**.

### Service Test - "Business Logic Verification"

| Verification Target | Description |
|---------------------|-------------|
| Business rules | State transitions, calculation logic |
| Exception conditions | `assertThatThrownBy` |
| Permission validation | Behavior per permission level |
| Edge cases | Boundary conditions, null handling |
| State changes | Entity state mutations |

**What NOT to test**: HTTP status codes, SQL query correctness.

```kotlin
val article = saveArticle(authorId = ownerId)
assertThatThrownBy { articleService.updateArticle(UpdateCommand(articleId = article.id, editorId = strangerId)) }
    .isInstanceOf(ArticlePermissionDeniedException::class.java)
```

#### A-1. Public Method Coverage Rule (required)

Every `public fun` of a Service class has **at least 1 success-path test** and **1 failure-path test per domain exception it throws**.

**Exception**: *internal Service-to-Service* methods like `get*Entity()` / `list*Entities()` may be covered indirectly by the cross-domain caller test that uses them (when a standalone test would duplicate the caller test by verifying the same branches).

#### A-2. Private Method Testing Strategy (required)

Private methods are **never called directly** — reflection, `@VisibleForTesting`, and relaxing visibility are all forbidden. Reach private branches via the execution path by combining public-method inputs (`Command` fields, ID existence, permission level, status enum, etc.).

Rationale: testing private methods directly makes tests break on refactoring, so you cannot distinguish a *real regression* from an *internal structure change*. A private branch unreachable through the public surface alone is **dead code**, and is a target for *removal*, not testing.

```kotlin
// ✅ Good — reach the private branch by combining public inputs
@Test
fun `cannot delete Article when caller is not author`() {
    val article = saveArticle(authorId = ownerId)
    assertThatThrownBy { articleService.deleteArticle(DeleteArticleCommand(article.id, strangerId)) }
        .isInstanceOf(ArticlePermissionDeniedException::class.java)
}
// → this single test naturally covers the deny branch of private fun checkAuthor(...).

// ❌ Bad — calling private directly via reflection / relaxed visibility
internal fun checkAuthor(...) { ... }   // do not change production visibility for testing
```

### Repository Test - "Data Access Verification"

| Verification Target | Description |
|---------------------|-------------|
| Complex queries | QueryDSL correctness |
| Pagination | Paging logic |
| JSONB queries | PostgreSQL special operations |

**What NOT to test**: Simple CRUD (provided by JpaRepository), business rules.

```kotlin
saveArticle(title = "B", status = ArticleStatus.PUBLISHED)
saveArticle(title = "A", status = ArticleStatus.DRAFT)
val results = articleRepository.pageByQuery(
    PageArticleQuery(status = ArticleStatus.PUBLISHED, pageable = PageRequest.of(0, 10, Sort.by("title"))),
)
assertThat(results.content).extracting("title").containsExactly("B")
```

### Unit Tests (Non-SpringBootTest)

Pure JUnit tests without Spring context are acceptable for stateless utilities:
- Do NOT extend `SpringBootTestSupport`
- Use `@Nested` for organization
- No database, mocking, or Spring dependency needed

### Test Pyramid

| Layer | Test Ratio | Reason |
|-------|------------|--------|
| Controller | 20% | Verify HTTP I/O only, no logic |
| Service | 60% | Focus on core business logic |
| Repository | 20% | Complex queries only (exclude simple CRUD) |

## 3. Test Data Management

**ALWAYS use helper methods** from SpringBootTestSupport for test data creation:
- **NEVER hardcode test data** (emails, names, UUIDs)
- **NEVER use class-level/global variables for test data** — all data lives in test methods

Helpers are random-seeded (e.g. Faker-based or a small random util) and customized via named parameter + default value.

```kotlin
// ✅ Good - Use helper methods with local scope
class ArticleServiceTest : SpringBootTestSupport() {
    @Test
    fun `create article success`() {
        val title = randomTitle()
        val article = saveArticle(title = title)
        assertThat(article.title).isEqualTo(title)
    }

    private fun saveArticle(title: String = randomTitle(), authorId: UUID = randomUuid()): Article {
        // Helper calls service (not direct entity creation)
    }
}

// ❌ Bad - Hardcoded data and global variables
class ArticleServiceTest : SpringBootTestSupport() {
    private val testTitle = "hello"           // ❌ Global
    @Test
    fun `create`() {
        val a = Article(title = "hardcoded")  // ❌ Hardcoded
    }
}
```

## 4. Test Helper Methods

Helper methods perform **only one task**:

| Prefix | Responsibility | Example |
|--------|----------------|---------|
| `save*()` | Save a single entity (via service) | `saveArticle()`, `saveComment()` |
| `build*()` | Create a single object (no save) | `buildArticle()`, `buildCreateCommand()` |
| `random*()` | Generate random values | `randomTitle()`, `randomEmail()` |
| `mock*()` | Configure mocks | `mockExternalClientSuccess()` |
| `ensure*()` | Verify/set preconditions | `ensureCategoryExists()` |

```kotlin
// ✅ Good - Compose single-responsibility helpers
val article = saveArticle(authorId = userId)
val comment = buildComment(article)
commentRepository.save(comment)

// ❌ Bad - Do not bundle multiple tasks into a single helper
fun saveArticleWithComments(...) = ... // Saves Article + Comment + ... → multiple tasks
```

**Save Helpers** call actual services (production-equivalent behavior). **Build Helpers** call constructors directly, no saving.

**Exception**: immutable relationship entities may be created together when an FK constraint forbids independent existence.

## 5. Controller Tests

Controller tests verify real HTTP requests/responses via MockMvc.

- **Test method name**: `HTTP_METHOD endpoint should result` (e.g., `GET api_v1_articles_id should return 404 when not found`)

```kotlin
class ArticleControllerTest : SpringBootTestSupport() {
    @Nested
    inner class `Get Single Article` {
        @Test
        fun `GET api_v1_articles_id should return an Article`() {
            val article = saveArticle(title = "My Article")
            mockMvc.perform(get("/api/v1/articles/${article.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("My Article"))
        }
    }
}
```

**Cleanup FK order** (child → parent, to avoid FK constraint violations):
```kotlin
@AfterEach
fun cleanup() {
    commentRepository.deleteAll()   // child table first
    articleRepository.deleteAll()   // parent table last
}
```

## 6. Assertions & Verification

- **Use enum references** (not strings) in assertions
- **Verify exception messages** when testing exceptions
- **Always use `requireNotNull()`** instead of `!!` for clearer error messages

```kotlin
// ✅ Good
val article = articleRepository.findBySlug(slug)
requireNotNull(article) { "Article not found with slug: $slug" }
assertThat(article.status).isEqualTo(ArticleStatus.PUBLISHED)

// ❌ Bad
val article = articleRepository.findBySlug(slug)!!         // ❌ Unclear NPE
assertThat(article.status).isEqualTo("PUBLISHED")          // ❌ String instead of enum
```

### ZonedDateTime Comparison

When comparing `ZonedDateTime`, **always use `toInstant()` and `truncatedTo(ChronoUnit.MICROS)`**:
- **Timezone difference**: Local (`Asia/Seoul`) and CI (`UTC`) differ.
- **Precision difference**: Java nanoseconds (9 digits) vs PostgreSQL microseconds (6 digits).

```kotlin
import java.time.temporal.ChronoUnit

// ✅ Good - Instant comparison + microsecond truncate
val baseTime = ZonedDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MICROS)
article.update(publishedAt = baseTime)
val result = repository.findById(article.id)
assertThat(result.publishedAt?.toInstant()).isEqualTo(baseTime.toInstant())

// ❌ Bad - Direct ZonedDateTime comparison (fails in CI due to timezone/precision mismatch)
assertThat(result.publishedAt).isEqualTo(ZonedDateTime.now().minusDays(1))
```

## 7. Debugging & Troubleshooting Tests

- JSONB / query debugging: enable `org.hibernate.SQL: DEBUG`, `org.hibernate.type.descriptor.sql: TRACE`
- Preventing `LazyInitializationException`: confirm that the test method has `@Transactional`, or that the Service loads the associated entities together via `@EntityGraph` / `fetchJoin`

## 8. Test Quality Criteria

Use these 4 criteria when writing or reviewing tests.

### Criterion 1: Core Logic Coverage

Every domain's critical business logic MUST have dedicated tests:

| Domain | Required Test Coverage |
|--------|-----------------------|
| CRUD operations | Create, Read (single + page), Update, Delete |
| Permission checks | Allow/deny each operation per permission level |
| State transitions | Status changes (e.g., DRAFT → PUBLISHED → ARCHIVED) |
| Edge cases | Soft delete re-access, empty results, boundary values |
| Service public method coverage | 1+ success path + 1+ failure path per domain exception for every `public fun` (§2.5 A-1) |

### Criterion 2: No Duplicate Verification

| Allowed | Not Allowed |
|---------|-------------|
| Same logic tested at different layers (Controller/Service/Repository) | Two test groups in the same class testing identical assertions |
| Similar structure across different domains | "Method-axis" AND "Role-axis" tests for the same permission matrix |
| Multiple input values in a single test | Separate tests per input value when the code path is identical |

#### C-1. Avoid Trivial Insert/CRUD Tests (required)

Do **NOT** write the following — they re-verify behavior already guaranteed by JpaRepository / JPA / Spring, so their regression signal is 0:

| Anti-pattern | Reason |
|--------------|--------|
| `repo.save(x); assertThat(repo.findById(x.id)).isPresent` | Re-verifies the JpaRepository contract |
| `entity.field = v; repo.save(entity); assertThat(repo.findById...field).isEqualTo(v)` | Re-verifies JPA dirty checking |
| `repo.deleteById(x.id); assertThat(repo.findById(x.id)).isEmpty` | Re-verifies JpaRepository delete |
| Asserting only a simple 1:1 field match right after `Service.create()` (mapping with no business logic) | Controller response verification is sufficient |
| The behavior of Jackson deserialization / Spring DI / Bean Validation itself | Re-verifies framework behavior |

#### C-2. Areas That MUST Have a Standalone Test (required)

| Area | Test Location | Test Point |
|------|---------------|------------|
| QueryDSL Custom Repository (`Custom*RepositoryImpl`) | Repository test | Composite `where` (And/Or/In/IsNull), JOIN, subqueries, soft-delete filter, sorting, paging. One case per nullable-field branch of the Query |
| JSONB query utilities | Repository test | Type inference, sort direction, null handling |
| `Command.init {}` structural validation | Service test (or Command unit test) | Domain exception on empty list / negative / format violation |
| Domain enum mapping (`*EnumMapper`) | Unit test | Bidirectional mapping for every enum value |
| Permission matrix branches (private `check*`) | Service test (via public input, §2.5 A-2) | Permission level × each operation |
| Transactional side-effect ordering (`@TransactionalEventListener(phase = AFTER_COMMIT)`) | Service / integration test | Published on commit, not published on rollback |
| Soft delete re-access (`deletedAt IS NULL` filter) | Service test | GET after delete → 404, excluded from list response |
| Cross-domain `get*Entity()` call path | Service test (caller domain) | Propagation of the target domain's `*NotFoundException` |

#### C-3. "Regression Signal Strength" Self-Check

Write a test only when all 3 of the following questions are *yes* before adding it:

1. If this test fails, can you assert that *production* behavior is broken?
2. Has the same behavior NOT already been verified by *another layer's test*?
3. Does this test NOT re-verify *framework* (JpaRepository, Spring DI, Jackson) behavior?

→ Write it only if all three are yes. If any is no, choose either *strengthening an existing test* or *not writing it*.

### Criterion 3: Convention Compliance

- [ ] Inherits from `SpringBootTestSupport`
- [ ] Uses `@Nested` for grouping
- [ ] Does NOT use `@DisplayName`
- [ ] Does NOT use `@Transactional` (except Repository tests)
- [ ] Uses random helper methods (no hardcoding)
- [ ] Cleanup in `@AfterEach` with correct FK order
- [ ] Uses import statements (no FQN) — Test #0a
- [ ] Service test covers business branches; Controller test for the same endpoint asserts only routing/contract (Test #0b — §2.5 B-1)
- [ ] Modifying a Service signature? → Searched `*Test.kt` for usages and updated affected tests (Test #0c)
- [ ] Exception-message assertions match the English text (Test #0d)

### Criterion 4: E2E API Coverage

| Check | Description |
|-------|-------------|
| Endpoint coverage | Every `@GetMapping`/`@PostMapping`/`@PutMapping`/`@DeleteMapping` has a corresponding test |
| Success path | At least one test verifies 200 OK with correct response body |
| Permission denial | At least one test verifies 403 for unauthorized access (when auth exists) |
| Error path | Tests for 404 / 400 where applicable |
| Parameter variation | Each endpoint satisfies at least one of each §2.5 B-1 variation |
