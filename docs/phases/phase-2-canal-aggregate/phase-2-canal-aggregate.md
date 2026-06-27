---
kind: phase
name: phase-2-canal-aggregate
test_specs_aware: true
sources_mtime:
  docs/phases/phase-2-canal-aggregate/context.md: "2026-06-26T18:34:02"
  docs/decisions/technical-decisions-canal-aggregate.md: "2026-06-26T18:19:37"
  docs/decisions/technical-decisions-user-auth.md: "2026-06-26T18:31:14"
  docs/decisions/technical-decisions-phase-01-base.md: "2026-06-26T11:25:22"
---

# Phase 02 — Criação automática do canal do usuário (slice canal-aggregate)

## Objective

Criar automaticamente o canal de cada usuário no momento do cadastro, derivando o slug do prefixo do e-mail com fallback determinístico em caso de colisão — implementação síncrona dentro do `RegisterUserUseCase` (slice irmão `user-auth`), atômica via `@Transactional`, mantendo o domínio livre de anotações de infraestrutura (Clean Architecture).

---

## Step Implementations

### SI-02.1 — Migration Flyway: tabela `canais`

**Description:** Criar a migration que materializa a tabela `canais` no schema, com as constraints que garantem a invariante de um canal por usuário e unicidade de slug.

**Technical actions:**

1. Criar `backend/src/main/resources/db/migration/V4__create_canais_table.sql` — tabela `canais` com `id uuid PK`, `user_id uuid FK → users(id) unique not null`, `slug varchar(255) unique not null`, `name varchar(255) not null`, `created_at timestamptz default now()` (per `### Data Model → Canal`, `canal-aggregate/TD-02`; convenção Flyway SQL puro forward-only per `## Inherited Conventions` → `phase-01-base/TD-02`).

**Tests:** _(empty — Infra; cobertura de constraints validada pelos testes de integração de `CanalRepository` em SI-02.2)_

**Dependencies:** none _(depende em runtime de `V2__create_users_table.sql` do slice `user-auth` para o FK `user_id`; não há SI nesta fatia que crie `users`)_

**Acceptance criteria:**

- Aplicar as migrations sobre um banco limpo cria a tabela `canais` com constraint unique em `slug` e em `user_id`.
- Inserir dois registros em `canais` com o mesmo `slug` viola a constraint unique.
- Inserir um segundo registro em `canais` com um `user_id` já existente viola a constraint unique (no máximo um canal por usuário).
- Inserir um registro com `user_id` inexistente em `users` é rejeitado pela foreign key.

---

### SI-02.2 — Entidade de domínio `Canal` + mapeamento JPA + repositório

**Description:** Modelar o `Canal` como entidade de domínio pura e expor seu contrato de persistência, mantendo a separação Clean Architecture entre domínio e infraestrutura.

**Technical actions:**

1. Criar `domain/entities/Canal` — entidade pura sem anotações de infraestrutura (per `backend/CLAUDE.md` → "sem `@Entity` no domínio"), com `id`, `userId`, `slug`, `name`, `createdAt`; o `id` (UUID v4) é atribuído na construção da entidade (per `canal-aggregate/TD-02`).
2. Criar `domain/repositories/CanalRepository` — interface com `save(Canal)` e `existsBySlug(String)` (a verificação de colisão exigida por `canal-aggregate/TD-02`).
3. Criar mapeamento JPA em `infrastructure` (entidade `@Entity` separada + mapper domínio↔persistência) para a tabela `canais` (per `### Data Model → Canal`, `phase-01-base/TD-05`).
4. Implementar em `infrastructure` o adapter de `CanalRepository` via Spring Data JPA.

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `Canal` | Unit (Mockito, sem DB): construção atribui `id` UUID; getters | `backend/src/test/java/.../domain/entities/CanalTest.java` |
| `CanalRepository` (adapter) | Integration (Testcontainers PostgreSQL real): `save`, `existsBySlug`, constraints unique | `backend/src/test/java/.../infrastructure/CanalRepositoryIntegrationTest.java` |

**Dependencies:** SI-02.1 — a tabela `canais` precisa existir para o teste de integração do adapter.

**Acceptance criteria:**

- Construir um `Canal` atribui um `id` UUID não-nulo sem qualquer persistência.
- `existsBySlug` retorna `true` para um slug já persistido e `false` para um slug ausente.
- Persistir um `Canal` com `slug` duplicado falha por violação de constraint unique.
- Persistir um `Canal` cujo `userId` já está associado a outro canal falha por violação de constraint unique.

---

### SI-02.3 — Derivação de slug com fallback determinístico

**Description:** Isolar a regra de derivação do slug do canal — slug primário a partir do prefixo do e-mail, com fallback determinístico em caso de colisão — numa unidade de domínio testável independentemente.

**Technical actions:**

