---
kind: phase
name: phase-02-user-auth
sources_mtime:
  docs/project-plan.md: "2026-06-25T20:16:47-0300"
  docs/decisions/technical-decisions-user-auth.md: "2026-06-26T18:31:14-0300"
  docs/phases/phase-1-phase-01-base/context.md: "2026-06-26T11:25:22-0300"
  docs/phases/phase-1-ia-coding/context.md: "2026-06-26T11:25:22-0300"
  docs/inventories/screen-inventory-phase-02-user-auth.md: "2026-06-26T15:12:12-0300"
  docs/phases/phase-02-user-auth/library-refs.md: "2026-06-26T20:05:15-0300"
---

# phase-02-user-auth — Context

## Scope

**Phase name:** Fase 02 — Cadastro, Login e Gerenciamento de Conta

**Capabilities** (literal, `docs/project-plan.md`):

- Serviço de envio de e-mails transacionais
- Cadastro de usuário com e-mail e senha
- Criação automática do canal do usuário a partir do prefixo do e-mail
- Confirmação de conta via e-mail com link de ativação
- Login e controle de sessão do usuário
- Logout
- Recuperação de senha: solicitação via e-mail → link com token → redefinição
- Telas de cadastro, login, confirmação de conta e recuperação de senha

**Out of scope:** Upload e processamento de vídeos, gerenciamento de vídeos e canal, página de visualização, interações sociais (likes, comentários, inscrições), página inicial e busca — reservados para Fases 03–07. Logout não possui tela dedicada (ação de header, escopo da Fase 07).

**Deliverables:** Fluxo completo de cadastro → confirmação → login → recuperação de senha funcionando. Canal criado automaticamente para cada usuário.

**Affected subprojects:** `backend/` (lógica de autenticação — Spring Security, Spring Mail, tabelas `users`, `user_tokens`, canal automático); `frontend/` (telas de auth com Angular Material).

**Deferred subprojects:** nenhum — ambos os subprojetos ativos nesta fase.

**Sequencing notes:** Depende de Fase 01 (base configurada: Gradle, Flyway, Docker Compose, Clean Architecture). Fases 03 e 06 dependem desta fase para usuário autenticado disponível.

**Neighbors (for boundary detection only):**

- **Fase 01 (prior):** Configuração Base do Projeto — fundação técnica (Gradle, Flyway, Docker Compose, Clean Architecture, variáveis de ambiente).
- **Fase 03 (following):** Upload e Processamento de Vídeos — assume usuário autenticado; consome o mecanismo de sessão definido aqui (httpOnly cookie + endpoint `/api/me`).

---

## Decisions Index

| Ref | Source | Scope | Topic | Status | Decision | Libraries |
|-----|--------|-------|-------|--------|----------|-----------|
| user-auth/TD-01 | phase | Cross-layer | Auth Strategy & Token Transport | decided | JWT em httpOnly cookie (Option B) | spring-security-oauth2-resource-server |
| user-auth/TD-02 | phase | Backend | Password Hashing Algorithm | decided | BCrypt (Option A) | — |
| user-auth/TD-03 | phase | Backend | One-Time Token Strategy | decided | Token opaco em DB (Option A) | — |
| user-auth/TD-04 | phase | Cross-layer | API Error Response Contract | decided | RFC 7807 ProblemDetail (Option A) | — |
| user-auth/TD-05 | phase | Frontend | Angular UI Component Library | decided | Angular Material (Option A) | @angular/material |
| user-auth/TD-06 | phase | Backend | User Entity Primary Key Type | decided | UUID v4 nativo Hibernate 6 (Option A) | — |

_Source files:_

- user-auth — `docs/decisions/technical-decisions-user-auth.md` (scope_type: phase)

---

## Capability Coverage

| Capability (from project-plan.md) | Covered by |
|-----------------------------------|------------|
| Serviço de envio de e-mails transacionais | user-auth/TD-03 |
| Cadastro de usuário com e-mail e senha | user-auth/TD-02, user-auth/TD-04, user-auth/TD-06 |
| Criação automática do canal do usuário a partir do prefixo do e-mail | — _(sem TD neste slice — coberto pelo slice sibling `canal-aggregate`)_ |
| Confirmação de conta via e-mail com link de ativação | user-auth/TD-03 |
| Login e controle de sessão do usuário | user-auth/TD-01, user-auth/TD-04 |
| Logout | user-auth/TD-01 |
| Recuperação de senha: solicitação via e-mail → link com token → redefinição | user-auth/TD-03 |
| Telas de cadastro, login, confirmação de conta e recuperação de senha | user-auth/TD-04, user-auth/TD-05 |

