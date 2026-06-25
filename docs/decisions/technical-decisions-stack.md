# ADR: Seleção de Stack — Angular LTS + Java 25 + Spring Boot LTS

## Status

Aceito

## Contexto

O planejamento inicial do StreamTube utilizava Next.js (frontend) e Nest.js (backend) em um monorepo JavaScript. Antes do início de qualquer implementação, foi tomada a decisão de mudar a stack para tecnologias com ecossistemas mais maduros e melhor adequação aos requisitos do projeto (uploads de até 10GB, processamento pesado em segundo plano, autenticação robusta).

## Decisão

Adotar **Angular LTS** para o frontend SPA e **Java 25 + Spring Boot LTS** para o backend REST API.

Os demais componentes permanecem inalterados: PostgreSQL, S3/MinIO, FFmpeg, SMTP.

## Justificativa

### Backend — Java 25 + Spring Boot LTS

- Spring Boot oferece ecossistema completo e battle-tested: Spring Security (autenticação/autorização), Spring Mail (SMTP), Spring Data JPA (persistência), Spring Web (REST API)
- Java 25 inclui Virtual Threads estáveis (Project Loom), ideais para I/O intensivo como uploads de 10GB e múltiplas conexões simultâneas sem consumo excessivo de threads do OS
- Spring AMQP (RabbitMQ) e Spring Kafka são candidatos naturais para a Message Queue (ainda TBD), ambos com integração nativa ao ecossistema Spring
- Multipart upload e streaming de arquivos grandes têm suporte nativo no Spring Web

### Frontend — Angular LTS

- Framework SPA totalmente opinionado com TypeScript nativo, CLI robusto (`ng`), roteamento, HttpClient e formulários reativos nativos — sem necessidade de escolher e integrar bibliotecas separadas
- Angular Signals (disponível nas versões recentes) para gerenciamento de estado reativo
- Build otimizado via `ng build` com tree-shaking e lazy loading de rotas

## Consequências

- O repositório passa a ter dois subprojetos independentes: `backend/` (Maven ou Gradle) e `frontend/` (Angular workspace) — sem monorepo JS na raiz
- Comandos de build: `mvn verify` ou `./gradlew build` para o backend; `ng build` para o frontend
- Não há `package.json` na raiz; `node_modules` é exclusivo do subprojeto `frontend/`
- Docker Compose mantém a mesma estrutura de serviços; apenas os Dockerfiles de `frontend` e `api` mudam
- **Message Queue:** decisão postergada para a Fase 03 — candidatos são Spring AMQP (RabbitMQ) e Spring Kafka; a escolha será documentada em `technical-decisions-phase-03.md`
