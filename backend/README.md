# Backend — FC StreamTube

API REST em Spring Boot 3 · Java 25 · Gradle · PostgreSQL · Flyway.

## Requisitos

- Java 25 (`sdk use java 25.0.2-tem`)
- PostgreSQL (via `docker compose up -d` na raiz do repo)

## Comandos

```bash
./gradlew build     # compila e roda os testes
./gradlew test      # apenas testes (Testcontainers sobe PostgreSQL automaticamente)
./gradlew bootRun   # inicializa localmente
```

## Arquitetura — Clean Architecture

```
com.fcstreamtube/
├── domain/entities/        ← Entidades puras (sem @Entity, sem anotações de infra)
├── domain/repositories/    ← Interfaces de persistência
├── application/usecases/   ← Um UseCase por ação de negócio
├── infrastructure/         ← JPA entities, repositórios Spring Data, adapters
└── interfaces/controllers/ ← Controllers REST
```

**Regra:** imports de `infrastructure` em `domain` ou `application` são proibidos.

## Migrations Flyway

Localização: `src/main/resources/db/migration/`

| Padrão | Uso |
|---|---|
| `V{n}__{descrição}.sql` | Migration versionada (ex: `V2__create_users_table.sql`) |
| `R__{descrição}.sql` | Migration repetível (views, funções) |

Flyway é forward-only — nunca reverta, crie uma nova migration corretiva.
