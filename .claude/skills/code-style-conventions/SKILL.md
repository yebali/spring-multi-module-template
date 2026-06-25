---
name: code-style-conventions
description: Use when writing or reviewing ANY Kotlin file in this spring-multi-module-template repo (projectA / ProjectB) — covers the cross-cutting code-organization rules that are not tied to a single layer. Covers single-use intermediate inlining (expression body + `.let(::from)`), comment / KDoc policy (WHY-only comments, `//` line comments inside methods, no `/* */` block comments, minimal KDoc), one top-level class/object/interface/enum per .kt file (with allowed exceptions), shared util placement decision tree + the duty to review existing code for duplicates / extraction candidates, no `@Suppress("UNCHECKED_CAST")`, and single-expression trailing-lambda one-liner formatting. Triggers on edits to any `*.kt` file, or mentions of comment / KDoc style, "one class per file", file organization, util placement, shared method extraction, duplicate implementation, single-use intermediate variable / single-use intermediate / expression body. This is the cross-cutting peer of the layer skills (repository / service / controller / test-conventions); apply it alongside whichever layer skill matches the file.
---

# Code Style Conventions (Cross-Cutting)

Single source of truth for the **cross-cutting** code-organization rules — comment policy, file organization, shared util placement — that apply to EVERY layer regardless of whether the file is a Controller, Service, Repository, Entity, or util. This skill is the operational checklist — apply it alongside the matching layer skill (`repository` / `service` / `controller` / `test-conventions`).

> **Rule-number notation**: this skill cites its own rules as `Style #N`.

---

## 1. Comment / KDoc Policy (Style #1)

**Default principle: do not write comments.** A well-named identifier already expresses intent, so write a comment only when there is a non-obvious **WHY** that needs extra explanation.

**Threshold — only when the explanation is truly necessary**: even when a case falls under the "allowed to write" list below, ask again whether the comment is *truly necessary*. If a skilled reader can understand it from the code and identifiers alone, a "nice-to-have" explanation is just clutter, so leave it out — keep a comment only when **its absence would make the reader misunderstand or wrongly modify the code**. If you are hesitating whether to add it, don't.

| Rule | Content |
|---|---|
| WHY-only | No restating the WHAT that an identifier already says. Only non-obvious constraints/invariants, workaround background, temporary implementation + replacement plan |
| Inside methods = `//` | Comments inside a method body use only `//` line comments. `/* */` block comments are forbidden |
| Placement | A WHY comment goes on the line directly above the code it explains, one per semantic unit — do not list one on every code line |
| Minimal KDoc | KDoc only for non-obvious public API contracts. No self-evident label KDoc that an identifier already says |
| Language | WHY comments / KDoc may be in Korean (only user-facing exception messages are in English — see service-conventions §5) |

```kotlin
// ✅ Good — non-obvious WHY with //, on the line directly above the target code
fun assign(memberId: UUID) {
    // displayOrder is unique only within the board scope — global identification needs the (boardId, displayOrder) pair
    val key = SlotKey(boardId, displayOrder)
}

// ❌ Bad — /* */ block + restating WHAT on every code line
fun assign(memberId: UUID) {
    /* create slot key */
    val key = SlotKey(boardId, displayOrder)  // make the key
}

// ❌ Bad — self-evident label KDoc that the identifier already says
/** Article status */
enum class ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }

// ✅ Good — the name is enough, so no KDoc
enum class ArticleStatus { DRAFT, PUBLISHED, ARCHIVED }
```

---

## 2. One Top-Level Type Per File (Style #2)

A single `.kt` file declares **only one** top-level `class` / `object` / `interface` / `enum class`. Inner / nested classes (such as the inner data classes of Command/Result) are unrelated.

**Allowed exceptions** (may be placed together in one file):

| Exception | Description |
|---|---|
| `sealed` hierarchy | `sealed class` / `sealed interface` + its direct subtypes |
| `@Entity` + `@IdClass` | JPA Entity + its composite-key `data class` |
| `@Configuration` + `@ConfigurationProperties` | Spring config class + its bound properties `data class` |
| Cohesive util file | An `object` forming one cohesive API + its directly attached extension functions + auxiliary `enum` |
| Application + main | `@SpringBootApplication` class + the `fun main` in the same file |

