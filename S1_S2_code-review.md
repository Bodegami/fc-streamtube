# Code Review — SI-02.1 e SI-02.2

> Escopo: arquivos não commitados na branch `feature/user-auth-01`. Avaliado contra a spec (`phase-02-user-auth.md`), decisões técnicas (`technical-decisions-user-auth.md`) e as convenções do `backend/CLAUDE.md`.

---

## Round 1

### Resumo Executivo

**Status geral: BOM** — a estrutura está correta, a separação de camadas é respeitada, os testes de integração funcionam. Há **1 issue crítico**, **3 importantes** e **5 menores** que precisam ser corrigidos antes de avançar para SI-02.3.

### Issues Críticos

#### [CRÍTICO] `UserTokenRepository` usa `String` no lugar de `UserTokenType`

**Arquivos:** `domain/repositories/UserTokenRepository.java:12`, `infrastructure/persistence/repositories/UserTokenRepositoryAdapter.java:35`, `SpringUserTokenJpaRepository.java:11`

```java
// Errado — vaza representação de infra para a interface de domínio
void deleteByUserIdAndType(UUID userId, String type);

// Correto — type-safe, em linguagem do domínio
void deleteByUserIdAndType(UUID userId, UserTokenType type);
```

O domínio conhece `UserTokenType` (está no mesmo pacote). Ao usar `String`, qualquer chamador pode passar `"INVALID_TYPE"` sem erro de compilação. O adapter faz a tradução para `.name()` antes de chegar no Spring Data — isso já é responsabilidade da camada de infraestrutura, não do contrato de domínio.

### Issues Importantes

#### [IMPORTANTE] `createdAt` inicializado em Java em vez de delegado ao banco

**Arquivos:** `UserJpaEntity.java:28`, `UserTokenJpaEntity.java:31`

```java
private Instant createdAt = Instant.now();  // Java inicializa antes do INSERT
```

O Hibernate inclui esse valor no INSERT, tornando o `DEFAULT NOW()` da migration inoperante. Se um teste criar duas entidades no mesmo milissegundo, os timestamps são idênticos — o relógio da JVM e o do PostgreSQL também podem divergir. O correto para colunas gerenciadas pelo banco:

```java
@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
private Instant createdAt;
```

Assim o Hibernate omite a coluna no INSERT e o banco aplica o default.

#### [IMPORTANTE] `UserTokenMapper.toDomain` sem proteção contra enum inválido

**Arquivo:** `UserTokenMapper.java:28`

```java
UserTokenType.valueOf(entity.getType())  // lança IllegalArgumentException se o valor do DB for inválido
```

Se alguém inserir diretamente no banco um `type` com typo (ex: `"EMAIL_CONFIRM"`), a aplicação explode com uma exception não-tratada em vez de um erro de domínio compreensível. Adicionar um bloco try/catch ou um método de lookup estático protege contra isso.

#### [IMPORTANTE] `User` record omite `createdAt`

**Arquivo:** `domain/entities/User.java`

O data model da spec lista `created_at` como campo da entidade User. A entidade de domínio não precisa gerá-lo (isso é do banco), mas precisa carregá-lo para que use cases futuros possam acessar quando foi criado o usuário (ex: auditoria, tokens com `expires_at` relativo ao cadastro). O mapper atual descarta esse dado na volta do banco.

### Issues Menores

#### [MENOR] Testes de integração faltando cobertura de métodos do contrato

**UserRepositoryIT.java** — não testa `existsByEmail`. O acceptance criteria do SI-02.2 não exige explicitamente, mas o método existe no contrato de domínio e será usado pelo `RegisterUserUseCase`. Um teste de 5 linhas previne regressão silenciosa.

**UserTokenRepositoryIT.java** — não testa `deleteByUserIdAndType`. Esse método é o coração do "reenviar e-mail de confirmação invalida tokens anteriores" (TD-03). É a única operação de escrita não testada nos ITs.

#### [MENOR] `token.token()` — accessor com nome redundante

**Arquivo:** `domain/entities/UserToken.java:9`

