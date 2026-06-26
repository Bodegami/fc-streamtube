---
scope_type: phase
related_phases: [1]
status: decided
date: 2026-06-25
scope_description: "Decisões técnicas para a Fase 01 — fundação do projeto: build tool, migration tool, Docker Compose, env vars e arquitetura Spring Boot"
covers_capabilities:
  - "Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)"
  - "Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados"
  - "Ambiente de desenvolvimento local com todos os serviços via Docker Compose"
  - "Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)"
---

# Technical Decisions — Phase 01: Base do Projeto

_Subprojects in scope:_

- `backend/` — Spring Boot (Java 25); alvo das decisões de build tool, migration tool e arquitetura de pacotes
- `frontend/` — Angular LTS; **explicitamente adiado** nesta fase ("será criado depois, não agora") — nenhuma decisão técnica neste documento
- Repo-wide — estrutura do Docker Compose e layout de variáveis de ambiente

---

## TD-01: Build Tool — Maven vs Gradle

**Scope:** Backend

**Capability:** "Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)"

**Context:** O projeto Spring Boot requer uma ferramenta de build para gerenciar dependências, compilação, testes e empacotamento. A escolha define a estrutura do subprojeto `backend/`, os scripts de CI/CD e o workflow local de desenvolvimento. A ferramenta escolhida impacta diretamente os comandos documentados no `machine-environment.md`.

**Options:**

### Option A: Maven
- Usa `pom.xml` como descritor. Spring Boot provê `spring-boot-starter-parent` como POM pai, simplificando o gerenciamento de versões. O Spring Initializr gera `./mvnw` (wrapper) por padrão, eliminando a necessidade de instalação global.
- **Pros:** Default do Spring Initializr; `./mvnw` wrapper gerado automaticamente; `machine-environment.md` já documenta o workflow Maven (`./mvnw clean install`, SDKMAN como fallback); XML previsível e sem ambiguidade de DSL; onboarding imediato.
- **Cons:** Verbosidade XML para builds customizados; builds incrementais mais lentos que Gradle sem cache externo.

### Option B: Gradle (Kotlin DSL)
- Usa `build.gradle.kts`. Build cache + daemon permitem builds incrementais mais rápidos. Spring Boot suporta via plugin `org.springframework.boot`.
- **Pros:** Builds incrementais mais rápidos (build cache, daemon); DSL Kotlin é tipado com autocomplete em IDEs; mais flexível para tasks customizadas.
- **Cons:** Curva de aprendizado maior; `machine-environment.md` documenta apenas Maven, exigindo adição do Gradle ao workflow documentado; `build.gradle.kts` pode crescer em complexidade.

**Recommendation:** Option B (Gradle Kotlin DSL) — `build.gradle.kts` oferece builds incrementais mais rápidos via build cache e daemon, DSL Kotlin tipada com autocomplete em IDEs, e maior flexibilidade para tasks customizadas conforme o projeto cresce; o wrapper `./gradlew` mantém o build reprodutível sem instalação global (o workflow Gradle deve ser adicionado ao `machine-environment.md`, hoje documentado apenas para Maven).

**Decision:** _[Option B]_

---

## TD-02: Migration Tool — Flyway vs Liquibase

**Scope:** Backend

**Capability:** "Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)"

**Context:** O projeto necessita de uma ferramenta para versionar e aplicar migrações do schema PostgreSQL de forma controlada. Phase 01 cria apenas a estrutura inicial (sem tabelas ainda), mas a ferramenta escolhida define o padrão para todas as fases seguintes. Spring Boot tem auto-configuração nativa para ambas via starters.

**Options:**

### Option A: Flyway
- Migrações versionadas em SQL puro (`V1__description.sql`) armazenadas em `src/main/resources/db/migration/`. Spring Boot detecta `spring-boot-starter-flyway` e executa automaticamente as migrações pendentes na inicialização. Suporta migrações repetíveis (`R__description.sql`) para views e funções.
- **Pros:** SQL puro sem camada de abstração — qualquer dev com conhecimento de PostgreSQL lê e escreve migrações; auto-config Spring Boot nativa e trivial; nomenclatura explícita (`V{versão}__{descrição}.sql`); amplamente adotado no ecossistema Spring.
- **Cons:** Sem rollback automático na versão community (Flyway Teams pago); migrações são forward-only por design.

