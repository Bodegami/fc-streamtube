---
kind: phase
name: phase-1-phase-01-base
test_specs_aware: true
sources_mtime:
  docs/phases/phase-1-phase-01-base/context.md: "2026-06-25T21:59:11"
  docs/project-plan.md: "2026-06-25T20:16:47"
  docs/decisions/technical-decisions-phase-01-base.md: "2026-06-25T21:57:59"
---

# Fase 01 — Configuração Base do Projeto

## Objective

Estabelecer a fundação do repositório com dois subprojetos separados (`backend/` em Spring Boot inicializado via Gradle; `frontend/` Angular adiado), um ambiente de desenvolvimento local com todos os serviços orquestrados por Docker Compose, e a estrutura inicial do banco PostgreSQL (Flyway, sem tabelas ainda) — entregando um ambiente de desenvolvimento funcional com banco configurado.

---

## Step Implementations

### SI-1.1 — Inicializar repositório e estrutura de subprojetos

**Description:** Estabelecer a raiz do monorepo com o subprojeto `backend/` e o slot adiado de `frontend/`, mais `.gitignore` e `README` de onboarding.

**Technical actions:**

1. Criar a estrutura raiz do monorepo: diretório `backend/` (slot do Spring Boot) — `frontend/` é explicitamente adiado ("será criado depois, não agora"), portanto NÃO criar agora.
2. Criar `.gitignore` na raiz cobrindo artefatos Gradle (`backend/build/`, `backend/.gradle/`), arquivos de IDE e `.env` (o `.env` será materializado em SI-1.4 — aqui apenas garante que não seja versionado).
3. Criar `README.md` na raiz documentando os subprojetos (`backend/` ativo, `frontend/` adiado) e o comando único de onboarding (`docker compose up -d`).

**Tests:** _(empty — Infra)_

**Dependencies:** none

**Acceptance criteria:**

- A raiz do repositório contém o diretório `backend/` e NÃO contém `frontend/` (adiamento explícito observável).
- `git status` não lista `.env` como untracked candidato a commit (coberto pelo `.gitignore`).
- `README.md` descreve os dois subprojetos e o comando de subida do ambiente.

---

### SI-1.2 — Inicializar projeto Spring Boot (backend) com Gradle

**Description:** Gerar o subprojeto `backend/` via Spring Initializr usando Gradle (Kotlin DSL), estabelecendo a ferramenta de build oficial do projeto.

**Technical actions:**

1. Gerar `backend/` via Spring Initializr — build tool **Gradle (Kotlin DSL)**, Java 25, packaging Jar (per `phase-01-base/TD-01`).
2. Declarar dependências iniciais em `backend/build.gradle.kts`: Spring Web, Spring Data JPA, PostgreSQL Driver, Flyway (per `phase-01-base/TD-01`, `phase-01-base/TD-02`).
3. Versionar o wrapper `backend/gradlew` + `backend/gradle/wrapper/` e `backend/settings.gradle.kts` com o nome do projeto.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `Application` (context bootstrap) | Integration: `contextLoads` (Spring context inicializa) | `backend/src/test/java/.../ApplicationTests.java` |

**Dependencies:** SI-1.1

**Acceptance criteria:**

- `cd backend && ./gradlew build` conclui sem erros e produz o artefato Jar.
- `./gradlew bootRun` inicializa o contexto Spring sem exceções de configuração.
- O wrapper `./gradlew` está versionado (build reprodutível sem Gradle global instalado).

---

### SI-1.3 — Estrutura de pacotes Clean Architecture (backend)

**Description:** Criar a estrutura de pacotes Clean Architecture que todas as fases seguintes (02–07) devem seguir, isolando domínio de infraestrutura.

**Technical actions:**

1. Criar os pacotes `domain/entities/`, `domain/repositories/` (interfaces), `application/usecases/`, `infrastructure/`, `interfaces/controllers/` sob o package raiz do backend (per `phase-01-base/TD-05`).
2. Adicionar `.gitkeep` (ou `package-info.java`) por pacote para versionar diretórios vazios.
3. Documentar no `README` do backend a convenção arquitetural: domínio sem anotações de infraestrutura (`@Entity`), com objetos de mapeamento separados entre domínio e infraestrutura (per `phase-01-base/TD-05`).

**Tests:** _(empty — Infra/estrutural; smoke-gated pelo build de SI-1.2)_

**Dependencies:** SI-1.2

**Acceptance criteria:**

- Os cinco pacotes Clean Architecture existem sob o package raiz do backend.
- `cd backend && ./gradlew build` continua passando com a nova estrutura de pacotes.
- A convenção "sem `@Entity` no domínio" está documentada e legível para as fases seguintes.

---

### SI-1.4 — Layout de variáveis de ambiente (`.env` raiz)

**Description:** Estabelecer `.env` único na raiz como source-of-truth de configuração de desenvolvimento, com `.env.example` versionado para onboarding.

**Technical actions:**

1. Criar `.env.example` na raiz com chaves de configuração de dev (credenciais e nome do banco PostgreSQL, portas, host) com valores fictícios (per `phase-01-base/TD-04`).
2. Garantir que `.env` permaneça no `.gitignore` da raiz (não versionado) e que `.env.example` seja versionado (per `phase-01-base/TD-04`).
3. Documentar no `README` o passo de onboarding `cp .env.example .env`.