O campo `token` no record `UserToken` cria o acessor `userToken.token()`, resultando em construções como `found.get().token()` nos testes. Considere renomear para `tokenValue` para deixar o código mais expressivo. Isso é opcional mas melhora a leitura.

#### [MENOR] FK sem `ON DELETE CASCADE` em V3

**Arquivo:** `V3__create_user_tokens_table.sql:12`

A FK `fk_user_tokens_user` não define comportamento ao deletar um `User`. Hoje não há use case de deleção de usuário no escopo, então está OK. Vale registrar que sem isso, qualquer futura tentativa de deletar um user sem primeiro deletar seus tokens vai quebrar com FK violation.

#### [MENOR] TDD não seguido em SI-02.1 (já reconhecido)

O `progress.md` já documenta isso. Para SI-02.3 em diante, o ciclo RED → GREEN → REFACTOR deve ser seguido desde o primeiro commit do teste.

### O que estava BEM no Round 1

| Aspecto | Avaliação |
|---------|-----------|
| `record` para entidades de domínio | Correto — imutabilidade sem boilerplate |
| `@Entity` apenas em `infrastructure/` | Correto — regra crítica do CLAUDE.md respeitada |
| Adapter pattern (`UserRepositoryAdapter`) | Correto — inversão de dependência real |
| `SpringUserJpaRepository` package-private | Correto — não vaza Spring Data para fora do pacote |
| Naming de testes (`given_when_then`) | Correto — convenção do backend/CLAUDE.md |
| `@Transactional` no `deleteByUserIdAndType` | Correto — delete derivado do Spring Data precisa |
| Migrations SQL puro sem ORM | Correto — forward-only, Flyway vanilla |
| Testcontainers com PostgreSQL real | Correto — não H2, não mock |
| UUID v4 via `GenerationType.UUID` | Correto — Hibernate 6 nativo, sem dependência extra (TD-06) |

---

## Round 2

> Segunda análise após aplicação das correções do Round 1. Mesmos eixos: Java 25, SOLID, Clean Architecture, TDD, boas práticas de testes.

### Correções do Round 1 — Status

| Issue | Status |
|---|---|
| `deleteByUserIdAndType` com `String` em vez de `UserTokenType` | **Resolvido** |
| `createdAt` inicializado em Java (`Instant.now()`) | **Resolvido** (`insertable = false`) |
| `User` record sem `createdAt` | **Resolvido** |
| `UserTokenMapper.valueOf` sem proteção | **Resolvido** (try/catch + `IllegalStateException`) |
| Testes de `existsByEmail` ausentes | **Resolvido** (2 testes adicionados) |
| Teste de `deleteByUserIdAndType` ausente | **Resolvido** (1 teste adicionado) |

### Resumo Executivo

**Status geral: MUITO BOM** — os issues críticos e importantes foram eliminados. Restam **1 importante** e **3 menores**, todos focados em qualidade de testes.

### Issue Importante

#### [IMPORTANTE] Testes com e-mails hardcoded não são isolados entre execuções

**Arquivo:** `UserRepositoryIT.java:30,39,57`

```java
new User(null, "Alice", "alice@test.com", "hashed", false, null)
new User(null, "Bob",   "bob@test.com",   "hashed", false, null)
new User(null, "Dave",  "dave@test.com",  "hashed", false, null)
```

O container Testcontainers é `static` — o mesmo banco persiste entre todos os testes da classe na mesma execução JVM. Não há rollback (`@Transactional` não está no teste) nem limpeza entre testes. Se os testes rodarem mais de uma vez na mesma sessão (rerun via IDE, test retry em CI) ou se dois testes com o mesmo e-mail forem adicionados no futuro, a constraint `uq_users_email` vai causar falha não-relacionada à lógica testada.

O próprio `AuthSchemaMigrationTest` já usa o padrão correto:

```java
// AuthSchemaMigrationTest — padrão a seguir
String email = "dup-" + UUID.randomUUID() + "@test.com";

// UserRepositoryIT — correto seria:
new User(null, "Alice", "alice-" + UUID.randomUUID() + "@test.com", "hashed", false, null)
```

