---
name: controller-conventions
description: Use when writing or reviewing Kotlin Spring `@RestController` classes and `controller/rest/*Request.kt` / `*Response.kt` DTOs in this spring-multi-module-template repo (projectA / ProjectB). Covers no-FQN, CRUD HTTP mapping order (`@PostMapping`→`@GetMapping`→`@PutMapping`→`@DeleteMapping`→action), Request/Response inner DTOs (duplication allowed), `@ResponseStatus` prohibited (default 200 OK), Bean Validation prohibited (`@Valid`/`@NotBlank`/`@NotNull`/`@Size`/etc; no `jakarta.validation.*`), `required = false` prohibited (use Kotlin nullable + default values), Entity import prohibited in `controller/rest/*`, expression body + `.let(::from)`, no filler suffix (`Payload`/`Dto`/`Data`/`Info`), self-referential DTO OpenAPI Schema, pagination response wrapping, `/api/v1/` versioning prefix, and RESTful URL design. Triggers on edits to `**/controller/**.kt`, `*Controller.kt` / `*Request.kt` / `*Response.kt`; mention of `@RestController` / `@RequestMapping` / `@RequestParam` / `@PathVariable` / `@ResponseStatus` / `@Valid` / Bean Validation / pagination / OpenAPI Schema; or any new endpoint, REST DTO, or pagination response.
---

# Controller Layer Conventions

Single source of truth for `@RestController` + Request/Response DTO conventions. Apply BEFORE writing controller code.

> **Rule-number notation**: this skill cites its own rules as `Ctrl #N`.

---

## 1. Hard Rules (never violate when writing Controller / Request / Response code)

| # | Rule | Where it applies |
|---|------|-----------------|
| Ctrl #1 | Never use FQN — always use `import` statements | every Kotlin source file |
| Ctrl #2 | Public methods follow CRUD ordering: Create → Read → Update → Delete → Other (HTTP-method based) | Controller method order |
| Ctrl #3 | Request/Response DTOs MUST be inner data classes. Same shape across files is fine — duplicate, don't extract | `*Request.kt`, `*Response.kt` |
| Ctrl #4 | `@ResponseStatus` is forbidden. Every endpoint uses default 200 OK | Controller method |
| Ctrl #5 | All Bean Validation annotations are forbidden — `@Valid`, `@NotBlank`, `@NotNull`, `@NotEmpty`, `@Size`, `@Positive`, `@Max`, `@Min`, `@Pattern`, `@Email`, custom `@Constraint`. Do NOT import `jakarta.validation.*` | Controller / Request DTO |
| Ctrl #6 | `@RequestParam(required = false)` / `@RequestHeader(required = false)` / `@RequestBody(required = false)` are forbidden. Use Kotlin nullable types (`String?`) or default values (`= PageRequest()`) | Controller signature |
| Ctrl #7 | Files under `controller/rest/*` MUST NOT import Entity types. `@Entity` / `@Embeddable` / entity-inner enums are forbidden as Request/Response field types. Response receives `Result` and converts via `from(result)` | `controller/rest/*` |
| Ctrl #8 | Single-use intermediates (`val result = service.x(...); return Resp.from(result)`) → expression body + `.let(::from)` (code-style-conventions §4) | Controller method |

---

## 2. Endpoint Signatures

```kotlin
// ✅ Good
@RestController
@RequestMapping("/api/v1/articles")
class ArticleController(private val articleService: ArticleService) {

    @PostMapping
    fun createArticle(@RequestBody request: CreateArticleRequest): CreateArticleResponse =
        articleService.createArticle(request.toCommand())
            .let(CreateArticleResponse::from)

    @GetMapping("/{id}")
    fun getArticle(@PathVariable id: UUID): GetArticleResponse =
        articleService.getArticle(GetArticleCommand(id))
            .let(GetArticleResponse::from)

    @DeleteMapping("/{id}")
    fun deleteArticle(@PathVariable id: UUID) {
        articleService.deleteArticle(DeleteArticleCommand(id))
    }
}
```

