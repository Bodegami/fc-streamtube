# phase-02-user-auth — Progress

**Status:** in_progress
**SIs:** 5/17 completed

### SI-02.1 — Migrations Flyway (users, user_tokens)
- **Status:** completed
- **Tests:** 4 passing
- **Observations:**
  - TDD não foi seguido neste SI (teste escrito após as migrations). Corrigido a partir do SI-02.2: teste escrito antes da implementação (ciclo RED → GREEN → REFACTOR).
  - OrbStack não estava rodando; iniciado programaticamente via `open -a OrbStack`. Symlink `/var/run/docker.sock` requer `sudo` e foi criado manualmente pelo usuário (sessão única).

### SI-02.2 — Entidades de domínio + mapeamento JPA + repositórios
- **Status:** completed
- **Tests:** 13 passing (após 2 rounds de code review)
- **Observations:**
  - Round 1: `deleteByUserIdAndType` usa `UserTokenType` no domínio; `createdAt` com `insertable = false`; `User` record inclui `createdAt`; `UserTokenMapper` protege `valueOf()` com `IllegalStateException`.
  - Round 2: e-mails hardcoded substituídos por `UUID.randomUUID()` suffix em todos os ITs; setters dead code (`setEmailConfirmed`, `setUsedAt`) removidos; `UserRepositoryAdapter.save()` usava `saveAndFlush()` + `entityManager.refresh()` (corrigido no Round 3).
  - Round 3: substituído `insertable = false` por `@CreationTimestamp` em `UserJpaEntity` — Hibernate popula o campo em memória antes do INSERT, eliminando a necessidade de `EntityManager` manual no adapter. `UserRepositoryAdapter` simplificado: removidos `EntityManager`, `@Transactional` e `saveAndFlush()` — padrão agora igual ao `UserTokenRepositoryAdapter`.
  - Campo `token` renomeado para `tokenValue` em `UserToken` record — eliminado accessor ambíguo `userToken.token()`.
  - FK `fk_user_tokens_user` sem `ON DELETE CASCADE` — aceitável no escopo atual. Criar migration corretiva quando surgir use case de deleção de usuário.
  - Round 4: `@CreationTimestamp` aplicado em `UserTokenJpaEntity.createdAt` — consistência com `UserJpaEntity` (ambos usam o mesmo padrão Hibernate).
  - Round 4: Teste `givenValidToken_whenPersisted_thenUuidIsGeneratedAutomatically` adicionado em `UserTokenRepositoryIT` — paridade de cobertura com `UserRepositoryIT`.
  - Round 4: `FlywayMigrationTests.givenBlankDatabase_whenApplicationStarts_thenNoDomainTablesExist` corrigido — asserção e nome atualizados para refletir V2+V3 (2 tabelas de domínio existem após todas as migrations).

### SI-02.3 — Spring Security: JWT em httpOnly cookie + STATELESS + PasswordEncoder
- **Status:** completed
- **Tests:** 7 passing (3 unit + 4 integration)
- **Observations:**
  - `SecurityConfig` em `infrastructure/security/`: constructor injection para `jwtSecret`, `JwtDecoder` (HMAC-SHA256 via `NimbusJwtDecoder.withSecretKey`), `SecurityFilterChain` (STATELESS, `oauth2ResourceServer`, endpoints públicos liberados). `PasswordEncoder` extraído para `PasswordEncoderConfig` (SRP).
  - `CookieBearerTokenResolver` como classe nomeada (`implements BearerTokenResolver`) — extrai JWT do cookie `access_token`, ignora valor vazio/blank, testável unitariamente.
  - `PasswordEncoderConfig` em `infrastructure/security/`: responsabilidade única de registrar o bean `DelegatingPasswordEncoder` com BCrypt default.
  - `AuthCookieHelper`: TTL injetado via `@Value("${app.jwt.expiration-seconds}")` no construtor — cookie e JWT compartilham a mesma fonte de verdade do YAML.
  - JWT secret lido de `${APP_JWT_SECRET}` com fallback de dev em `application.yml` — chave em bytes via `String.getBytes(UTF_8)`, sem base64.
  - `SecurityConstants.ACCESS_TOKEN_COOKIE` centraliza o nome do cookie `"access_token"` — `CookieBearerTokenResolver` e `AuthCookieHelper` referenciam a constante, eliminando duplicação de literal.
  - `SecurityConfigIT` usa `@Import(MeStubController.class)` para criar endpoint `/api/me` apenas no contexto de teste — sem poluir produção. Cobre: sem cookie (401), JWT válido (200), JWT expirado (401), assinatura inválida (401).
  - CSRF desabilitado — com `SameSite=Strict` e sessão STATELESS, a proteção é feita no browser.
  - **Decisão técnica:** `SecurityConfigIT` usa `@SpringBootTest` (contexto completo + Testcontainers) em vez de `@WebMvcTest`. Tradeoff consciente: overhead de CI (~5s) aceito em favor de simplicidade de configuração e fidelidade do contexto — evita a necessidade de mockar beans de infraestrutura (JPA, Flyway, `JwtDecoder`). Reavaliar quando o tempo total de CI se tornar relevante.