O mesmo se aplica aos emails "carol@test.com" e "eve@test.com" em `UserTokenRepositoryIT`.

### Issues Menores

#### [MENOR] `setEmailConfirmed` e `setUsedAt` são dead code

**Arquivos:** `UserJpaEntity.java:46`, `UserTokenJpaEntity.java:50`

```java
public void setEmailConfirmed(boolean emailConfirmed) { this.emailConfirmed = emailConfirmed; }
public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
```

O adapter nunca chama esses setters — ele sempre cria uma nova `UserJpaEntity` via `toJpa(domainObject)`. Em Clean Architecture, atualizar `email_confirmed = true` ou `used_at = now()` é feito criando um novo record de domínio com o campo alterado e chamando `save()`. Esses setters sobreviveram de uma intenção de mutation direta que não existe no modelo atual. Não causam bug, mas criam superfície de API enganosa — um futuro desenvolvedor pode achar que são a forma correta de atualizar e usá-los diretamente, bypassando o mapeamento.

#### [MENOR] Nenhum teste verifica que `createdAt` é populado após `save`

**Arquivo:** `UserRepositoryIT.java`

`createdAt` agora está no record `User` e é mapeado pelo `UserMapper`. A coluna é gerenciada pelo banco (`insertable = false`, `DEFAULT NOW()`). Porém nenhum teste verifica que o campo é não-nulo após `save()`. Uma linha adicional protege contra regressão caso alguém modifique `insertable` ou a migration no futuro:

```java
assertThat(saved.createdAt()).isNotNull();
```

#### [MENOR] `userToken.token()` — accessor com nome redundante

**Arquivo:** `domain/entities/UserToken.java:9`, `UserTokenRepositoryIT.java:48`

```java
assertThat(found.get().token()).isEqualTo(opaqueToken);  // .token() em UserToken é ambíguo
```

O campo `token` no record `UserToken` gera o acessor `.token()`, resultando em `userToken.token()`. É cosmético mas cria leitura ambígua — o leitor não sabe imediatamente se `token` é o objeto ou o valor string. `tokenValue` resolveria sem custo.

### O que está BEM — validado no Round 2

| Aspecto | Avaliação |
|---------|-----------|
| `deleteByUserIdAndType(UUID, UserTokenType)` | Correto — type-safe no contrato de domínio |
| `createdAt` com `insertable = false` | Correto — DB gerencia o timestamp |
| `User.createdAt` no record + mapper | Correto — dado disponível na camada de aplicação |
| `UserTokenMapper` com try/catch + `IllegalStateException` | Correto — falha explícita com contexto |
| `UserTokenRepositoryAdapter.deleteByUserIdAndType` faz `.name()` | Correto — tradução na borda de infraestrutura |
| Testes `existsByEmail` (2 casos: true/false) | Correto — cobertura de borda |
| Teste `deleteByUserIdAndType` com assert post-delete | Correto — verifica o efeito, não só a execução |
| Toda arquitetura de camadas (domain / infra / adapter) | Correto — regra crítica do CLAUDE.md respeitada |
| Testcontainers com PostgreSQL real | Correto — sem H2, sem mock |

### Prioridade Antes de SI-02.3

1. **Corrigir e-mails hardcoded nos ITs** — usar `UUID.randomUUID()` suffix (importante, ~5 linhas)
2. **Remover `setEmailConfirmed` e `setUsedAt`** — dead code (minor, delete 2 linhas)
3. **Adicionar assert `createdAt` não-nulo** em `UserRepositoryIT` (minor, 1 linha)
4. `token` → `tokenValue` no record — opcional, pode aguardar refactor futuro

---

## Round 3

> Terceira análise após aplicação das correções do Round 2. Mesmos eixos: Java 25, SOLID, Clean Architecture, TDD, boas práticas de testes. Foco especial nos adapters.

### Correções do Round 2 — Status

| Issue | Status |
|---|---|
| E-mails hardcoded nos ITs | **Resolvido** — todos usam `UUID.randomUUID()` suffix |
| Dead code `setEmailConfirmed` / `setUsedAt` | **Resolvido** — removidos das JPA entities |
| Teste `createdAt` não-nulo após `save` | **Resolvido** — novo teste adicionado |

