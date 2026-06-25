---
name: repository-conventions
description: Use when writing or reviewing Kotlin Spring Data JPA Repository / QueryDSL code in this spring-multi-module-template repo (projectA / ProjectB). Covers `@Query`/`@Modifying` prohibited, no domain-boundary-crossing Repository injection, no FQN, CRUD ordering, JSONB requires both `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`, `*Entity`/`*Entities` suffix, no `@Transactional` / `@Repository` on `Custom*RepositoryImpl`, no QueryDSL query-skeleton extraction into generic helpers; query priority derived → QueryDSL `Custom*Repository` → Service composition; `@EntityGraph` allowed-exception (MultipleBagFetchException); `by()` BooleanBuilder pattern; Q-class default singleton import + self-join meaningful alias; `@Column` policy (name/nullable/insertable/updatable prohibited; columnDefinition/length/unique allowed); JSONB query mapping; cascade/orphanRemoval; JPQL-bulk-delete vs entity-delete pitfall; cross-entity computed property delegation. Triggers on edits to `**/repository/**.kt`, `**/entity/**.kt`, `*Repository.kt`, `Custom*RepositoryImpl.kt`; mention of `@Query` / `@Modifying` / `@EntityGraph` / QueryDSL / `BooleanBuilder` / `JPAQueryFactory` / `findBy*` / `pageBy*`; or any new repository / custom query / pagination query.
---

# Repository Layer Conventions

Single source of truth for Repository / QueryDSL conventions. This project uses QueryDSL 7.1 (OpenFeign fork, `io.github.openfeign.querydsl`) with a `JPAQueryFactory` bean configured in `QueryDslConfig`. Apply BEFORE writing repository code.

> **Rule-number notation**: this skill cites its own rules as `Repo #N`.

---

## 1. Hard Rules (never violate when writing Repository code)

| # | Rule | Where it applies |
|---|------|-----------------|
| Repo #1 | `@Query` (JPQL/native) and `@Modifying` are STRICTLY PROHIBITED on every Repository method (UPDATE/DELETE/SELECT alike) | every `*Repository.kt` |
| Repo #2 | Never inject another domain's Repository. Go through that domain's Service | Repository / Service constructor |
| Repo #3 | JSONB columns require BOTH `@JdbcTypeCode(SqlTypes.JSON)` AND `@Column(columnDefinition = "jsonb")` | `@Entity` JSONB fields |
| Repo #4 | Methods returning Entity for cross-domain Service-to-Service use end with `Entity` (single) / `Entities` (collection) | Service exposing entities |
| Repo #5 | Never use FQN — always use `import` statements | every Kotlin source file |
| Repo #6 | Public methods follow CRUD ordering: Create → Read → Update → Delete → Other | Repository / Service / Controller |
| Repo #7 | Do NOT put `@Transactional` on a `Custom*RepositoryImpl` (neither class nor method) — the transaction boundary is owned by the calling Service. Write multi-queries as a single fetchJoin query, and when fetchJoin-ing 2+ collections declare one of them as a `Set` to avoid `MultipleBagFetchException` | `Custom*RepositoryImpl.kt` |
| Repo #8 | Do NOT put stereotype annotations such as `@Repository` / `@Component` on a `Custom*RepositoryImpl`. Spring Data's fragment-implementation mechanism auto-registers the bean via the `*Impl` suffix convention | `Custom*RepositoryImpl.kt` |
| Repo #9 | Do NOT extract the QueryDSL query skeleton (`selectFrom…join…where…fetch`) into a generic helper method — the query chain must show in full inside each method so it maps to the actual SQL and stays readable. Extracting a `by()` predicate or a chainable extension is allowed and recommended | every class that uses QueryDSL |

---

## 2. Query Construction Priority (try in this order)

