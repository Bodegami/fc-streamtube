# S4 — Code Review: SI-02.4 (RFC 7807 ProblemDetail)

Revisor: Claude Sonnet 4.6
Branch: `feature/user-auth-01`
Escopo: arquivos não commitados da etapa SI-02.4

---

## Round 1

### Arquivos revisados

| Arquivo | Status |
|---|---|
| `domain/exceptions/EmailAlreadyExistsException.java` | novo |
| `domain/exceptions/InvalidCredentialsException.java` | novo |
| `domain/exceptions/InvalidTokenException.java` | novo |
| `interfaces/controllers/GlobalExceptionHandler.java` | novo |
| `interfaces/controllers/GlobalExceptionHandlerIT.java` | novo |
| `backend/build.gradle.kts` | modificado (+validation starter) |
| `backend/src/main/resources/application.yml` | modificado (+problem-details) |

---

### P1 — Deve corrigir (bloqueia próximos SIs ou quebra contrato de API)

---

#### CR-01 · Exceções de domínio sem construtor `(String, Throwable)`

**Arquivo:** `domain/exceptions/EmailAlreadyExistsException.java`, `InvalidCredentialsException.java`, `InvalidTokenException.java`

**Problema:** As três exceções só expõem `(String message)`. Quando um Use Case capturar uma exceção de infraestrutura (ex: `DataIntegrityViolationException` do JPA ao detectar e-mail duplicado) e relançar como `EmailAlreadyExistsException`, a causa raiz será perdida. Isso torna debugging em produção muito mais difícil.

**Regra violada:** Java best practice para hierarquias de exceção — todo `RuntimeException` personalizado deve expor os quatro construtores canônicos ou, no mínimo, o par `(String)` + `(String, Throwable)`.

**Correção:**

```java
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }

    public EmailAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

Aplicar o mesmo padrão em `InvalidCredentialsException` e `InvalidTokenException`.

---

#### CR-02 · Métodos `@ExceptionHandler` são package-private

**Arquivo:** `interfaces/controllers/GlobalExceptionHandler.java` — linhas 39, 47, 55

**Problema:** `handleEmailAlreadyExists`, `handleInvalidCredentials` e `handleInvalidToken` não declaram `public`. Spring resolve `@ExceptionHandler` via reflexão e funciona com package-private em testes unitários, mas proxies CGLIB (usados quando a classe recebe outro `@Bean` como dependência) exigem visibilidade `public` para sobrescrever métodos corretamente. O `handleMethodArgumentNotValid` override (linha 26) também está package-private, o que é inconsistente com a visibilidade do método na superclasse.

**Regra violada:** SOLID-L (Liskov): subtipos devem respeitar os contratos dos pais — `ResponseEntityExceptionHandler.handleMethodArgumentNotValid` é `protected`, não package-private.

**Correção:** adicionar `public` (handlers custom) e `@Override protected` (override da superclasse):

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(...) { ... }

@ExceptionHandler(EmailAlreadyExistsException.class)
public ResponseEntity<ProblemDetail> handleEmailAlreadyExists(...) { ... }
```

---

#### CR-03 · Sem handler de fallback para `Exception.class`

**Arquivo:** `interfaces/controllers/GlobalExceptionHandler.java`

**Problema:** `ResponseEntityExceptionHandler` captura apenas exceções do framework Spring MVC. Exceções de aplicação não mapeadas (ex: `NullPointerException`, futuras exceções de domínio ainda não registradas no handler) passam pelo filtro e chegam ao `BasicErrorController` do Spring Boot, que pode retornar `text/html` ou um JSON sem formato RFC 7807. Isso quebra o contrato de API para os consumidores (frontend Angular) que esperam sempre `application/problem+json`.

**Regra violada:** RFC 7807 — o contrato de erro deve ser consistente em todos os cenários.