### Resumo Executivo

**Status geral: BOM com um design problem relevante.** As correções de Round 2 foram aplicadas corretamente, mas ao resolver o problema do `createdAt` DB-managed surgiu um anti-pattern novo: `UserRepositoryAdapter` está misturando `EntityManager` com `SpringUserJpaRepository` — dois padrões que não devem coexistir no mesmo adapter. Há **1 issue importante** de design e **1 menor** residual.

### Issue Importante

#### [IMPORTANTE] `UserRepositoryAdapter` mistura `EntityManager` + `SpringUserJpaRepository`

**Arquivo:** `UserRepositoryAdapter.java:7,18-31`

```java
public class UserRepositoryAdapter implements UserRepository {

    private final SpringUserJpaRepository jpaRepository;  // Spring Data JPA
    private final EntityManager entityManager;             // JPA direto — dois padrões juntos

    @Override
    @Transactional
    public User save(User user) {
        UserJpaEntity saved = jpaRepository.saveAndFlush(mapper.toJpa(user));
        entityManager.refresh(saved);   // força SELECT para ler o createdAt do banco
        return mapper.toDomain(saved);
    }
}
```

**O que aconteceu:** `insertable = false` (Round 1) fez o `createdAt` ser gerenciado pelo banco. Depois do `saveAndFlush()`, o objeto em memória ainda tem `createdAt = null`. Para recuperar o valor do banco, o `EntityManager.refresh()` foi adicionado, que força um SELECT extra. Para isso funcionar dentro da mesma transação, o método recebeu `@Transactional`.

**Por que é um problema:**

Spring Data JPA (`JpaRepository`) existe exatamente para abstrair o `EntityManager`. Quando os dois coexistem no mesmo adapter:

1. **Dois níveis de abstração sobrepostos** — o contrato do `SpringUserJpaRepository` já gerencia o `EntityManager` internamente. Injetar e usar o `EntityManager` diretamente ao lado dele quebra o encapsulamento e torna o comportamento imprevisível (o `EntityManager` do Spring é um proxy, seu estado pode variar por escopo).
2. **2 round-trips desnecessários ao banco** — `saveAndFlush()` faz INSERT + flush; `entityManager.refresh()` faz um SELECT. O problema tem solução com 1 único round-trip.
3. **`@Transactional` no adapter** — a transação pertence ao use case (camada de aplicação), não ao adapter. Quando o adapter declara `@Transactional` para satisfazer necessidades internas de infraestrutura, ele passa a controlar o escopo da transação de fora para dentro — inversão de responsabilidades.
4. **Inconsistência entre adapters** — `UserTokenRepositoryAdapter.save()` não tem `@Transactional` nem `EntityManager`. Os dois adapters não seguem o mesmo padrão.

**A causa raiz:** `@Column(insertable = false)` delega o `createdAt` ao banco, mas cria a necessidade de um SELECT de retorno. A solução correta é usar `@CreationTimestamp` do Hibernate, que popula o campo **em memória antes do INSERT**, eliminando a necessidade de qualquer leitura de retorno.

**Correção:**

Em `UserJpaEntity.java` — trocar `insertable = false` por `@CreationTimestamp`:

```java
// Antes
@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
private Instant createdAt;

// Depois
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;
```

Em `UserRepositoryAdapter.java` — remover `EntityManager` e simplificar `save()`:

```java
// Remover: campo EntityManager, parâmetro no construtor, import jakarta.persistence.EntityManager
// Remover: @Transactional no save()
// Reverter: saveAndFlush() → save()

@Override
public User save(User user) {
    return mapper.toDomain(jpaRepository.save(mapper.toJpa(user)));
}
```

Com `@CreationTimestamp`, o Hibernate popula `createdAt` no objeto gerenciado antes de emitir o INSERT — `save()` já devolve a entidade com o campo preenchido, sem SELECT adicional. O `DEFAULT NOW()` da migration continua como fallback para inserts via JdbcTemplate (testes de migration). O teste `givenValidUser_whenPersisted_thenCreatedAtIsPopulatedByDatabase` continua passando.