---

## Decisions Detail

### user-auth/TD-01

**Recommendation:** JWT em httpOnly cookie combina a escalabilidade stateless do JWT com a postura de segurança superior de um httpOnly cookie (sem XSS via JS); o `BearerTokenResolver` customizado é mínimo; `SameSite=Strict` descarta CSRF sem CSRF token adicional; Angular com `withCredentials: true` funciona identicamente para Option B e C (sem lock-in de transporte no cliente). Evita adicionar Redis ou tabela de sessão JDBC em Phase 02 (Option C). Evita o risco XSS de localStorage (Option A).
**Libraries:** spring-security-oauth2-resource-server
**Renders in:** Backend (Spring Security `BearerTokenResolver`) + Frontend (Angular `HttpClient` com `withCredentials: true` + endpoint `/api/me`)

### user-auth/TD-02

**Recommendation:** BCrypt é o padrão do Spring Security, sem dependência adicional, e battle-tested para o volume de uma plataforma de vídeo em early stage; `DelegatingPasswordEncoder` permite migrar transparentemente para Argon2 em fases futuras sem reprocessar todas as senhas (o `{id}` prefixo identifica o algoritmo por hash).
**Libraries:** —

### user-auth/TD-03

**Recommendation:** token opaco em DB é a solução mais simples, explícita e revogável para os dois fluxos da Fase 02; a tabela `user_tokens` é uma migration direta; "reenviar e-mail de confirmação" invalida tokens anteriores naturalmente; sem dependência de comportamento interno do Spring Security que pode mudar entre minor releases.
**Libraries:** —

### user-auth/TD-04

**Recommendation:** Spring Boot 3.x suporta nativamente com um único `application.properties` flag; Angular pode definir uma interface `ProblemDetail` com campos de extensão por domínio (ex: `invalidFields: Record<string, string>` para erros de validação de formulário); padronização garante consistência entre todos os endpoints das Fases 02–07 sem cerimônia adicional.
**Libraries:** —
**Renders in:** Frontend (Angular interface `ProblemDetail` + `ErrorHandlerService` genérico)

### user-auth/TD-05

**Recommendation:** a biblioteca oficial do Angular elimina o risco de incompatibilidade de versão ao longo das 7 fases do projeto; todos os componentes necessários para a Phase 02 (form fields, botões, snackbar, cards) estão disponíveis como standalone imports; a accessibilidade embutida cobre os requisitos de UX sem implementação adicional.
**Libraries:** @angular/material
**Renders in:** Frontend

### user-auth/TD-06

**Recommendation:** UUID v4 — suporte nativo no Hibernate 6 sem dependência adicional; não-sequencial (previne enumeração de userId via JWT); diretamente compatível com `canal-aggregate/TD-02`'s slug fallback; a fragmentação de índice B-tree do UUID v4 é irrelevante no volume de uma plataforma em early stage.
**Libraries:** —

---

## Inherited Decisions Detail

### phase-01-base/TD-01

**Recommendation:** `build.gradle.kts` oferece builds incrementais mais rápidos via build cache e daemon, DSL Kotlin tipada com autocomplete em IDEs, e maior flexibilidade para tasks customizadas conforme o projeto cresce; o wrapper `./gradlew` mantém o build reprodutível sem instalação global.
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

**Recommendation:** separa explicitamente regras de negócio de domínio (`domain/entities`, `domain/repositories`) das de aplicação (`application/usecases`), com Use Cases como classes únicas altamente testáveis e alinhamento com DDD; o domínio fica livre de anotações de infraestrutura, com objetos de mapeamento separados entre domínio e persistência.
**Libraries:** —

### ia-coding/TD-01

**Recommendation:** o ambiente de desenvolvimento já possui configuração global extensa e matura (guides de arquitetura, testing, Git, Docker), eliminando overhead de configuração do zero; o mecanismo de `CLAUDE.md` por projeto/subprojeto é exatamente o padrão documentado no hub global.
**Libraries:** —

### ia-coding/TD-02

**Recommendation:** alinha com o padrão de três níveis documentado no hub global; `backend/CLAUDE.md` e `frontend/CLAUDE.md` podem conter contexto mínimo (stack, comandos, referência à arquitetura) na Fase 01 e crescer organicamente com cada fase; Claude Code usa o arquivo mais próximo automaticamente.
**Libraries:** —

