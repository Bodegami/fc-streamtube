---
kind: phase
name: phase-1-phase-01-base
sources_mtime:
  docs/project-plan.md: "2026-06-25T20:16:47"
  docs/decisions/technical-decisions-phase-01-base.md: "2026-06-25T21:57:59"
---

# phase-1-phase-01-base — Context

## Scope

**Phase name:** Fase 01 — Configuração Base do Projeto

**Capabilities** (literal, `docs/project-plan.md`):

- Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)
- Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados
- Ambiente de desenvolvimento local com todos os serviços via Docker Compose
- Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)
- Fundação de IA para coding.

**This slice covers** (per `covers_capabilities`):
- "Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)"
- "Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados"
- "Ambiente de desenvolvimento local com todos os serviços via Docker Compose"
- "Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)"

**Sibling slice:** `ia-coding` owns "Fundação de IA para coding." (see `docs/phases/phase-1-ia-coding/context.md`).

**Out of scope:** Implementação de qualquer feature de negócio (Fases 02–07); setup do subprojeto Angular (explicitamente adiado: "será criado depois, não agora"); criação de tabelas do PostgreSQL; qualquer lógica de domínio; seleção de ferramenta de AI coding e granularidade de CLAUDE.md (slice ia-coding).

**Deliverables:** Ambiente de desenvolvimento funcional, banco de dados configurado.

**Affected subprojects:** `backend/` (Spring Boot — Gradle, Flyway, Clean Architecture); Repo-wide (Docker Compose, variáveis de ambiente).

**Deferred subprojects:** `frontend/` — setup Angular explicitamente adiado. Nenhuma decisão técnica de frontend neste documento.

**Sequencing notes:** Fase 01 não tem dependências. Todas as demais fases dependem dela direta ou indiretamente.

**Neighbors (for boundary detection only):**

- **Fase 02 (following):** Cadastro, Login e Gerenciamento de Conta — depende da Fase 01. Introduz autenticação, email transacional e sessão de usuário.

---

## Decisions Index

| Ref | Source | Scope | Topic | Status | Decision | Libraries |
|-----|--------|-------|-------|--------|----------|-----------|
| phase-01-base/TD-01 | phase | Backend | Build Tool — Maven vs Gradle | decided | Option B | — |
| phase-01-base/TD-02 | phase | Backend | Migration Tool — Flyway vs Liquibase | decided | Option A | — |
| phase-01-base/TD-03 | phase | Repo-wide | Estrutura do Docker Compose | decided | Option A | — |
| phase-01-base/TD-04 | phase | Repo-wide | Layout de Variáveis de Ambiente | decided | Option A | — |
| phase-01-base/TD-05 | phase | Backend | Spring Boot Architecture Pattern | decided | Option B | — |

_Source files:_

- phase-01-base — `docs/decisions/technical-decisions-phase-01-base.md` (scope_type: phase)

---

## Capability Coverage

| Capability (from project-plan.md) | Covered by |
|-----------------------------------|------------|
| Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle) | phase-01-base/TD-01, phase-01-base/TD-04 |
| Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados | phase-01-base/TD-05 |
| Ambiente de desenvolvimento local com todos os serviços via Docker Compose | phase-01-base/TD-03, phase-01-base/TD-04 |
| Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda) | phase-01-base/TD-02 |

---

## Decisions Detail

### phase-01-base/TD-01

**Recommendation:** `build.gradle.kts` oferece builds incrementais mais rápidos via build cache e daemon, DSL Kotlin tipada com autocomplete em IDEs, e maior flexibilidade para tasks customizadas conforme o projeto cresce; o wrapper `./gradlew` mantém o build reprodutível sem instalação global (o workflow Gradle deve ser adicionado ao `machine-environment.md`, hoje documentado apenas para Maven).
**Libraries:** —

### phase-01-base/TD-02

**Recommendation:** SQL puro elimina camada de abstração desnecessária; rollback forward-only é a prática padrão em equipes com continuous delivery (novas migrações corrigem problemas ao invés de reverter); auto-config Spring Boot com zero configuração adicional além do starter.
**Libraries:** —

### phase-01-base/TD-03

**Recommendation:** único `compose.yaml` na raiz com profiles para serviços opcionais; alinha com `docker-k8s.md`; zero fricção de onboarding. Profiles e `include` podem ser introduzidos quando a complexidade justificar.
**Libraries:** —

### phase-01-base/TD-04

**Recommendation:** `.env` único na raiz é o padrão Docker Compose nativo; Angular não precisa de `.env` em runtime (usa `environment.ts` versionado); onboarding de linha única.
**Libraries:** —

### phase-01-base/TD-05

**Recommendation:** separa explicitamente regras de negócio de domínio (`domain/entities`, `domain/repositories`) das de aplicação (`application/usecases`), com Use Cases como classes únicas altamente testáveis e alinhamento com DDD (`domain-driven-design.md`); o domínio fica livre de anotações de infraestrutura, com objetos de mapeamento separados entre domínio e persistência. Option C é explicitamente rejeitado pelos guides do projeto.
**Libraries:** —

---

## Inherited Decisions Detail

_No inherited TD details (Fase 01 é a primeira fase — sem fases anteriores)._

---

## Inherited Conventions

_No inherited conventions (Fase 01 é a primeira fase)._

---

## Inherited Deferred Capabilities

_No inherited deferred capabilities._

---

## Non-UI / Deferred Capabilities

_None._

---

## Testing Requirements

### backend/

_No testing guide available (`testing-guide-backend` skill not found) — layer requirements deferred to implementation._

### frontend/

_Deferred subproject — testing requirements will be defined when the Angular subproject is initialized (Fase 01+)._