### SI-02.4 — Contrato de erro RFC 7807 ProblemDetail
- **Status:** completed
- **Tests:** 8 passing (após code review Round 1–5 — CR-01 a CR-25 aplicados; SI-02.4 pronto para commit)
- **Observations:**
  - `spring.mvc.problem-details.enabled: true` adicionado em `application.yml`; `spring-boot-starter-validation` adicionado em `build.gradle.kts` (necessário para `@Valid` / `@NotBlank` no Spring Boot 3).
  - Exceções de domínio em `domain/exceptions/`: `EmailAlreadyExistsException` (409), `InvalidCredentialsException` (401), `InvalidTokenException` (410) — cada uma com par de construtores `(String)` + `(String, Throwable)`.
  - `GlobalExceptionHandler` movido para `interfaces/advice/` (CR-11); estende `ResponseEntityExceptionHandler`; handlers `public` (CR-02); usa parâmetro `status` em vez de hardcode (CR-06); constante `INVALID_FIELDS_PROPERTY` (CR-07); headers criados via `new HttpHeaders() + addAll()` em vez do `writableHttpHeaders()` deprecated (CR-05).
  - Fallback `@ExceptionHandler(Exception.class)` adicionado (CR-03); loga `log.error("Unhandled exception: {}", ex.getMessage(), ex)` via SLF4J antes de retornar 500 (CR-13) — sem o log, stack traces de exceções não mapeadas seriam descartados silenciosamente em produção.
  - Testes assertam `$.title`, `$.detail`, `$.type`, `$.status` e valor da mensagem de validação (CR-04/08/09/15/16). `$.detail` do 400 assertado com valor exato `"Request body validation failed"` (CR-16) — string literal fixa no handler, não vinda de `ex.getMessage()`.
  - Teste `givenUnhandledException_whenThrown_thenReturns500WithProblemDetail` adicionado (CR-14) — cobre o caminho do handler genérico que foi introduzido sem ciclo RED→GREEN no Round 1.
  - Teste `GlobalExceptionHandlerIT` usa `@SpringBootTest` + `@WithMockUser` (classe) — endpoints do stub `/test/**` não estão no permit-all do `SecurityConfig`, então `@WithMockUser` injeta autenticação no contexto sem precisar de JWT real.
  - `backend/CLAUDE.md` atualizado: `domain/exceptions/` e `interfaces/advice/` adicionados ao diagrama de pacotes (CR-10).
  - CR-12 (`InvalidTokenException` genérico): diferido para revisão em SI-02.7 e SI-02.9, quando o contexto de tokens estiver implementado.
  - `log.error` do handler genérico usa `request.getDescription(false)` (produz `"uri=/caminho"`) — contexto HTTP preservado no log sem expor dados de sessão (CR-17). Padrão de log verificado em `givenUnhandledException` via `ListAppender` Logback.
  - Handler `@ExceptionHandler(AccessDeniedException.class)` adicionado antes do `Exception.class` genérico (CR-18) — evita que `@PreAuthorize` retorne 500 em vez de 403 quando method security for introduzida nos SIs futuros. Coberto por `givenAccessDeniedException_whenThrown_thenReturns403WithProblemDetail`.
  - Imports de `GlobalExceptionHandler` reorganizados: `org.springframework.security.*` movido para após `org.slf4j.*` (CR-21).
  - `handleAccessDenied` recebe `WebRequest request` e emite `log.warn("Access denied on [{}]", request.getDescription(false))` — trilha de auditoria para acessos negados no layer MVC; não loga `ex.getMessage()` para não vazar modelo de autorização (CR-22). Verificado por `givenAccessDeniedException_whenThrown_thenLogsWarnWithRequestUri` via `ListAppender`.
  - Teste `givenUnhandledException_whenThrown_thenReturns500WithProblemDetail` separado em dois (CR-19/CR-20): HTTP response + log behavior independentes. `hasSize(1)` substituído por `anySatisfy()` no teste de log — resiliente a adições futuras de log statements na classe.
  - Setup/teardown de `ListAppender` extraído para `@BeforeEach`/`@AfterEach` (CR-24) — eliminada duplicação de 5 linhas entre os dois métodos de log. Testes de log ficaram com apenas 3 linhas de corpo.
  - `import java.util.List` removido (CR-23) — ficou órfão após migração para `anySatisfy()`.
  - Imports de `GlobalExceptionHandler` totalmente ordenados (CR-25): `org.springframework.http.*` precede `org.springframework.security.*` dentro do grupo `springframework`.