**API versioning** — every REST endpoint is prefixed with `/api/v1/...`.

**Authenticated principal** — if/when authentication is added, inject the principal as a typed **method parameter** (e.g. `@AuthenticationPrincipal`), not by manually parsing a `@RequestHeader`. The controller stays free of header-parsing code.

**Optional parameters**:
```kotlin
// ✅ Good — Kotlin nullable + default value
@GetMapping
fun pageArticles(
    @RequestParam title: String?,
    @RequestBody pageRequest: PageRequest = PageRequest(),
): PageArticleResponse = ...

// ❌ Bad — required = false
@GetMapping
fun pageArticles(
    @RequestParam(required = false) title: String?,
    @RequestBody(required = false) pageRequest: PageRequest?,
)
```

---

## 3. CRUD Mapping Order

Public methods follow **HTTP-method-based CRUD order**:

| Order | Category | HTTP |
|-------|----------|------|
| 1 | Create | `@PostMapping` |
| 2 | Read | `@GetMapping` (POST search — when a body is required — also belongs to Read) |
| 3 | Update | `@PutMapping` |
| 4 | Delete | `@DeleteMapping` |
| 5 | Other | action endpoints |

When a controller hosts multiple sub-domains, apply CRUD ordering within each sub-domain group. **Do NOT use `// === Create ===` style section comments.**

---

## 4. Request/Response DTOs

### 4.1 Inner data class + duplication allowed (Ctrl #3)

```kotlin
// ✅ Good — inner DTO inside the Request
data class CreateArticleRequest(
    val title: String,
    val tags: List<Tag>,
) {
    data class Tag(val name: String, val color: String?)

    fun toCommand() = CreateArticleCommand(
        title = title,
        tags = tags.map { CreateArticleCommand.TagInput(it.name, it.color) },
    )
}

// ❌ Bad — extracting a shared DTO into its own top-level file
data class TagPayload(...)   // ← separate file
```

**Each Request/Response uses only its own inner DTO — reusing another Request/Response is forbidden**: even within the same layer, a `*Response` must not use another `*Response` (or its inner DTO) as a field or element type. Even if the shape is identical, declare a duplicate inner DTO of its own — because each endpoint's response contract must evolve independently.

```kotlin
// ❌ Bad — ListXxxResponse reuses GetXxxResponse as an element
data class ListArticleResponse(val articles: List<GetArticleResponse>)

// ✅ Good — ListXxxResponse declares its own inner DTO (even though it has the same shape as GetArticleResponse)
data class ListArticleResponse(val articles: List<Article>) {
    data class Article(val id: UUID, /* ... */)
}
```

### 4.2 No filler suffixes

Don't append meaningless suffixes like `Payload`, `Dto`, `Data`, `Info`, `Object`, `Vo`, `Wrapper`. The outer class (`*Request` / `*Response`) already conveys the role; inner classes carry only domain meaning.

| Prefer | Avoid |
|--------|-------|
| `Tag` (inner of Request) | `TagPayload`, `TagDto` |
| `Author` (inner of Response) | `AuthorInfo`, `AuthorData` |

**Exception**: when a name collision is essential within the same file. Try package separation or Kotlin rename import (`as`) first; only fall back to a suffix when those don't work.

### 4.3 No Entity imports (Ctrl #7)

```kotlin
// ❌ Bad — entity / @Embeddable leaks into the HTTP contract
data class GetArticleResponse(
    val author: Author,                  // ← Author is @Embeddable
)

// ✅ Good — Response is built from a Result only (never references Entity)
data class GetArticleResponse(
    val id: UUID,
    val author: AuthorDto,
) {
    data class AuthorDto(val name: String, val email: String?)
    companion object {
        fun from(result: GetArticleResult) = GetArticleResponse(
            id = result.id,
            author = AuthorDto(result.author.name, result.author.email),
        )
    }
}
```

**Entity-inner enums follow the same rule** — do not use an entity-inner enum (`Article.Status`) as a Request/Response field type. Use the service-layer enum instead.

