# Code Review — SI-02.3: Spring Security JWT + PasswordEncoder
## Round 6 — Final

**Branch:** `feature/user-auth-01`
**Data:** 2026-06-27
**Escopo:** 8 arquivos — `SecurityConstants.java`, `SecurityConfig.java`, `CookieBearerTokenResolver.java`, `PasswordEncoderConfig.java`, `AuthCookieHelper.java`, `SecurityConfigIT.java`, `CookieBearerTokenResolverTest.java`, `PasswordEncoderTest.java`

---

## Resultado Geral

**Aprovado sem ressalvas. Pronto para commit.**

Não há issues abertos. A última correção (`buildExpiredJwt` com `minusSeconds(3600)`) foi aplicada corretamente.

| Severidade | R1 | R2 | R3 | R4 | R5 | R6 |
|------------|----|----|----|----|----|----|
| Blocker    | 0  | 0  | 0  | 0  | 0  | 0  |
| Major      | 2  | 0  | 0  | 0  | 0  | 0  |
| Minor      | 5  | 3  | 3  | 2  | 1  | 0  |

---

## O Que Foi Corrigido Neste Round

### ✅ [m-1 R5] `buildExpiredJwt()` — margem aumentada de 1s para 3600s

```java
// antes — margem frágil
return buildJwt(jwtSecret, Instant.now().minusSeconds(1));

// depois — inequivocamente expirado
return buildJwt(jwtSecret, Instant.now().minusSeconds(3600));
```

O JWT expirado agora está 1 hora no passado — bem além de qualquer `clockSkew` que o `NimbusJwtDecoder` possa ter configurado. O teste é robusto contra mudanças futuras na política de tolerância de tempo.

---

## Análise Final — Todos os Arquivos

### `SecurityConstants.java` ✅

```java
final class SecurityConstants {
    static final String ACCESS_TOKEN_COOKIE = "access_token";
    private SecurityConstants() {}
}
```

Package-private, `final`, construtor privado. A constante não vaza para fora de `infrastructure/security`. Nenhuma dependência de framework. Sem issues.

---

### `SecurityConfig.java` ✅

- Constructor injection para `jwtSecret` — SOLID-D, testável sem Spring context.
- `JwtDecoder`: HMAC-SHA256 via `NimbusJwtDecoder.withSecretKey`, chave derivada de UTF-8 bytes.
- `SecurityFilterChain`: depende de `BearerTokenResolver` (interface), não da implementação concreta.
- Authorization matrix fiel ao spec: 6 endpoints `permitAll()`, `anyRequest().authenticated()` cobre `/api/me` e `/api/auth/logout`.
- CSRF desabilitado (correto: `SameSite=Strict` + `STATELESS` dispensam CSRF token).
- httpBasic e formLogin explicitamente desabilitados.
- Sem issues.

---

### `CookieBearerTokenResolver.java` ✅

- Implementa `BearerTokenResolver` — substituível via qualquer `@Component` da interface.
- `COOKIE_NAME` referencia `SecurityConstants.ACCESS_TOKEN_COOKIE` — DRY.
- Guarda `cookies == null` — evita NPE quando o browser não envia cookies.
- Guarda `isBlank()` — evita que cookie vazio/whitespace seja passado ao `JwtDecoder`.
- Stateless — thread-safe por design.
- Sem issues.

---

### `PasswordEncoderConfig.java` ✅

Única responsabilidade, sem estado, sem dependências. Sem issues.

---

### `AuthCookieHelper.java` ✅

- `tokenMaxAge` injetado de `app.jwt.expiration-seconds` — sincronizado com o TTL do JWT.
- `COOKIE_NAME` referencia `SecurityConstants.ACCESS_TOKEN_COOKIE` — DRY.
- `buildSetCookie`: `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`, `Max-Age=3600` — fiel ao `user-auth/TD-01`.
- `buildClearCookie`: `Max-Age=0` via `Duration.ZERO` — correto para expirar o cookie no logout.
- Stateless — thread-safe por design.
- Sem issues.

---

### `CookieBearerTokenResolverTest.java` ✅

5 testes unitários puros com `MockHttpServletRequest`. Todos os branches da implementação cobertos:

| Branch | Teste |
|--------|-------|
| `getCookies() == null` | `givenNoCookies_whenResolve_thenReturnsNull` |
| Cookie com valor válido | `givenAccessTokenCookie_whenResolve_thenReturnsValue` |
| Cookie com valor em branco | `givenAccessTokenCookieWithBlankValue_whenResolve_thenReturnsNull` |
| Cookie de nome diferente | `givenOtherCookieOnly_whenResolve_thenReturnsNull` |
| Cookie não é o primeiro do array | `givenAccessTokenIsNotFirstCookie_whenResolve_thenReturnsValue` |

Fixtures usam `SecurityConstants.ACCESS_TOKEN_COOKIE`. FIRST-compliant. Sem issues.

---

### `PasswordEncoderTest.java` ✅

