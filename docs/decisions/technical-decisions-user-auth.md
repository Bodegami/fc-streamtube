---
scope_type: phase
related_phases: [2]
status: decided
date: 2026-06-26
scope_description: "Decisões técnicas para a Fase 02 — autenticação, sessão, hashing de senha, tokens de e-mail, contrato de erros da API e biblioteca de UI Angular"
---

# Technical Decisions — Phase 02: Autenticação e Gerenciamento de Conta

_Subprojects in scope:_

- `backend/` — lógica de autenticação (login/logout, registro, confirmação de e-mail, recuperação de senha), hashing de senha, emissão de tokens e contrato de erros da API
- `frontend/` — telas de cadastro, login, confirmação de conta e recuperação de senha; estratégia de estado de autenticação no cliente; biblioteca de componentes UI

---

## TD-01: Auth Strategy & Token Transport

**Scope:** Cross-layer

**Capability:** "Login e controle de sessão do usuário" + "Logout"

**Context:** A estratégia de autenticação define como o backend emite credenciais após o login e como o frontend as apresenta em cada requisição subsequente. A escolha impacta segurança (XSS, CSRF), escalabilidade horizontal e a implementação em ambos os subprojetos. O backend usa Spring Security 6.4 (Spring Boot 3.5.3); o frontend é Angular LTS (SPA) com `HttpClient`. Não há Redis no stack atual, o que influencia a viabilidade de sessão distribuída.

**Options:**

### Option A: Stateless JWT via Authorization Bearer header

- O backend assina um JWT (HMAC-SHA256 ou RSA) ao fazer login e o retorna no corpo da resposta. Angular armazena o token (localStorage ou memória in-page) e um `HttpInterceptor` injeta `Authorization: Bearer {token}` em toda requisição. O backend valida via `spring-security-oauth2-resource-server` com `JwtDecoder` customizado.
- **Pros:** Stateless (escala horizontal sem Redis); Angular pode inspecionar o payload do JWT (expiração, claims de roles) sem chamada de API; interceptor de auth direto.
- **Cons:** localStorage expõe o token a ataques XSS; armazenamento in-memory perde o token ao recarregar a página (força novo login ou fluxo de refresh); refresh token adiciona complexidade.

### Option B: Stateless JWT via httpOnly cookie

- O backend assina o mesmo JWT, mas o define via `Set-Cookie: access_token={jwt}; HttpOnly; Secure; SameSite=Strict` na resposta de login. Um `BearerTokenResolver` customizado no Spring Security extrai o token do cookie em vez do header `Authorization`. Angular usa `withCredentials: true` no `HttpClient` — o navegador envia o cookie automaticamente. Para verificar se o usuário está autenticado, Angular chama um endpoint `/me` (retorna dados do usuário ou 401).
- **Pros:** Zero acesso JS ao token (elimina XSS como vetor de roubo); SameSite=Strict mitiga CSRF sem CSRF token adicional; stateless (sem Redis); sessão persiste entre abas e recarregamentos.
- **Cons:** `BearerTokenResolver` customizado (~15 linhas) necessário no Spring Security; Angular não pode inspecionar o JWT diretamente (sem acesso ao cookie httpOnly); endpoint `/me` obrigatório para estado de auth no cliente.

### Option C: Sessão stateful (Spring Security padrão + JDBC Session)

- Spring Security gerencia uma sessão server-side; o JSESSIONID é enviado como cookie httpOnly pelo navegador automaticamente. O `SecurityContext` é armazenado em memória (dev) ou em tabela JDBC (`spring-session-jdbc`) para escala. Angular usa `withCredentials: true` — mesmo padrão frontend que a Option B.
- **Pros:** Revogação imediata no logout (sessão destruída no servidor); Spring Security cuida de tudo nativamente sem JWT; sem dependência de libs JWT.
- **Cons:** Escala horizontal requer sessão compartilhada (JDBC ou Redis) — adiciona complexidade de infra não prevista para Phase 02; sessão stateful é mais pesada que JWT para uma API REST; CSRF protection obrigatória (cookies de sessão são vulneráveis a CSRF cross-origin).

**Recommendation:** Option B — JWT em httpOnly cookie combina a escalabilidade stateless do JWT com a postura de segurança superior de um httpOnly cookie (sem XSS via JS); o `BearerTokenResolver` customizado é mínimo; `SameSite=Strict` descarta CSRF sem CSRF token adicional; Angular com `withCredentials: true` funciona identicamente para Option B e C (sem lock-in de transporte no cliente). Evita adicionar Redis ou tabela de sessão JDBC em Phase 02 (Option C). Evita o risco XSS de localStorage (Option A).

