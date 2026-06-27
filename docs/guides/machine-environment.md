# Machine Environment Guide

This file tells Claude Code exactly how to operate on this machine. When setting up a project, running commands, or managing runtimes, follow these instructions — do not guess or use defaults from other environments.

---

## Docker and Kubernetes → Always OrbStack

When the user mentions Docker, containers, images, or Kubernetes, the runtime is **OrbStack**. Use standard `docker`, `docker compose`, and `kubectl` commands — they work as-is.

Never suggest installing Docker Desktop. Never ask which container runtime to use.

Before any container or Kubernetes task, read `./docs/guides/docker-k8s.md`.

### OrbStack + Testcontainers (Java) — Required configuration

OrbStack uses its own socket (`~/.orbstack/run/docker.sock`) and requires Docker API ≥ 1.40. The docker-java library bundled in Testcontainers connects via `/var/run/docker.sock` (which does not exist in OrbStack) and sends requests with API version 1.32 — both are rejected.

**One-time setup (creates a symlink that persists until manually removed; the socket file at the target disappears when OrbStack stops, but the symlink itself remains):**

```bash
sudo ln -sf ~/.orbstack/run/docker.sock /var/run/docker.sock
```

**Required `build.gradle.kts` configuration for every project using Testcontainers:**

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    // docker-java bundled in Testcontainers reads "api.version" as a system property.
    // OrbStack rejects requests with API < 1.40; docker-java default is 1.32.
    jvmArgs("-Dapi.version=1.41")
}
```

> **Quick diagnosis:** if tests fail with `BadRequestException: client version 1.32 is too old`, the cause is the API version. If they fail with `NoSuchFileException (/var/run/docker.sock)`, the symlink does not exist.

---

## Managing Java Versions → SDKMAN!

To install, switch, or check Java versions, use `sdk`. Never use `brew install java` or modify `JAVA_HOME` manually.

```bash
# Check active Java version
sdk current java

# List installed versions
sdk list java

# Install a specific version
sdk install java 21.0.4-tem

# Switch version in the current shell
sdk use java 21.0.4-tem

# Set a new global default
sdk default java 21.0.4-tem
```

**Current default:** Java 25.0.2 (Temurin).

**Per-project:** if the project has a `.sdkmanrc` file, run `sdk env install` then `sdk env` to activate the correct version before doing anything else.

---

## Managing Python Versions → uv

To install Python versions, create virtual environments, or manage packages, use `uv`. Never use `pip` directly or `pyenv`.

```bash
# Install a Python version
uv python install 3.12

# Create a virtual environment (uses .python-version or pyproject.toml if present)
uv venv

# Create with a specific version
uv venv --python 3.12

# Install all project dependencies
uv sync

# Add a dependency
uv add requests

# Add a dev dependency
uv add --dev pytest

# Run a command inside the project environment
uv run python main.py
uv run pytest

# Pin Python version for the project
uv python pin 3.12
```

**Currently installed:** 3.14.4 (default), 3.12.3, 3.11.15.

**When setting up a Python project:** check for `.python-version` or `pyproject.toml` first. If found, run `uv sync` to restore the environment. If not found, ask the user which Python version to use before creating the venv.

---

## Managing Node.js Versions → NVM

To install or switch Node.js versions, use `nvm`. Never use `brew install node`.

```bash
# Install a version
nvm install 20
nvm install 22

# Use a version in the current shell
nvm use 20

# Set global default
nvm alias default 20

# Check active version
nvm current