| # | Strategy | Scope |
|---|----------|-------|
| 1 | **Spring Data derived query** | Single 1-segment field — `findBy{X}` / `existsBy{X}` / `countBy{X}` / `deleteBy{X}`. No nested paths, no `And`/`Or` combinations |
| 2 | **QueryDSL `Custom*Repository` + `Custom*RepositoryImpl`** | Everything else — compound predicates, nested paths (`AuthorDeletedAtIsNull`), dynamic predicates, projections, subqueries, multi-table JOIN, pagination/sort, and rewriting `@EntityGraph` as `leftJoin().fetchJoin()` |
| 3 | **`@EntityGraph` + derived query** | **Allowed only in the documented exception** (see below) |
| 4 | **`@Query` / `@Modifying`** | Permanently prohibited |

```kotlin
// ❌ Bad — @Query (Repo #1)
@Modifying
@Query("DELETE FROM Comment c WHERE c.article.id = :articleId")
fun deleteAllByArticleId(@Param("articleId") articleId: UUID)

// ✅ Good — QueryDSL Custom Repository for bulk DML
class CustomCommentRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CustomCommentRepository {
    override fun deleteAllByArticleId(articleId: UUID) {
        queryFactory.delete(comment)
            .where(comment.article.id.eq(articleId))
            .execute()
    }
}
```

### Exception for #3 (must be justified in KDoc)

- **MultipleBagFetchException risk** — when eager-fetching 2+ `List`-typed collections in a single query would trigger Hibernate's bag-fetch restriction.

Do NOT work around `@EntityGraph` limitations by adding `@Column(insertable=false, updatable=false)` read-only mappings — that conflicts with the `@Column` policy in §5.

---

## 3. QueryDSL Patterns

### 3.1 Extract `by()` BooleanBuilder — required, call inline at every use site

Do NOT build `BooleanBuilder` inline inside `pageByQuery()`. Extract it into `private fun by(query): BooleanBuilder` so it is reusable across the ID query and the count query. **Call it inline as `.where(by(query))` — do NOT extract an intermediate variable like `val where = by(query)`** (an application of code-style-conventions §4).

```kotlin
// ✅ Good — call by(query) inline at each use site
override fun pageByQuery(query: PageArticleQuery): Page<Article> {
    val ids = queryFactory.select(article.id).from(article)
        .where(by(query)).orderBy(...).fetch()
    val total = queryFactory.select(article.count()).from(article)
        .where(by(query)).fetchOne()!!
    ...
}

private fun by(query: PageArticleQuery): BooleanBuilder =
    BooleanBuilder(article.deletedAt.isNull)
        .and(query.title?.let { article.title.containsIgnoreCase(it) })
        .and(query.status?.let { article.status.eq(it) })

// ❌ Bad — extracting an intermediate variable val where = by(query) (semantic noise)
override fun pageByQuery(query: PageArticleQuery): Page<Article> {
    val where = by(query)
    val ids = ... .where(where) ...
}
```

### 3.2 Q-class import — use the default singleton directly

Do NOT define local alias fields like `private val xxx_ = QXxx("xxx_")`. Import the kapt-generated default singleton instead.

```kotlin
// ✅ Good
import com.yebali.template.article.entity.QArticle.article

queryFactory.selectFrom(article).where(article.id.eq(id)).fetchOne()

// ❌ Bad
companion object { private val article_ = QArticle("article_") }
```

**Self-join exception** — when a single query references the same table twice, define a meaningful alias (NOT a formal suffix like `article_`):

```kotlin
// ✅ Good — meaningful self-join alias
val parentCategory = QCategory("parentCategory")
queryFactory.selectFrom(category)
    .leftJoin(parentCategory).on(category.parentId.eq(parentCategory.id))
    .fetch()
```

### 3.3 Do NOT extract the query skeleton (Repo #9)

Write QueryDSL queries so the `selectFrom…join…where…fetch` chain is **fully visible** inside each method — it is most readable when it maps 1:1 to the actually-executed SQL. Do NOT factor the skeleton into something shared just because several repositories use similar queries — duplication of the QueryDSL query skeleton is **NOT** subject to code-style-conventions §3 (duplication removal).

