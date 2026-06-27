---
kind: phase
name: phase-2-canal-aggregate
sources_mtime:
  docs/project-plan.md: "2026-06-25T20:16:47"
  docs/decisions/technical-decisions-canal-aggregate.md: "2026-06-26T18:19:37"
  docs/decisions/technical-decisions-user-auth.md: "2026-06-26T18:31:14"
  docs/decisions/technical-decisions-phase-01-base.md: "2026-06-26T11:25:22"
  docs/phases/phase-02-user-auth/context.md: "2026-06-26T17:55:22"
  docs/phases/phase-1-phase-01-base/context.md: "2026-06-26T11:25:22"
---

# phase-2-canal-aggregate — Context

## Scope

**Phase name:** Cadastro, Login e Gerenciamento de Conta

**Capabilities** (literal, `docs/project-plan.md`):

- Serviço de envio de e-mails transacionais
- Cadastro de usuário com e-mail e senha
- Criação automática do canal do usuário a partir do prefixo do e-mail
- Confirmação de conta via e-mail com link de ativação
- Login e controle de sessão do usuário
- Logout
- Recuperação de senha: solicitação via e-mail → link com token → redefinição
- Telas de cadastro, login, confirmação de conta e recuperação de senha

**Out of scope:** Upload e processamento de vídeo, gerenciamento de vídeos, edição de canal, interações sociais (likes, comentários, inscrições), busca e home page — todos diferidos para fases posteriores.

**Deliverables:** Fluxo completo de cadastro → confirmação → login → recuperação de senha funcionando. Canal criado automaticamente para cada usuário.

**Affected subprojects:** backend/, frontend/

**Deferred subprojects:** (nenhum — backend e frontend são ativados nesta fase)

**Sequencing notes:** Depende da Fase 01. Esta é uma fatia da Fase 02 (depends_on_slices: [user-auth]).

**Neighbors (for boundary detection only):**

- **Phase 01:** Fundação — repositório, ambiente de desenvolvimento, inicialização Angular/Spring Boot, schema PostgreSQL, Docker Compose.
- **Phase 03:** Upload e processamento de vídeos — uploads grandes assíncronos, serviços de armazenamento, streaming, extração de metadados.

---

## Decisions Index

| Ref | Source | Scope | Topic | Status | Decision | Libraries |
|-----|--------|-------|-------|--------|----------|-----------|
| canal-aggregate/TD-01 | phase | Backend | Canal Creation Trigger | decided | Option A | — |
| canal-aggregate/TD-02 | phase | Backend | Channel Slug Collision Strategy | decided | Option D | — |

_Source files:_

- canal-aggregate — `docs/decisions/technical-decisions-canal-aggregate.md` (scope_type: phase)

---

## Capability Coverage

| Capability (from project-plan.md) | Covered by |
|-----------------------------------|------------|
| Criação automática do canal do usuário a partir do prefixo do e-mail | canal-aggregate/TD-01, canal-aggregate/TD-02 |

---

## Decisions Detail

### canal-aggregate/TD-01

**Recommendation:** Criação síncrona dentro do `RegisterUserUseCase` é a implementação mais simples e segura quando a criação do canal é uma invariante rígida do cadastro; atomicidade via `@Transactional` no adaptador de infraestrutura; ambas as portas (`UserRepository`, `CanalRepository`) são injetadas como interfaces, preservando o Clean Architecture. Domain events são valiosos quando o efeito colateral é opcional ou eventual — nenhum dos dois se aplica aqui em Phase 02.
**Libraries:** —

### canal-aggregate/TD-02

**Recommendation:** Fallback determinístico via `{email_prefix}_{primeiros-4-hex-do-userId}` é race-condition safe (UUID atribuído na construção da entidade, derivação do slug é determinística), não requer loop de retry ao DB (no máximo uma verificação de fallback), mantém slugs primários limpos para o caso comum e produz resultado previsível e reproduzível.
**Libraries:** —

---

## Inherited Decisions Detail

### user-auth/TD-01

**Recommendation:** JWT em httpOnly cookie combina a escalabilidade stateless do JWT com a postura de segurança superior de um httpOnly cookie (sem XSS via JS); o `BearerTokenResolver` customizado é mínimo; `SameSite=Strict` descarta CSRF sem CSRF token adicional; Angular com `withCredentials: true` funciona identicamente sem lock-in de transporte no cliente.
**Libraries:** —

### user-auth/TD-02

**Recommendation:** BCrypt é o padrão do Spring Security, sem dependência adicional, e battle-tested para o volume de uma plataforma de vídeo em early stage; `DelegatingPasswordEncoder` permite migrar transparentemente para Argon2 em fases futuras sem reprocessar todas as senhas.
**Libraries:** —

### user-auth/TD-03

**Recommendation:** Token opaco em DB é a solução mais simples, explícita e revogável para os dois fluxos da Fase 02; a tabela `user_tokens` é uma migration direta; "reenviar e-mail de confirmação" invalida tokens anteriores naturalmente; sem dependência de comportamento interno do Spring Security.
**Libraries:** —

