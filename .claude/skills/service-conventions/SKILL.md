---
name: service-conventions
description: Use when writing or reviewing Kotlin Spring `@Service` classes and `service/command/*Command.kt` / `*Result.kt` DTOs in this spring-multi-module-template repo (projectA / ProjectB). Covers extend `Logger` (never `LoggerFactory`), English exception messages, `*Entity`/`*Entities` suffix for entity-returning methods, no FQN, read callers before modifying, CRUD ordering, no raw `List<*Result>` (wrap), `@Transactional` method-level only (`readOnly=true` for reads, none for stateless methods, class-level forbidden), no domain-boundary-crossing Repository injection (go through the other Service via `getXxxEntity()`), Command/Result inner DTO (duplication allowed), no `require`/`check`/`error` in Service & Command (throw named exception subclass), no generic `IllegalArgumentException`/`IllegalStateException`, no Entity/@Embeddable/entity-inner enum as Command/Result field types, inline single-use intermediates, `check*`/`verify*`/`is*` validation prefixes, private method ordering (validation → helper → conversion), Service-layer enum + `EnumMapper` separation, structural validation in `Command.init{}` vs business validation in Service `check*()`. Triggers on edits to `**/service/**.kt`, `**/service/command/**.kt`, `**/enumerate/**.kt`, `*Service.kt` / `*Command.kt` / `*Result.kt` / `*EnumMapper.kt`; mention of `@Transactional` / `Command` / `Result` / `getEntity` / `EnumMapper` / CRUD ordering / `check*` validation; or any new Service method, Command/Result DTO, business validation, or enum.
---

# Service Layer Conventions

Single source of truth for `@Service` + Command/Result DTO conventions. Apply BEFORE writing service code.

> **Rule-number notation**: this skill cites its own rules as `Svc #N`.

---

## 1. Hard Rules (never violate when writing Service / Command code)

| # | Rule | Where it applies |
|---|------|-----------------|
| Svc #1 | Extend `com.yebali.template.util.Logger`. Never use `LoggerFactory` directly | every Service / Component |
| Svc #2 | Thrown exception `message` fields MUST be English. Korean only in KDoc | Service throw sites |
| Svc #3 | Entity-returning methods exposed for use by another domain/Service MUST end with `Entity` (single) or `Entities` (collection) | Service public method |
| Svc #4 | Never use FQN — always use `import` statements | every Kotlin source file |
| Svc #5 | BEFORE modifying any Service/Entity, read its callers, related tests, and dependent code | every change |
| Svc #6 | Public methods follow CRUD ordering: Create → Read → Update → Delete → Other | Service / Controller / Repository |
| Svc #7 | Never return raw `List<*Result>` / `List<*Response>` — always wrap in a class | Service return type |
| Svc #8 | `@Transactional` is method-level only. Class-level forbidden. `readOnly = true` for reads, plain `@Transactional` for writes, NO annotation for stateless methods (e.g. pure parsing) | Service method |
| Svc #9 | No long-running external calls (HTTP / external process exec / large file IO) inside `@Transactional`. Split DB writes (each its own `@Transactional` so the row lock releases) from the external call, then compensate failures explicitly | Service method that orchestrates an external system |
| Svc #10 | Never inject another domain's Repository. Call that domain's Service instead | Service constructor |
| Svc #11 | Command/Result DTOs MUST be declared as inner data classes. Same shape across files is fine — duplicate, don't extract | Command/Result class body |
| Svc #12 | `require()`, `check()`, `checkNotNull()`, `error()` are **prohibited** in Service and Command. Throw a named domain exception subclass. **Exception**: utility classes that validate programmer preconditions may keep `require()` | `*Service.kt`, `*Command.kt` |
| Svc #13 | Never throw `IllegalArgumentException`, `IllegalStateException`, `UnsupportedOperationException` directly for domain errors | all production code |
| Svc #14 | `@Entity` / `@Embeddable` / entity-inner enums MUST NOT be used as Command / Result field types. Entity import allowed ONLY as `companion object { fun from(entity: Xxx) }` factory parameter | `*Command.kt`, `*Result.kt` |
| Svc #15 | Single-use intermediates with meaningless names (`val result = ...; return X.from(result)`) → expression body + `.let(::from)` (code-style-conventions §4) | Service / Result factory |

---