### Issue Menor

#### [MENOR] `userToken.token()` — accessor com nome redundante (residual)

**Arquivo:** `domain/entities/UserToken.java:9`, `UserTokenRepositoryIT.java:48`

Persiste do Round 2. O campo `token` no record `UserToken` gera o acessor `.token()`, resultando em `found.get().token()` — ambíguo para o leitor. Renomear para `tokenValue` resolve sem impacto funcional. Opcional, pode aguardar refactor futuro.

### O que está BEM — validado no Round 3

| Aspecto | Avaliação |
|---------|-----------|
| Dead code removido das JPA entities | Correto — sem setters que não são chamados |
| UUIDs randômicos em todos os ITs | Correto — testes isolados entre execuções |
| Teste `createdAt` não-nulo | Correto — comportamento DB-delegado coberto |
| `UserTokenRepositoryAdapter` — padrão limpo, só `jpaRepository` | Correto — referência de como `UserRepositoryAdapter` deve ficar |
| `@Transactional` em `deleteByUserIdAndType` | Correto — delete derivado Spring Data precisa de transação |
| Separação domain / infrastructure sem vazamento | Correto — regra crítica respeitada |
| `UserTokenMapper` com `IllegalStateException` | Correto — falha explícita com contexto |

### Prioridade de Correção

1. **Substituir `insertable = false` por `@CreationTimestamp`** em `UserJpaEntity` e simplificar `UserRepositoryAdapter.save()` — remove `EntityManager`, remove `@Transactional` no adapter, reverte para `save()` (importante, ~6 linhas alteradas)
2. `token` → `tokenValue` no record `UserToken` — opcional, cosmético

---

## Round 4

> Quarta análise após aplicação das correções do Round 3. Mesmos eixos: Java 25, SOLID, Clean Architecture, TDD, boas práticas de testes.

### Correções do Round 3 — Status

| Issue | Status |
|---|---|
| `EntityManager` + `JpaRepository` misturados em `UserRepositoryAdapter` | **Resolvido** — adapter limpo, só `jpaRepository.save()` |
| `insertable = false` em `UserJpaEntity.createdAt` | **Resolvido** — substituído por `@CreationTimestamp` |
| `@Transactional` indevido no `save()` do adapter | **Resolvido** — removido junto com `EntityManager` |
| `token` → `tokenValue` no record `UserToken` | **Resolvido** — renomeado, mapper e testes atualizados |

### Resumo Executivo

**Status geral: EXCELENTE.** Toda a arquitetura está correta, os adapters estão limpos e consistentes, os testes cobrem os acceptance criteria da spec. Restam **2 issues menores**, ambos de consistência e cobertura de testes, sem impacto funcional.

### Issues Menores

#### [MENOR] `UserTokenJpaEntity.createdAt` usa `insertable = false` — inconsistente com `UserJpaEntity`

**Arquivo:** `UserTokenJpaEntity.java:30`

```java
// UserJpaEntity — @CreationTimestamp (Hibernate gerencia, valor disponível em memória após save)
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

// UserTokenJpaEntity — insertable = false (banco gerencia, Hibernate não conhece o valor)
@Column(name = "created_at", nullable = false, updatable = false, insertable = false)
private Instant createdAt;
```

A correção do Round 3 foi aplicada apenas em `UserJpaEntity`. `UserTokenJpaEntity` ficou com o padrão anterior. Dois padrões diferentes para o mesmo campo no mesmo layer de infraestrutura. Hoje não causa bug — `UserToken` domain record não expõe `createdAt`, então o valor `null` em memória nunca chega ao domínio. Mas um desenvolvedor futuro que ler os dois arquivos vai encontrar dois padrões e não saber qual é a convenção do projeto.

**Correção:**

```java
// UserTokenJpaEntity.java
@CreationTimestamp
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;
```

#### [MENOR] `UserTokenRepositoryIT` não testa UUID auto-gerado no token

**Arquivo:** `UserTokenRepositoryIT.java`