3 testes unitários puros. Todos os comportamentos do acceptance criteria cobertos:

| Comportamento | Teste |
|---------------|-------|
| Encode + matches positivo | `givenRawPassword_whenEncoded_thenMatchesViaEncoder` |
| Prefixo `{bcrypt}` presente | `givenEncodedPassword_whenInspected_thenHasBcryptPrefix` |
| Matches negativo | `givenWrongPassword_whenMatches_thenReturnsFalse` |

FIRST-compliant. Sem issues.

---

### `SecurityConfigIT.java` ✅

4 testes de integração cobrindo os acceptance criteria do SI-02.3 e invariantes de segurança adicionais:

| Teste | Cenário |
|-------|---------|
| `givenNoAuthCookie_whenGetApiMe_thenReturns401` | Sem cookie |
| `givenValidJwtInCookie_whenGetApiMe_thenReturns200` | JWT válido |
| `givenExpiredJwtInCookie_whenGetApiMe_thenReturns401` | JWT expirado 1h atrás |
| `givenJwtWithWrongSignatureInCookie_whenGetApiMe_thenReturns401` | Assinatura inválida |

- `buildJwt(String secret, Instant expiresAt)` centraliza a lógica de construção — DRY.
- Fixtures usam `SecurityConstants.ACCESS_TOKEN_COOKIE`.
- `MeStubController` como inner class com `@Import` — contexto de produção limpo.
- `@SpringBootTest` + `@ServiceConnection` — padrão estabelecido do projeto.
- Sem issues.

---

## Checklist de Conformidade Final

### SOLID (5/5 ✅)

| Princípio | Status | Evidência |
|-----------|--------|-----------|
| S — Single Responsibility | ✅ | 5 classes de produção, cada uma com uma razão de mudança |
| O — Open/Closed | ✅ | Nova estratégia de token: novo `@Component` sem tocar `SecurityConfig` |
| L — Liskov Substitution | ✅ | `CookieBearerTokenResolver` honra integralmente `BearerTokenResolver` |
| I — Interface Segregation | ✅ | Interfaces do Spring Security são focadas e mínimas |
| D — Dependency Inversion | ✅ | `SecurityConfig` depende de `BearerTokenResolver`, não da implementação |

### Clean Architecture (✅)

| Regra | Status |
|-------|--------|
| Código de infra em `infrastructure/security/` | ✅ |
| Domínio sem anotações de infraestrutura | ✅ |
| Dependências apontando para dentro | ✅ |
| `SecurityConstants` inacessível fora do pacote | ✅ |

### TDD / FIRST (✅)

| Propriedade | Status |
|-------------|--------|
| Fast | ✅ — unit tests sem Spring context |
| Independent | ✅ — sem estado compartilhado |
| Repeatable | ✅ — `Instant` explícito, sem randomness |
| Self-validating | ✅ — assertions claras |
| Timely | ✅ — testes escritos antes/junto da impl |

### Acceptance Criteria SI-02.3 (7/7 ✅)

| Critério | Status |
|----------|--------|
| `GET /api/me` sem cookie → 401 | ✅ |
| `GET /api/me` com JWT válido → 200 | ✅ |
| JWT expirado → 401 | ✅ |
| JWT com assinatura inválida → 401 | ✅ |
| `encoder.matches(raw, encoded)` → `true` | ✅ |
| `encoder.matches(wrong, encoded)` → `false` | ✅ |
| Hash com prefixo `{bcrypt}` | ✅ |

---

## Histórico Completo de Todos os Rounds

| Ponto | R1 | R2 | R3 | R4 | R5 | R6 |
|-------|----|----|----|----|----|----|
| M-1: AuthCookieHelper TTL drift | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| M-2: Field injection SecurityConfig | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| m-1: Teste negativo PasswordEncoder | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| m-2: JWT expirado/inválido sem teste | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| m-3: PasswordEncoder em classe separada | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| m-4: BearerTokenResolver como lambda | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| m-5: @SpringBootTest overhead | ⚠️ | ⚠️ | ⚠️ | encerrado | — | — |
| R2-m-1: SOLID-D parâmetro concreto | — | ❌ | ✅ | ✅ | ✅ | ✅ |
| R2-m-2: CookieBearerTokenResolver sem teste | — | ❌ | ✅ | ✅ | ✅ | ✅ |
| R3-m-1: COOKIE_NAME duplicado | — | — | ❌ | ✅ | ✅ | ✅ |
| R3-m-2: access_token não-primeiro no array | — | — | ❌ | ✅ | ✅ | ✅ |
| R4-m-1: literal "access_token" nos testes | — | — | — | ❌ | ✅ | ✅ |
| R4-m-2: SecurityConstants public | — | — | — | ❌ | ✅ | ✅ |
| R5-m-1: margem de 1s no JWT expirado | — | — | — | — | ❌ | ✅ |

---

*Revisão gerada por Claude Code — Sonnet 4.6 — 2026-06-27 (Round 6 — Final)*