---

## Inherited Conventions

- Build tool backend: Gradle Kotlin DSL (`build.gradle.kts`); wrapper `./gradlew` versionado — sem Gradle global _(from phase-1-phase-01-base)_
- Migrations: Flyway com SQL puro, nomeação `V{N}__{descrição}.sql`; forward-only (novas migrations corrigem, sem rollback) _(from phase-1-phase-01-base)_
- Docker: único `compose.yaml` na raiz; serviços de tooling sob `profiles: [tools]`; env vars injetados via `env_file: .env` _(from phase-1-phase-01-base)_
- Env vars: `.env` único na raiz (não versionado); `.env.example` versionado como template de onboarding _(from phase-1-phase-01-base)_
- Arquitetura backend: Clean Architecture — `domain/entities`, `domain/repositories` (interfaces), `application/usecases`, `infrastructure`, `interfaces/controllers`; domínio sem anotações de infraestrutura (`@Entity`); objetos de mapeamento separados _(from phase-1-phase-01-base)_
- AI coding: Claude Code; `CLAUDE.md` em 3 níveis (global hub → project hub → subproject context); subproject `CLAUDE.md` cresce por fase _(from phase-1-ia-coding)_

---

## Inherited Deferred Capabilities

_No inherited deferred capabilities._

---

## UI Inventory

**Source:** `docs/inventories/screen-inventory-phase-02-user-auth.md`
**Screens in scope:** 4

### UI ↔ Capability Join

| Screen | Route | Verb | Capability | Covering Component |
|--------|-------|------|------------|-------------------|
| Create user account | `/signup` | registrar novo usuário com nome, email e senha | Cadastro de usuário com e-mail e senha | "Create account" submit button (MatButton) |
| User login | `/login` | autenticar usuário com email e senha | Login e controle de sessão do usuário | "Sign in" submit button (MatButton) |
| Reset password | `/forgot-password` | solicitar link de reset de senha por email | Recuperação de senha: solicitação via e-mail → link com token → redefinição | "Send reset link" submit button (MatButton) |
| Set new password | `/reset-password` | redefinir senha usando token recebido por email | Recuperação de senha: solicitação via e-mail → link com token → redefinição | "Reset password" submit button (MatButton) |

### Server-connected Components

- `"Create account" submit button (MatButton)` (Create user account) — `Reuse?: new`
- `"Sign in" submit button (MatButton)` (User login) — `Reuse?: new`
- `"Send reset link" submit button (MatButton)` (Reset password) — `Reuse?: new`
- `"Reset password" submit button (MatButton)` (Set new password) — `Reuse?: new`

### Open Questions from Inventory

- ~~**OQ-01:** "Set new password" — should password fields include a visibility toggle (eye icon)?~~ ✓ **Resolved** — deferred to implementation. The "Create account" screen already establishes the pattern (`MatIconButton matSuffix`). Implementer adds the toggle to both fields without a separate Figma frame.
- ~~**OQ-02:** "Set new password" — what does the UI show when the reset token is expired or invalid?~~ ✓ **Resolved** — on invalid/expired token, redirect to `/forgot-password` and display a `MatSnackBar` with the message "Link expirado. Solicite um novo." No additional Figma frame required.
- ~~**OQ-03:** "Reset password" — auth footer reads "Sign up" instead of "Sign in".~~ ✓ **Resolved** — fixed manually in Figma on 2026-06-26.
- ~~**OQ-04:** "User login" — password field placeholder reads "Enter your email".~~ ✓ **Resolved** — fixed manually in Figma on 2026-06-26 (corrected to "Enter your password").
- ~~**OQ-05:** Figma MCP rate limit hit before updating "Set new password" placeholders.~~ ✓ **Resolved** — placeholders fixed manually in Figma on 2026-06-26.

---

## Non-UI / Deferred Capabilities

| Capability | Status | Rationale | TD refs |
|-----------|--------|-----------|---------|
| Serviço de envio de e-mails transacionais | non-ui | backend_service | user-auth/TD-03 |
| Confirmação de conta via e-mail com link de ativação | non-ui | no_dedicated_screen_confirmed | user-auth/TD-03 |
| Logout | non-ui | header_action_no_dedicated_screen | user-auth/TD-01 |

---

## Testing Requirements

### backend/

_No testing guide available (`testing-guide-backend` skill not found) — layer requirements deferred to implementation._

### frontend/

_No testing guide available (`testing-guide-frontend` skill not found) — layer requirements deferred to implementation._