```kotlin
// ❌ Bad — two unrelated top-level types in one file
class AdvertiseRunner : ApplicationRunner { ... }
class MenuInitializer : ApplicationRunner { ... }   // → split into a separate file

// ✅ Good — a sealed hierarchy is allowed in one file
sealed interface UploadResult {
    data class Success(val fileId: UUID) : UploadResult
    data class Failure(val reason: String) : UploadResult
}
```

---

## 3. Shared Util Placement & No Duplication (Style #3)

This repository has no cross-module shared module — `projectA` / `ProjectB` are each independent, and both have `com.yebali.template` as their package root. Therefore util placement is decided strictly within the **module-internal scope**.

**(a) New code — preventing re-duplication**: before writing a new util method, first search the locations in the table below, and reuse an existing one if the same functionality already exists.

| Usage scope | Location |
|---|---|
| Shared across multiple domains within one module, pure Kotlin / regardless of Spring dependency | `{module}/.../util/` (e.g. `projectA/.../com/yebali/template/util/`) |
| Used only within a single domain | `{module}/.../{domain}/util/` |

> If the same logic is needed in both modules, since there is currently no shared module, place it in each module. If the sharing frequency grows, consider creating a common module (`common`) — until then, module-internal duplication is allowed.

**(b) Existing code — duplication / commonization review**: periodically review already-implemented code for ① methods duplicated across domains, and ② candidates that should be extracted into a common method. Logic that turns out to be shared across multiple domains within a module is lifted up to `{module}/.../util/` per the table above. This review is always performed by the `convention-audit` skill (auto-triggered) on every run.

---

## 4. Single-Use Intermediate Inlining (Style #4)

Inline intermediate variables that are used only once and have a meaningless name. This is a layer-agnostic cross-cutting rule — it applies identically in Controller / Service / Repository / test.

There are two target patterns:

| Pattern | Inlining method |
|---|---|
| (a) Receives the **result** of a method call and immediately passes it to the next call | Consolidate with expression body + `.let(::from)`, etc. |
| (b) Pre-creates an object that will be a method-call **argument** (Command/Request/Result/Response, etc.) as a `val` and passes it once | Create it directly at the argument position in the call |

**Inline when all three conditions are true (common to both patterns):**
- The variable is referenced exactly once
- The name is meaningless (`result`, `page`, `mapped`, `x`, `cmd`, `request`, `req`)
- Adjacent logging / events / side effects do not depend on that variable

### (a) Result inlining

```kotlin
// ❌ Bad — single-use `page` intermediate variable
fun pageArticles(command: PageArticleCommand): PageArticleResult {
    val page = articleRepository.pageByQuery(command.toQuery())
    return PageArticleResult.from(page)
}

// ✅ Good — expression body + .let(::from)
fun pageArticles(command: PageArticleCommand): PageArticleResult =
    PageArticleResult.from(articleRepository.pageByQuery(command.toQuery()))
```

### (b) Argument inlining

Do not split a Command/Request object that is used only once in a call into a separate `val cmd = ...` / `val request = ...` — create it directly at the argument position in the call. Even if the argument is long and does not fit on one line, keep it at the argument position (this repository has `max_line_length` off, so length itself is not a problem; only do the line break inside the argument).

```kotlin
// ❌ Bad — a single-use, meaningless `cmd` exists only for the save call
val cmd = CreateArticleCommand(title = request.title, body = request.body, authorId = memberId)
val saved = articleService.createArticle(cmd)

// ✅ Good — create it directly inside the call
val saved = articleService.createArticle(
    CreateArticleCommand(title = request.title, body = request.body, authorId = memberId),
)
```