**Correção:**

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    body.setTitle("Internal Server Error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
}
```

---

#### CR-04 · Testes não validam `$.title` e `$.detail` — contrato RFC 7807 incompleto

**Arquivo:** `interfaces/controllers/GlobalExceptionHandlerIT.java` — linhas 52–73

**Problema:** Os três testes de exceção customizada (`givenDuplicateEmail`, `givenInvalidCredentials`, `givenInvalidToken`) verificam somente `$.status` e o `Content-Type`. O título e o detalhe da resposta — campos obrigatórios documentados nas `Observations` do SI-02.4 — não são assertados. Uma regressão que apague esses campos passaria verde.

**Regra violada:** testing-strategy.md — *"Tests must have a clear pass/fail assertion"* (FIRST-S). O nome dos testes promete "WithDetail" mas o assert não cobre o detalhe.

**Correção (exemplo para o teste de 409):**

```java
@Test
void givenDuplicateEmail_whenThrown_thenReturns409WithDetail() throws Exception {
    mockMvc.perform(post("/test/email-exists"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Email Already Exists"))
            .andExpect(jsonPath("$.detail").isNotEmpty());
}
```

Aplicar o mesmo padrão nos testes de 401 e 410, e também no de 400 (que deve verificar `$.title` = "Validation Error" e `$.detail` não vazio).

---

### P2 — Deve melhorar (qualidade de design / cobertura de contrato)

---

#### CR-05 · `handleMethodArgumentNotValid` não seta `MediaType.APPLICATION_PROBLEM_JSON` explicitamente

**Arquivo:** `GlobalExceptionHandler.java` — linha 35

**Problema:** Os handlers de P1 (400, 401, 409, 410) setam `MediaType.APPLICATION_PROBLEM_JSON` explicitamente via `.contentType(...)`. Mas `handleMethodArgumentNotValid` delega para `handleExceptionInternal` sem forçar o content type, dependendo da negociação de conteúdo do `ResponseEntityExceptionHandler`. Isso é uma inconsistência no contrato: o 400 pode chegar com `application/json` em vez de `application/problem+json` dependendo do `Accept` header do cliente.

**Correção:**

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body validation failed");
    body.setTitle("Validation Error");
    // ... (build invalidFields)
    body.setProperty("invalidFields", invalidFields);
    headers = HttpHeaders.writableHttpHeaders(headers);
    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return handleExceptionInternal(ex, body, headers, HttpStatus.BAD_REQUEST, request);
}
```

---

#### CR-06 · `handleMethodArgumentNotValid` ignora parâmetro `status` e hardcoda `BAD_REQUEST`

**Arquivo:** `GlobalExceptionHandler.java` — linhas 28 e 35

**Problema:** O método recebe `HttpStatusCode status` como parâmetro (resolvido pelo Spring com base no tipo de exceção), mas o ignora e hardcoda `HttpStatus.BAD_REQUEST` em dois lugares. Se o Spring mudar a resolução de status para `MethodArgumentNotValidException` em futuras versões, a inconsistência será silenciosa.

**Correção:** usar `status` ou `HttpStatus.valueOf(status.value())` consistentemente:

```java
ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status.value()), "Request body validation failed");
// ...
return handleExceptionInternal(ex, body, headers, HttpStatus.valueOf(status.value()), request);
```

---

#### CR-07 · Magic string `"invalidFields"` — extrair para constante

**Arquivo:** `GlobalExceptionHandler.java` — linha 33

**Problema:** `"invalidFields"` é uma string que faz parte do contrato de API (referenciada no frontend e nos testes). Se for digitada errada em qualquer ponto, o erro só aparece em runtime. Deve ser uma constante acessível também pelo teste.

**Correção:**

```java
// Em GlobalExceptionHandler ou em uma classe de constantes
private static final String INVALID_FIELDS_PROPERTY = "invalidFields";

// uso:
body.setProperty(INVALID_FIELDS_PROPERTY, invalidFields);
```

---

#### CR-08 · Testes não validam o campo `$.type` (RFC 7807)

**Arquivo:** `GlobalExceptionHandlerIT.java`

**Problema:** RFC 7807 define que quando `type` não é explicitamente setado, o valor padrão é `"about:blank"`. Os testes não verificam esse campo. Se o handler começar a setar um `type` URI por engano (ou não setar quando deveria), nenhum teste falharia.

**Correção:** adicionar em todos os testes de resposta de erro:

```java
.andExpect(jsonPath("$.type").value("about:blank"))
```

---

#### CR-09 · Teste de validação não verifica o conteúdo da mensagem de campo inválido

**Arquivo:** `GlobalExceptionHandlerIT.java` — linha 48

**Problema:** O teste verifica que `$.invalidFields.name` *existe*, mas não verifica o *valor* da mensagem de validação. Se o `@NotBlank` for substituído por uma constraint diferente (ou a mensagem for alterada), o teste permanece verde sem exercitar o contrato corretamente.

**Correção:**

```java
.andExpect(jsonPath("$.invalidFields.name").value("must not be blank"))
```

---

### P3 — Sugestão (melhoria de design, não bloqueia)

---

#### CR-10 · Pacote `domain/exceptions/` não está no diagrama do `backend/CLAUDE.md`

**Arquivo:** `backend/CLAUDE.md` — seção "Estrutura de pacotes"

**Problema:** O diagrama define `domain/entities/` e `domain/repositories/`, mas não menciona `domain/exceptions/`. A ausência pode confundir futuros desenvolvedores sobre onde criar exceções de domínio.

**Sugestão:** atualizar o diagrama para incluir `domain/exceptions/`:

```
com.fcstreamtube/
├── domain/
│   ├── entities/       ← Entidades de domínio puras (sem anotações de infra)
│   ├── exceptions/     ← Exceções lançadas pelas regras de negócio
│   └── repositories/   ← Interfaces (contratos de persistência)
```

---

#### CR-11 · `GlobalExceptionHandler` no pacote `interfaces/controllers/`

**Arquivo:** `interfaces/controllers/GlobalExceptionHandler.java`

**Problema:** `@RestControllerAdvice` é uma preocupação transversal (cross-cutting concern), não um controller. A documentação do Spring recomenda o pacote `advice` separado dos controllers. Isso não afeta o funcionamento, mas facilita navegação e separa responsabilidades.

**Sugestão (não obrigatório):** mover para `interfaces/advice/GlobalExceptionHandler.java` em um refactor futuro.

---

#### CR-12 · Nome `InvalidTokenException` genérico — DDD Ubiquitous Language

**Arquivo:** `domain/exceptions/InvalidTokenException.java`

**Observação:** A DDD guide instrui: *"substituir termos puramente técnicos por termos ricos da Linguagem Ubíqua"*. `InvalidTokenException` é genérico — não expressa *qual* tipo de token nem *por que* é inválido. À medida que o domínio crescer (confirmation token, password reset token), a mesma exceção poderá cobrir cenários distintos. Considerar se faz sentido especializar (ex: `ConfirmationTokenExpiredException`, `ResetTokenNotFoundException`) nos SIs futuros.

**Ação:** não bloqueia o SI-02.4, mas registrar para revisão no SI-02.7 (confirmação de conta) e SI-02.9 (reset de senha).

---

### Resumo executivo

| ID | Prioridade | Arquivo(s) | Ação |
|---|---|---|---|
| CR-01 | P1 | 3 exceções de domínio | Adicionar construtor `(String, Throwable)` |
| CR-02 | P1 | `GlobalExceptionHandler` | Adicionar `public` nos handlers; `protected` no override |
| CR-03 | P1 | `GlobalExceptionHandler` | Adicionar handler fallback `Exception.class` |
| CR-04 | P1 | `GlobalExceptionHandlerIT` | Assertar `$.title` e `$.detail` em todos os testes |
| CR-05 | P2 | `GlobalExceptionHandler` | Forçar `APPLICATION_PROBLEM_JSON` no override de 400 |
| CR-06 | P2 | `GlobalExceptionHandler` | Usar parâmetro `status` em vez de hardcode |
| CR-07 | P2 | `GlobalExceptionHandler` | Extrair `"invalidFields"` para constante |
| CR-08 | P2 | `GlobalExceptionHandlerIT` | Assertar `$.type` = `"about:blank"` |
| CR-09 | P2 | `GlobalExceptionHandlerIT` | Assertar valor da mensagem de campo inválido |
| CR-10 | P3 | `backend/CLAUDE.md` | Atualizar diagrama de pacotes |
| CR-11 | P3 | estrutura de pacotes | Mover handler para `interfaces/advice/` (refactor futuro) |
| CR-12 | P3 | `InvalidTokenException` | Revisar nome nos SIs 02.7 e 02.9 |

**P1 total: 4 itens** — devem ser corrigidos antes do commit deste SI.
**P2 total: 5 itens** — devem ser corrigidos neste SI; não bloqueiam compilação mas quebram contrato ou cobertura.
**P3 total: 3 itens** — sugestões de design para manter o backlog visível.

---

## Round 2

**Data:** 2026-06-28
**Estado de entrada:** todas as correções do Round 1 aplicadas e verificadas.

### CR aplicados com sucesso (Round 1 → Round 2)

| ID | Status |
|---|---|
| CR-01 | ✅ construtores `(String, Throwable)` nas 3 exceções |
| CR-02 | ✅ `protected` no override; `public` nos handlers custom |
| CR-03 | ✅ `handleGenericException(Exception.class)` adicionado |
| CR-04 | ✅ `$.title` e `$.detail` assertados nos 4 testes |
| CR-05 | ✅ `APPLICATION_PROBLEM_JSON` forçado no 400 via `newHeaders` |
| CR-06 | ✅ `HttpStatus.valueOf(status.value())` usado consistentemente |
| CR-07 | ✅ constante `INVALID_FIELDS_PROPERTY` extraída |
| CR-08 | ✅ `$.type` = `"about:blank"` assertado nos 4 testes |
| CR-09 | ✅ `$.invalidFields.name` verifica o valor `"must not be blank"` |
| CR-10 | ✅ `backend/CLAUDE.md` atualizado com `domain/exceptions/` e `interfaces/advice/` |
| CR-11 | ✅ handler movido para `interfaces/advice/GlobalExceptionHandler.java` |
| CR-12 | — adiado para SI-02.7 / SI-02.9 (conforme documentado) |

---

### Novos apontamentos

---

#### CR-13 · `handleGenericException` não loga a exceção capturada

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java` — linha 71–79

**Problema:** O handler genérico captura qualquer `Exception` não mapeada e retorna 500, mas não registra a exceção em nenhum log. Em produção, o stack trace é silenciosamente descartado — o único artefato visível é o corpo `"An unexpected error occurred"`. Sem o log, diagnosticar um bug de runtime torna-se impossível: não há stack trace, não há classe de origem, nenhuma pista do que falhou.

Isso viola o princípio de observabilidade e é especialmente grave porque o handler foi criado *para* capturar o inesperado (CR-03), mas ao fazê-lo destrói a informação diagnóstica.

**Regra violada:** Java best practice para exception handlers — exceções genéricas devem sempre ser logadas em nível `ERROR` com stack trace completo antes de retornar ao cliente.

**Correção:**

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    // ... (resto dos campos existentes)

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        body.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
```

**Nota:** `ResponseEntityExceptionHandler` já usa internamente um logger via `LoggerFactory`. A convenção é consistente com o padrão do próprio Spring.

---

#### CR-14 · Nenhum teste cobre o handler de fallback `handleGenericException`

**Arquivo:** `interfaces/advice/GlobalExceptionHandlerIT.java`

**Problema:** O `GlobalExceptionHandlerIT` tem 4 testes que cobrem os 4 handlers específicos (400, 401, 409, 410), mas não há nenhum teste para o novo handler `handleGenericException(Exception.class)` adicionado no CR-03. O caminho de exceção genérica (500) fica sem cobertura: se o método tiver um bug (ex: a própria lógica de logging lançar `NullPointerException`), nenhum teste falhará.

**Regra violada:** testing-strategy.md — TDD: o CR-03 introduziu código de produção sem ciclo RED → GREEN correspondente.

**Correção:** adicionar ao `StubController` e ao `GlobalExceptionHandlerIT`:

```java
// No StubController:
@PostMapping("/test/generic-error")
void throwGenericException() {
    throw new RuntimeException("Unexpected internal error");
}

// No GlobalExceptionHandlerIT:
@Test
void givenUnhandledException_whenThrown_thenReturns500WithProblemDetail() throws Exception {
    mockMvc.perform(post("/test/generic-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
}
```

---

### Apontamentos menores (P3)

---

#### CR-15 · Teste do 400 não verifica `$.status` — inconsistência com os outros testes

**Arquivo:** `GlobalExceptionHandlerIT.java` — linha 42–52

**Problema:** Os testes de 409, 401 e 410 assertam `.andExpect(jsonPath("$.status").value(NNN))`, mas o teste de 400 (`givenInvalidBody_whenPosted_thenReturns400WithInvalidFields`) não. É uma pequena inconsistência que enfraquece o contrato do campo `status` no body da resposta 400.

**Correção:** adicionar ao teste de 400:

```java
.andExpect(jsonPath("$.status").value(400))
```

---

#### CR-16 · `$.detail` do teste de 400 verifica `isNotEmpty()` em vez do valor exato

**Arquivo:** `GlobalExceptionHandlerIT.java` — linha 50

**Problema:** O handler seta explicitamente `detail = "Request body validation failed"` (string literal em `GlobalExceptionHandler`). O teste verifica apenas `isNotEmpty()`. Se o texto da mensagem for alterado por engano, o teste não detecta a regressão. Os outros testes (401, 409, 410) também usam `isNotEmpty()` — porém, nesses casos o valor vem de `ex.getMessage()` (controlado pelo chamador), o que torna `isNotEmpty()` aceitável. O 400 é diferente: o texto vem do handler, é um contrato fixo.

**Correção:**

```java
.andExpect(jsonPath("$.detail").value("Request body validation failed"))
```

---

### Resumo executivo Round 2

| ID | Prioridade | Arquivo(s) | Ação |
|---|---|---|---|
| CR-13 | P1 | `GlobalExceptionHandler` | Adicionar `log.error(ex.getMessage(), ex)` no handler genérico |
| CR-14 | P1 | `GlobalExceptionHandlerIT` | Adicionar teste para o caminho 500 (RuntimeException genérica) |
| CR-15 | P3 | `GlobalExceptionHandlerIT` | Adicionar `$.status = 400` no teste de validação |
| CR-16 | P3 | `GlobalExceptionHandlerIT` | Assertar valor exato de `$.detail` no teste de 400 |

**P1 total: 2 itens** — devem ser corrigidos antes do commit.
**P3 total: 2 itens** — sugestões de qualidade, não bloqueiam.

---

## Round 3

**Data:** 2026-06-28
**Estado de entrada:** todas as correções do Round 2 aplicadas e verificadas.

### CR aplicados com sucesso (Round 2 → Round 3)

| ID | Status |
|---|---|
| CR-13 | ✅ `log.error("Unhandled exception: {}", ex.getMessage(), ex)` adicionado |
| CR-14 | ✅ teste `givenUnhandledException_whenThrown_thenReturns500WithProblemDetail` adicionado |
| CR-15 | ✅ `$.status = 400` assertado no teste de validação |
| CR-16 | ✅ `$.detail` asserta o valor exato `"Request body validation failed"` |

---

### Novos apontamentos

---

#### CR-17 · `WebRequest request` declarado mas não utilizado em `handleGenericException`

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java` — linha 75

**Problema:** O parâmetro `WebRequest request` é injetado pelo Spring e declarado na assinatura do método, mas nunca referenciado no corpo. Isso tem duas consequências:

1. **Dead parameter** — ferramentas de análise estática (SonarLint, SpotBugs, Checkstyle) irão sinalizar `unused parameter`. Em projetos com linting obrigatório no CI, isso quebra o build.

2. **Oportunidade de log desperdiçada** — o `WebRequest` carrega informações do request atual (URI, método HTTP, headers). No contexto de um 500 genérico — onde um erro inesperado ocorreu — saber *qual endpoint* causou o erro é essencial para diagnóstico. O log atual só registra `ex.getMessage()`, sem nenhum contexto HTTP.

**Correção:** usar o `request` para enriquecer o log:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
    log.error("Unhandled exception on [{}]: {}", request.getDescription(false), ex.getMessage(), ex);
    ProblemDetail body = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    body.setTitle("Internal Server Error");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
}
```

`request.getDescription(false)` retorna `"uri=/caminho/do/endpoint"` sem incluir dados de sessão (o `false` exclui o `clientInfo`). O log ficaria: `Unhandled exception on [uri=/api/auth/login]: Some message`.

---

### Apontamento P3

---

#### CR-18 · `@ExceptionHandler(Exception.class)` interceptará `AccessDeniedException` de method-level security

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java`

**Contexto:** O `SecurityConfig` atual usa autorização por caminho (`anyRequest().authenticated()`), sem `@PreAuthorize`. No momento, esse ponto não é um problema.

**Risco futuro:** quando SIs posteriores adicionarem `@PreAuthorize` (ex: verificar se o usuário autenticado é dono do canal antes de acessar um recurso), o `AccessDeniedException` lançado pelo interceptor de method security propaga dentro do dispatcher servlet — depois dos filtros do Spring Security — e será capturado pelo `@ExceptionHandler(Exception.class)`. O resultado será um 500 `"An unexpected error occurred"` em vez de 403 Forbidden, quebrando o contrato de segurança.

**Ação recomendada:** antes de qualquer SI que introduza `@PreAuthorize`, adicionar um handler específico:

```java
import org.springframework.security.access.AccessDeniedException;

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    body.setTitle("Forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
}
```

**Ação agora:** registrar este item no backlog do projeto; não bloqueia o SI-02.4.

---

### Resumo executivo Round 3

| ID | Prioridade | Arquivo(s) | Ação |
|---|---|---|---|
| CR-17 | P2 | `GlobalExceptionHandler` | Usar `request.getDescription(false)` no `log.error` do handler genérico |
| CR-18 | P3 | `GlobalExceptionHandler` | Adicionar handler `AccessDeniedException` antes de qualquer uso de `@PreAuthorize` |

**P2 total: 1 item** — corrigir neste SI antes do commit; linting e log incompleto.
**P3 total: 1 item** — registrar no backlog; não bloqueia SI-02.4.

---

**Estado geral após Round 3:** o código está correto e bem estruturado. Todos os critérios de Clean Architecture, RFC 7807, TDD e Java best practices estão atendidos. Os dois itens restantes são melhorias de qualidade observacional (CR-17) e prevenção de regressão futura (CR-18).

---

## Round 4

**Data:** 2026-06-28
**Estado de entrada:** todas as correções do Round 3 aplicadas e verificadas.

### CR aplicados com sucesso (Round 3 → Round 4)

| ID | Status |
|---|---|
| CR-17 | ✅ `request.getDescription(false)` usado no `log.error` — URI agora aparece no log |
| CR-18 | ✅ `handleAccessDenied(AccessDeniedException.class)` adicionado com handler 403 + teste `givenAccessDeniedException_whenThrown_thenReturns403WithProblemDetail` |

---

### Novos apontamentos

---

#### CR-19 · Teste de log usa `hasSize(1)` — frágil a adições futuras na classe

**Arquivo:** `GlobalExceptionHandlerIT.java` — linha 114

**Problema:** A asserção `assertThat(logs).hasSize(1)` verifica que exatamente um evento foi capturado pelo `ListAppender`. O appender está corretamente limitado ao logger de `GlobalExceptionHandler` (não ao root logger), então eventos de outros componentes não vazam. No entanto, se um segundo `log.*` statement for adicionado a qualquer outro handler da classe no futuro (ex: um `log.warn` no `handleAccessDenied`), esse teste quebrará silenciosamente — sem relação com a mudança testada.

**Regra violada:** testing-strategy.md — *"Tests must not depend on each other"* (FIRST-I): implicitamente, o teste depende do estado interno da implementação (quantidade exata de logs gerados pela classe).

**Correção:** substituir a asserção de quantidade exata por uma asserção de existência com predicado:

```java
assertThat(logs).anySatisfy(event -> {
    assertThat(event.getLevel()).isEqualTo(Level.ERROR);
    assertThat(event.getFormattedMessage())
            .contains("uri=/test/generic-error")
            .contains("Unexpected internal error");
});
```

Isso verifica que O evento de erro correto está presente, sem quebrar quando outros logs forem adicionados à classe no futuro.

---

#### CR-20 · Teste de 500 verifica dois comportamentos distintos num único método

**Arquivo:** `GlobalExceptionHandlerIT.java` — linhas 97–122

**Problema:** O método `givenUnhandledException_whenThrown_thenReturns500WithProblemDetail` verifica dois comportamentos ortogonais do handler:

1. **Comportamento HTTP:** status 500, body com ProblemDetail correto.
2. **Comportamento de log:** evento ERROR emitido com a mensagem e URI corretas.

**Regra violada:** testing-strategy.md — *"One test = one behavior. Never test multiple things in a single test case."*

Quando esse teste falhar, não ficará imediatamente claro se o problema é na resposta HTTP ou no log. A causa do RED não será óbvia.

**Correção:** separar em dois métodos:

```java
@Test
void givenUnhandledException_whenThrown_thenReturns500WithProblemDetail() throws Exception {
    mockMvc.perform(post("/test/generic-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
}

@Test
void givenUnhandledException_whenThrown_thenLogsErrorWithRequestUri() throws Exception {
    Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);

    try {
        mockMvc.perform(post("/test/generic-error"));

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains("uri=/test/generic-error")
                    .contains("Unexpected internal error");
        });
    } finally {
        logger.detachAppender(logAppender);
    }
}
```

---

### Apontamentos menores (P3)

---

#### CR-21 · Imports desordenados em `GlobalExceptionHandler.java`

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java` — linhas 6–8

**Problema:** O import `org.springframework.security.access.AccessDeniedException` foi inserido entre `org.slf4j.Logger` e `org.slf4j.LoggerFactory`, quebrando a ordenação alfabética por grupo:

```java
// estado atual (incorreto)
import org.slf4j.Logger;
import org.springframework.security.access.AccessDeniedException;  // ← fora de ordem
import org.slf4j.LoggerFactory;
```

**Correção:** executar "Organize Imports" na IDE (⌘⇧O no IntelliJ IDEA):

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
// ... demais imports org.springframework.*
```

---

#### CR-22 · `handleAccessDenied` não registra o evento 403

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java` — linhas 75–82

**Problema:** O handler `handleAccessDenied` retorna 403 silenciosamente. Quando `@PreAuthorize` for introduzido em SIs futuros, acessos negados (ex: usuário tentando modificar canal de outro usuário) não produzirão nenhum rastro nos logs. Para auditoria de segurança, eventos 403 são relevantes — diferentemente de 400/409 (erros de cliente esperados), um 403 dentro do MVC layer indica que autenticação passou mas autorização falhou, o que pode ser sinal de tentativa de abuso.

**Sugestão:** logar em `WARN` (não `ERROR` — 403 é esperado, não bug de sistema):

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
    log.warn("Access denied on [{}]", request.getDescription(false));
    ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    body.setTitle("Forbidden");
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(body);
}
```

Não logar `ex.getMessage()` na resposta nem no log em nível de detail — pode vazar informação sobre o modelo de autorização.

---

### Resumo executivo Round 4

| ID | Prioridade | Arquivo(s) | Ação |
|---|---|---|---|
| CR-19 | P2 | `GlobalExceptionHandlerIT` | Substituir `hasSize(1)` por `anySatisfy()` na asserção de log |
| CR-20 | P2 | `GlobalExceptionHandlerIT` | Separar teste de 500 em dois métodos: HTTP response + log |
| CR-21 | P3 | `GlobalExceptionHandler` | Reorganizar imports (Organize Imports na IDE) |
| CR-22 | P3 | `GlobalExceptionHandler` | Adicionar `log.warn` em `handleAccessDenied` para auditoria |

**P2 total: 2 itens** — devem ser corrigidos neste SI; violam princípios do testing-strategy.md.
**P3 total: 2 itens** — melhorias incrementais; não bloqueiam o SI.

---

**Estado geral após Round 4:** implementação de produção (`GlobalExceptionHandler`) está completa e sem pendências. Os 4 itens restantes são todos no teste de integração (CR-19, CR-20) ou cosméticos (CR-21, CR-22).

---

## Round 5

**Data:** 2026-06-28
**Estado de entrada:** todas as correções do Round 4 aplicadas e verificadas.

### CR aplicados com sucesso (Round 4 → Round 5)

| ID | Status |
|---|---|
| CR-19 | ✅ `hasSize(1)` substituído por `anySatisfy()` no teste de log 500 |
| CR-20 | ✅ teste de 500 separado em dois métodos: `thenReturns500WithProblemDetail` + `thenLogsErrorWithRequestUri` |
| CR-21 | ✅ imports parcialmente reorganizados — `LoggerFactory` agora imediatamente após `Logger` |
| CR-22 | ✅ `log.warn("Access denied on [{}]", ...)` adicionado em `handleAccessDenied` + teste `thenLogsWarnWithRequestUri` |

---

### Novos apontamentos (todos P3)

Não há itens P1 ou P2 neste round. A implementação de produção está completa e sem pontos críticos. Os três apontamentos abaixo são cosméticos ou de organização de teste.

---

#### CR-23 · `import java.util.List` orphaned em `GlobalExceptionHandlerIT`

**Arquivo:** `GlobalExceptionHandlerIT.java` — linha 30

**Problema:** A transição de `hasSize(1)` para `anySatisfy()` (CR-19/CR-20) eliminou a variável explícita `List<ILoggingEvent> logs = logAppender.list`. Agora `logAppender.list` é passado diretamente para `assertThat()`, cujo tipo é inferido — `java.util.List` deixou de ser referenciado em qualquer ponto do arquivo. O import órfão gera aviso de compilador (`-Xlint:unused`) e é sinalizado pelo SonarLint/IntelliJ como "unused import".

**Correção:** remover a linha 30:

```java
// remover:
import java.util.List;
```

---

#### CR-24 · Boilerplate de captura de log duplicado em dois métodos de teste

**Arquivo:** `GlobalExceptionHandlerIT.java` — linhas 110–126 e 142–157

**Problema:** Os métodos `givenUnhandledException_whenThrown_thenLogsErrorWithRequestUri` e `givenAccessDeniedException_whenThrown_thenLogsWarnWithRequestUri` têm setup/teardown idênticos de 5 linhas:

```java
Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
logAppender.start();
logger.addAppender(logAppender);
// ...
} finally {
    logger.detachAppender(logAppender);
}
```

Duplicação viola DRY. Se o mecanismo de captura precisar mudar (ex: migração para outro backend SLF4J), a mudança deve ser feita em dois lugares.

**Correção recomendada:** extrair para `@BeforeEach` / `@AfterEach` (a note: a testing-strategy.md desencoraja setup compartilhado "que não é estritamente necessário" — aqui é necessário para ambos os testes, então é justificado):

```java
private ch.qos.logback.classic.Logger handlerLogger;
private ListAppender<ILoggingEvent> logAppender;

