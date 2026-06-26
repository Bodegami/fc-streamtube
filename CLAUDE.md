# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Diagram

fc-streamtube/ (monorepo root)
├── CLAUDE.md                         <-- Project Context (this file)
│
├── docs/
│   ├── project-plan.md               <-- Project roadmap and phases
│   │
│   ├── guides/
│   │   ├── git-flow.md               <-- Git branching and commit rules
│   │   ├── docker-k8s.md             <-- Containerization standards
│   │   ├── testing-strategy.md       <-- Testing approach and rules
│   │   ├── machine-environment.md    <-- Local toolchain and setup
│   │   ├── clean-architecture.md     <-- Architecture guide: Clean Architecture
│   │   ├── domain-driven-design.md   <-- Architecture guide: DDD
│   │   ├── software-architecture.md  <-- Architecture guide: Hexagonal, Onion, BFF
│   │   └── event-driven-architecture.md  <-- Architecture guide: EDA, Kafka, Saga, CQRS
│   │
│   ├── decisions/                     <-- [RPI: Research] Technical decision records per phase
│   │   ├── technical-decisions-ia-coding.md
│   │   ├── technical-decisions-phase-01-base.md
│   │   └── technical-decisions-stack.md
│   │
│   ├── diagrams/                      <-- [RPI: Plan] Visual artifacts generated during planning
│   │   ├── software-arch.mermaid
│   │   ├── skills-workflow.mermaid
│   │   ├── skill-plan-phase.mermaid
│   │   ├── skill-implement-phase.mermaid
│   │   └── skill-research.mermaid
│   │
│   └── phases/                        <-- [RPI: Implement] Execution context and validation per phase
│       ├── phase-1-ia-coding/
│       │   ├── context.md
│       │   └── validation.md
│       └── phase-1-phase-01-base/
│           ├── context.md
│           ├── phase-1-phase-01-base.md
│           └── validation.md
│
├── backend/                           <-- (coming soon)
│   └── CLAUDE.md                      <-- Local context: specific tests conventions, rules and etc
│
└── frontend/                          <-- (coming soon)
    └── CLAUDE.md                      <-- Local context: function paradigm, specific language version and etc

# Project Development Guidelines

This document governs assistant behavior within the fc-streamtube monorepo. Guide files are local to this project under `./docs/guides/`.

## 1. Context Strategy (REQUIRED — Read Before Acting)

**STOP. Before executing any command, suggesting any approach, or taking any action in the domains below, you MUST read the corresponding guide file first.**
Do not proceed without reading it. No exceptions.

Guide files are located at `./docs/guides/`. If the file does not exist, fall back to `~/.claude/docs/guides/`, and if neither exists, apply standard industry best practices.

- **Git/GitHub**: Read `./docs/guides/git-flow.md` before any git operation or branch/commit decision.
- **Containerization**: Read `./docs/guides/docker-k8s.md` before any `docker`, `docker compose`, `orb`, or `kubectl` command.
- **Testing**: Read `./docs/guides/testing-strategy.md` before writing or modifying any tests.
- **Environment/Setup**: Read `./docs/guides/machine-environment.md` before any of: setting up a project, installing dependencies, running tests, switching language versions (Java, Python, Node), using Maven, diagnosing runtime issues, or any task that involves the local machine toolchain.

## 2. Universal Standards (Short Rules)

- **Git**: Commits must follow [Conventional Commits](https://www.conventionalcommits.org/). Prefer atomic commits.
- **Docker**: Always use multi-stage builds to reduce image size. Avoid running as root.
- **Testing**: Prioritize edge case coverage. If the guide file (`testing-strategy.md`) is not found, follow the rule: *Arrange-Act-Assert*.

## 3. Workflow

1. **Identify**: Determine which domain the task belongs to (e.g., "Is this a deploy? Then I need to read `docker-k8s.md`").
2. **Read**: Read the local guide at `./docs/guides/` before executing.
3. **Execute**: Apply logic according to the guide.

## Project Overview

This is a monorepo for the fc-streamtube project. It will contain two sub-projects:
- `backend/` — API and business logic services
- `frontend/` — Web client application

When working inside a sub-project, read its own `CLAUDE.md` first — it contains project-specific context that overrides or extends this file.

## Architecture

**Before proposing, evaluating, or implementing any architectural decision, read the relevant guide file first.**

Guide files are located at `./docs/guides/`. If the file does not exist, fall back to `~/.claude/docs/guides/`.

- **Clean Architecture**: Read `./docs/guides/clean-architecture.md` before structuring layers, defining dependencies between modules, or deciding where business rules live.
- **Domain-Driven Design**: Read `./docs/guides/domain-driven-design.md` before modeling domains, defining aggregates/entities/value objects, or designing bounded contexts.
- **Software Architecture Patterns**: Read `./docs/guides/software-architecture.md` before choosing or applying patterns such as Hexagonal Architecture, Backend for Frontend (BFF), Onion Architecture, or similar structural approaches.
- **Event-Driven Architecture**: Read `./docs/guides/event-driven-architecture.md` before designing event flows, choosing messaging patterns (pub/sub, event sourcing, CQRS), or integrating event brokers.
