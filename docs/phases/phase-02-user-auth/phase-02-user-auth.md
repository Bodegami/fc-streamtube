---
kind: phase
name: phase-02-user-auth
test_specs_aware: true
sources_mtime:
  docs/phases/phase-02-user-auth/context.md: "2026-06-26T20:05:26-0300"
  docs/phases/phase-02-user-auth/library-refs.md: "2026-06-26T20:05:15-0300"
  docs/decisions/technical-decisions-user-auth.md: "2026-06-26T18:31:14-0300"
---

# Phase 02 — Cadastro, Login e Gerenciamento de Conta

## Objective

Implementar o fluxo completo de autenticação da Fase 02 — cadastro com nome/e-mail/senha, confirmação de conta por e-mail, login com sessão via JWT em `httpOnly` cookie, logout e recuperação de senha (solicitação → token por e-mail → redefinição) — com backend Spring Security / Spring Mail (tabelas `users` e `user_tokens`, hashing BCrypt, contrato de erro RFC 7807 `ProblemDetail`) e as 4 telas Angular Material (`/signup`, `/login`, `/forgot-password`, `/reset-password`). A criação automática do canal é responsabilidade do slice irmão `canal-aggregate` (`depends_on_slices: [user-auth]`) e está fora deste slice.

---

## Step Implementations

### SI-02.1 — Migrations Flyway (users, user_tokens)

**Description:** Criar as duas migrations SQL puras que materializam o schema de autenticação no PostgreSQL.

**Technical actions:**

1. Criar `backend/src/main/resources/db/migration/V2__create_users_table.sql` — tabela `users` com `id uuid PK`, `name`, `email unique not null`, `password_hash`, `email_confirmed boolean default false`, `created_at timestamptz default now()` (per `### Data Model → User`, `user-auth/TD-06`).
2. Criar `backend/src/main/resources/db/migration/V3__create_user_tokens_table.sql` — tabela `user_tokens` com `id uuid PK`, `user_id uuid FK → users`, `token unique not null`, `type`, `expires_at`, `used_at nullable`, `created_at` (per `### Data Model → UserToken`, `user-auth/TD-03`).
3. Adicionar índice único em `users(email)` e índice em `user_tokens(user_id, type)`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `V2`/`V3` migrations | Integration (Testcontainers): aplicar migrations e verificar schema/constraints | `backend/src/test/java/.../migration/AuthSchemaMigrationTest.java` |

**Dependencies:** none

**Acceptance criteria:**

- Aplicar as migrations em PostgreSQL limpo cria as tabelas `users` e `user_tokens` sem erro.
- Inserir dois `users` com o mesmo `email` viola a constraint `unique`.
- `users.email_confirmed` assume `false` quando omitido no insert.

---

### SI-02.2 — Entidades de domínio + mapeamento JPA + repositórios

**Description:** Modelar `User` e `UserToken` como entidades de domínio puras com objetos de mapeamento JPA separados e contratos de repositório, seguindo Clean Architecture.

**Technical actions:**

1. Criar `domain/entities/User` e `domain/entities/UserToken` — POJOs sem anotações de infraestrutura (per `backend/CLAUDE.md` — "sem `@Entity` no domínio"; `## Inherited Conventions`).
2. Criar `domain/repositories/UserRepository` e `domain/repositories/UserTokenRepository` — interfaces (contratos de persistência).
3. Criar mapeamentos JPA em `infrastructure/` (`UserJpaEntity`, `UserTokenJpaEntity` + mappers) com `id` UUID v4 nativo Hibernate 6 (per `user-auth/TD-06`).
4. Implementar os repositórios em `infrastructure/` (Spring Data JPA adapters) incluindo lookup por `email` e por `token`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `UserRepository` impl | Integration (Testcontainers): persist + findByEmail | `backend/src/test/java/.../UserRepositoryIT.java` |
| `UserTokenRepository` impl | Integration (Testcontainers): persist + findByToken | `backend/src/test/java/.../UserTokenRepositoryIT.java` |

**Dependencies:** SI-02.1 (tabelas devem existir)

**Acceptance criteria:**

- Persistir um `User` gera um `id` UUID não-sequencial automaticamente.
- `findByEmail` retorna o `User` persistido e vazio para e-mail inexistente.
- `findByToken` retorna o `UserToken` correspondente ao valor opaco informado.
- Classes em `domain/entities` não contêm anotações `@Entity`/`@Table`/`@Column`.

---

### SI-02.3 — Spring Security: JWT em httpOnly cookie + STATELESS + PasswordEncoder

**Description:** Configurar a infraestrutura de segurança — resource server JWT lendo o token do cookie, sessão stateless e encoder de senha — base para todos os endpoints autenticados.

**Technical actions:**