### 4.4 Self-referential DTO — explicit OpenAPI schema

```kotlin
// ✅ Good — recursive collection needs @field:ArraySchema
data class CategoryTreeDto(
    val id: UUID,
    val name: String,
    @field:ArraySchema(schema = Schema(implementation = CategoryTreeDto::class))
    val children: List<CategoryTreeDto>,
)
```

---

## 5. Response Conversion — Expression Body + `.let(::from)` (Ctrl #8)

The general rule for inlining single-use intermediate variables is canonically owned by `code-style-conventions` §4. Its application in the Controller is to convert the result of `service.x(request.toCommand(...))` directly via an expression body + `.let(*Response::from)` (see the §2 Endpoint Signatures example).

---

## 6. Pagination Response

```kotlin
@GetMapping
fun pageArticles(
    @RequestBody pageRequest: PageRequest = PageRequest(),
): PaginationResponse<ArticleSummaryResponse> =
    articleService.pageArticles(pageRequest.toCommand())
        .let { PaginationResponse.from(it.page) { result -> ArticleSummaryResponse.from(result) } }
```

Do not return `Page<T>` directly for a page response; wrap it in a consistent response wrapper (a `PaginationResponse<T>`-style type holding content / totalElements / totalPages / size / empty). The wrapper type does not exist in this project yet, so define a module-shared wrapper when writing the first pagination endpoint.

---

## 7. Where to Put Validation (alternative to forbidden Bean Validation)

| Kind | Location |
|------|----------|
| Structural (required, length, format) | `Command.init {}` (Service layer) |
| Business (entity existence, permissions) | Service `check*()` |

The Controller carries **no validation code**. After converting Request → Command, the Service is responsible for validation.

---

## 8. File Layout

| Item | Rule |
|------|------|
| Location | `controller/` (Controllers), `controller/rest/` (Request/Response) — at the root, no subfolders |
| Per file | One class per file (ktlint single-class rule) |
| Naming | `{Action}{Entity}Request.kt` / `{Action}{Entity}Response.kt` |

---

## 9. RESTful API design (as far as possible)

Endpoints follow REST principles **as far as possible** — resources (nouns) are expressed in the URL, actions (verbs) via the HTTP method.

| Principle | Rule | ✅ | ❌ |
|------|------|----|----|
| Resource = plural noun | Do not put a verb in the URL path | `/api/v1/articles` | `/api/v1/getArticle`, `/api/v1/article/create` |
| Action = HTTP method | POST=create, GET=read, PUT=update, DELETE=delete | `POST /api/v1/articles` | `POST /api/v1/articles/create` |
| Single item = `/{id}` | Distinguish collection vs single item by path | `GET /api/v1/articles/{id}` | `GET /api/v1/articles/get?id=...` |
| Hierarchical resource = nested path | Put ownership relations under the parent resource (2 levels max recommended) | `GET /api/v1/articles/{articleId}/comments` | `GET /api/v1/comments?articleId=...` when ownership is clear |
| Filter / sort / paging = query string | List parameters go in the query string. But if a search body is required, POST search is also in the Read category (§3) | `GET /api/v1/articles?status=PUBLISHED` | a separate verb path for reading |

**Non-CRUD actions (state transitions / commands)**: actions that cannot be expressed as pure CRUD go in an **action endpoint under the resource** — use a verb, but always subordinate it to the resource (the "Other" category of the §3 CRUD table).

```
POST /api/v1/articles/{id}/publish      ← publish (state transition)
POST /api/v1/articles/{id}/archive      ← archive (state transition)
```

**No PATCH** — express partial updates with `PUT` (resource replacement) too. The absence of PATCH from the §3 CRUD mapping table is intentional.

**Key decision question**: if a verb is about to appear in a new endpoint URL, stop — (1) Is it CRUD expressible via an HTTP method? → use the method. (2) Is it a non-CRUD action subordinate to a resource? → `/{resource}/{id}/{action}`. A verb must never appear at the top level of a URL.