### Option B: Liquibase
- Changesets em XML, YAML, JSON ou SQL organizados em `src/main/resources/db/changelog/`. Spring Boot detecta `spring-boot-starter-liquibase` e aplica changesets não executados. Rollback nativo na versão community via tag `<rollback>`.
- **Pros:** Rollback nativo na versão gratuita; suporte a múltiplos formatos; mais poderoso para diff e geração automática de changesets.
- **Cons:** Changesets XML/YAML adicionam verbosidade e uma camada de abstração sobre SQL; curva de aprendizado maior; overhead de configuração mais alto.

**Recommendation:** Option A (Flyway) — SQL puro elimina camada de abstração desnecessária; rollback forward-only é a prática padrão em equipes com continuous delivery (novas migrações corrigem problemas ao invés de reverter); auto-config Spring Boot com zero configuração adicional além do starter.

**Decision:** _[Option A]_

---

## TD-03: Estrutura do Docker Compose

**Scope:** Repo-wide

**Capability:** "Ambiente de desenvolvimento local com todos os serviços via Docker Compose"

**Context:** O ambiente de desenvolvimento precisa orquestrar múltiplos serviços: API Spring Boot, PostgreSQL, MinIO (object storage), Message Queue (TBD — Phase 03), Video Worker e um serviço de email para testes (ex: Mailpit). A estrutura do `compose.yaml` define o workflow de inicialização para desenvolvedores e para CI/CD.

**Options:**

### Option A: `compose.yaml` único na raiz do repositório
- Um único arquivo declara todos os serviços. Profiles Docker Compose (`profiles: [tools]`) isolam serviços opcionais (ex: Mailpit, adminer). Desenvolvedores executam `docker compose up -d` na raiz.
- **Pros:** Alinha com os comandos documentados em `docker-k8s.md` (execução na raiz); one-command startup; sem navegação entre diretórios; padrão mais comum para projetos multi-subprojeto.
- **Cons:** Arquivo cresce conforme o projeto avança (Phase 03 adiciona queue + worker).

### Option B: Compose files múltiplos com `include`
- `compose.yaml` base na raiz + arquivos por ambiente (`compose.dev.yaml`, `compose.ci.yaml`) usando a diretiva `include:`.
- **Pros:** Separação por ambiente; arquivo base menor.
- **Cons:** Desenvolvedores precisam saber qual combinação usar; mais fricção no onboarding; `include:` tem suporte limitado em versões mais antigas do Compose CLI.

### Option C: `infra/compose.yaml` em subdiretório dedicado
- Toda configuração de infraestrutura em `infra/` separado de `backend/` e `frontend/`.
- **Pros:** Separação clara entre código e infraestrutura.
- **Cons:** Exige `docker compose -f infra/compose.yaml` em todo comando; rompe o workflow de `docker-k8s.md` que assume execução na raiz.

**Recommendation:** Option A — único `compose.yaml` na raiz com profiles para serviços opcionais; alinha com `docker-k8s.md`; zero fricção de onboarding. Profiles e `include` podem ser introduzidos quando a complexidade justificar.

**Decision:** _[Option A]_

---

## TD-04: Layout de Variáveis de Ambiente

**Scope:** Repo-wide

**Capability:** "Ambiente de desenvolvimento local com todos os serviços via Docker Compose" + "Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)"

**Context:** O projeto precisa de uma estratégia para gerenciar variáveis de ambiente de desenvolvimento: credenciais de banco, portas, chaves MinIO, configurações de email. Docker Compose as consome para configurar os serviços; Spring Boot as lê em runtime dentro do container. Angular usa um mecanismo diferente (`src/environments/environment.ts`, build-time), tornando este um concern Repo-wide sem impacto Cross-layer direto.

**Options:**

### Option A: `.env` único na raiz + `.env.example` versionado
- `.env` na raiz consumido pelo Docker Compose via interpolação `${VAR}` ou `env_file: .env`. O Spring Boot, rodando no container, lê as variáveis injetadas via `environment:` no `compose.yaml`. O `.env` fica no `.gitignore`; `.env.example` com valores fictícios é versionado como referência de onboarding. Angular usa `src/environments/environment.ts` para config de build-time (independente do `.env`).
- **Pros:** Single source of truth para configuração de dev; padrão amplamente adotado com Docker Compose; onboarding trivial (`cp .env.example .env`).
- **Cons:** Namespace compartilhado entre todos os serviços — possibilidade de colisão de nomes em projetos com muitos serviços.