1. Configurar `JwtDecoder` (HMAC-SHA256 com chave simétrica via `NimbusJwtDecoder.withSecretKey`) e o emissor de JWT (per `library-refs.md → JwtDecoder`, `user-auth/TD-01`).
2. Implementar `BearerTokenResolver` customizado que extrai o JWT do cookie `access_token` (per `library-refs.md → Custom BearerTokenResolver`).
3. Configurar `SecurityFilterChain` com `oauth2ResourceServer().jwt()` + `bearerTokenResolver(...)` + `SessionCreationPolicy.STATELESS`, liberando os endpoints anônimos e exigindo auth em `/api/me` e `/api/auth/logout` (per `### Authorization Matrix`).
4. Definir bean `DelegatingPasswordEncoder` com BCrypt como default (per `user-auth/TD-02`).
5. Criar helper `ResponseCookie` para emitir/expirar o cookie `access_token` (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age=3600`) (per `library-refs.md → ResponseCookie`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `SecurityFilterChain` | Integration (Testcontainers + MockMvc): rota protegida sem cookie → 401; com JWT válido no cookie → 200 | `backend/src/test/java/.../SecurityConfigIT.java` |
| `PasswordEncoder` | Unit: encode/matches BCrypt com prefixo `{bcrypt}` | `backend/src/test/java/.../PasswordEncoderTest.java` |

**Dependencies:** none

**Acceptance criteria:**

- Requisição a `GET /api/me` sem o cookie `access_token` retorna `401`.
- Requisição com um JWT válido no cookie `access_token` é autenticada (não usa header `Authorization`).
- Senha codificada pelo encoder casa via `matches` e carrega o prefixo de algoritmo `{bcrypt}`.

---

### SI-02.4 — Contrato de erro RFC 7807 ProblemDetail

**Description:** Estabelecer o formato de resposta de erro da API (RFC 7807) e o handler global que traduz exceções de domínio nos `errorCode`s do catálogo — convenção herdada pelas Fases 03–07.

**Technical actions:**

1. Habilitar `spring.mvc.problemdetails.enabled=true` em `application.properties` (per `user-auth/TD-04`).
2. Criar exceções de domínio mapeando o `### Error Catalog` (`EmailAlreadyExistsException`, `InvalidCredentialsException`, `InvalidTokenException`, etc.).
3. Implementar `@RestControllerAdvice` global que converte cada exceção em `ProblemDetail` com `type`/`title`/`status`/`detail` e extensão `invalidFields` para `VALIDATION_ERROR` (per `### Error Catalog`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `GlobalExceptionHandler` | Integration (MockMvc): cada exceção → status + `ProblemDetail` esperado | `backend/src/test/java/.../GlobalExceptionHandlerIT.java` |

**Dependencies:** none

**Acceptance criteria:**

- Erro de validação de corpo retorna `400` com `ProblemDetail` contendo `invalidFields` por campo.
- Exceção de e-mail duplicado retorna `409` com `detail` correspondente a `EMAIL_ALREADY_EXISTS`.
- Toda resposta de erro tem `Content-Type: application/problem+json`.

---

### SI-02.5 — Serviço de e-mails transacionais (Spring Mail)

**Description:** Implementar o `EmailService` que despacha e-mails transacionais de forma assíncrona, cobrindo a capability "Serviço de envio de e-mails transacionais".

**Technical actions:**

1. Adicionar dependência `spring-boot-starter-mail` e configurar `JavaMailSender` via `application.properties` (host/porta/credenciais SMTP por env var, per `## Inherited Conventions → .env`).
2. Criar `EmailService` em `infrastructure/` com métodos `sendConfirmationEmail` e `sendPasswordResetEmail`, anotados `@Async` (per `### Events/Messages`).
3. Habilitar `@EnableAsync` e definir templates de corpo com os links `GET /api/auth/confirm?token=...` e `/reset-password?token=...` (per `### Events/Messages` payloads).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `EmailService` | Unit (Mockito): verifica montagem da mensagem + chamada ao `JavaMailSender` mockado | `backend/src/test/java/.../EmailServiceTest.java` |

**Dependencies:** none

**Acceptance criteria:**

- Chamar `sendConfirmationEmail` monta uma mensagem destinada ao e-mail do usuário contendo o link com o token de confirmação.
- Chamar `sendPasswordResetEmail` monta uma mensagem contendo o link `/reset-password?token=...`.
- Falha de SMTP não propaga exceção que reverta a operação chamadora (best-effort, per `### Events/Messages`).

---

### SI-02.6 — Cadastro de usuário: POST /api/auth/register

**Route:** POST /api/auth/register
**Test Specs:** see `backend/specs/auth-register.plan.md`

**Description:** Implementar o caso de uso de cadastro — criar usuário com senha hasheada e disparar o e-mail de confirmação.

**Technical actions:**

1. Criar `application/usecases/RegisterUserUseCase` — valida e-mail único, hashea a senha (per `user-auth/TD-02`), persiste `User` com `email_confirmed = false` (per `user-auth/TD-06`).
2. Gerar `UserToken` tipo `EMAIL_CONFIRMATION` (opaco, com `expires_at`) e disparar `EmailService.sendConfirmationEmail` (per `user-auth/TD-03`, `### Events/Messages`).
3. Criar `interfaces/controllers/AuthController#register` com request/response per `### API Contracts → POST /api/auth/register` e validação de corpo (`### API Contracts → Validation Rules`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `RegisterUserUseCase` | Unit (Mockito): happy path + e-mail duplicado lança exceção | `backend/src/test/java/.../RegisterUserUseCaseTest.java` |
| `AuthController#register` | Integration (Testcontainers + MockMvc): 201 + persistência + token emitido | `backend/src/test/java/.../RegisterControllerIT.java` |

**Dependencies:** SI-02.2 (entidades/repos), SI-02.3 (PasswordEncoder), SI-02.4 (erros), SI-02.5 (e-mail)

**Acceptance criteria:**

- `POST /api/auth/register` com corpo válido retorna `201` com `{ id, email, name }` e persiste o usuário com senha hasheada (nunca em texto puro).
- `POST /api/auth/register` com e-mail já cadastrado retorna `409` com `errorCode` `EMAIL_ALREADY_EXISTS`.
- `POST /api/auth/register` com corpo inválido retorna `400` com `VALIDATION_ERROR` e `invalidFields`.
- Cadastro bem-sucedido cria um `user_tokens` do tipo `EMAIL_CONFIRMATION` e dispara o e-mail de confirmação.

---

### SI-02.7 — Confirmação de conta: GET /api/auth/confirm + POST /api/auth/resend-confirmation

**Route:** GET /api/auth/confirm
**Test Specs:** see `backend/specs/auth-confirm.plan.md`

**Description:** Implementar a confirmação de conta via token de e-mail e o reenvio do e-mail de confirmação.

**Technical actions:**

1. Criar `ConfirmAccountUseCase` — valida o token `EMAIL_CONFIRMATION` (existência, não usado, não expirado), marca `User.email_confirmed = true` e `UserToken.used_at = now()` (per `user-auth/TD-03`).
2. Criar `ResendConfirmationUseCase` — invalida tokens `EMAIL_CONFIRMATION` anteriores do usuário, emite um novo e dispara o e-mail; resposta neutra (per `user-auth/TD-03`).
3. Criar `AuthController#confirm` (query `token`) e `AuthController#resendConfirmation` per `### API Contracts → GET /api/auth/confirm` e `POST /api/auth/resend-confirmation`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ConfirmAccountUseCase` | Unit (Mockito): token válido confirma; token expirado/usado lança `InvalidTokenException` | `backend/src/test/java/.../ConfirmAccountUseCaseTest.java` |
| `AuthController#confirm`/`#resendConfirmation` | Integration (Testcontainers + MockMvc): 204/202 + efeitos de DB | `backend/src/test/java/.../ConfirmControllerIT.java` |

**Dependencies:** SI-02.2, SI-02.4, SI-02.5, SI-02.6 (token emitido no cadastro)

**Acceptance criteria:**

- `GET /api/auth/confirm?token=<válido>` retorna `204` e marca `email_confirmed = true` e o token como usado.
- `GET /api/auth/confirm?token=<expirado|usado|inexistente>` retorna `410` com `errorCode` `INVALID_TOKEN`.
- `POST /api/auth/resend-confirmation` retorna `202`, invalida tokens de confirmação anteriores e dispara um novo e-mail.

---

### SI-02.8 — Login e sessão: POST /api/auth/login + POST /api/auth/logout + GET /api/me

**Route:** POST /api/auth/login
**Test Specs:** see `backend/specs/auth-login.plan.md`

**Description:** Implementar autenticação com emissão do JWT em cookie `httpOnly`, encerramento de sessão e endpoint de sessão atual.

**Technical actions:**

1. Criar `LoginUseCase` — valida credenciais (e-mail + `matches` BCrypt), emite JWT e seta o cookie `access_token` via `ResponseCookie` (per `user-auth/TD-01`, `### API Contracts → POST /api/auth/login`).
2. Criar `AuthController#logout` que expira o cookie `access_token` (`Max-Age=0`); exige autenticação (per `### Authorization Matrix`).
3. Criar `AuthController#me` (`GET /api/me`) que retorna `{ id, email, name }` do usuário autenticado a partir do JWT, ou `401` (per `### API Contracts → GET /api/me`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `LoginUseCase` | Unit (Mockito): credenciais válidas/invalidas | `backend/src/test/java/.../LoginUseCaseTest.java` |
| `AuthController` login/logout/me | Integration (Testcontainers + MockMvc): cookie setado/expirado, /api/me 200 vs 401 | `backend/src/test/java/.../SessionControllerIT.java` |

**Dependencies:** SI-02.2, SI-02.3 (security + cookie helper), SI-02.4

**Acceptance criteria:**

- `POST /api/auth/login` com credenciais válidas retorna `200` com `{ id, email, name }` e `Set-Cookie: access_token` com `HttpOnly`, `Secure`, `SameSite=Strict`.
- `POST /api/auth/login` com senha incorreta retorna `401` com `errorCode` `INVALID_CREDENTIALS`.
- `GET /api/me` com cookie válido retorna `200` com os dados do usuário; sem cookie retorna `401` `UNAUTHENTICATED`.
- `POST /api/auth/logout` autenticado retorna `204` e emite `Set-Cookie` expirando `access_token`.

---

### SI-02.9 — Recuperação de senha: POST /api/auth/forgot-password + POST /api/auth/reset-password

**Route:** POST /api/auth/forgot-password
**Test Specs:** see `backend/specs/auth-forgot-password.plan.md`

**Description:** Implementar o fluxo de recuperação de senha — solicitação neutra (anti-enumeração) e redefinição via token.

**Technical actions:**

1. Criar `RequestPasswordResetUseCase` — se o e-mail existir, emite `UserToken` tipo `PASSWORD_RESET` e dispara o e-mail; resposta sempre `202` neutra (per `user-auth/TD-03`, `### API Contracts → POST /api/auth/forgot-password`).
2. Criar `ResetPasswordUseCase` — valida o token `PASSWORD_RESET`, re-hashea a senha (BCrypt, per `user-auth/TD-02`) e marca o token como usado.
3. Criar `AuthController#forgotPassword` e `AuthController#resetPassword` per `### API Contracts`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ResetPasswordUseCase` | Unit (Mockito): token válido redefine; token inválido lança `InvalidTokenException` | `backend/src/test/java/.../ResetPasswordUseCaseTest.java` |
| `AuthController` forgot/reset | Integration (Testcontainers + MockMvc): 202 neutro + 204 redefinição + 410 token inválido | `backend/src/test/java/.../PasswordRecoveryControllerIT.java` |

**Dependencies:** SI-02.2, SI-02.3, SI-02.4, SI-02.5

**Acceptance criteria:**

- `POST /api/auth/forgot-password` retorna `202` independentemente de o e-mail existir (não revela existência).
- Quando o e-mail existe, emite token `PASSWORD_RESET` e dispara o e-mail de reset.
- `POST /api/auth/reset-password` com token válido retorna `204` e permite login subsequente com a nova senha.
- `POST /api/auth/reset-password` com token expirado/usado retorna `410` `INVALID_TOKEN`.

---

### SI-02.10 — Bootstrap do projeto Angular + Angular Material

**Description:** Inicializar o subprojeto `frontend/` com Angular 22 standalone e Angular Material configurado — fundação de todas as telas.

**Technical actions:**

1. Criar o projeto Angular em `frontend/` (standalone, routing habilitado) e `frontend/CLAUDE.md` mínimo (stack, comandos) per padrão de 3 níveis (`## Inherited Conventions → AI coding`).
2. Adicionar `@angular/material` e rodar `ng add @angular/material` (tema + tipografia + animations) (per `user-auth/TD-05`, `library-refs.md → @angular/material`).
3. Configurar `provideHttpClient(withFetch())` e o roteamento base da aplicação (`app.config.ts`, `app.routes.ts`).

**Tests:** _(empty — bootstrap de projeto; smoke-gated pelo build/compile)_

**Dependencies:** none

**Acceptance criteria:**

- `cd frontend && npm run build` compila o projeto Angular limpo sem erros.
- Angular Material está disponível (tema aplicado; imports standalone resolvem).

---

### SI-02.11 — Infra de auth no cliente: ProblemDetail, ErrorHandlerService, AuthService e guard

**Description:** Construir a camada de autenticação do cliente — estado de sessão via `/api/me`, tratamento genérico de erros RFC 7807 e guarda de rota.

**Technical actions:**

1. Definir a interface `ProblemDetail` (com `invalidFields: Record<string, string>`) e um `ErrorHandlerService` genérico que traduz respostas de erro em mensagens/`MatSnackBar` (per `user-auth/TD-04`).
2. Criar `AuthService` que chama `GET /api/me` no app init para determinar o estado de sessão (signal/observable de usuário) e expõe `login`/`logout`; configurar `HttpClient` com `withCredentials: true` (per `user-auth/TD-01`, `### API Contracts → GET /api/me`).
3. Criar interceptor `withCredentials` + guard de rota (redirect-if-authenticated para telas anônimas) e registrar as rotas `/signup`, `/login`, `/forgot-password`, `/reset-password`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ErrorHandlerService` | Unit: mapeia `ProblemDetail` → mensagem/`invalidFields` | `frontend/src/app/core/error-handler.service.spec.ts` |
| `AuthService` | Unit (HttpTestingController): `/api/me` 200 popula sessão; 401 → não autenticado | `frontend/src/app/core/auth.service.spec.ts` |

**Dependencies:** SI-02.10

**Acceptance criteria:**

- No app init, `AuthService` consulta `GET /api/me` com `withCredentials: true` e popula o estado de sessão quando autenticado.
- Resposta `401` de `/api/me` resulta em estado "não autenticado" sem erro de runtime.
- `ErrorHandlerService` extrai `invalidFields` de um `ProblemDetail` de validação.

---

### SI-02.12 — Componentes compartilhados de layout de auth

**Description:** Criar os componentes presentacionais reutilizados pelas 4 telas de auth (marcados `(new)` no inventário), construídos uma vez e reaproveitados.

**Technical actions:**

1. Criar `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts` — fundo escuro full-page + centralização (per `### UI Contracts`).
2. Criar `frontend/src/app/shared/components/auth-card/auth-card.component.ts` — container card branco.
3. Criar `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts` — ícone + wordmark "StreamTube".
4. Criar `frontend/src/app/shared/components/auth-footer/auth-footer.component.ts` — configurável via inputs (mensagem + link), reusado em signup/login/forgot.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `auth-footer.component` | Unit: renderiza a mensagem/link recebidos via inputs | `frontend/src/app/shared/components/auth-footer/auth-footer.component.spec.ts` |

**Dependencies:** SI-02.10

**Acceptance criteria:**

- Os 4 componentes existem em `frontend/src/app/shared/components/` e compilam.
- `auth-footer` exibe a cópia e o destino de link conforme os inputs (configurável por tela).

---

### SI-02.13 — Componente password-strength

**Description:** Criar o indicador de força de senha (lógica computada) exibido inline na tela de cadastro.

**Technical actions:**

1. Criar `frontend/src/app/shared/components/password-strength/password-strength.component.ts` — recebe a senha via input e computa/exibe o nível de força e a dica ("Weak password: Add numbers and symbols.") (per `### UI Contracts → Create user account`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `password-strength.component` | Unit: computa nível para senhas fraca/média/forte | `frontend/src/app/shared/components/password-strength/password-strength.component.spec.ts` |

**Dependencies:** SI-02.10

**Acceptance criteria:**

- O componente exibe um nível de força que muda conforme a composição da senha (comprimento, dígitos, símbolos).
- Para uma senha fraca, exibe a dica de melhoria correspondente.

---

### SI-02.14b — Tela /signup (Create user account)

**Route:** /signup
**Test Specs:** see `frontend/specs/signup.plan.md`
**UI Contract:** see `## Technical Specifications` → `### UI Contracts` → `#### Screen: Create user account`

**Description:** Construir e ligar a tela de cadastro (Angular standalone + reactive form) ao endpoint de registro.

**Technical actions:**

1. Criar `SignupComponent` standalone com reactive form (Full Name, Email, Password + toggle, Confirm Password + toggle, Terms checkbox) usando `MatFormField`/`MatInput`/`MatIconButton`/`MatButton`/`MatCheckbox` e o `password-strength` (per UI Contract).
2. Aplicar o espelho de validação client-side (`### API Contracts → Validation Rules`) e a confirmação de senha (client-only); desabilitar submit enquanto inválido.
3. Ligar o submit a `POST /api/auth/register` (`withCredentials`); em sucesso, redirecionar para `/` autenticado.
4. Mapear erros per `### Error Catalog → UX mapping` (inline `EMAIL_ALREADY_EXISTS`/`VALIDATION_ERROR`) via `ErrorHandlerService`.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `SignupComponent` | Unit (HttpTestingController): submit happy path + mapeamento de erro + validação pré-submit | `frontend/src/app/features/auth/signup/signup.component.spec.ts` |

**Dependencies:** SI-02.11, SI-02.12, SI-02.13, SI-02.6 (endpoint register)

**Acceptance criteria:**

- Submeter o formulário válido chama `POST /api/auth/register` e, em `201`, redireciona para `/`.
- E-mail já cadastrado (`409`) exibe erro inline no campo Email.
- O botão de submit fica desabilitado enquanto o formulário é inválido (incl. senhas divergentes / termos não aceitos).

---

### SI-02.15b — Tela /login (User login)

**Route:** /login
**Test Specs:** see `frontend/specs/login.plan.md`
**UI Contract:** see `## Technical Specifications` → `### UI Contracts` → `#### Screen: User login`

**Description:** Construir e ligar a tela de login ao endpoint de autenticação e ao estado de sessão.

**Technical actions:**

1. Criar `LoginComponent` standalone com reactive form (Email, Password) + link "Forgot password?" + `auth-footer` ("Sign up") (per UI Contract).
2. Aplicar validação client-side (`### API Contracts → Validation Rules`).
3. Ligar o submit a `POST /api/auth/login` (`withCredentials`); em sucesso, atualizar `AuthService` (sessão) e redirecionar para `/`.
4. Mapear `INVALID_CREDENTIALS` para `MatSnackBar` "Invalid credentials. Please try again." (per `### Error Catalog → UX mapping`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `LoginComponent` | Unit (HttpTestingController): login happy path + snackbar em 401 | `frontend/src/app/features/auth/login/login.component.spec.ts` |

**Dependencies:** SI-02.11, SI-02.12, SI-02.8 (endpoint login + /api/me)

**Acceptance criteria:**

- Submeter credenciais válidas chama `POST /api/auth/login` e redireciona para `/` com a sessão ativa.
- Credenciais inválidas (`401`) exibem o `MatSnackBar` de erro sem expor detalhes.
- Os links navegam para `/forgot-password` e `/signup`.

---

### SI-02.16b — Tela /forgot-password (Reset password)

**Route:** /forgot-password
**Test Specs:** see `frontend/specs/forgot-password.plan.md`
**UI Contract:** see `## Technical Specifications` → `### UI Contracts` → `#### Screen: Reset password`

**Description:** Construir e ligar a tela de solicitação de reset, com o estado de sucesso inline.

**Technical actions:**

1. Criar `ForgotPasswordComponent` standalone com reactive form (Email) + `auth-footer` ("Sign in") e o success state (swap inline via boolean local) (per UI Contract).
2. Aplicar validação client-side do campo Email.
3. Ligar o submit a `POST /api/auth/forgot-password`; em qualquer resposta `202`, exibir o success state ("You should receive the email shortly.") sem redirect.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ForgotPasswordComponent` | Unit (HttpTestingController): submit → success state; validação de email | `frontend/src/app/features/auth/forgot-password/forgot-password.component.spec.ts` |

**Dependencies:** SI-02.11, SI-02.12, SI-02.9 (endpoint forgot-password)

**Acceptance criteria:**

- Submeter um e-mail válido chama `POST /api/auth/forgot-password` e troca o card para o success state sem mudar de rota.
- O success state preserva o shell do card (logo + back arrow) e não dispara redirect automático.
- Back arrow e link "Sign in" navegam para `/login`.

---

### SI-02.17b — Tela /reset-password (Set new password)

**Route:** /reset-password
**Test Specs:** see `frontend/specs/reset-password.plan.md`
**UI Contract:** see `## Technical Specifications` → `### UI Contracts` → `#### Screen: Set new password`

**Description:** Construir e ligar a tela de redefinição de senha, acessada pelo link do e-mail com token no query param.

**Technical actions:**

1. Criar `ResetPasswordComponent` standalone com reactive form (New password + toggle, Confirm password + toggle) que lê `token` do query param no init (per UI Contract; sem footer/back link).
2. Aplicar validação client-side (`password` min 8/max 128; confirmação igual; `token` presente).
3. Ligar o submit a `POST /api/auth/reset-password` (token + nova senha); em `204`, redirecionar para `/login`.
4. Em `410` `INVALID_TOKEN` (ou token ausente), redirecionar para `/forgot-password` + `MatSnackBar` "Link expirado. Solicite um novo." (per `### Error Catalog → UX mapping`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ResetPasswordComponent` | Unit (HttpTestingController): reset happy path + redirect em token inválido + validação | `frontend/src/app/features/auth/reset-password/reset-password.component.spec.ts` |

**Dependencies:** SI-02.11, SI-02.12, SI-02.9 (endpoint reset-password)

**Acceptance criteria:**

- Com token válido, submeter a nova senha chama `POST /api/auth/reset-password` e, em `204`, redireciona para `/login`.
- Token inválido/expirado/ausente redireciona para `/forgot-password` exibindo o `MatSnackBar` "Link expirado. Solicite um novo."
- O botão de submit fica desabilitado enquanto a nova senha e a confirmação não forem válidas e iguais.

---

## Technical Specifications

### Data Model

#### User

| Field | Type | Constraints |
|-------|------|-------------|
| id | uuid | PK, generated (UUID v4 nativo Hibernate 6, per `user-auth/TD-06`) |
| name | varchar(255) | not null — "Full Name" do `/signup` |
| email | varchar(255) | unique, not null |
| password_hash | varchar(255) | not null — BCrypt via `DelegatingPasswordEncoder` (per `user-auth/TD-02`) |
| email_confirmed | boolean | not null, default false (per `user-auth/TD-03`) |
| created_at | timestamptz | not null, default now() |

**Relations:** `User` has many `UserToken` (one-to-many, `UserToken.user_id` → `User.id`). `User` has one `Channel` (criado pelo slice `canal-aggregate` — fora deste slice).
**Indexes:** unique on `email`.
**Migration:** `V2__create_users_table.sql` (Flyway SQL puro, forward-only, per `## Inherited Conventions`).
**Domínio:** entidade `domain/entities/User` sem anotações de infraestrutura; objeto de mapeamento JPA separado em `infrastructure/` (per `backend/CLAUDE.md` — regra crítica "sem `@Entity` no domínio").

#### UserToken

| Field | Type | Constraints |
|-------|------|-------------|
| id | uuid | PK, generated (per `user-auth/TD-06`) |
| user_id | uuid | not null, FK → `User.id` |
| token | varchar(255) | not null, unique — token opaco (per `user-auth/TD-03`) |
| type | varchar(32) | not null — `EMAIL_CONFIRMATION` \| `PASSWORD_RESET` |
| expires_at | timestamptz | not null |
| used_at | timestamptz | nullable — null = não consumido; preenchido ao usar |
| created_at | timestamptz | not null, default now() |

**Relations:** `UserToken` belongs to `User` (many-to-one).
**Indexes:** unique on `token`; index on `(user_id, type)` para invalidar tokens anteriores ao reenviar (per `user-auth/TD-03` — "reenviar e-mail de confirmação invalida tokens anteriores naturalmente").
**Migration:** `V3__create_user_tokens_table.sql` (Flyway SQL puro, forward-only).
**Domínio:** entidade `domain/entities/UserToken` sem anotações de infraestrutura; mapeamento JPA separado em `infrastructure/`.

### API Contracts

> Tier backend (Scope-driven). Não há BFF tier: `frontend/CLAUDE.md` não documenta camada proxy/BFF — o Angular chama o backend diretamente via `HttpClient` com `withCredentials: true` (per `user-auth/TD-01`). Todos os paths sob `/api`. Erros seguem RFC 7807 `ProblemDetail` (per `user-auth/TD-04`, ver `### Error Catalog`).

#### POST /api/auth/register (SI-02.5)

**Request headers:**
- Content-Type: application/json

**Request body:**
- name: string, required — min 1, max 255
- email: string, required — formato de e-mail válido
- password: string, required — min 8, max 128

**Response 201:**
- id: string (uuid)
- email: string
- name: string

**Error responses:**
- 409 EMAIL_ALREADY_EXISTS: e-mail já cadastrado
- 400 VALIDATION_ERROR: corpo falha na validação de schema (`invalidFields` por campo)

_Side-effect: dispara e-mail de confirmação (ver `### Events/Messages → email.confirmation`). Conta criada com `email_confirmed = false`, mas login não é bloqueado por confirmação (per inventory — redirect direto para Home autenticado). Criação do canal: responsabilidade do slice `canal-aggregate`._

---

#### GET /api/auth/confirm (SI-02.6)

**Request query parameters:**
- token: string, required — token opaco recebido por e-mail (per `user-auth/TD-03`)

**Response 204:** No content. Marca `User.email_confirmed = true` e `UserToken.used_at = now()`.

**Error responses:**
- 410 INVALID_TOKEN: token não encontrado, já usado ou expirado
- 400 VALIDATION_ERROR: parâmetro `token` ausente ou vazio

---

#### POST /api/auth/resend-confirmation (SI-02.6)

**Request headers:**
- Content-Type: application/json

**Request body:**
- email: string, required — formato de e-mail válido

**Response 202:** Accepted (resposta neutra — não revela se o e-mail existe). Invalida tokens `EMAIL_CONFIRMATION` anteriores do usuário e emite um novo (per `user-auth/TD-03`).

**Error responses:**
- 400 VALIDATION_ERROR: `email` ausente ou inválido

---

#### POST /api/auth/login (SI-02.7)

**Request headers:**
- Content-Type: application/json

**Request body:**
- email: string, required
- password: string, required

**Response 200:**
- id: string (uuid)
- email: string
- name: string

**Set-Cookie side-effect:** define cookie `access_token` com o JWT — `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age=3600` (per `user-auth/TD-01` + `library-refs.md → ResponseCookie`). O cliente não armazena token (per `user-auth/TD-01`).

**Error responses:**
- 401 INVALID_CREDENTIALS: e-mail ou senha incorretos
- 400 VALIDATION_ERROR: corpo inválido

---

#### POST /api/auth/logout (SI-02.7)

**Request headers:**
- Cookie: access_token=&lt;jwt&gt;

**Response 204:** No content. Expira o cookie `access_token` (`Max-Age=0`).

**Error responses:**
- 401 UNAUTHENTICATED: cookie `access_token` ausente ou inválido

---

#### GET /api/me (SI-02.7)

**Request headers:**
- Cookie: access_token=&lt;jwt&gt;

**Response 200:**
- id: string (uuid)
- email: string
- name: string

**Error responses:**
- 401 UNAUTHENTICATED: cookie `access_token` ausente ou inválido (usado pelo frontend no app init para determinar estado de sessão, per `user-auth/TD-01`)

---

#### POST /api/auth/forgot-password (SI-02.8)

**Request headers:**
- Content-Type: application/json

**Request body:**
- email: string, required — formato de e-mail válido

**Response 202:** Accepted (resposta neutra — sempre sucesso, não revela se o e-mail existe; previne enumeração). Se o e-mail existir, emite token `PASSWORD_RESET` e dispara e-mail (ver `### Events/Messages → email.password_reset`).

**Error responses:**
- 400 VALIDATION_ERROR: `email` ausente ou inválido

---

#### POST /api/auth/reset-password (SI-02.8)

**Request headers:**
- Content-Type: application/json

**Request body:**
- token: string, required — token opaco `PASSWORD_RESET` (per `user-auth/TD-03`)
- password: string, required — nova senha, min 8, max 128

**Response 204:** No content. Re-hashea a senha (BCrypt, per `user-auth/TD-02`), marca `UserToken.used_at = now()`.

**Error responses:**
- 410 INVALID_TOKEN: token não encontrado, já usado ou expirado
- 400 VALIDATION_ERROR: `token`/`password` ausente ou inválido

---

#### Validation Rules — auth endpoints

- `name`: required, min 1, max 255
- `email`: required, formato de e-mail válido, max 255
- `password`: required, min 8, max 128 (aplicado em `register` e `reset-password`)
- `token`: required, não vazio (aplicado em `confirm` query param e `reset-password` body)

### Authorization Matrix

Política de sessão `STATELESS` com JWT em `httpOnly` cookie (per `user-auth/TD-01`). Endpoints anônimos compõem o fluxo de entrada; `logout` e `/api/me` exigem cookie válido.

| Endpoint | Anonymous | Authenticated |
|----------|-----------|---------------|
| POST /api/auth/register | ✓ | ✓ |
| GET /api/auth/confirm | ✓ | ✓ |
| POST /api/auth/resend-confirmation | ✓ | ✓ |
| POST /api/auth/login | ✓ | ✓ |
| POST /api/auth/forgot-password | ✓ | ✓ |
| POST /api/auth/reset-password | ✓ | ✓ |
| POST /api/auth/logout | ✗ | ✓ |
| GET /api/me | ✗ | ✓ |

### Error Catalog

**Formato de resposta de erro (estabelecido nesta fase, herdado pelas Fases 03–07):** RFC 7807 `ProblemDetail` (per `user-auth/TD-04`) — Spring Boot 3.x nativo via `spring.mvc.problemdetails.enabled=true`. Cada resposta carrega `type`, `title`, `status`, `detail`; erros de validação adicionam a extensão `invalidFields: Record<string, string>` (campo → mensagem). O frontend consome via interface `ProblemDetail` + `ErrorHandlerService` genérico (per `user-auth/TD-04`).

| errorCode | HTTP | Trigger |
|-----------|------|---------|
| VALIDATION_ERROR | 400 | Corpo/parâmetro falha na validação de schema; popula `invalidFields` |
| INVALID_CREDENTIALS | 401 | Login com e-mail/senha incorretos |
| UNAUTHENTICATED | 401 | Acesso a `/api/me` ou `logout` sem cookie `access_token` válido |
| EMAIL_ALREADY_EXISTS | 409 | Cadastro com e-mail já em uso |
| INVALID_TOKEN | 410 | Token de confirmação ou reset não encontrado, já usado ou expirado |

### Events/Messages

Serviço de e-mails transacionais (capability "Serviço de envio de e-mails transacionais", Non-UI). Implementado via Spring Mail. Despacho desacoplado da request (`@Async`) para não bloquear a resposta HTTP; sem broker de mensagens nesta fase.

#### email.confirmation

**Payload:**

```json
{ "to": "string (email)", "userId": "uuid", "confirmationToken": "string", "confirmationUrl": "string" }
```

**Producer:** `EmailService` disparado por `RegisterUserUseCase` / `ResendConfirmationUseCase` (per `user-auth/TD-03`)
**Consumer:** SMTP (Spring Mail `JavaMailSender`)
**Trigger:** após `POST /api/auth/register` (sucesso) ou `POST /api/auth/resend-confirmation`. Link aponta para `GET /api/auth/confirm?token=...`.
**Delivery semantics:** best-effort (SMTP; falha de envio não reverte a criação do usuário — logada para reprocessamento manual).

#### email.password_reset

**Payload:**

```json
{ "to": "string (email)", "userId": "uuid", "resetToken": "string", "resetUrl": "string" }
```

**Producer:** `EmailService` disparado por `RequestPasswordResetUseCase` (per `user-auth/TD-03`)
**Consumer:** SMTP (Spring Mail `JavaMailSender`)
**Trigger:** após `POST /api/auth/forgot-password` quando o e-mail existe. Link aponta para a tela `/reset-password?token=...` do frontend.
**Delivery semantics:** best-effort.

### UI Contracts

> Telas Angular standalone com Angular Material (per `user-auth/TD-05`). Componentes compartilhados de auth (`auth-layout`, `auth-card`, `brand-logo`, `auth-footer`, `password-strength`) são `new` e construídos no bootstrap do frontend (SI-02.9). Estado de sessão determinado via `GET /api/me` no app init (per `user-auth/TD-01`); `HttpClient` com `withCredentials: true`. Erros mapeados via `ErrorHandlerService` a partir de `ProblemDetail` (per `user-auth/TD-04`).

#### Screen: Create user account

**Route:** `/signup`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=140-333 (node `zqPFL1161k3PlcEjtNrJzS:140-333`)
**Purpose:** "Cadastro de usuário com e-mail e senha"

**Auth requirement:** Anonymous _(source: §Authorization Matrix — POST /api/auth/register)_

**Rendering strategy:** Client Component (Angular SPA standalone) — _sem TD de arquitetura de renderização dedicado; padrão SPA do Angular 22 (per `user-auth/TD-05`)._

**Reused DS components:**
- `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts (new)` — fundo escuro full-page + centralização
- `frontend/src/app/shared/components/auth-card/auth-card.component.ts (new)` — container card branco
- `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts (new)` — ícone + wordmark "StreamTube"
- `frontend/src/app/shared/components/password-strength/password-strength.component.ts (new)` — indicador de força de senha inline
- `frontend/src/app/shared/components/auth-footer/auth-footer.component.ts (new)` — "Already have an account?" + link "Sign in" (configurável via inputs)
- `MatFormField` + `MatInput` + `MatIconButton` (toggle de visibilidade) + `MatButton` + `MatCheckbox` (per `library-refs.md → @angular/material`)

**Server-connected components:**
- `"Create account" submit button (MatButton)` — verbs: registrar novo usuário com nome, email e senha | endpoint: `POST /api/auth/register` (§API Contracts) | reuse: new

**Behaviors:**

*Rendered states:*
- Loading: botão "Create account" em estado disabled/spinner durante submit.
- Empty: formulário vazio (estado inicial).
- Success: redirect direto para `/` (Home) já autenticado (per inventory — sem tela de confirmação).
- Error: erros de campo inline + `MatSnackBar` para erros gerais.

*Interactions:*
- Toggle de visibilidade (`MatIconButton matSuffix`) → alterna `type` dos campos Password e Confirm Password.
- Digitação no campo Password → atualiza `password-strength` inline ("Weak password: Add numbers and symbols.").
- Back arrow (←) → navega para `/login`.
- Link "Sign in" do footer → navega para `/login`.

**Error Catalog → UX mapping:**

| errorCode (from §Error Catalog) | UX treatment |
|---------------------------------|--------------|
| `VALIDATION_ERROR` | `mat-error` inline abaixo de cada campo (via `invalidFields`) |
| `EMAIL_ALREADY_EXISTS` | `mat-error` inline no campo Email ("E-mail já cadastrado") + CTA "Sign in" |

**Client-side validation mirror:** _(source: §API Contracts → Validation Rules)_
- `name`: required, min 1, max 255
- `email`: required, formato de e-mail válido, max 255
- `password`: required, min 8, max 128
- Confirm Password: required, deve ser igual a `password` (validação client-only; não enviada ao backend)
- Terms of Service: checkbox required (client-only)

**Accessibility notes:**
- Acessibilidade embutida do Angular Material (per `user-auth/TD-05`); `mat-error` associado via `aria-describedby` automático do `MatFormField`.

---

#### Screen: User login

**Route:** `/login`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=138-179 (node `zqPFL1161k3PlcEjtNrJzS:138-179`)
**Purpose:** "Login e controle de sessão do usuário"

**Auth requirement:** Anonymous _(source: §Authorization Matrix — POST /api/auth/login)_

**Rendering strategy:** Client Component (Angular SPA standalone) — _padrão SPA do Angular 22 (per `user-auth/TD-05`)._

**Reused DS components:**
- `auth-layout`, `auth-card`, `brand-logo`, `auth-footer` (configurável: "Don't have an account?" + "Sign up") — todos `(new)`, compartilhados (SI-02.9)
- `MatFormField` + `MatInput` + `MatButton`

**Server-connected components:**
- `"Sign in" submit button (MatButton)` — verbs: autenticar usuário com email e senha | endpoint: `POST /api/auth/login` (§API Contracts) | reuse: new

**Behaviors:**

*Rendered states:*
- Loading: botão "Sign in" disabled/spinner durante submit.
- Success: cookie `access_token` setado pelo backend; redirect para `/` (Home).
- Error: `MatSnackBar` com "Invalid credentials. Please try again." (per `library-refs.md → MatSnackBar`).

*Interactions:*
- Link "Forgot password?" (right-aligned, inline com label Password) → navega para `/forgot-password`.
- Link "Sign up" do footer → navega para `/signup`.

**Error Catalog → UX mapping:**

| errorCode (from §Error Catalog) | UX treatment |
|---------------------------------|--------------|
| `INVALID_CREDENTIALS` | `MatSnackBar` "Invalid credentials. Please try again." |
| `VALIDATION_ERROR` | `mat-error` inline por campo |

**Client-side validation mirror:** _(source: §API Contracts → Validation Rules)_
- `email`: required, formato de e-mail válido
- `password`: required

**Accessibility notes:**
- Acessibilidade embutida do Angular Material; placeholder do campo Password corrigido para "Enter your password" (per inventory OQ-04 resolvido).

---

#### Screen: Reset password

**Route:** `/forgot-password`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=140-289 (node `zqPFL1161k3PlcEjtNrJzS:140-289`)
**Purpose:** "Recuperação de senha: solicitação via e-mail → link com token → redefinição"

**Auth requirement:** Anonymous _(source: §Authorization Matrix — POST /api/auth/forgot-password)_

**Rendering strategy:** Client Component (Angular SPA standalone) — _padrão SPA do Angular 22 (per `user-auth/TD-05`)._

**Reused DS components:**
- `auth-layout`, `auth-card`, `brand-logo`, `auth-footer` ("Remember your password?" + "Sign in") — todos `(new)`, compartilhados (SI-02.9)
- `MatFormField` + `MatInput` + `MatButton`

**Server-connected components:**
- `"Send reset link" submit button (MatButton)` — verbs: solicitar link de reset de senha por email | endpoint: `POST /api/auth/forgot-password` (§API Contracts) | reuse: new

**Behaviors:**

*Rendered states:*
- Form state (default): input de e-mail + botão "Send reset link".
- Success state: corpo do card troca para mensagem de confirmação ("You should receive the email shortly."); form escondido, shell do card (logo + back arrow) permanece. Toggle via boolean local no componente — sem mudança de rota.
- Loading: botão disabled/spinner durante submit.
- Error: `mat-error` inline para validação.

*Interactions:*
- Submit bem-sucedido → troca para success state (sem redirect automático).
- Back arrow (←) → navega para `/login`.
- Link "Sign in" do footer → navega para `/login`.

**Error Catalog → UX mapping:**

| errorCode (from §Error Catalog) | UX treatment |
|---------------------------------|--------------|
| `VALIDATION_ERROR` | `mat-error` inline no campo Email |

_Nota: resposta 202 é neutra (não revela existência do e-mail) — UI sempre mostra o success state após submit válido._

**Client-side validation mirror:** _(source: §API Contracts → Validation Rules)_
- `email`: required, formato de e-mail válido

**Accessibility notes:**
- Acessibilidade embutida do Angular Material; troca form→success preserva foco no card.

---

#### Screen: Set new password

**Route:** `/reset-password`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=58987-2044 (node `zqPFL1161k3PlcEjtNrJzS:58987-2044`)
**Purpose:** "Recuperação de senha: solicitação via e-mail → link com token → redefinição"

**Auth requirement:** Anonymous _(source: §Authorization Matrix — POST /api/auth/reset-password)_

**Rendering strategy:** Client Component (Angular SPA standalone) — _padrão SPA do Angular 22 (per `user-auth/TD-05`)._

**Reused DS components:**
- `auth-layout`, `auth-card`, `brand-logo` — `(new)`, compartilhados (SI-02.9). Sem footer e sem back link (entry point a partir do e-mail).
- `MatFormField` + `MatInput` + `MatIconButton` (toggle visibilidade, ambos os campos) + `MatButton`

**Server-connected components:**
- `"Reset password" submit button (MatButton)` — verbs: redefinir senha usando token recebido por email | endpoint: `POST /api/auth/reset-password` (§API Contracts) | reuse: new

**Behaviors:**

*Rendered states:*
- Loading: componente lê `token` do query param (`/reset-password?token=abc123`) no init; botão disabled/spinner durante submit.
- Success: senha redefinida → redirect para `/login`.
- Error (token inválido/expirado): redirect para `/forgot-password` + `MatSnackBar` "Link expirado. Solicite um novo." (per inventory OQ-02 resolvido).

*Interactions:*
- Toggle de visibilidade (`MatIconButton matSuffix`) → alterna `type` dos campos New password e Confirm password (per inventory OQ-01 resolvido).

**Error Catalog → UX mapping:**

| errorCode (from §Error Catalog) | UX treatment |
|---------------------------------|--------------|
| `INVALID_TOKEN` | Redirect para `/forgot-password` + `MatSnackBar` "Link expirado. Solicite um novo." |
| `VALIDATION_ERROR` | `mat-error` inline por campo |

**Client-side validation mirror:** _(source: §API Contracts → Validation Rules)_
- `password` (New password): required, min 8, max 128
- Confirm password: required, deve ser igual a New password (client-only)
- `token`: lido do query param; ausência → tratar como token inválido

**Accessibility notes:**
- Acessibilidade embutida do Angular Material; placeholders "Enter new password" / "Confirm new password" (per inventory OQ-05 resolvido).

---

### UI ↔ API Traceability Matrix

| Verb | Component | Screen | Endpoint (from API Contracts) | TD ref |
|------|-----------|--------|-------------------------------|--------|
| registrar novo usuário com nome, email e senha | "Create account" submit button (MatButton) | /signup | POST /api/auth/register | user-auth/TD-04 |
| autenticar usuário com email e senha | "Sign in" submit button (MatButton) | /login | POST /api/auth/login | user-auth/TD-01 |
| solicitar link de reset de senha por email | "Send reset link" submit button (MatButton) | /forgot-password | POST /api/auth/forgot-password | user-auth/TD-03 |
| redefinir senha usando token recebido por email | "Reset password" submit button (MatButton) | /reset-password | POST /api/auth/reset-password | user-auth/TD-03 |

_Capabilities marcadas em `## Non-UI / Deferred Capabilities` (envio de e-mails, confirmação de conta, logout) são excluídas desta matriz._

---

## Dependency Map

**Backend**

```
SI-02.1 (root — migrations)
└── SI-02.2 — depends on SI-02.1 (tabelas antes das entidades/repos)
SI-02.3 (root — Spring Security + cookie + encoder)
SI-02.4 (root — contrato de erro RFC 7807)
SI-02.5 (root — serviço de e-mail)
SI-02.6 — depends on SI-02.2 + SI-02.3 + SI-02.4 + SI-02.5 (cadastro)
└── SI-02.7 — depends on SI-02.6 (+ SI-02.2, SI-02.4, SI-02.5) (confirmação reusa token do cadastro)
SI-02.8 — depends on SI-02.2 + SI-02.3 + SI-02.4 (login/logout/me)
SI-02.9 — depends on SI-02.2 + SI-02.3 + SI-02.4 + SI-02.5 (recuperação de senha)
```

**Frontend**

```
SI-02.10 (root — bootstrap Angular + Material)
├── SI-02.11 — depends on SI-02.10 (infra de auth no cliente)
├── SI-02.12 — depends on SI-02.10 (componentes de layout)
└── SI-02.13 — depends on SI-02.10 (password-strength)
SI-02.14b — depends on SI-02.11 + SI-02.12 + SI-02.13 + SI-02.6 (tela /signup)
SI-02.15b — depends on SI-02.11 + SI-02.12 + SI-02.8 (tela /login)
SI-02.16b — depends on SI-02.11 + SI-02.12 + SI-02.9 (tela /forgot-password)
SI-02.17b — depends on SI-02.11 + SI-02.12 + SI-02.9 (tela /reset-password)
```

**Cross-layer edges:** cada tela (SI-02.14b–17b) depende do endpoint backend que consome — `/signup`→SI-02.6, `/login`→SI-02.8, `/forgot-password` e `/reset-password`→SI-02.9. Backend e frontend podem ser desenvolvidos em paralelo até o ponto de wiring das telas.

---

## Deliverables

- [ ] SI-02.1 — Migrations Flyway (users, user_tokens)
- [ ] SI-02.2 — Entidades de domínio + mapeamento JPA + repositórios
- [ ] SI-02.3 — Spring Security: JWT em httpOnly cookie + STATELESS + PasswordEncoder
- [ ] SI-02.4 — Contrato de erro RFC 7807 ProblemDetail
- [ ] SI-02.5 — Serviço de e-mails transacionais (Spring Mail)
- [ ] SI-02.6 — Cadastro de usuário: POST /api/auth/register
- [ ] SI-02.7 — Confirmação de conta: GET /api/auth/confirm + resend
- [ ] SI-02.8 — Login e sessão: login + logout + /api/me
- [ ] SI-02.9 — Recuperação de senha: forgot-password + reset-password
- [ ] SI-02.10 — Bootstrap do projeto Angular + Angular Material
- [ ] SI-02.11 — Infra de auth no cliente: ProblemDetail, ErrorHandlerService, AuthService, guard
- [ ] SI-02.12 — Componentes compartilhados de layout de auth
- [ ] SI-02.13 — Componente password-strength
- [ ] SI-02.14b — Tela /signup (Create user account)
- [ ] SI-02.15b — Tela /login (User login)
- [ ] SI-02.16b — Tela /forgot-password (Reset password)
- [ ] SI-02.17b — Tela /reset-password (Set new password)

**Per-screen deliverables:**

- [ ] Tela Create user account (`/signup`) é roteável e renderiza loading/success/error
- [ ] Tela User login (`/login`) é roteável e renderiza loading/success/error
- [ ] Tela Reset password (`/forgot-password`) é roteável (form + success state)
- [ ] Tela Set new password (`/reset-password`) é roteável e trata token inválido
- [ ] Cada tela passa nos testes de componente (`frontend` Unit)

**Full test suites:**

- [ ] Backend testes passam (`cd backend && ./gradlew test`) — inclui integração Testcontainers
- [ ] Backend compila/empacota (`cd backend && ./gradlew build`)
- [ ] Frontend testes passam (`cd frontend && npm test`)
- [ ] Frontend compila (`cd frontend && npm run build`)
- [ ] E2E das telas autenticadas (Playwright) — specs autoradas via `/plan-test-specs user-auth`
