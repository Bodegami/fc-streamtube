# CLAUDE.md — Backend

## Stack

- Java 25 · Spring Boot 3 · Gradle (Kotlin DSL)
- PostgreSQL · Flyway (SQL puro, forward-only)
- Testcontainers para testes de integração

## Clean Architecture — Convenções obrigatórias

### Estrutura de pacotes

```
com.fcstreamtube/
├── domain/
│   ├── entities/       ← Entidades de domínio puras (sem anotações de infra)
│   ├── exceptions/     ← Exceções lançadas pelas regras de negócio
│   └── repositories/   ← Interfaces (contratos de persistência)
├── application/
│   ├── usecases/       ← Um UseCase por ação (CreateXxxUseCase, etc.)
│   └── ports/
│       └── out/        ← Output Ports (interfaces de saída: EmailGateway, etc.)
├── infrastructure/     ← Implementações JPA, adapters externos
└── interfaces/
    ├── advice/         ← @RestControllerAdvice (GlobalExceptionHandler)
    └── controllers/    ← Controllers REST (Spring MVC)
```

### Regra crítica: sem `@Entity` no domínio

As classes em `domain/entities/` NÃO devem conter anotações de infraestrutura:
- Proibido: `@Entity`, `@Table`, `@Column` (JPA/Hibernate)
- Proibido: `@JsonProperty`, `@JsonIgnore` (Jackson)
- Proibido: qualquer anotação de framework externo

Crie objetos de mapeamento separados em `infrastructure/` para persistência.

### Migrations Flyway

- Convenção: `V{versão}__{descrição}.sql` (ex: `V2__create_users_table.sql`)
- Migrations repetíveis: `R__{descrição}.sql` (views, funções)
- Localização: `src/main/resources/db/migration/`
- Nunca reverter — corrija com nova migration forward

## Testes

- Unidade: sem DB, sem Spring context — mocks via Mockito
- Integração: Testcontainers com PostgreSQL real (não H2, não mock)
- Naming: `given[Contexto]_when[Acao]_then[Resultado]`

## Build

```bash
./gradlew build        # compila + testa
./gradlew test         # apenas testes
./gradlew bootRun      # sobe localmente (requer PostgreSQL)
./gradlew bootJar      # gera Jar em build/libs/
```