**Decision:** _[Option B]_

---

## TD-02: Password Hashing Algorithm

**Scope:** Backend

**Capability:** "Cadastro de usuário com e-mail e senha"

**Context:** O cadastro de usuário requer hashing seguro da senha antes de persistir. Spring Security 6.4 oferece múltiplas implementações de `PasswordEncoder`. A escolha impacta a resistência a ataques GPU/ASIC e as dependências do `backend/`.

**Options:**

### Option A: BCrypt (`BCryptPasswordEncoder`)

- Padrão do Spring Security. Nenhuma dependência adicional. Work factor configurável (default 10 — alvo de ~100ms por hash). Amplamente suportado e battle-tested desde 1999. O `DelegatingPasswordEncoder` usa `{bcrypt}` como prefixo padrão, permitindo migração futura transparente.
- **Pros:** Zero dependência extra; suportado nativamente pelo Spring Security sem configuração adicional; amplamente adotado no ecossistema Spring/JVM; custo computacional é configurável via strength parameter.
- **Cons:** Não é memory-hard (custo de memória baixo → GPUs e ASICs conseguem paralelizar ataques de força bruta); limite de 72 bytes de input (senhas > 72 chars são truncadas silenciosamente); OWASP 2023 recomenda Argon2 para projetos novos.

### Option B: Argon2id (`Argon2PasswordEncoder`)

- Vencedor do Password Hashing Competition (2015); recomendação #1 do OWASP 2023 para projetos novos. Memory-hard: a função exige um bloco de memória configurável (padrão ~64MB), tornando ataques GPU/ASIC economicamente inviáveis. Spring Security 6.4 suporta via `Argon2PasswordEncoder`; requer dependência adicional: `org.bouncycastle:bcpkix-jdk18on`.
- **Pros:** Memory-hard (resiste a ataques GPU/ASIC); parâmetros configuráveis (memória, iterações, paralelismo); nenhum limite prático de tamanho de input; recomendado pelo OWASP 2023.
- **Cons:** Requer `bouncycastle` como dependência de runtime no `build.gradle.kts`; consome mais memória por operação de hash (custo no servidor ao logar muitos usuários simultâneos); Spring Security 7.x trará `Argon2Password4jPasswordEncoder` sem BouncyCastle, mas isso requer upgrade de Spring Boot.

**Recommendation:** Option A (BCrypt) — BCrypt é o padrão do Spring Security, sem dependência adicional, e battle-tested para o volume de uma plataforma de vídeo em early stage; `DelegatingPasswordEncoder` permite migrar transparentemente para Argon2 em fases futuras sem reprocessar todas as senhas (o `{id}` prefixo identifica o algoritmo por hash). O `bcpkix-jdk18on` da BouncyCastle é uma dependência externa de runtime que vale a adição se o OWASP compliance for um requisito explícito — se for, escolher Option B.

**Decision:** _[Option A]_

---

## TD-03: One-Time Token Strategy

**Scope:** Backend

**Capability:** "Serviço de envio de e-mails transacionais" + "Confirmação de conta via e-mail com link de ativação" + "Recuperação de senha: solicitação via e-mail → link com token → redefinição"

**Context:** Dois fluxos da Fase 02 dependem de tokens de uso único enviados por e-mail: (1) confirmação de e-mail após cadastro e (2) reset de senha. O token viaja no link do e-mail como query param. O backend deve gerálo, armazená-lo (ou não), validá-lo e invalidá-lo após o uso. O envio em si usa Spring Mail + JavaMailSender (SMTP definido no stack ADR); a decisão aqui é sobre o mecanismo do token.

**Options:**

### Option A: Token opaco (UUID/SecureRandom) armazenado em tabela DB

- `SecureRandom` gera 32 bytes aleatórios → Base64-URL encoding → armazenado em tabela `user_tokens` com `user_id`, `type` (CONFIRMATION | PASSWORD_RESET), `token`, `expires_at`, `used_at`. Validação: busca por token no DB, verifica expiração e se não foi usado, marca como usado.
- **Pros:** Revogável a qualquer momento (delete ou mark-used); "reenviar e-mail" invalida tokens anteriores do mesmo tipo; link inócuo após uso; sem crypto adicional além do `SecureRandom`.
- **Cons:** Requer tabela `user_tokens` no schema (Flyway migration); DB lookup em cada validação (latência trivial para esse fluxo de baixa frequência).