1. Criar em `domain` o factory/serviço `ChannelSlugFactory` que deriva o slug primário a partir do `email_prefix` (a parte do e-mail antes de `@`) (per `canal-aggregate/TD-02`).
2. Implementar o fallback determinístico `{email_prefix}_{primeiros-4-hex-do-userId}`, aplicado quando o slug primário já existe — colisão verificada por uma única chamada a `CanalRepository.existsBySlug`, sem loop de retry (per `canal-aggregate/TD-02`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `ChannelSlugFactory` | Unit (Mockito, mock `CanalRepository`): slug primário, fallback em colisão, determinismo, verificação única | `backend/src/test/java/.../domain/ChannelSlugFactoryTest.java` |

**Dependencies:** SI-02.2 — usa `CanalRepository.existsBySlug` para detectar colisão.

**Acceptance criteria:**

- Para o e-mail `joao@x.com` sem colisão, o slug derivado é `joao`.
- Quando o slug `joao` já existe, o slug derivado é `joao_` seguido dos 4 primeiros caracteres hexadecimais do `userId`.
- A derivação é determinística: o mesmo `email` e `userId` produzem sempre o mesmo slug de fallback.
- A derivação consulta a existência de slug no repositório no máximo uma vez (sem loop de retry).

---

### SI-02.4 — Criação síncrona do canal no `RegisterUserUseCase`

**Description:** Conectar a criação do canal ao fluxo de cadastro, criando o `Canal` de forma síncrona e atômica junto com o `User`, tornando o canal uma invariante rígida do registro.

**Technical actions:**

1. Injetar `CanalRepository` e `ChannelSlugFactory` (SI-02.3) no `RegisterUserUseCase` (do slice `user-auth`) como interfaces, preservando o Clean Architecture (per `canal-aggregate/TD-01`).
2. Após persistir o `User`, derivar o slug e persistir o `Canal` correspondente dentro da mesma fronteira `@Transactional` do adaptador de infraestrutura (per `canal-aggregate/TD-01`).
3. Garantir atomicidade: uma falha na criação do canal reverte a criação do usuário via rollback transacional (per `canal-aggregate/TD-01`).

**Tests:**

| Artifact | Layer | Test file |
|----------|-------|-----------|
| `RegisterUserUseCase` (canal) | Unit (Mockito, mock `UserRepository` + `CanalRepository`): cadastro cria canal; falha no canal propaga exceção | `backend/src/test/java/.../application/usecases/RegisterUserUseCaseCanalTest.java` |
| Cadastro → canal | Integration (Testcontainers + MockMvc): `POST /api/auth/register` cria usuário e canal atomicamente | `backend/src/test/java/.../interfaces/RegisterUserChannelIntegrationTest.java` |

**Dependencies:** SI-02.2 (entidade + repositório), SI-02.3 (derivação de slug).

**Acceptance criteria:**

- Após um cadastro bem-sucedido, existe exatamente um `Canal` associado ao `userId` do novo usuário.
- O `slug` do canal criado corresponde ao prefixo do e-mail quando não há colisão.
- Quando o prefixo do e-mail colide com um canal existente, o canal é criado com o slug de fallback `{email_prefix}_{primeiros-4-hex-do-userId}`.
- Uma falha na persistência do canal reverte a persistência do usuário — nenhum `User` órfão (sem canal) permanece no banco.

---

## Technical Specifications

### Data Model

#### Canal

| Field | Type | Constraints |
|-------|------|-------------|
| id | uuid | PK, generated (UUID v4, mesmo padrão de `## Inherited Conventions` → `user-auth/TD-06`) |
| user_id | uuid | FK → `users(id)`, unique, not null |
| slug | varchar(255) | unique, not null |
| name | varchar(255) | not null |
| created_at | timestamptz | default now() |

**Relations:** `Canal` pertence a um `User` (`Canal.user_id` → `User.id`). `User` tem um `Canal` (one-to-one) — declarado em `user-auth`'s Data Model como "`User` has one `Channel` (criado pelo slice `canal-aggregate`)".

**Indexes:** unique on `slug`; unique on `user_id` (garante no máximo um canal por usuário, refletindo a invariante de cardinalidade 1:1).

**Slug derivation (per `canal-aggregate/TD-02`):** o slug primário é o `email_prefix` (parte do e-mail antes de `@`). Em colisão com um slug já existente, aplica-se o fallback determinístico `{email_prefix}_{primeiros-4-hex-do-userId}`. Como o `userId` (UUID v4) é atribuído na construção da entidade, a derivação é **determinística e race-condition safe** — no máximo uma verificação de fallback ao DB, sem loop de retry.

**Migration:** `V4__create_canais_table.sql` (Flyway SQL puro, forward-only, per `## Inherited Conventions` → `phase-01-base/TD-02`). Depende de `V2__create_users_table.sql` (FK `user_id` → `users`), criada pelo slice `user-auth`.

---

## Dependency Map

```
SI-02.1 (root — migration tabela canais)
└── SI-02.2 — depends on SI-02.1 (tabela deve existir para o teste de integração do repositório)
    ├── SI-02.3 — depends on SI-02.2 (usa CanalRepository.existsBySlug)
    └── SI-02.4 — depends on SI-02.2 + SI-02.3 (precisa da entidade/repositório e da derivação de slug)
```

---

## Deliverables

- [ ] SI-02.1 — Migration Flyway: tabela `canais`
- [ ] SI-02.2 — Entidade de domínio `Canal` + mapeamento JPA + repositório
- [ ] SI-02.3 — Derivação de slug com fallback determinístico
- [ ] SI-02.4 — Criação síncrona do canal no `RegisterUserUseCase`

**Full test suites:**

- [ ] Backend tests pass (`cd backend && ./gradlew test`)
- [ ] Build + compilação passam (`cd backend && ./gradlew build`)