@BeforeEach
void setUpLogCapture() {
    handlerLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);
}

@AfterEach
void tearDownLogCapture() {
    handlerLogger.detachAppender(logAppender);
}
```

Os testes de log ficam limpos:
```java
@Test
void givenUnhandledException_whenThrown_thenLogsErrorWithRequestUri() throws Exception {
    mockMvc.perform(post("/test/generic-error"));
    assertThat(logAppender.list).anySatisfy(event -> {
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage())
                .contains("uri=/test/generic-error")
                .contains("Unexpected internal error");
    });
}
```

Os outros testes (400, 401, 409, 410, 500-HTTP, 403-HTTP) não assertam `logAppender.list`, portanto o setup compartilhado não os afeta.

---

#### CR-25 · `org.springframework.security.*` ainda precede `org.springframework.http.*`

**Arquivo:** `interfaces/advice/GlobalExceptionHandler.java` — linhas 8–9

**Problema:** O CR-21 corrigiu a separação entre `org.slf4j.Logger` e `org.slf4j.LoggerFactory`. Porém, dentro do grupo `org.springframework.*`, `security` (`s`) continua antes de `http` (`h`) — inversão alfabética:

```java
import org.springframework.security.access.AccessDeniedException;  // linha 8  ← s
import org.springframework.http.HttpHeaders;                        // linha 9  ← h
```

`h` < `s`, portanto `http.*` deveria vir antes de `security.*`.

**Correção:** re-executar "Organize Imports" na IDE com a configuração padrão (IntelliJ: ⌘⇧O):

```java
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
// ...
```

---

### Resumo executivo Round 5

| ID | Prioridade | Arquivo(s) | Ação |
|---|---|---|---|
| CR-23 | P3 | `GlobalExceptionHandlerIT` | Remover `import java.util.List` (orphaned) |
| CR-24 | P3 | `GlobalExceptionHandlerIT` | Extrair setup/teardown de log para `@BeforeEach`/`@AfterEach` |
| CR-25 | P3 | `GlobalExceptionHandler` | Corrigir ordenação `http.*` antes de `security.*` (Organize Imports) |

**P1 total: 0** — nenhum bloqueio.
**P2 total: 0** — nenhuma violação de contrato ou princípio.
**P3 total: 3** — apenas refinamentos cosméticos e de organização de teste.

---

**Estado geral após Round 5:** o código está pronto para commit. A implementação de produção (`GlobalExceptionHandler` + exceções de domínio) está completa, correta e bem estruturada. O teste de integração cobre todos os 7 handlers (400, 401, 403, 409, 410, 500-HTTP, 500-log). Os 3 itens P3 restantes são puramente cosméticos — não afetam comportamento, segurança ou cobertura.

---

## Round 6 — Encerramento

**Data:** 2026-06-28
**Estado de entrada:** todas as correções do Round 5 aplicadas e verificadas.

### CR aplicados com sucesso (Round 5 → Round 6)

| ID | Status |
|---|---|
| CR-23 | ✅ `import java.util.List` removido do arquivo de teste |
| CR-24 | ✅ setup de log extraído para `@BeforeEach`/`@AfterEach` — testes de log ficaram limpos e sem duplicação |
| CR-25 | ✅ imports corretamente ordenados: `http.*` (h) → `security.*` (s) → `validation.*` (v) → `web.*` (w) |

### Novos apontamentos

Nenhum. Revisão concluída sem pendências.

---

### Resultado final da revisão

| Critério | Status |
|---|---|
| Exceções de domínio — Clean Architecture | ✅ sem anotações de infra, construtores canônicos, pacote correto |
| `GlobalExceptionHandler` — contrato RFC 7807 | ✅ todos os campos obrigatórios setados, `APPLICATION_PROBLEM_JSON` em todos os paths |
| Visibilidade e polimorfismo | ✅ `protected` no override, `public` nos handlers |
| Observabilidade | ✅ `log.error` com URI no 500, `log.warn` com URI no 403 |
| Segurança | ✅ `AccessDeniedException` mapeado para 403; mensagem de 500 não vaza detalhes internos |
| Cobertura de testes — FIRST | ✅ 8 testes de integração cobrindo todos os handlers e comportamentos de log |
| TDD | ✅ cada handler tem RED → GREEN correspondente |
| Estrutura de pacotes — Clean Architecture | ✅ `domain/exceptions/`, `interfaces/advice/`, `backend/CLAUDE.md` atualizado |
| Organização de código | ✅ constante `INVALID_FIELDS_PROPERTY`, imports ordenados, sem dead code |
| Itens adiados | CR-12 (`InvalidTokenException` — DDD Ubiquitous Language) registrado para SI-02.7/SI-02.9 |

**O SI-02.4 está aprovado para commit.**