| Allowed / recommended (reads inline within the query chain) | Prohibited (hides the query shape from the call site) |
|---|---|
| Extracting a `by()` BooleanBuilder predicate (§3.1) | Extracting the entire select-join-where-fetch skeleton into a separate method |
| Chainable extension functions such as `withPagination()` / `jsonbField()` | Extracting the pagination tail into a `pageOf()`-style helper |
| Encapsulating conditions in a Query object such as `Page*Query` | Extracting a shared query skeleton into an interface default method |

---

## 4. Repository Naming

| Type | Method prefix | Return | Query class |
|------|---------------|--------|-------------|
| Single | `findBy*` / `existsBy*` / `countBy*` / `deleteBy*` | `Entity?` / `Boolean` / `Long` | not needed |
| Multiple | derived `findAllBy*` or Custom `listBy*` | `List<Entity>` | `List*Query` (when needed) |
| Paginated | `pageByQuery(query)` | `Page<Entity>` | `Page*Query` required |

`get*` is reserved for the Service layer. Do NOT use `get*` on Repositories.

---

## 5. Entity `@Column` Policy (when modifying entities while writing repository code)

| Option | Use | Reason |
|--------|-----|--------|
| `name` | ❌ | Hibernate `PhysicalNamingStrategy` auto-converts camelCase → snake_case |
| `nullable` | ❌ | Inferred from Kotlin `String?` vs `String` |
| `insertable` / `updatable` | ❌ | For cross-domain refs use `@ManyToOne val xxx: Xxx` single mapping + `xxx?.id` access |
| `columnDefinition` | ✅ | PostgreSQL-specific types: `jsonb`, `text` |
| `length` | ✅ | Only when different from default 255 |
| `unique` | ✅ | Single-column UNIQUE (compound goes in `@Table(uniqueConstraints=...)`) |

```kotlin
// ✅ Good — let inference handle everything; annotate only special types / length / constraints
@Entity
class Article(
    @Enumerated(EnumType.STRING)
    val status: Status,                                  // name auto, nullable inferred
    val createdBy: UUID,                                 // no annotation needed
    @Column(unique = true, length = 64)
    val slug: String,                                    // UNIQUE + length only
    @Column(columnDefinition = "text")
    var body: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val attributes: Map<String, Any> = emptyMap(),       // JSONB requires both annotations
)

// ❌ Bad — redeclaring auto-inferable info
@Entity
class Article(
    @Column(name = "created_by", nullable = false)       // both auto
    val createdBy: UUID,
)
```

**Exception**: a `BaseTimeEntity` audit field (`createdAt`, `lastModifiedAt`) requires `@Column(updatable=false)` to cooperate with `AuditingEntityListener` — explicit exception to the general policy.