```kotlin
// ❌ Bad — single-use request → command intermediate variable in the Controller
@PostMapping
fun createArticle(@RequestBody request: CreateArticleRequest): CreateArticleResponse {
    val command = request.toCommand()
    return CreateArticleResponse.from(articleService.createArticle(command))
}

// ✅ Good — convert directly at the argument position
@PostMapping
fun createArticle(@RequestBody request: CreateArticleRequest): CreateArticleResponse =
    articleService.createArticle(request.toCommand()).let(CreateArticleResponse::from)
```

### When to keep the variable

- Referenced two or more times (multiple field accesses such as `saved.title`, `saved.id`)
- Adjacent logging / events point to that variable (`logger.info { "saved: ${result.id}" }`)
- The side-effect ordering of a transaction / external call depends on that variable
- The name itself conveys meaning (`oldStatus`, `compensationTarget`)

If any one of these is true, keep the variable — the goal of inlining is "removing noise", not "forcing a single line".

---

## 5. No `@Suppress("UNCHECKED_CAST")` (Style #5)

`@Suppress("UNCHECKED_CAST")` is not used anywhere — not in production / test / shared helpers. Covering an unchecked cast with `@Suppress` means that on cast failure only a `ClassCastException` is left, so you cannot tell what went wrong, and you also lose compiler type safety. This is a layer-agnostic cross-cutting rule.

Solving it with a type-safe expression makes `@Suppress` itself unnecessary:

```kotlin
// ❌ Bad — casting a raw List by covering it with @Suppress
@Suppress("UNCHECKED_CAST")
val articles = query.resultList as List<Article>

// ✅ Good — produce a real List<Article> with filterIsInstance
val articles = query.resultList.filterIsInstance<Article>()

// ❌ Bad — casting a Jackson raw Map
@Suppress("UNCHECKED_CAST")
val map = objectMapper.readValue(body, Map::class.java) as Map<String, Any>

// ✅ Good — preserve the generic type with TypeReference
val map = objectMapper.readValue(body, object : TypeReference<Map<String, Any>>() {})
```

Consider `filterIsInstance<T>()` / Jackson `TypeReference` / `mapNotNull`, etc. first.

---

## 6. Single-Expression Trailing Lambda on One Line (Style #6)

When the trailing lambda body is a **single short expression** and the receiver/arguments also fit on one line — as in `requireNotNull(x) { "msg" }` / `require(c) { "msg" }` / `check(c) { "msg" }` — write the entire call on one line. Do not pointlessly split `{` · body · `}` into three lines.

This repository has no `.editorconfig`, so ktlint `max_line_length` is off — there is no need to break a line for length reasons.

```kotlin
// ❌ Bad — splitting a short message lambda into three lines
targetPath = requireNotNull(request.targetPath) {
    "targetPath must be present for MOVE"
},

// ✅ Good — one line
targetPath = requireNotNull(request.targetPath) { "targetPath must be present for MOVE" },
```

**Exception**: if the argument (receiver expression) is itself multi-line in its body, leave it as is — because the cause of the line break is the argument, not the message lambda.

---

## Pre-Write Checklist

- [ ] Is the comment a non-obvious WHY rather than a restatement of WHAT? (Style #1)
- [ ] Did you avoid `/* */` block comments in the method body? Are comments inside methods `//`? (Style #1)
- [ ] Did you avoid self-evident label KDoc that the identifier already says? (Style #1)
- [ ] Does the new `.kt` file have exactly one top-level type? If two or more, does it fall under an allowed exception? (Style #2)
- [ ] Before writing a new util, did you first search the module's `util/` package? (Style #3)
- [ ] Did you write the short message lambda of `requireNotNull`/`require`/`check` on one line instead of splitting it into three lines? (Style #6)
- [ ] Did you inline a single-use, meaningless result intermediate variable (`result` / `page` / `mapped`) with expression body + `.let(::from)`? (Style #4)
- [ ] Did you inline a Command/Request/Result object passed to a call only once (`val cmd = ...` / `val request = ...`) so it is created directly at the call's argument position? (Style #4)
- [ ] Did you avoid `@Suppress("UNCHECKED_CAST")`? Did you solve the unchecked cast with a type-safe expression such as `filterIsInstance` / `TypeReference`? (Style #5)