# Read from project's .nvmrc
nvm use   # reads .nvmrc automatically
```

**Important:** no Node version is currently installed. Before running any JS/TS project, check for `.nvmrc` and run `nvm install` + `nvm use` first.

---

## Maven → Use Wrapper or Install via SDKMAN!

Maven is not installed globally. Follow this order:

1. If the project has `./mvnw`, use it — it carries the correct Maven version:
   ```bash
   ./mvnw clean install
   ./mvnw test
   ./mvnw -pl <module> test
   ```

2. If no wrapper exists, install Maven via SDKMAN! before running any `mvn` command:
   ```bash
   sdk install maven
   mvn clean install
   ```

Never install Maven via Homebrew.

---

## Gradle → Use Wrapper or Install via SDKMAN!

Gradle is not installed globally. Follow this order:

1. If the project has `./gradlew` (Gradle Wrapper), use it — it carries the correct Gradle version and never requires a global install:
   ```bash
   ./gradlew build
   ./gradlew test
   ./gradlew clean build
   ./gradlew :<module>:test          # target a single module in a multi-module build
   ./gradlew bootRun                 # Spring Boot run task
   ./gradlew tasks                   # list available tasks
   ./gradlew build --refresh-dependencies   # force re-resolve dependencies
   ./gradlew build -x test           # build, skipping tests
   ```

2. If no wrapper exists, install Gradle via SDKMAN! before running any `gradle` command — then prefer generating a wrapper so the version is pinned for the project:
   ```bash
   sdk install gradle
   gradle wrapper             # generates ./gradlew pinned to a version
   ./gradlew build
   ```

**Notes:**
- Gradle honors the active Java version from SDKMAN!. Make sure the right Java is selected (`sdk current java` or `sdk env`) before running a build — a mismatched JDK is the most common build failure.
- The Gradle daemon is enabled by default; that is fine. Only pass `--no-daemon` when diagnosing a stuck or stale daemon.
- Distinguish Groovy DSL (`build.gradle`) from Kotlin DSL (`build.gradle.kts`) when editing build scripts — the syntax differs.

Never install Gradle via Homebrew.

### Kotlin Gradle DSL + Java 25 — Known bug

The Kotlin Gradle plugin bundled in Gradle 8.x fails to parse the Java version string `25.0.2` (a bug in Kotlin's IntelliJ core library). The build breaks with `IllegalArgumentException: 25.0.2`.

**Workaround:** run the Gradle daemon on Java 21, but still compile the code with the Java 25 toolchain:

```properties
# backend/gradle.properties — replace <path> with your local SDKMAN! Java 21 path
# Example: $HOME/.sdkman/candidates/java/21.0.10-tem
org.gradle.java.home=<path-to-java-21>
```

> **Note:** `gradle.properties` is committed to the repository with the project author's machine path. After cloning, update `org.gradle.java.home` to match your local SDKMAN! installation. Run `sdk list java` to find installed versions.

The `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` in `build.gradle.kts` still compiles the code with Java 25 — only the Gradle daemon runs on Java 21.

---

## Running Tests

Before writing or running any test, read `./docs/guides/testing-strategy.md`.

| Language | Command |
|---|---|
| Python | `uv run pytest` |
| Java (Maven wrapper) | `./mvnw test` |
| Java (Maven global) | `mvn test` |
| Java (Gradle wrapper) | `./gradlew test` |
| Java (Gradle global) | `gradle test` |
| Node.js | `nvm use && npm test` or `nvm use && npx jest` |

Always run tests from the project root unless the project is multi-module, in which case target the specific module.

---

## Setting Up a New Project Environment

When the user asks to set up or initialize a project environment, follow this sequence:

1. **Identify the language** from `pom.xml` (Java/Maven), `build.gradle` / `build.gradle.kts` (Java/Gradle), `pyproject.toml` / `.python-version` (Python), `package.json` / `.nvmrc` (Node).
2. **Activate the correct runtime version** using the manager above.
3. **Install dependencies:**
   - Java (Maven): `./mvnw install` or `mvn install`
   - Java (Gradle): `./gradlew build` or `gradle build`
   - Python: `uv sync`
   - Node: `npm install` or `yarn install` or `pnpm install`
4. **Verify:** run the project's test suite to confirm the environment works.

---

## Browser Automation → playwright-cli

`playwright-cli` is installed globally and available as `playwright-cli` when NVM is active.

- **Package:** `@playwright/cli` v0.1.14 (Microsoft — `github.com/microsoft/playwright-cli`)
- **Binary:** `/Users/devbodegami/.nvm/versions/node/v24.18.0/bin/playwright-cli`

Before any browser automation or E2E task, read `./docs/guides/playwright.md`.

**Critical:** headless browsers are blocked by CloudFront on authenticated sites (e.g. Figma). To attach to a real Chrome session without closing the existing browser:

```bash
# Open a second Chrome instance with debugging (leaves existing Chrome untouched)
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome \
  --remote-debugging-port=9222 \
  --user-data-dir=/tmp/chrome-debug-profile &

# Attach playwright-cli
playwright-cli attach --cdp=http://localhost:9222
```

---

## MCP Servers & Secrets

No MCP servers are currently configured. When a task requires one:

- **GitHub MCP** — requires the env var `GITHUB_TOKEN` (GitHub Personal Access Token with `repo` and `read:org` scopes).
- **Figma MCP** — API-based access to Figma designs; has a **daily rate limit**. When rate-limited, use playwright-cli + Chrome CDP to access Figma via browser (see `./docs/guides/playwright.md`).
- For GitHub operations, prefer the `gh` CLI first. Fall back to GitHub MCP only if `gh` is unavailable.

```bash
# Check gh authentication status before any GitHub task
gh auth status
```