### Option B: `.env` por subprojeto (`backend/.env`)
- Docker Compose aponta para `backend/.env` via `env_file`. Frontend usa `src/environments/environment.ts` (independente).
- **Pros:** Namespace isolado por subprojeto; clareza de quais vars pertencem ao backend.
- **Cons:** `env_file` com path relativo no `compose.yaml` na raiz exige `env_file: ./backend/.env`; onboarding menos intuitivo (dois arquivos de exemplo para manter).

**Recommendation:** Option A — `.env` único na raiz é o padrão Docker Compose nativo; Angular não precisa de `.env` em runtime (usa `environment.ts` versionado); onboarding de linha única.

**Decision:** _[Option A]_

---

## TD-05: Spring Boot Architecture Pattern

**Scope:** Backend

**Capability:** "Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados"

**Context:** A estrutura de pacotes definida na inicialização do `backend/` é o padrão arquitetural que todas as fases seguintes devem seguir. Os guides `clean-architecture.md` e `software-architecture.md` documentam padrões disponíveis mas não prescrevem qual adotar para este projeto. A escolha impacta como Controllers, Services, Repositories e Domain objects são organizados em cada feature das Fases 02–07.

**Options:**

### Option A: Hexagonal Architecture (Ports & Adapters)
- Estrutura de pacotes: `domain/`, `application/ports/` (interfaces de entrada e saída), `infrastructure/adapters/` (implementações). Controllers REST são Driving Adapters. Repositórios JPA são Driven Adapters (implementam portas de saída definidas no domínio). Documentado em `~/.claude/docs/guides/software-architecture.md` com exemplos Spring/Micronaut.
- **Pros:** Nomenclatura explícita dos contratos (porta = interface, adaptador = implementação); testes de unidade isolados (mockar a porta de saída); Spring Boot mapeia naturalmente (Controllers → Driving Adapters, `@Repository` → Driven Adapter); sem proibição de anotações JPA em adapters.
- **Cons:** Mais estrutura inicial que uma arquitetura layered simples; pode parecer over-engineered para as primeiras fases do projeto.

### Option B: Clean Architecture
- Pacotes: `domain/entities/`, `domain/repositories/` (interfaces), `application/usecases/`, `infrastructure/`, `interfaces/controllers/`. Use Cases são classes granulares (`CreateUserUseCase`, etc.). Documentado em `~/.claude/docs/guides/clean-architecture.md`.
- **Pros:** Separação explícita entre regras de negócio de domínio e de aplicação; Use Cases como classes únicas são altamente testáveis; alinhado com DDD (`domain-driven-design.md`).
- **Cons:** O guide proíbe anotações de infraestrutura (`@Entity`) nas entidades de domínio — exige objetos de mapeamento separados entre domínio e infraestrutura (mais cerimônia por feature); cada Use Case como classe aumenta o número de arquivos.

### Option C: Spring Layered (tradicional)
- Pacotes: `controller/`, `service/`, `repository/`, `entity/`. Padrão CRUD Spring sem dependency inversion explícita.
- **Pros:** Mínima cerimônia; familiar para a maioria dos desenvolvedores Spring.
- **Cons:** `clean-architecture.md` rejeita explicitamente este padrão ("veto de anotações de infraestrutura nas entidades"); nenhum dos guides do projeto o recomenda; acoplamento entre camadas dificulta testes unitários.

**Recommendation:** Option B (Clean Architecture) — separa explicitamente regras de negócio de domínio (`domain/entities`, `domain/repositories`) das de aplicação (`application/usecases`), com Use Cases como classes únicas altamente testáveis e alinhamento com DDD (`domain-driven-design.md`); o domínio fica livre de anotações de infraestrutura, com objetos de mapeamento separados entre domínio e persistência. Option C é explicitamente rejeitado pelos guides do projeto.

**Decision:** _[Option B]_

---

## Decisions Summary

| ID | Scope | Decision | Recommendation | Choice |
|----|-------|----------|----------------|--------|
| TD-01 | Backend | Build Tool — Maven vs Gradle | Gradle (Kotlin DSL) | _[Option B]_ |
| TD-02 | Backend | Migration Tool — Flyway vs Liquibase | Flyway | _[Option A]_ |
| TD-03 | Repo-wide | Estrutura do Docker Compose | `compose.yaml` único na raiz | _[Option A]_ |
| TD-04 | Repo-wide | Layout de Variáveis de Ambiente | `.env` único na raiz | _[Option A]_ |
| TD-05 | Backend | Spring Boot Architecture Pattern | Clean Architecture | _[Option B]_ |