### user-auth/TD-04

**Recommendation:** Spring Boot 3.x suporta nativamente com um único `application.properties` flag; Angular pode definir uma interface `ProblemDetail` com campos de extensão por domínio; padronização garante consistência entre todos os endpoints das Fases 02–07 sem cerimônia adicional.
**Libraries:** —

### user-auth/TD-05

**Recommendation:** A biblioteca oficial do Angular elimina o risco de incompatibilidade de versão ao longo das 7 fases do projeto; todos os componentes necessários para a Phase 02 estão disponíveis como standalone imports; a accessibilidade embutida cobre os requisitos de UX sem implementação adicional.
**Libraries:** @angular/material

### user-auth/TD-06

**Recommendation:** Suporte nativo no Hibernate 6 sem dependência adicional; não-sequencial (previne enumeração de userId via JWT); diretamente compatível com `canal-aggregate/TD-02`'s slug fallback; a fragmentação de índice B-tree do UUID v4 é irrelevante no volume de uma plataforma em early stage.
**Libraries:** —

### phase-01-base/TD-01

**Recommendation:** `build.gradle.kts` oferece builds incrementais mais rápidos via build cache e daemon, DSL Kotlin tipada com autocomplete em IDEs, e maior flexibilidade para tasks customizadas conforme o projeto cresce; o wrapper `./gradlew` mantém o build reproduzível sem instalação global.
**Libraries:** —

### phase-01-base/TD-02

**Recommendation:** SQL puro elimina camada de abstração desnecessária; rollback forward-only é a prática padrão em equipes com continuous delivery; auto-config Spring Boot com zero configuração adicional além do starter.
**Libraries:** Flyway

### phase-01-base/TD-03

**Recommendation:** Único `compose.yaml` na raiz com profiles para serviços opcionais; alinha com `docker-k8s.md`; zero fricção de onboarding.
**Libraries:** —

### phase-01-base/TD-04

**Recommendation:** `.env` único na raiz é o padrão Docker Compose nativo; Angular não precisa de `.env` em runtime (usa `environment.ts` versionado); onboarding de linha única.
**Libraries:** —

### phase-01-base/TD-05

**Recommendation:** Separa explicitamente regras de negócio de domínio (`domain/entities`, `domain/repositories`) das de aplicação (`application/usecases`), com Use Cases como classes únicas altamente testáveis; domínio livre de anotações de infraestrutura, com objetos de mapeamento separados entre domínio e persistência.
**Libraries:** —

---

## Inherited Conventions

- JWT em httpOnly cookie (`SameSite=Strict`); Angular com `withCredentials: true`; `BearerTokenResolver` customizado no Spring Security _(from phase 02 / user-auth)_
- BCrypt com `DelegatingPasswordEncoder`; prefixo `{bcrypt}` permite migração futura transparente _(from phase 02 / user-auth)_
- Tokens de uso único opacos em tabela `user_tokens` (`type`, `expires_at`, `used_at`); reenvio invalida tokens anteriores _(from phase 02 / user-auth)_
- RFC 7807 `ProblemDetail` para todos os erros da API; extensível com campos customizados por domínio _(from phase 02 / user-auth)_
- Angular Material standalone components; Angular 22.x LTS _(from phase 02 / user-auth)_
- UUID v4 para chave primária da entidade User (`@GeneratedValue(strategy = GenerationType.UUID)`); armazenado como `uuid` no PostgreSQL _(from phase 02 / user-auth)_
- Gradle Kotlin DSL (`build.gradle.kts`); wrapper `./gradlew`; sem instalação global _(from phase 01)_
- Flyway SQL puro; migrations `V{N}__{descrição}.sql`; forward-only _(from phase 01)_
- `compose.yaml` único na raiz; serviços opcionais em profiles _(from phase 01)_
- `.env` na raiz (não versionado); `.env.example` versionado como template de onboarding _(from phase 01)_
- Clean Architecture: `domain/entities` (sem anotações de infra), `domain/repositories` (interfaces), `application/usecases` (single-action), `infrastructure`, `interfaces/controllers` _(from phase 01)_

---

## Inherited Deferred Capabilities

_No inherited deferred capabilities._

---

## Non-UI / Deferred Capabilities

_None._

---

## Testing Requirements

### backend/

_No testing guide skill available — layer requirements deferred to implementation. Conventions from `backend/CLAUDE.md`:_

| Artifact type | Required layers |
|---------------|-----------------|
| Domain entity / value object | Unit (sem Spring context, sem DB — Mockito) |
| Use Case | Unit (mocks via ports — `UserRepository`, `CanalRepository`) |
| Infrastructure adapter (JPA) | Integration (Testcontainers PostgreSQL real) |
| Controller (REST) | Integration (Testcontainers PostgreSQL real + MockMvc) |

_Naming convention: `given{Contexto}_when{Acao}_then{Resultado}`_

### frontend/

_No testing guide skill available — layer requirements deferred to implementation._
