# fc-streamtube

Monorepo para a plataforma de streaming de vídeo FC StreamTube.

## UX Figma
https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=0-1&p=f&t=3Xt7ShJRWlLdzjUa-0


## Subprojetos

| Subprojeto | Status | Tecnologia |
|---|---|---|
| `backend/` | ativo | Spring Boot 3 · Java 25 · Gradle · PostgreSQL |
| `frontend/` | adiado | Angular LTS (será criado na Fase 02+) |

## Subir o ambiente de desenvolvimento

```bash
# 1. Copiar variáveis de ambiente
cp .env.example .env

# 2. Subir todos os serviços (PostgreSQL + backend)
docker compose up -d

# 3. (Opcional) Subir serviços auxiliares de tooling
docker compose --profile tools up -d
```

## Backend

```bash
cd backend

# Build
./gradlew build

# Rodar localmente (requer PostgreSQL no ar)
./gradlew bootRun

# Testes
./gradlew test
```

## Frontend

Angular LTS — deferred to Phase 02. A `frontend/` directory and its `CLAUDE.md` will be added when that phase begins.

## Pipeline de Planejamento e Implementação (AI-assisted)

Cada fase ou slice do projeto passa por um pipeline de etapas. A sequência deve ser seguida em ordem — cada etapa depende da anterior.

> **`/plan-pipeline`** não é um passo a executar — é a skill de orientação. Chame-a apenas quando quiser entender o fluxo ou quando não souber por qual etapa começar.

### Planejamento

| Etapa | Comando | O que faz |
|-------|---------|-----------|
| 1. Pesquisa | `/research <slug>` | Investiga opções técnicas e produz `technical-decisions-{slug}.md` com contexto, opções, prós/contras e recomendação para cada TD. |
| 2. Contexto | `/plan-context <slug>` | Agrega todas as decisões relevantes em um único `context.md` — índice de TDs, cobertura de capabilities, inventário de telas e convenções herdadas. |
| 3. Validação | `/plan-validate <slug>` | Valida consistência do `context.md`: detecta TDs pendentes, ambiguidades e gaps. Produz `validation.md` com `status: clean` ou `status: dirty`. |
| 4. Resolução | `/plan-resolve <slug>` | Fecha os issues do `validation.md`: preenche TDs pendentes, busca docs de bibliotecas via Context7 e grava `library-refs.md`. Repetir 3→4 até `status: clean`. |
| 5. Build | `/plan-build <slug>` | Gera o plano final (`phase-NN-{slug}.md`) a partir do `context.md` validado e limpo. Bloqueia se `validation.md` não estiver `clean`. |
| 6. Test Specs *(opcional)* | `/plan-test-specs <slug>` | Deriva specs de cenários E2E (`*.plan.md`) para SIs de telas e controllers. Só necessário se o plano gerado tiver `test_specs_aware: true` no frontmatter e SIs com campo `**Test Specs:**`. |

### Implementação

| Comando | Quando usar |
|---------|-------------|
| `/implement <slug>` | **Padrão atual.** Executa o plano gerado por `/plan-build` (formato moderno: `docs/phases/phase-NN-{slug}/phase-NN-{slug}.md`). Suporta fases, slices e tasks. Delega execução de testes a subagentes. |
| `/implement-phase <slug>` | Legado. Usa apenas para planos gerados pelo antigo `/plan-phase` (arquivo único em `docs/phases/phase-NN-*.md`). Não suporta tasks, Test Specs, Figma SI-Xa/Xb, nem subagentes. |

### Status atual das fases

| Fase / Slice | `/research` | `/plan-context` | `/plan-validate` | `/plan-resolve` | `/plan-build` | `/plan-test-specs` | `/implement` |
|---|---|---|---|---|---|---|---|
| `phase-01-base` | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| `user-auth` (Fase 02) | ✓ | ✓ | ✓ clean | ✓ | pendente | — | — |
| `canal-aggregate` (Fase 02) | ✓ | ✓ | ✓ clean | ✓ | pendente | — | — |

### Ordem de implementação

Para fases com múltiplos slices, implementar na ordem: **`user-auth` → `canal-aggregate`** — o canal é criado automaticamente a partir do `userId` gerado pelo slice de autenticação.

---

## Pré-requisitos

- [OrbStack](https://orbstack.dev/) (runtime Docker local)
- Java 25 via [SDKMAN!](https://sdkman.io/) — `sdk install java 25.0.2-tem` (non-LTS; chosen to match the project's target runtime — see `docs/guides/machine-environment.md`)
- Java 21 via SDKMAN! — `sdk install java 21.0.10-tem` (required for the Gradle daemon workaround; see `docs/guides/machine-environment.md`)