### Option B: Token JWT assinado (stateless)

- Gera um JWT com claims `sub` (userId), `type`, `exp` (ex: 24h). Assina com HMAC-SHA256 (reutiliza a infra de JWT do TD-01 se Option B/A for escolhida). O token viaja no link do e-mail; o backend valida a assinatura e expiração, sem consultar o banco.
- **Pros:** Stateless — sem tabela adicional; reutiliza a infra de JWT; nenhum DB lookup na validação.
- **Cons:** Não revogável antes do `exp` — link de confirmação antigo continua válido mesmo após "reenviar e-mail"; se a conta for confirmada, o link antigo ainda pode ser clicado (sem `used_at`); revogação exigiria uma denylist (nova tabela, complexidade equivalente à Option A).

### Option C: Spring Security `OneTimeTokenService` (Spring Security 6.4)

- Spring Security 6.4 adicionou `OneTimeTokenLoginConfigurer` com `JdbcOneTimeTokenService` para persistência. Projetado para fluxos de magic-link login (passwordless). O `GenerateOneTimeTokenRequest` + `OneTimeTokenGenerationSuccessHandler` gerencia geração, armazenamento e entrega do token.
- **Pros:** Implementação gerenciada pelo Spring Security; `JdbcOneTimeTokenService` cuida de expiração e storage nativamente; TTL configurável.
- **Cons:** Projetado para magic-link login (passwordless), não para confirmação de e-mail ou reset de senha — adaptar para esses fluxos exige sobrescrever o `SuccessHandler` e redirecionar para telas diferentes; acoplamento à API interna do Spring Security; complexidade maior que a Option A para o mesmo resultado.

**Recommendation:** Option A — token opaco em DB é a solução mais simples, explícita e revogável para os dois fluxos da Fase 02; a tabela `user_tokens` é uma migration direta; "reenviar e-mail de confirmação" invalida tokens anteriores naturalmente; sem dependência de comportamento interno do Spring Security que pode mudar entre minor releases. Option C seria preferível se o projeto usasse magic-link login como fluxo primário (sem senha), o que não é o caso.

**Decision:** _[Option A]_

---

## TD-04: API Error Response Contract

**Scope:** Cross-layer

**Capability:** Transversal — covers: "Cadastro de usuário com e-mail e senha", "Login e controle de sessão do usuário", "Telas de cadastro, login, confirmação de conta e recuperação de senha"

**Context:** Toda tela da Fase 02 faz requisições à API e deve exibir erros ao usuário (e-mail já cadastrado, credenciais inválidas, token expirado, campos obrigatórios, etc.). O contrato de resposta de erro define o shape JSON que o backend retorna e que o Angular deve parsear para exibir feedback. Spring Boot 3.x tem `ProblemDetail` nativo; a escolha impacta como Angular tipa e processa erros em todas as fases seguintes.

**Options:**

### Option A: RFC 7807 Problem Details (`ProblemDetail`)

- Spring Boot 3.x introduziu suporte nativo a RFC 7807 via a classe `ProblemDetail` e a hierarquia `ErrorResponseException`. Ativado com `spring.mvc.problemdetails.enabled=true`. Shape padrão: `{ "type": "...", "title": "...", "status": 400, "detail": "...", "instance": "..." }` + campos customizáveis (ex: `invalidFields` para erros de validação de formulário). Angular tipifica como `ProblemDetail` interface.
- **Pros:** Suporte nativo no Spring Boot 3 (zero dependência extra); shape padronizado (RFC); extensível com campos customizados por tipo de erro; Angular pode ter um único `ErrorHandlerService` genérico; interoperável com clients futuros.
- **Cons:** Campo `type` deve ser uma URI — requer placeholder ou URL real de documentação de erros; ligeiramente mais verboso que um body customizado simples.

### Option B: Body de erro customizado via `@ControllerAdvice`

- `@ControllerAdvice` + `@ExceptionHandler` retornam um POJO customizado: `{ "code": "USER_ALREADY_EXISTS", "message": "E-mail já está em uso" }`. Controle total sobre o shape.
- **Pros:** Controle total do shape; simples de entender para devs que não conhecem RFC 7807; `code` enum facilita i18n no frontend.
- **Cons:** Shape não padronizado; Angular precisa de tipo customizado sem reuso entre projetos; inconsistências podem surgir entre endpoints implementados por diferentes pessoas; reproduz manualmente o que o Spring já oferece nativamente.

### Option C: `BasicErrorController` padrão do Spring Boot

