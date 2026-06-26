# Git Flow Guide

This guide is authoritative for all Git usage, branch naming, commit messages, and Pull Request workflows across all projects.

---

## Commit Messages — Conventional Commits

All commits MUST follow the [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) specification.

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Commit Types

| Type | When to use | SemVer impact |
|---|---|---|
| `feat` | New feature | MINOR |
| `fix` | Bug fix | PATCH |
| `docs` | Documentation only | none |
| `style` | Formatting, whitespace (no logic change) | none |
| `refactor` | Code restructure without feature/fix | none |
| `perf` | Performance improvement | none |
| `test` | Adding or updating tests | none |
| `build` | Build system or dependency changes | none |
| `ci` | CI/CD configuration changes | none |
| `chore` | Maintenance tasks (no production code) | none |

### Scope (optional)

A noun describing the affected section, in parentheses: `feat(auth): add OAuth2 support`

### Breaking Changes

Two valid ways — both trigger a MAJOR bump:

```
# Option 1 — exclamation mark
feat(api)!: remove deprecated /v1 endpoints

# Option 2 — footer
feat(api): remove deprecated /v1 endpoints

BREAKING CHANGE: /v1 endpoints have been removed. Migrate to /v2.
```

### Body and Footers

- Body begins one blank line after the description. Explains the *why*, not the *what*.
- Footers use git trailer format: `Token: value` or `Token #value`.
- `BREAKING CHANGE` must be uppercase. All other types and tokens are case-insensitive.
- When a commit fits multiple types, split it into multiple atomic commits instead.

### Examples

```
feat(cart): add discount coupon support

fix(auth): prevent session expiry on active use

docs(readme): update local setup instructions

refactor(order)!: replace synchronous processing with event-driven flow

BREAKING CHANGE: OrderService.process() is now async. All callers must await it.
```

---

## Branch Naming Convention

All branch names must be lowercase, hyphen-separated, and prefixed by type.

| Pattern | Purpose |
|---|---|
| `feature/<ticket>-<short-description>` | New feature development |
| `fix/<ticket>-<short-description>` | Bug fix (non-critical) |
| `hotfix/<ticket>-<short-description>` | Urgent production fix |
| `release/<version>` | Release preparation |
| `chore/<short-description>` | Maintenance (deps, config, tooling) |
| `docs/<short-description>` | Documentation only |

### Examples

```
feature/PROJ-42-user-authentication
fix/PROJ-88-cart-total-rounding
hotfix/PROJ-101-payment-gateway-timeout
release/1.4.0
chore/upgrade-node-20
docs/update-api-reference
```

---

## Key Git Commands

### Starting work

```bash
# Update local main before branching
git switch main
git pull --rebase origin main

# Create and switch to a new branch
git switch -c feature/PROJ-42-my-feature
```

### Staging and committing

```bash
# Stage specific files (never use git add . blindly)
git add src/module/file.ts

# Commit with Conventional Commits message
git commit -m "feat(module): add new capability"

# Amend last commit message (only if not pushed yet)
git commit --amend --no-edit
```

### Syncing

```bash
# Pull with rebase to keep history linear
git pull --rebase origin main

# Push branch and set upstream
git push -u origin feature/PROJ-42-my-feature
```

### Undoing changes

```bash
# Revert a commit (safe — creates a new undo commit)
git revert <commit-hash>

# Unstage a file (does not discard changes)
git restore --staged <file>

# Discard local changes in a file (destructive)
git restore <file>
```

### Inspection

```bash
# Compact log with graph
git log --oneline --graph --decorate

# Show changes in a specific commit
git show <commit-hash>

# Show who changed each line
git blame <file>
```

### Worktrees (parallel branches without stashing)

```bash
# Create a worktree to work on a branch without leaving current branch
git worktree add ../project-hotfix hotfix/PROJ-101-timeout

# List active worktrees
git worktree list

# Remove a worktree after merging
git worktree remove ../project-hotfix
```

### Switching branches

```bash
# Modern way (preferred over git checkout for branch switching)
git switch <branch-name>

# Create and switch in one command
git switch -c <new-branch>

# Return to previous branch
git switch -
```

---

## Pull Requests

### Preferred: GitHub CLI (`gh`)

Always prefer `gh` for PR operations. It is faster and does not require browser interaction.

```bash
# Create a PR (interactive, picks up current branch)
gh pr create --title "feat(auth): add OAuth2 support" --body "..."

# Create with base branch explicitly
gh pr create --base main --title "..." --body "..."

# View PR status and checks
gh pr status
gh pr checks

# Review and merge
gh pr review --approve
gh pr merge --squash

# List open PRs
gh pr list

# View a specific PR
gh pr view <number>

# Checkout a PR branch locally
gh pr checkout <number>
```

### Fallback: GitHub MCP

Use GitHub MCP only when `gh` is not available or insufficient (e.g., in environments without CLI access). Check for available MCP tools before falling back.

### PR Best Practices

- One PR = one logical change. Do not bundle unrelated fixes.
- PR title must follow Conventional Commits format.
- Link the related issue/ticket in the PR body.
- Prefer squash merge to keep main history linear.
- Delete the branch after merging.