---

## 10. Non-HTTP Inbound Adapters

Non-HTTP inbound handlers (message listeners, etc.) go in a directory that is a sibling of `controller/` (e.g. `<domain>/messaging/`). Do not put them in the Service directory — the Service holds business logic, while an inbound adapter is a thin adapter that only converts payload → Command and then delegates to the Service. Do not put business logic in the adapter.

---

## 11. SSE Endpoint OpenAPI Annotation

A controller method that exposes a Server-Sent Events stream (`produces = MediaType.TEXT_EVENT_STREAM_VALUE` + returns `SseEmitter`) MUST declare its response schema with swagger `@ApiResponse`. springdoc cannot derive the event payload from `SseEmitter` alone, so without it frontend codegen has nothing to map.

```kotlin
@ApiResponse(
    responseCode = "200",
    description = "SSE event stream — <event order + termination summary>",
    content = [
        Content(
            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
            schema = Schema(/* see table below */),
        ),
    ],
)
@PostMapping(produces = [MediaType.TEXT_EVENT_STREAM_VALUE], ...)
fun streamXxx(...): SseEmitter { ... }
```

Pick `schema` by the actual wire shape:

| Wire shape | `schema` |
|---|---|
| Single payload type for every event | `Schema(implementation = XxxStreamEvent::class)` |
| Multiple payload types, one variant per event (sealed interface + N data classes) | `Schema(oneOf = [Variant1::class, Variant2::class, ...])` |

**Rules**:
- Do NOT add `@Operation(summary/description)` — the method name + tag + `@ApiResponse.description` already convey the meaning (code-style-conventions §1).
- The wire DTO lives in `controller/rest/` and stays separate from service-internal domain events. Only wire variants appear in `@Schema(oneOf = [...])` (Ctrl #7).

---

## 12. Pre-Write Checklist

- [ ] FQN used anywhere? → Replace with `import` (Ctrl #1)
- [ ] About to modify a Controller? → Read its callers (frontend, integration tests) first
- [ ] Added `@ResponseStatus`? → Remove (default 200 OK) (Ctrl #4)
- [ ] Imported `jakarta.validation.*`? → Remove. Move validation into Command/Service (Ctrl #5)
- [ ] Used `@Valid`, `@NotBlank`, etc.? → Remove (Ctrl #5)
- [ ] Used `@RequestParam(required = false)` / `@RequestBody(required = false)`? → Use a nullable type or default value (Ctrl #6)
- [ ] Request/Response imports `@Entity` / `@Embeddable` / entity-inner enum? → Move to inner DTO (Ctrl #7)
- [ ] Extracted a DTO into a top-level file? → Move it inside the class as an inner data class — duplication is fine (Ctrl #3)
- [ ] Inner DTO named `*Payload` / `*Dto` / `*Info`? → Rename to a domain-meaningful name (§4.2)
- [ ] Self-referential collection missing `@field:ArraySchema(schema = Schema(implementation = ...))`?
- [ ] Pattern `val result = service.x(...); return Resp.from(result)`? → Expression body + `.let(::from)` (Ctrl #8)
- [ ] Returning `Page<T>` directly? → Wrap in a `PaginationResponse`-style wrapper (§6)
- [ ] Public methods follow CRUD HTTP-method order (Post → Get → Put → Delete → action)? (Ctrl #2)
- [ ] Missing `/api/v1/` prefix?
- [ ] Is there a verb in the URL path? → Use a resource noun + HTTP method; for non-CRUD actions use `/{resource}/{id}/{action}` (§9)
- [ ] Receiving a single item via a `?id=` query? → Use a `/{id}` path (§9)
- [ ] Used `@PatchMapping`? → Use `@PutMapping` instead (§9)
- [ ] SSE endpoint? → `@ApiResponse(content = [Content(mediaType = TEXT_EVENT_STREAM_VALUE, schema = Schema(...))])` declared, no `@Operation` (§11)
