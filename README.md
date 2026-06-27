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

## Pipeline de Planejamento (AI-assisted)

Cada fase ou slice do projeto passa por um pipeline de etapas antes da implementação. A sequência abaixo deve ser seguida em ordem — cada etapa depende da anterior.

| Etapa | Comando | O que faz |
|-------|---------|-----------|
| 1. Pesquisa | `/research <slug>` | Investiga opções técnicas para a fase e produz um documento de decisões (`technical-decisions-{slug}.md`) com contexto, opções, prós/contras e recomendação para cada TD. |
| 2. Contexto | `/plan-context <slug>` | Agrega todas as decisões relevantes (da fase + fases herdadas) em um único `context.md` — índice de TDs, cobertura de capabilities, inventário de telas e convenções herdadas. |
| 3. Validação | `/plan-validate <slug>` | Lê o `context.md` e valida consistência: detecta TDs pendentes, ambiguidades, gaps de dependência, inconsistências e telas sem capability mapeada. Produz `validation.md` com `status: clean` ou `status: dirty`. |
| 4. Resolução | `/plan-resolve <slug>` | Fecha os issues abertos no `validation.md`: faz perguntas ao usuário, preenche `**Decision:**` nos TDs pendentes, busca docs das bibliotecas via Context7 e grava `library-refs.md`. |
| 5. Build | `/plan-build <slug>` | Gera o plano de implementação final da fase a partir do `context.md` validado e limpo. |

### Status atual das fases

| Fase / Slice | `/research` | `/plan-context` | `/plan-validate` | `/plan-resolve` | `/plan-build` |
|---|---|---|---|---|---|
| `phase-01-base` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `user-auth` (Fase 02) | ✓ | ✓ | ✓ clean | ✓ | pendente |
| `canal-aggregate` (Fase 02) | ✓ | ✓ | ✓ clean | ✓ | pendente |

### Ordem de implementação

Para fases com múltiplos slices, implementar na ordem: **`user-auth` → `canal-aggregate`** — o canal é criado automaticamente a partir do `userId` gerado pelo slice de autenticação.

---

## Pré-requisitos

- [OrbStack](https://orbstack.dev/) (runtime Docker local)
- Java 25 via [SDKMAN!](https://sdkman.io/) — `sdk install java 25.0.2-tem` (non-LTS; chosen to match the project's target runtime — see `docs/guides/machine-environment.md`)
- Java 21 via SDKMAN! — `sdk install java 21.0.10-tem` (required for the Gradle daemon workaround; see `docs/guides/machine-environment.md`)
