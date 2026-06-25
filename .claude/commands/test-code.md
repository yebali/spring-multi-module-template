Generate test code for the changed code

**All output and responses MUST be in Korean (한국어).**

Write or update integration tests (MockMvc-based) for the code changed in the current diff. The test conventions (`SpringBootTestSupport`, `@Nested`, helper naming, per-layer responsibilities, quality criteria, etc.) live in the `test-conventions` skill as the source of truth; this command is the entry point that applies those rules to the changes.

## What this command does

Write tests for the Controller endpoints / Service public methods that were changed or newly added in the current diff — **limited to the changes**, not the entire module.

## Steps

1. **Identify the change scope**: Use `git diff` / `git status` to identify the changed or added Controller endpoints and Service methods.
2. **Compare against existing coverage** (`test-conventions` §8 Criterion 4 / A-1): List the public endpoints/methods of each changed Controller/Service and compare them against the existing `*Test.kt`, writing tests **starting with what is not yet covered** — do not create duplicate coverage.
3. **Write the tests** (compliant with `test-conventions`):
   - Inherit `SpringBootTestSupport`, group with `@Nested`, do not use `@DisplayName`
   - Use or add helpers in `SpringBootTestSupport` (`save*`/`build*`/`random*`, etc.) for test data
   - Verify **business logic**, not the framework (`test-conventions` §8 C-1 / C-3)
   - Verify each changed endpoint down to its actual effect (DB state change, or persistence confirmed via a subsequent GET) — do not look only at the HTTP response
   - Write a success path + applicable error paths (404 / 400 / 409) for each changed endpoint to satisfy the §2.5 B-1 variation matrix
   - Avoid `@Transactional` as much as possible (except for Repository tests — `test-conventions` §2)
4. **Remove duplicates**: If you see tests duplicated regardless of the changes, remove them.
5. **Run and verify**: Confirm everything is green with `./gradlew :<module>:test --tests "<TestClass>"`.
6. **Summary**: Briefly summarize the work in Korean.