**Cross-domain Entity reference** — model with `@ManyToOne val author: Member?`, NOT a separate `authorId: UUID?` field. Access ID via `article.author?.id`. The Repository injection prohibition (Repo #2) does not apply to Entity imports — Entity may freely import other Entities.

---

## 6. JSONB Queries (PostgreSQL)

### 6.1 JSONB mapping — both annotations required (Repo #3)

```kotlin
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
val params: Map<String, Any> = emptyMap()
```

Missing either annotation breaks Hibernate's JSONB serialization (silently for read, with cryptic errors on write).

### 6.2 JSONB queries

When querying a JSONB field with QueryDSL, keep a module-shared JSONB-operation util (e.g. `jsonbField(name)` + a type-casting extension) and call it inline within the query chain (Repo #9). This template has no JSONB util yet, so define one in the module's `util/query/` when writing the first JSONB query.

> Tests run on H2 (`MODE=PostgreSQL`), so JSONB behavior may differ from PostgreSQL. If verifying JSONB queries matters, consider adopting Testcontainers PostgreSQL.

---

## 7. Domain-Boundary Repository Injection Prohibited (Repo #2)

Never inject another domain's Repository directly. Go through that domain's Service.

| Dependency | Allowed |
|------------|---------|
| A Service → B Service | ✅ |
| A Service → B Repository | ❌ |
| A Repository → B Repository | ❌ |

When a cross-domain Entity reference is needed, call B Service's `getXxxEntity()` / `listXxxEntities()` (Repo #4 suffix rule). Concrete cross-domain flow lives in `service-conventions` §6.

---

## 8. Cascade / OrphanRemoval Essentials

- Owning relationships: `cascade = [CascadeType.ALL], orphanRemoval = true`
- Soft-deleted entities: no cascade; the Service layer handles deletion explicitly
- High-volume accumulating data: do NOT declare `@OneToMany` on the parent (OOM risk)
- Defense in depth: combine JPA cascade with DB `ON DELETE CASCADE`

**Do NOT mix JPQL bulk delete with entity delete:**
```kotlin
// ❌ Bad — TransientObjectException (session inconsistency)
commentRepository.deleteAllByArticleId(article.id)
articleRepository.delete(article)

// ✅ Good — both go through JPA entity delete
commentRepository.deleteAll(article.comments)
articleRepository.delete(article)
```

---

## 9. Cross-Entity Computed Property Delegation

When an entity needs a value from a related entity, delegate via a computed property — do NOT duplicate the column.

```kotlin
@Entity
class Comment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    val article: Article,
) : BaseTimeEntity() {
    // ✅ Good — delegating computed property
    val articleTitle: String
        get() = article.title
}
```

**Important**: the related entity must be loaded (via `@EntityGraph` on the query, or `fetchJoin()` in QueryDSL) before accessing the delegated property *outside* a transaction. Otherwise `LazyInitializationException` fires at access time.

---

## 10. Pre-Write Checklist

- [ ] FQN used anywhere? → Replace with `import` (Repo #5)
- [ ] Wrote `@Query` or `@Modifying`? → **Remove immediately**, retry from priority #1 → #2 (Repo #1)
- [ ] Added `@Repository` / `@Component` to a `Custom*RepositoryImpl`? → Remove (Repo #8)
- [ ] Added `@Transactional` to a `Custom*RepositoryImpl`? → Remove — boundary belongs to the calling Service (Repo #7)
- [ ] Can the query be expressed as a derived query? (single segment, no And/Or)
- [ ] For compound conditions, did you create a `Custom*Repository` interface + `Impl`?
- [ ] Built `BooleanBuilder` inline inside `pageByQuery()`? → Extract to `by()`; call `.where(by(query))` inline (§3.1)
- [ ] Did you extract the QueryDSL query skeleton into a generic helper? → Keep the skeleton inside each method as-is (Repo #9, §3.3)
- [ ] Created a Q-class local alias (`article_`)? → Replace with default singleton import
- [ ] Self-join needs alias? → Use a meaningful name (`parentCategory`), NOT a formal suffix
- [ ] Injected another domain's Repository? → Route through that domain's Service (Repo #2)
- [ ] Added `@Column(name=...)` / `nullable=...` / `insertable=false`? → Remove (see §5 table for allowed cases)
- [ ] JSONB column missing either `@JdbcTypeCode(SqlTypes.JSON)` or `@Column(columnDefinition = "jsonb")`? → Add both (Repo #3)
- [ ] Using `@EntityGraph` + derived? → Match the MultipleBagFetchException exception in §2; note reason in KDoc
- [ ] Mixed JPQL bulk delete with entity delete in the same transaction? → Use entity delete on both sides (§8)
- [ ] Cross-entity computed property accessed outside a transaction? → Eager-load the relation first (§9)
- [ ] Repository method named `get*`? → Rename to `findBy*` / `listBy*` / `pageByQuery` (`get*` is Service-layer)
- [ ] Public methods follow CRUD ordering? (Repo #6)
- [ ] Modifying a Service/Entity? → Read its callers, related tests, and dependent code first