`UserRepositoryIT` tem `givenValidUser_whenPersisted_thenUuidIsGeneratedAutomatically` que verifica que `saved.id()` é não-nulo após persistir. `UserTokenRepositoryIT` não tem o equivalente para `UserToken`. O `UserTokenJpaEntity` usa o mesmo `@GeneratedValue(strategy = GenerationType.UUID)` — a paridade de cobertura evita regressão silenciosa se alguém alterar a estratégia de geração.

```java
@Test
void givenValidToken_whenPersisted_thenUuidIsGeneratedAutomatically() {
    User user = userRepository.save(new User(null, "Frank", "frank-" + UUID.randomUUID() + "@test.com", "hashed", false, null));
    UserToken token = new UserToken(null, user.id(), UUID.randomUUID().toString(),
            UserTokenType.EMAIL_CONFIRMATION, Instant.now().plus(1, ChronoUnit.DAYS), null);

    UserToken saved = userTokenRepository.save(token);

    assertThat(saved.id()).isNotNull();
}
```

### Validação Completa do Estado Atual

#### Domínio

| Arquivo | Avaliação |
|---|---|
| `User` record — 6 campos, sem anotações de infra | Correto |
| `UserToken` record — `tokenValue` renomeado, sem anotações de infra | Correto |
| `UserTokenType` enum | Correto |
| `UserRepository` — contrato mínimo e necessário | Correto |
| `UserTokenRepository` — `deleteByUserIdAndType(UUID, UserTokenType)` type-safe | Correto |

#### Infraestrutura — JPA Entities

| Arquivo | Avaliação |
|---|---|
| `UserJpaEntity` — `@CreationTimestamp`, sem setters desnecessários | Correto |
| `UserTokenJpaEntity` — `insertable = false` em vez de `@CreationTimestamp` | **Inconsistente** (issue menor acima) |
| `@GeneratedValue(UUID)` em ambas | Correto — Hibernate 6 nativo |
| `protected` no-arg constructor em ambas | Correto — JPA exige, não expõe publicamente |

#### Infraestrutura — Mappers

| Arquivo | Avaliação |
|---|---|
| `UserMapper.toJpa` / `toDomain` | Correto |
| `UserTokenMapper.toJpa` — usa `token.tokenValue()` | Correto |
| `UserTokenMapper.toDomain` — try/catch + `IllegalStateException` | Correto |

#### Infraestrutura — Adapters

| Arquivo | Avaliação |
|---|---|
| `UserRepositoryAdapter` — só `jpaRepository`, sem `EntityManager` | Correto |
| `UserTokenRepositoryAdapter` — `@Transactional` apenas no delete | Correto |
| `SpringUserJpaRepository` — package-private | Correto |
| `SpringUserTokenJpaRepository` — package-private | Correto |

#### Migrations

| Arquivo | Avaliação |
|---|---|
| `V2__create_users_table.sql` — constraints, defaults, PK, UNIQUE | Correto |
| `V3__create_user_tokens_table.sql` — FK, índice composto `(user_id, type)` | Correto |

#### Testes

| Arquivo | Cobertura | Avaliação |
|---|---|---|
| `AuthSchemaMigrationTest` — 4 testes | tabelas, unique constraint, default `email_confirmed` | Correto |
| `UserRepositoryIT` — 6 testes | UUID, `createdAt`, findByEmail, existsByEmail (happy + not-found) | Correto |
| `UserTokenRepositoryIT` — 3 testes | findByToken, not-found, delete | Correto — falta UUID test |
| Todos os e-mails com `UUID.randomUUID()` suffix | Isolamento garantido | Correto |
| Naming `given_when_then` | Convenção CLAUDE.md respeitada | Correto |

### Prioridade de Correção

1. **Aplicar `@CreationTimestamp` em `UserTokenJpaEntity.createdAt`** — consistência de padrão (minor, 2 linhas)
2. **Adicionar teste de UUID auto-gerado em `UserTokenRepositoryIT`** — paridade de cobertura (minor, ~8 linhas)

Após essas duas correções, o SI-02.1 e SI-02.2 estarão **prontos para merge** — sem débito técnico pendente antes de SI-02.3.
