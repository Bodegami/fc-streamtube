---
scope_type: phase
related_phases: [2]
status: decided
date: 2026-06-26
scope_description: "Decisões técnicas para o agregado Canal (Fase 02): trigger de criação síncrono vs evento, e estratégia de slug derivado do prefixo de e-mail com tratamento de colisão"
covers_capabilities:
  - "Criação automática do canal do usuário a partir do prefixo do e-mail"
depends_on_slices: [user-auth]
---

# Technical Decisions — Canal Aggregate (Phase 02)

_Subprojects in scope:_

- `backend/` — entidade `Canal`, `RegisterUserUseCase` (trigger de criação), migration Flyway para tabela `channels`, lógica de derivação e colisão do slug
- `frontend/` — nenhuma decisão em aberto nesta fatia; a criação do canal é server-side; telas de edição do canal pertencem à Fase 04

---

## TD-01: Canal Creation Trigger

**Scope:** Backend

**Capability:** "Criação automática do canal do usuário a partir do prefixo do e-mail"

**Context:** O `RegisterUserUseCase` deve criar também uma entidade `Canal` associada ao novo usuário. A questão é se isso ocorre sincronamente dentro do mesmo use case (mesma transação DB), via domain event desacoplado, ou como chamada separada orquestrada pelo controller. O backend segue Clean Architecture (`backend/CLAUDE.md`): `application/usecases/` são classes single-action; `domain/entities/` são puras sem anotações de infra; repositórios são interfaces injetadas como portas. Spring Boot 3.5.3 com `@Transactional` aplicado no adaptador de infraestrutura.

**Options:**

### Option A: Criação síncrona dentro do RegisterUserUseCase (mesma transação)

- `RegisterUserUseCase` recebe `UserRepository` e `CanalRepository` como portas (interfaces). Cria os dois aggregates via construtores/factories de domínio e os persiste via suas respectivas portas. O adaptador de infraestrutura envolve toda a operação em uma única chamada `@Transactional`.
- **Pros:** Atômico — os dois são criados ou nenhum é; implementação mais simples; sem infraestrutura de eventos; qualquer falha (slug duplicado, e-mail duplicado) faz rollback dos dois; o modelo ports/adapters do Clean Architecture é preservado.
- **Cons:** Um único use case manipula dois aggregate roots simultaneamente — discutível quanto à responsabilidade única se `Canal` for um bounded context totalmente separado no futuro.

### Option B: Domain Event → Handler separado

- `RegisterUserUseCase` cria apenas `User`, depois publica um `UserRegisteredEvent` via porta `DomainEventPublisher`. Um `CanalCreationHandler` separado assina o evento e cria o `Canal`. O `ApplicationEventPublisher` do Spring é a implementação de infraestrutura da porta.
- **Pros:** `RegisterUserUseCase` não conhece `Canal`; cada use case manipula um único aggregate.
- **Cons:** Em Phase 02, a criação do canal é uma invariante rígida do cadastro — não é opcional nem eventual; eventos assíncronos ou cross-transaction criam o risco de "usuário criado sem canal"; a porta `DomainEventPublisher` adiciona cerimônia de infraestrutura para o que é efetivamente uma regra de mesmo-aggregate; consistência eventual é desnecessária aqui.

### Option C: Dois UseCases separados orquestrados pelo Controller

- `RegisterController` chama `RegisterUserUseCase`, depois chama `CreateCanalUseCase`. Duas chamadas de repositório, duas transações, orquestradas na camada HTTP.
- **Pros:** Desacoplamento completo entre os dois use cases.
- **Cons:** O controller passa a orquestrar regras de negócio — viola o Clean Architecture (controllers são interfaces, não orquestradores); atomicidade é perdida (usuário pode ser criado sem canal se o segundo use case falhar); a regra de negócio "cadastro cria um canal" vaza para a camada de interface.

**Recommendation:** Option A — criação síncrona dentro do `RegisterUserUseCase` é a implementação mais simples e segura quando a criação do canal é uma invariante rígida do cadastro; atomicidade via `@Transactional` no adaptador de infraestrutura; ambas as portas (`UserRepository`, `CanalRepository`) são injetadas como interfaces, preservando o Clean Architecture. Domain events (Option B) são valiosos quando o efeito colateral é opcional ou eventual — nenhum dos dois se aplica aqui em Phase 02.

**Decision:** _[Option A]_

---

## TD-02: Channel Slug Collision Strategy

**Scope:** Backend

**Capability:** "Criação automática do canal do usuário a partir do prefixo do e-mail"