## 2. Service Method Signatures

| Return type | Use case | Example |
|-------------|----------|---------|
| `*Result` | Single item exposed to Controller | `getArticle(cmd): GetArticleResult` |
| `List*Result` (wrapper) | Multiple items exposed to Controller | `listComments(cmd): ListCommentsResult` |
| `Page*Result` | Paginated result exposed to Controller | `pageArticles(cmd): PageArticleResult` |
| `Entity` (with `Entity`/`Entities` suffix) | **Internal** Service-to-Service only. Never call from Controller (Svc #3) | `getArticleEntity(id): Article` |
| `Unit` | Methods with side effects only | `deleteArticle(cmd)` |

**Raw `List<*Result>` returns are forbidden (Svc #7)** — always wrap in a class. The wrapper declares its **own** nested item DTO — it does NOT reuse another `*Result` class as the element type. Even when the shape is identical, each `*Result` redundantly declares its own inner DTO (Svc #11).

```kotlin
// ✅ Good — wrapper Result with its own nested DTO
data class ListCommentsResult(val comments: List<CommentDto>) {
    data class CommentDto(val id: UUID, val body: String, val authorName: String)
    companion object {
        fun from(comments: List<Comment>) = ListCommentsResult(comments.map { ... })
    }
}
```

---

## 3. Command/Result Pattern

```
Request.toCommand() → Command → Service → Result.from(Entity) → Response.from(Result)
```

| Conversion | Method | Location |
|------------|--------|----------|
| Request → Command | `request.toCommand()` | `*Request` (controller layer) |
| Command → Entity | `command.toEntity()` | `*Command` |
| Entity → Result | `Result.from(entity)` | `*Result` companion |

**File layout**: `service/command/` at the root, one class per file, named `{Action}{Entity}Command.kt` / `{Action}{Entity}Result.kt`. No subfolders, no multiple classes per file.

### 3.1 No Entity types as Command field types (Svc #14)

`@Entity` and `@Embeddable` types may NOT appear as Command field types. Use an inner DTO (duplication allowed). Entity import is permitted ONLY as a `companion object { fun from(entity: Xxx) }` factory parameter.

```kotlin
// ❌ Bad — @Embeddable leaking into Command
data class CreateArticleCommand(
    val author: Author,              // ← Author is @Embeddable
    val status: Article.Status,      // ← entity-inner enum
)

// ✅ Good — inner DTO + service-layer enum
data class CreateArticleCommand(
    val author: AuthorInput,
    val status: ArticleStatus,       // service-layer enum
) {
    data class AuthorInput(val name: String, val email: String?)
}
```

---

## 4. Validation Method Naming

### 4.1 Prefix by signature

| Prefix | Behavior | Return |
|--------|----------|--------|
| `check*` | Throws a domain exception on failure | `Unit` |
| `verify*` | Returns the validation result as an object (no throw) | `Verify*Result` |
| `is*` / `can*` / `has*` | Boolean predicate | `Boolean` |

**Prohibited**:
- The `validate*` prefix is prohibited. Its validation meaning overlaps with `check*` (throw) / `verify*` (return), making the signature ambiguous
- `check*` returning `Boolean` (name and behavior mismatch)

### 4.2 Location by validation kind

| Kind | Location | Example |
|------|----------|---------|
| Structural (required, length, format, empty collection) | `Command.init {}` | `if (tagIds.isEmpty()) throw EmptyTagException()` |
| Business (entity existence, permissions, state) | Service `check*()` private method | `private fun checkArticleExists(ids)` |
| External-response validation (e.g., token validity) | Service `verify*()` public method | `fun verifyResetToken(token): VerifyResetTokenResult` |

```kotlin
// ✅ Good — throw form (check*)
private fun checkPublishable(article: Article) {
    if (article.status == ArticleStatus.ARCHIVED) throw ArticleArchivedException(article.id)
}

// ✅ Good — Boolean predicate
fun canEdit(memberId: UUID, articleId: UUID): Boolean = ...

// ❌ Bad — `validate*` prefix is forbidden
private fun validatePublishable(article: Article) { ... }
```

---

## 5. Exception Policy

| Rule | Description |
|------|-------------|
| Domain isolation | Each domain's Service throws only its own domain's exceptions. The `articles` Service must NOT throw `CommentNotFoundException` |
| Named subclass | Every throw uses a domain-specific subclass (e.g., `ArticleNotFoundException` extends a project `EntityNotFoundException` base) |
| English message | User-visible exception `message` fields MUST be English (Svc #2). Korean stays in KDoc only |

```kotlin
// ✅ Good
class ArticleNotFoundException(id: UUID) : EntityNotFoundException(
    message = "Article not found: $id",
)

// ❌ Bad — generic exception thrown directly
throw IllegalArgumentException("invalid id")
require(id != null) { "id required" }
```

> This template does not yet have a common exception hierarchy / error response system. When writing the first domain exception, define a module-shared base exception (e.g., `EntityNotFoundException`) together with a `@RestControllerAdvice` handler, and introduce an error-code scheme for code identification only when one is actually needed.

---

## 6. Domain-Boundary Access

Access that crosses a domain (or module) boundary goes through the other domain's **Service** — never inject its Repository directly (Svc #10).

| Dependency | Allowed |
|------------|---------|
| A Service → B Service | ✅ |
| A Service → B Repository | ❌ (Svc #10) |
| A Service throws B's exception | ❌ (§5 domain isolation) |
| A Service → B Entity (via `B.getXxxEntity()`) | ⚠️ internal-only methods |

`getXxxEntity()` rules:
- Named with the `get*Entity()` / `list*Entities()` suffix to mark internal intent (Svc #3)
- Never invoked from the Controller layer — Service-to-Service only
- Throws the owning domain's own exception (e.g., `XxxNotFoundException`)
- Annotated with `@Transactional(readOnly = true)`, or participates in the caller's transaction

```kotlin
// ✅ Good — articles domain exposes getArticleEntity() for cross-domain use
@Service
class ArticleService(private val articleRepository: ArticleRepository) : Logger() {
    @Transactional(readOnly = true)
    fun getArticleEntity(articleId: UUID): Article =
        articleRepository.findByIdAndDeletedAtIsNull(articleId)
            ?: throw ArticleNotFoundException(articleId)
}

// ✅ Good — comments domain calls getArticleEntity() instead of injecting ArticleRepository
@Service
class CommentService(
    private val articleService: ArticleService,
    private val commentRepository: CommentRepository,
) : Logger() {
    @Transactional
    fun addComment(command: AddCommentCommand) {
        val article = articleService.getArticleEntity(command.articleId)
        commentRepository.save(Comment(article = article, body = command.body))
    }
}
```

### 6.1 Fetch strategy

When writing a cross-domain `getXxxEntity` / `listXxxEntities`, choose the fetch strategy based on the caller's access pattern:

| Caller pattern | Strategy |
|---|---|
| Accesses only the entity's basic fields | Derived query (`findBy*`) |
| Accesses related-entity fields (within the transaction, non-loop) | fetchJoin (Custom Repository + QueryDSL `fetchJoin()`) |
| Accesses outside the transaction boundary / inside a loop | **MUST** fetchJoin (avoid LazyInitializationException / N+1) |
| Needs only the related entity's FK | Projection (`select(entity.relation.id)`) |

```kotlin
// ❌ Bad — basic getXxxEntity call followed by lazy traversal (fails outside the transaction boundary)
val article = articleService.getArticleEntity(id)
article.comments.size  // LAZY
```

---

## 7. Method Order

### 7.1 Public methods — CRUD ordering (Svc #6)

| Order | Category | Service prefix |
|-------|----------|----------------|
| 1 | Create | `create*`, `add*`, `save*`, `copy*` |
| 2 | Read | `get*`, `list*`, `page*`, `find*`, `exists*`, `count*` |
| 3 | Update | `update*`, `upsert*`, `move*`, `rename*` |
| 4 | Delete | `delete*`, `remove*` |
| 5 | Other | `check*`, `can*`, action methods |

When a Service hosts multiple sub-domains, apply CRUD ordering within each sub-domain group. **Do NOT use `// === Create ===` style section comments.**

### 7.2 Private methods — fixed sub-order

Private methods follow ALL public methods, in this order:

1. **Validation / Check** — `check*()`, `assert*()` etc.
2. **Helper / Business** — internal computation, decomposition
3. **Conversion** — entity ↔ DTO mapping helpers

`companion object` is **always last** in the file.

---

## 8. `@Transactional` Details (Svc #8)

```kotlin
// ✅ Good
@Service
class ArticleService(...) : Logger() {
    @Transactional(readOnly = true)
    fun getArticle(cmd): GetArticleResult = ...

    @Transactional
    fun createArticle(cmd): CreateArticleResult = ...

    fun parseSlug(raw: String): Slug = ...   // No DB access — no annotation
}

// ❌ Bad — class-level declaration (silently propagates the wrong attribute to new methods)
@Service
@Transactional(readOnly = true)
class ArticleService(...) { ... }
```

### 8.1 No long-running external calls inside `@Transactional` (Svc #9)

**Prohibited**: external calls with variable/long response times (external HTTP, external process execution, large file IO, etc.) inside a `@Transactional` method. Holding a row lock for the duration of the transaction so that the external system cannot touch the same row leads to silent deadlock / connection pool exhaustion.

**Exceptions (allowed)**: short status-check HTTP (≤ ~100ms), audit log emission just before commit, and external calls inside `@TransactionalEventListener(phase = AFTER_COMMIT)`.

**Alternative pattern — transaction splitting + explicit compensation**:

```kotlin
// ❌ Bad — the external HTTP call is inside the transaction, so the row lock is held for the duration of the call
@Transactional
fun process(command): Result {
    val entity = entityService.create(command)         // INSERT (holds lock, before commit)
    val response = externalClient.call(entity.id)      // HTTP — external system cannot touch the same row
    return Result.from(entity, response)
}

// ✅ Good — each DB step commits immediately via its own @Transactional, the external call is outside the transaction
fun process(command): Result {
    val entity = entityService.create(command)         // commits via its own @Transactional (lock released)
    val response = try {
        externalClient.call(entity.id)                 // outside the transaction
    } catch (e: Throwable) {
        safeDelete(entity.id)                          // explicit compensation
        throw e
    }
    return Result.from(entity, response)
}

private fun safeDelete(id: UUID) {
    runCatching { entityService.delete(DeleteCommand(id)) }
        .onFailure { e -> logger.warn(e) { "Failed to compensate: id=$id" } }
}
```

**When compensating**: wrap each compensation in `runCatching` for best-effort. Run compensations in reverse order of creation. Compensation methods reuse the domain's existing `delete*` / `unlink*` methods as-is (preserving Svc #10).

---

## 9. Inline Single-Use Intermediates (Svc #15)

Inlining single-use intermediates is a layer-agnostic cross-cutting code-style rule. The inline conditions / criteria for keeping a variable / examples are authoritatively defined in `code-style-conventions` §4.

---

## 10. Logging (Svc #1)

Extend `com.yebali.template.util.Logger`. Never use `LoggerFactory` directly.

```kotlin
class ArticleService(...) : Logger() {
    fun createArticle(cmd): CreateArticleResult {
        logger.info { "Creating article: ${cmd.title}" }
        ...
    }
}
```

**Sole exception**: a `@RestControllerAdvice` global exception handler may use a file-level `KotlinLogging.logger {}` when its parent class already exposes a `logger` property that would clash.

---

## 11. Enum Layer Separation

Domain enums are split into two layers — the **Entity inner enum** (JPA persistence) and the **Service-layer enum** (`{domain}/enumerate/`, for Command/Result/business logic). Conversion between the two has a single conversion point: a bidirectional `EnumMapper` (`{domain}/util/{Domain}EnumMapper.kt`).

```kotlin
// Service-layer enum (enumerate/ArticleStatus.kt)
enum class ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }

// EnumMapper — bidirectional conversion
object ArticleEnumMapper {
    fun ArticleStatus.toEntityStatus(): Article.Status = when (this) {
        ArticleStatus.DRAFT -> Article.Status.DRAFT
        ArticleStatus.PUBLISHED -> Article.Status.PUBLISHED
        ArticleStatus.ARCHIVED -> Article.Status.ARCHIVED
    }
}
```

**Never use the Entity inner enum (`Article.Status`) directly in Command / Result / Request / Response** — map through the Service-layer enum.

> If inter-service messaging (e.g., an event bus) is introduced later and enums are transmitted across module boundaries, expand to a 3-Layer model (Wire / Service / Entity) by adding one more layer of wire-format-only enums. Until then, the 2-Layer model above is sufficient.

---

## 12. Service vs Component distinction

`@Service` is the signal of a **transactional domain use-case entry point**. Every other Spring bean / pure function uses a different annotation and suffix based on its role.

| Category | Annotation | suffix | Criterion |
|---|---|---|---|
| Domain use-case (transaction boundary) | `@Service` | `*Service` | Transactional use-case entry point for a domain entity |
| Spring lifecycle/event glue | `@Component` | `*Listener` / `*Handler` / `*Scheduler` / `*Filter` / `*Initializer` / `*Runner` | Receives framework callbacks (events, schedules, boot) |
| External system/library wrapper | `@Component` | `*Template` | Wraps DB features, OS APIs, or external libs |
| External service HTTP client | `@Component` | `*Client` | Outbound client calling another service's HTTP endpoint |
| Cross-cutting stateless collaborator component | `@Component` | `*Resolver` / `*Strategy` / `*Mapper` / `*Helper` / `*Extractor` | Stateless collaborator injected and used by a Service |
| pure function (no Spring) | (none) | — | Top-level / extension function in the `util/` package |

**Key criterion**: apply `@Service` only to transactional domain use-case entry points. Even if a Strategy / Resolver / Mapper contains domain logic, it is a collaborator injected and used by a Service, so it keeps `@Component` + a role suffix — it is NOT promoted to `@Service`.

```kotlin
// ✅ Good — a domain use case is a *Service
@Service
class ArticleService(private val articleRepository: ArticleRepository) : Logger() {
    @Transactional(readOnly = true)
    fun getArticleEntity(id: UUID): Article = ...
}

// ❌ Bad — naming an infra wrapper as *Service confuses it with a domain service
@Service
class AdvisoryLockService(...) { ... }   // → AdvisoryLockTemplate + @Component
```

---

## 13. Pre-Write Checklist

- [ ] FQN used anywhere? → Replace with `import` (Svc #4)
- [ ] About to modify a Service/Entity? → Read its callers, related tests, and dependent code first (Svc #5)
- [ ] Injected another domain's Repository? → Route through that domain's Service (Svc #10)
- [ ] Used `require` / `check` / `checkNotNull` / `error` in a Service or Command? → Replace with a named exception subclass (Svc #12)
- [ ] Threw `IllegalArgumentException` / `IllegalStateException` for a domain error? → Replace with a domain exception subclass (Svc #13)
- [ ] Service throws another domain's exception? → Replace with one from your own domain (§5)
- [ ] Method exposes an Entity to the Controller? → Convert to `Result`, or rename with `*Entity` / `*Entities` suffix to mark internal-only (Svc #3)
- [ ] Returned raw `List<*Result>`? → Wrap in a class (Svc #7)
- [ ] Command field uses `@Entity` / `@Embeddable` / entity-inner enum type? → Replace with inner DTO + service-layer enum (§3.1)
- [ ] `@Transactional` at the class level? → Move it to each method (Svc #8)
- [ ] Read-only operation missing `readOnly = true`? Stateless method (no DB) carries `@Transactional`? → Remove (Svc #8)
- [ ] External HTTP / process exec / large IO call inside a `@Transactional` method? → Split DB work from the external call + per-step own `@Transactional` commit + explicit compensation on failure (Svc #9, §8.1)
- [ ] Placed a Command DTO in a separate file? → Move it inside the Command class as an inner data class (Svc #11)
- [ ] Validation method signature matches its prefix — throw=Unit → `check*`, return-result → `verify*`, Boolean → `is*`/`can*`/`has*` (`validate*` prohibited)
- [ ] Business validation (DB lookups) inside `Command.init {}`? → Move to a Service `check*()` method
- [ ] Single-use `result` / `page` intermediate variable? → Expression body + `.let(::from)` (Svc #15)
- [ ] Public methods follow CRUD order (Create → Read → Update → Delete → Other)? (Svc #6)
- [ ] Private methods follow validation → helper → conversion order? `companion object` last? (§7.2)
- [ ] Used `LoggerFactory`? → Replace with `Logger()` extension (Svc #1)
- [ ] Exception `message` in Korean? → Translate to English; move Korean to KDoc (Svc #2)
- [ ] New enum needed? → Service-layer enum in `enumerate/` + `EnumMapper`; never expose Entity inner enum to Command/Result (§11)