- Sem configuração adicional; Spring Boot retorna `{ "timestamp": "...", "status": 400, "error": "Bad Request", "path": "/api/users" }` por padrão.
- **Pros:** Zero esforço de configuração.
- **Cons:** Shape pobre: sem código de erro estruturado, sem lista de campos inválidos; Angular precisaria parsear strings de `error.message` para exibir feedback útil ao usuário; inaceitável para formulários com validação por campo.

**Recommendation:** Option A (RFC 7807 `ProblemDetail`) — Spring Boot 3.x suporta nativamente com um único `application.properties` flag; Angular pode definir uma interface `ProblemDetail` com campos de extensão por domínio (ex: `invalidFields: Record<string, string>` para erros de validação de formulário); padronização garante consistência entre todos os endpoints das Fases 02–07 sem cerimônia adicional.

**Decision:** _[Option A]_

---

## TD-05: Angular UI Component Library

**Scope:** Frontend

**Capability:** "Telas de cadastro, login, confirmação de conta e recuperação de senha"

**Context:** A Fase 02 introduz as primeiras telas do frontend Angular: cadastro, login, confirmação de conta e recuperação de senha. A biblioteca de componentes escolhida aqui define o padrão visual e de componentes para todas as fases seguintes (Fase 04–07 adicionam mais telas). Angular LTS (versão atual: Angular 22.x). Não há menção a sistema de design específico no stack ADR — a escolha está em aberto.

**Options:**

### Option A: Angular Material (Google)

- Biblioteca oficial mantida pelo time do Angular; versão atual `@angular/material@22.0.2` para Angular 22.x. Instalação: `ng add @angular/material`. Segue Material Design 3 (M3). Componentes standalone (sem `NgModule` desde Angular 17+). Cobre: form fields (`MatFormField`, `MatInput`), botões, cards, snackbar, dialog, toolbar.
- **Pros:** Mantida pelo mesmo time que o Angular (zero risco de version mismatch); componentes standalone nativos; CDK para custom components; temas configuráveis via `mat-theme()` (SCSS); accessibilidade (ARIA) embutida; todo componente necessário para Phase 02 está disponível.
- **Cons:** Aesthetic Material Design é opinativo — personalização profunda exige conhecimento de SCSS e tokens M3; `ng add @angular/material` modifica `styles.scss` e `app.config.ts` automaticamente (mudanças auditáveis via `git diff`).

### Option B: PrimeNG

- Biblioteca de terceiro com 90+ componentes; `ng add primeng`. PrimeIcons + PrimeFlex incluídos. Suporta Angular 17+.
- **Pros:** Set de componentes maior que Angular Material; múltiplos temas pré-configurados (Lara, Aura, Nora) mais customizáveis visualmente; PrimeIcons integrado.
- **Cons:** Terceiro — versão pode atrasar em relação ao Angular major releases (ex: Angular 22 pode não ter suporte imediato); bundle maior para as telas simples da Phase 02; histórico de mudanças de API entre major versions que quebram código existente.

### Option C: Sem biblioteca de componentes (TailwindCSS + componentes custom)

- Nenhuma biblioteca de componentes; `tailwindcss` como utilitário de CSS via `ng add @analogjs/tailwind` ou config manual; todos os componentes (inputs, botões, cards) construídos do zero em Angular.
- **Pros:** Controle máximo sobre estilo e bundle; sem risco de version mismatch com bibliotecas de terceiro; componentes com exatamente o design desejado.
- **Cons:** Significativamente mais trabalho para construir componentes acessíveis (ARIA, focus management, keyboard navigation) que bibliotecas já entregam prontos; para 4+ telas na Phase 02 e mais 20+ telas nas fases seguintes, o custo de manutenção é alto; sem padrão de design system emergente da comunidade.

**Recommendation:** Option A (Angular Material) — a biblioteca oficial do Angular elimina o risco de incompatibilidade de versão ao longo das 7 fases do projeto; todos os componentes necessários para a Phase 02 (form fields, botões, snackbar, cards) estão disponíveis como standalone imports; a accessibilidade embutida cobre os requisitos de UX sem implementação adicional. PrimeNG (Option B) seria preferível apenas se o design system exigir flexibilidade visual além do Material Design — o que não está definido nos requisitos atuais.

**Decision:** _[Option A]_

---

## TD-06: User Entity Primary Key Type

**Scope:** Backend

**Capability:** "Cadastro de usuário com e-mail e senha"