**Context:** O `slug` do Canal (handle de URL) é derivado do prefixo do e-mail do usuário no momento do cadastro (ex: `joao` de `joao@gmail.com`). Dois usuários com domínios diferentes mas mesmo prefixo (ex: `joao@gmail.com` e `joao@hotmail.com`) geram uma colisão de slug. O slug é usado nas Fases 04–07 para URLs públicas do canal e precisa ser único — a tabela `channels` terá constraint `UNIQUE` na coluna `slug`. A Fase 04 permite ao usuário editar o nickname/nome do canal, mas editabilidade do slug em Fase 04 ainda não está definida.

**Options:**

### Option A: Falha no cadastro com 409 se o slug já existir

- `RegisterUserUseCase` deriva o slug do prefixo do e-mail, verifica unicidade e lança uma domain exception se já existir. O frontend exibe: "handle de canal já em uso; tente um e-mail diferente".
- **Pros:** Implementação mais simples; sem lógica de geração automática; slug sempre igual ao prefixo do e-mail.
- **Cons:** O usuário não controla o slug (é derivado do e-mail), então uma colisão é um bloqueador de cadastro irresolvível; quem tem `joao@hotmail.com` não consegue registrar se `joao@gmail.com` já existir — UX inaceitável.

### Option B: Adicionar sufixo numérico incremental na colisão

- Slug = prefixo do e-mail; se `joao` já existir, tenta `joao2`, `joao3`, até `joao99`. O usuário é informado do handle atribuído automaticamente no e-mail de confirmação ou no primeiro login.
- **Pros:** Cadastro sempre funciona; slug ainda reconhecível a partir do e-mail; usuário pode renomear na Fase 04.
- **Cons:** `joao99` é menos elegante; exige loop de retry no use case (até N checks ao DB); condição de corrida em alta concorrência (dois `joao@...` simultâneos → ambos tentam `joao2` → um falha na constraint DB); requer retry no nível do DB também.

### Option C: Slug baseado em UUID; display_name = prefixo do e-mail (sem unicidade)

- `Canal.slug` é um handle auto-gerado baseado em UUID curto (ex: `channel_a3f9bc`). `Canal.display_name` = prefixo do e-mail sem constraint de unicidade. Usuário define slug legível na Fase 04.
- **Pros:** Zero risco de colisão no cadastro; sem retry; constraint DB em coluna UUID; display name pode ser qualquer valor.
- **Cons:** Até a Fase 04, canais têm URLs ilegíveis; o requisito "prefixo do e-mail se torna o nome do canal" não é atendido para o handle de URL (apenas para o nome de exibição); experiência de onboarding pior.

### Option D: Sufixo hex de 4 chars (derivado do UUID do usuário) somente na colisão

- Slug = prefixo do e-mail se disponível; se já existir, slug = `{email_prefix}_{4-hex-chars}` derivados dos primeiros 4 chars hexadecimais do UUID do usuário. Fallback determinístico — sem loop de retry; o UUID é atribuído na construção da entidade antes da verificação do slug.
- **Pros:** Sem loop de retry; slug é determinístico dado o UUID do usuário (mesma entrada → mesmo slug); slugs primários limpos para o primeiro registrante com aquele prefixo; race-condition safe (UUID já existe antes da verificação de slug); sufixo curto e legível; slug editável na Fase 04.
- **Cons:** Sufixo `joao_a3fb` não carrega significado para o usuário; implementação ligeiramente mais complexa que a Option A.

**Recommendation:** Option D — fallback determinístico via `{email_prefix}_{primeiros-4-hex-do-userId}` é race-condition safe (UUID atribuído na construção da entidade, derivação do slug é determinística), não requer loop de retry ao DB (no máximo uma verificação de fallback), mantém slugs primários limpos para o caso comum (primeiro registrante com aquele prefixo) e produz resultado previsível e reproduzível. O loop de retry da Option B tem race conditions que exigem retry no nível do DB; a Option A bloqueia o cadastro por um motivo que o usuário não pode resolver; a Option C descarta inteiramente o requisito de slug derivado do e-mail.

**Decision:** _[Option D]_

---

## Decisions Summary

| ID | Scope | Decision | Recommendation | Choice |
|----|-------|----------|----------------|--------|
| TD-01 | Backend | Canal Creation Trigger | Criação síncrona no RegisterUserUseCase (Option A) | _[Option A]_ |
| TD-02 | Backend | Channel Slug Collision Strategy | Sufixo hex de 4 chars na colisão (Option D) | _[Option D]_ |
