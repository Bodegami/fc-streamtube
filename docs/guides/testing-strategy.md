# Testing Strategy Guide

This guide is authoritative. When writing any test (unit, integration, e2e, contract), you MUST follow these rules in order. No exceptions.

---

## Mandatory: TDD Cycle (Red → Green → Refactor)

You MUST implement tests using Test-Driven Development. This is not optional. Every feature or bug fix starts with a test.

### Step 1 — RED (Write a failing test)
- Write the test BEFORE any implementation exists.
- The test must fail for the right reason (not due to a syntax error or missing import).
- The test must describe the expected behavior in plain language (test name = specification).
- Do NOT write implementation code at this step.

```
# Example
test("should return 0 when cart is empty") → fails because CartService doesn't exist yet
```

### Step 2 — GREEN (Make it pass with minimum code)
- Write the MINIMUM production code needed to make the test pass.
- Do not add logic that is not covered by an existing test.
- Hardcoding a return value is acceptable at this step if it makes the test green.
- Do NOT refactor at this step.

```
# Example
def total(): return 0  ← valid minimum implementation to pass the first test
```

### Step 3 — REFACTOR (Improve design, keep tests green)
- Now apply SOLID, Design Patterns, OOP best practices, and language conventions.
- All tests must remain GREEN after refactoring.
- Remove duplication, improve naming, extract abstractions — but only what is needed now.
- Apply KISS (see below) to avoid over-engineering.

Repeat the cycle for each new behavior.

---

## FIRST Principles (test quality checklist)

Every test you write must satisfy all five properties:

| Property | Rule |
|---|---|
| **F**ast | Tests must run in milliseconds. Never hit real DBs, networks, or filesystems in unit tests. |
| **I**ndependent | Tests must not depend on each other. Any test must be runnable in isolation or in any order. |
| **R**epeatable | Same result every run, in any environment. No randomness, no time-of-day logic, no shared state. |
| **S**elf-validating | Tests must have a clear pass/fail assertion. No manual log inspection. |
| **T**imely | Tests are written before or alongside production code (TDD). Never written after the fact as an afterthought. |

If a test violates any of these, fix the test, not the rule.

---

## KISS in Testing (Keep It Simple)

- One test = one behavior. Never test multiple things in a single test case.
- Use simple, flat Given-When-Then (GWT) structure. No nested conditionals inside tests.
- Prefer obvious over clever. A future reader must understand the test without context.
- Avoid shared `setup/teardown` that is not strictly necessary — it hides dependencies.

```
# Given: set up inputs and dependencies (context/preconditions)
# When:  call the unit under test (action)
# Then:  verify the outcome (expected result)
```

---

## Supporting Best Practices

These apply during the REFACTOR step and when designing testable code:

### SOLID
- **S**ingle Responsibility: a class/function does one thing → easier to test in isolation.
- **O**pen/Closed: extend behavior without modifying tested code → tests stay stable.
- **L**iskov Substitution: subtypes must honor parent contracts → test against interfaces, not concretions.
- **I**nterface Segregation: small interfaces → fewer mocks in tests.
- **D**ependency Inversion: depend on abstractions → inject dependencies to enable test doubles.

### OOP
- Prefer composition over inheritance — flatter hierarchies are easier to mock.
- Avoid static methods and global state — they make tests stateful and order-dependent (violates FIRST-I/R).

### Design Patterns (test-relevant)
- **Factory / Builder**: use in test fixtures to construct complex objects cleanly.
- **Strategy**: inject test doubles via strategy interfaces.
- **Observer/Event**: test publishers and subscribers independently.
- **Repository**: always program to the repository interface; inject fakes in unit tests.

### Enterprise Patterns
- **Anti-Corruption Layer**: test the ACL boundary, not the external system.
- **Saga / Outbox**: test each step in isolation; integration tests verify orchestration.

### 12-Factor App
- **III – Config**: never hardcode config in tests. Use env vars or test config files.
- **VI – Processes**: tests must assume a stateless process. No in-memory global state between tests.
- **X – Dev/prod parity**: integration tests must run against the same engine as production (e.g., PostgreSQL, not SQLite).

### Language / Framework Conventions
- Follow the testing idioms of the project language (e.g., pytest fixtures, JUnit lifecycle, Jest matchers).
- Use the project's approved assertion library. Do not introduce new testing dependencies without discussion.
- Name tests using the pattern: `given_[context]_when_[action]_then_[expected result]` in snake_case lowercase.
  - Example in Python: `given_empty_cart_when_total_is_called_then_returns_zero`
  - Example in Java: `givenEmptyCart_whenTotalIsCalled_thenReturnsZero`

---

## Test Types and Scope

| Type | Scope | Uses real infra? | Follows TDD? |
|---|---|---|---|
| Unit | Single class/function | No — use doubles | Always |
| Integration | Module + DB/queue/cache | Yes | Always |
| Contract | API or event boundary | Mocked provider | Always |
| E2E | Full user flow | Yes | When feasible |

- Unit tests are the foundation. Aim for the majority of coverage here.
- Integration tests verify that wiring between components is correct — not business logic.
- Do not use E2E tests to cover business logic that belongs in unit tests (Testing Trophy / Testing Pyramid).

---

## Testcontainers — Standard for Java Integration Tests (Spring Boot + OrbStack)

### Dependencies (Gradle Kotlin DSL)

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")  // or other DB/infra module
```

### Test pattern with `@ServiceConnection`

Use `@ServiceConnection` so Spring Boot automatically configures the datasource to point to the container — no manual port or URL configuration needed:

```java
@SpringBootTest
@Testcontainers
class VideoRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void givenEmptyDatabase_whenCountVideos_thenReturnsZero() {
        // ...
    }
}
```

### Required `build.gradle.kts` configuration (OrbStack)

See `machine-environment.md` → **OrbStack + Testcontainers** for context. The `jvmArgs` below is required on this machine:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    // docker-java bundled in Testcontainers reads "api.version" as a system property.
    // OrbStack rejects requests with API < 1.40; docker-java default is 1.32.
    jvmArgs("-Dapi.version=1.41")
}
```

### Never use H2 in integration tests

The 12-Factor X – Dev/prod parity rule requires integration tests to run against the same engine as production. H2 and SQLite behave differently from PostgreSQL (types, constraints, functions). Always use Testcontainers with the real PostgreSQL image.

---

## E2E Tests → playwright-cli

For E2E tests on the frontend or full user flows, use `playwright-cli`. Before any browser automation task, read `./docs/guides/playwright.md`.

E2E tests follow the same TDD cycle and FIRST principles as other test types. Use the Claude Code skill `/e2e-nav-test` to auto-generate test cases from the running app.

| Scope | Tool |
|---|---|
| Unit / Integration | Language-native (pytest, JUnit, Jest) |
| E2E / Browser flows | `playwright-cli` |
| UI design review | `playwright-cli show --annotate` |

---

## What NOT to do

- Do NOT write tests after implementation and call it TDD.
- Do NOT test implementation details (private methods, internal state). Test behavior.
- Do NOT use `sleep()` or arbitrary delays in tests.
- Do NOT share mutable fixtures between tests.
- Do NOT skip the RED step because "the implementation is obvious".