### SI-02.5 — Serviço de e-mails transacionais (Spring Mail)
- **Status:** completed
- **Tests:** 12 passing (após code review — CR-01 a CR-07 aplicados)
- **Observations:**
  - `EmailGateway` criado em `application/ports/out/` — Output Port da Clean Architecture; Use Cases dependerão da interface, não da implementação concreta. `EmailService` agora `implements EmailGateway`. `backend/CLAUDE.md` atualizado com o novo pacote.
  - `EmailService` em `infrastructure/email/`: constructor injection para `JavaMailSender` e `from` (`@Value("${app.mail.from:...}")`); ambos os métodos `@Async`; falha SMTP capturada e logada via `log.error` — best-effort por design.
  - `log.info("Email sent to {}", to)` adicionado no caminho feliz — **temporário para diagnóstico inicial; remover quando a aplicação escalar** (log de alto volume é custoso em produção).
  - `@EnableAsync` em `FcStreamtubeBackendApplication` (main class) — idiomático para Spring Boot sem configuração adicional de executor.
  - Configuração SMTP em `application.yml` via env vars com defaults dev (`localhost:1025` = Mailhog): `${SMTP_HOST}`, `${SMTP_PORT}`, `${SMTP_USERNAME}`, `${SMTP_PASSWORD}`, `${SMTP_AUTH:false}`, `${SMTP_STARTTLS:false}`, `${APP_MAIL_FROM}`.
  - Testes cobrem: recipient, `from`, `subject`, body (URL presente), best-effort (exceção não propagada) e log behavior (`ListAppender`) — para ambos os métodos. Padrão `ListAppender` consistente com `GlobalExceptionHandlerIT` (SI-02.4).

### SI-02.6 — Cadastro de usuário: POST /api/auth/register
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.7 — Confirmação de conta: GET /api/auth/confirm + POST /api/auth/resend-confirmation
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.8 — Login e sessão: POST /api/auth/login + POST /api/auth/logout + GET /api/me
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.9 — Recuperação de senha: POST /api/auth/forgot-password + POST /api/auth/reset-password
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.10 — Bootstrap do projeto Angular + Angular Material
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.11 — Infra de auth no cliente: ProblemDetail, ErrorHandlerService, AuthService e guard
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.12 — Componentes compartilhados de layout de auth
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.13 — Componente password-strength
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.14b — Tela /signup (Create user account)
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.15b — Tela /login (User login)
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.16b — Tela /forgot-password (Reset password)
- **Status:** pending
- **Tests:** pending
- **Observations:** none

### SI-02.17b — Tela /reset-password (Set new password)
- **Status:** pending
- **Tests:** pending
- **Observations:** none
