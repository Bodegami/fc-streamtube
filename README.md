# fc-streamtube

Monorepo para a plataforma de streaming de vídeo FC StreamTube.

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

## Pré-requisitos

- [OrbStack](https://orbstack.dev/) (runtime Docker local)
- Java 25 via [SDKMAN!](https://sdkman.io/) — `sdk install java 25.0.2-tem` (non-LTS; chosen to match the project's target runtime — see `docs/guides/machine-environment.md`)
- Java 21 via SDKMAN! — `sdk install java 21.0.10-tem` (required for the Gradle daemon workaround; see `docs/guides/machine-environment.md`)