**Context:** A entidade `User` precisa de um tipo de chave primária definido antes da primeira migration. A escolha impacta diretamente `canal-aggregate/TD-02`, cuja estratégia de fallback de slug deriva os 4 primeiros chars hexadecimais do `userId` — o que só funciona se o PK for UUID. Além disso, o PK do usuário viaja como claim `sub` no JWT (user-auth/TD-01) e como FK na tabela `user_tokens` (user-auth/TD-03) e futuramente em `channels`, `videos`, `likes`, etc. Spring Boot 3.x + Hibernate 6 suportam `@GeneratedValue(strategy = GenerationType.UUID)` nativamente sem dependência adicional.

**Options:**

### Option A: UUID v4 (`@GeneratedValue(strategy = GenerationType.UUID)`)

- `@Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;` na entidade de domínio (via objeto de mapeamento em infraestrutura, per Clean Architecture). Hibernate 6 gera UUID v4 via `java.util.UUID.randomUUID()`. PostgreSQL armazena como `uuid` (16 bytes, indexável). Coluna `uuid` com `DEFAULT gen_random_uuid()` na migration Flyway.
- **Pros:** Suporte nativo no Hibernate 6 / Spring Boot 3.x (zero dependência extra); não-sequencial (previne enumeração de IDs em JWT/API); consistente com `canal-aggregate/TD-02` (fallback de slug usa primeiros 4 hex chars do userId); FK `uuid` em todas as tabelas relacionadas.
- **Cons:** 16 bytes vs 8 bytes (BIGINT); UUID v4 aleatório gera fragmentação de índice B-tree em inserções de alta frequência (irrelevante para usuários em early stage); menos legível em logs do que um inteiro.

### Option B: Auto-increment long (`BIGSERIAL` / `GenerationType.IDENTITY`)

- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`. PostgreSQL usa `BIGSERIAL`. Hibernate delega a geração ao banco.
- **Pros:** 8 bytes; sem fragmentação de índice; mais simples em queries de debug; padrão em muitos projetos CRUD.
- **Cons:** Sequencial — expõe contagem de usuários via JWT (`sub: 1, 2, 3...`); **incompatível com `canal-aggregate/TD-02`** (fallback de slug usa 4 hex chars de UUID; com BIGINT exigiria redesign de TD-02); IDENTITY requer round-trip ao DB para obter o ID gerado (sem pré-atribuição na construção da entidade).

### Option C: UUID v7 (time-ordered, biblioteca externa)

- UUID v7 é time-ordered (monotonicamente crescente por timestamp), resolvendo a fragmentação de índice do UUID v4. Requer dependência: `com.fasterxml.uuid:java-uuid-generator` ou similar. Java 25 não tem UUID v7 nativo (previsto para uma futura JEP).
- **Pros:** Time-ordered → melhor localidade de B-tree index; não-sequencial e não-enumerável; compatível com `canal-aggregate/TD-02`.
- **Cons:** Dependência externa de runtime (`java-uuid-generator`); complexidade de configuração do gerador no Hibernate 6 (custom `UUIDGenerator`); fragmentação de índice é irrelevante em early stage para uma plataforma de vídeos.

**Recommendation:** Option A (UUID v4) — suporte nativo no Hibernate 6 sem dependência adicional; não-sequencial (previne enumeração de userId via JWT); diretamente compatível com `canal-aggregate/TD-02`'s slug fallback; a fragmentação de índice B-tree do UUID v4 é irrelevante no volume de uma plataforma em early stage. Option C seria justificável apenas se o projeto atingir escala que torne a fragmentação mensurável — não é o caso agora. Option B é incompatível com o já decidido `canal-aggregate/TD-02`.

**Decision:** _[Option A]_

---

## Decisions Summary

| ID | Scope | Decision | Recommendation | Choice |
|----|-------|----------|----------------|--------|
| TD-01 | Cross-layer | Auth Strategy & Token Transport | JWT em httpOnly cookie (Option B) | _[Option B]_ |
| TD-02 | Backend | Password Hashing Algorithm | BCrypt (Option A) | _[Option A]_ |
| TD-03 | Backend | One-Time Token Strategy | Token opaco em DB (Option A) | _[Option A]_ |
| TD-04 | Cross-layer | API Error Response Contract | RFC 7807 ProblemDetail (Option A) | _[Option A]_ |
| TD-05 | Frontend | Angular UI Component Library | Angular Material (Option A) | _[Option A]_ |
| TD-06 | Backend | User Entity Primary Key Type | UUID v4 nativo Hibernate 6 (Option A) | _[Option A]_ |