**Tests:** _(empty — Infra)_

**Dependencies:** SI-1.1

**Acceptance criteria:**

- `.env.example` está versionado na raiz e `.env` NÃO está versionado (`git status` confirma).
- `cp .env.example .env` produz um arquivo com todas as chaves necessárias para subir o ambiente.
- Nenhum `.env` por subprojeto existe (layout de `.env` único confirmado).

---

### SI-1.5 — Orquestração do ambiente local via Docker Compose

**Description:** Único `compose.yaml` na raiz orquestrando PostgreSQL e o backend, com serviços auxiliares isolados por profile.

**Technical actions:**

1. Criar `compose.yaml` na raiz com os serviços `postgres` (PostgreSQL) e `backend` (per `phase-01-base/TD-03`).
2. Isolar serviços opcionais de tooling sob `profiles: [tools]` (ex.: cliente de DB), de modo que `docker compose up -d` padrão não os suba (per `phase-01-base/TD-03`).
3. Injetar as variáveis do `.env` da raiz nos containers via `env_file: .env` / `environment:` (per `phase-01-base/TD-04`).

**Tests:** _(empty — Infra)_

**Dependencies:** SI-1.2, SI-1.4

**Acceptance criteria:**

- `docker compose config` valida o arquivo sem erros de sintaxe ou variáveis indefinidas.
- `docker compose up -d` na raiz sobe `postgres` e `backend` e NÃO sobe serviços do profile `tools`.
- `docker compose --profile tools up -d` sobe adicionalmente os serviços auxiliares.

---

### SI-1.6 — Estrutura inicial de migrations Flyway (PostgreSQL)

**Description:** Configurar o Flyway e a estrutura de migrations versionadas em SQL puro, criando a baseline do schema sem nenhuma tabela de domínio ainda.

**Technical actions:**

1. Configurar o Flyway no backend (`spring.flyway.*` em `application.yml`, `locations: classpath:db/migration`) para aplicar migrations pendentes na inicialização (per `phase-01-base/TD-02`).
2. Criar `backend/src/main/resources/db/migration/V1__init_schema.sql` com a baseline (schema/extensões necessárias) — **sem tabelas de domínio** (per `phase-01-base/TD-02`).
3. Documentar a convenção de nomenclatura `V{versão}__{descrição}.sql` e migrations repetíveis `R__{descrição}.sql` no `README` do backend (per `phase-01-base/TD-02`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| Flyway migration `V1__init_schema` | Integration: backend sobe contra PostgreSQL e aplica a migration (Testcontainers ou DB de teste) | `backend/src/test/java/.../FlywayMigrationTests.java` |

**Dependencies:** SI-1.2, SI-1.5

**Acceptance criteria:**

- Ao subir o backend contra o PostgreSQL, o Flyway aplica `V1` e cria a tabela `flyway_schema_history`.
- Nenhuma tabela de domínio existe após a migration baseline (apenas schema/extensões + `flyway_schema_history`).
- Uma segunda inicialização não reaplica `V1` (migration já registrada como aplicada).

---

## Dependency Map

```
SI-1.1 — Repositório + estrutura de subprojetos (root)
├── SI-1.2 — Spring Boot (Gradle) — depends on SI-1.1
│   ├── SI-1.3 — Pacotes Clean Architecture — depends on SI-1.2
│   └── SI-1.5 — Docker Compose — depends on SI-1.2 + SI-1.4
│       └── SI-1.6 — Migrations Flyway — depends on SI-1.2 + SI-1.5
└── SI-1.4 — Layout de variáveis de ambiente — depends on SI-1.1
    └── (junta-se em SI-1.5)
```

Notas:

- `SI-1.5` é um join: requer o backend buildável (`SI-1.2`) e o `.env` materializado (`SI-1.4`).
- `SI-1.6` aplica migrations no PostgreSQL orquestrado por `SI-1.5`, portanto depende dele além do backend (`SI-1.2`).

---

## Deliverables

- [ ] SI-1.1 — Inicializar repositório e estrutura de subprojetos
- [ ] SI-1.2 — Inicializar projeto Spring Boot (backend) com Gradle
- [ ] SI-1.3 — Estrutura de pacotes Clean Architecture (backend)
- [ ] SI-1.4 — Layout de variáveis de ambiente (`.env` raiz)
- [ ] SI-1.5 — Orquestração do ambiente local via Docker Compose
- [ ] SI-1.6 — Estrutura inicial de migrations Flyway (PostgreSQL)

**Ambiente / Infra:**

- [ ] `cp .env.example .env && docker compose up -d` sobe `postgres` + `backend` na raiz (ambiente de desenvolvimento funcional).
- [ ] `docker compose config` valida o `compose.yaml` sem variáveis indefinidas.
- [ ] Flyway aplica `V1__init_schema` na inicialização e cria `flyway_schema_history` (banco configurado, sem tabelas de domínio).

**Full test suites:**

- [ ] Backend tests pass (`cd backend && ./gradlew test`)
- [ ] Backend builds successfully (`cd backend && ./gradlew build`)

_Frontend (`frontend/`) está adiado nesta fase — nenhum comando de teste/build/E2E de frontend se aplica._
