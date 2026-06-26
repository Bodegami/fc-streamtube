---
kind: phase
name: phase-1-ia-coding
sources_mtime:
  docs/project-plan.md: "2026-06-25T20:16:47"
  docs/decisions/technical-decisions-ia-coding.md: "2026-06-25T21:12:55"
---

# phase-1-ia-coding — Context

## Scope

**Phase name:** Fase 01 — Configuração Base do Projeto

**Capabilities** (literal, `docs/project-plan.md`):

- Repositório com dois subprojetos separados: `frontend/` (Angular) e `backend/` (Spring Boot — Maven ou Gradle)
- Projeto Angular (frontend) (será criado depois, não agora) e Spring Boot (backend) inicializados
- Ambiente de desenvolvimento local com todos os serviços via Docker Compose
- Estrutura inicial do banco de dados PostgreSQL (schema, migrations e seeds) (sem tabelas ainda)
- Fundação de IA para coding.

**This slice covers:** "Fundação de IA para coding." (per `covers_capabilities`)

**Out of scope:** Implementação de qualquer feature de negócio (Fases 02–07); setup do subprojeto Angular (explicitamente adiado: "será criado depois, não agora"); criação de tabelas do PostgreSQL; qualquer lógica de domínio.

**Deliverables:** Ambiente de desenvolvimento funcional, banco de dados configurado.

**Affected subprojects:** Repo-wide — artefatos de configuração IA (`CLAUDE.md` de projeto e subprojetos).

**Deferred subprojects:** `backend/` e `frontend/` — nenhuma decisão de código runtime neste slice; configuração de AI coding é tooling de desenvolvedor, não código de subprojeto.

**Sequencing notes:** Fase 01 não tem dependências. Todas as demais fases dependem dela direta ou indiretamente.

**Neighbors (for boundary detection only):**

- **Fase 02 (following):** Cadastro, Login e Gerenciamento de Conta — depende da Fase 01. Introduz autenticação, email transacional e sessão de usuário.

---

## Decisions Index

| Ref | Source | Scope | Topic | Status | Decision | Libraries |
|-----|--------|-------|-------|--------|----------|-----------|
| ia-coding/TD-01 | phase | Repo-wide | Ferramenta de AI Coding | decided | Option A | — |
| ia-coding/TD-02 | phase | Repo-wide | Granularidade do CLAUDE.md | decided | Option B | — |

_Source files:_

- ia-coding — `docs/decisions/technical-decisions-ia-coding.md` (scope_type: phase)

---

## Capability Coverage

| Capability (from project-plan.md) | Covered by |
|-----------------------------------|------------|
| Fundação de IA para coding. | ia-coding/TD-01, ia-coding/TD-02 |

---

## Decisions Detail

### ia-coding/TD-01

**Recommendation:** o ambiente de desenvolvimento já possui configuração global extensa e matura (guides de arquitetura, testing, Git, Docker), eliminando overhead de configuração do zero; o mecanismo de `CLAUDE.md` por projeto/subprojeto é exatamente o padrão documentado no hub global; as Options B e C descartariam esse investimento e ofereceriam menor flexibilidade para automação de tarefas complexas.
**Libraries:** —

### ia-coding/TD-02

**Recommendation:** alinha com o padrão de três níveis documentado no hub global; `backend/CLAUDE.md` e `frontend/CLAUDE.md` podem conter contexto mínimo (stack, comandos, referência à arquitetura) na Fase 01 e crescer organicamente com cada fase; Claude Code usa o arquivo mais próximo automaticamente, tornando o contexto relevante para a tarefa em execução.
**Libraries:** —

---

## Inherited Decisions Detail

_No inherited TD details (Fase 01 é a primeira fase — sem fases anteriores)._

---

## Inherited Conventions

_No inherited conventions (Fase 01 é a primeira fase)._

---

## Inherited Deferred Capabilities

_No inherited deferred capabilities._

---

## Non-UI / Deferred Capabilities

_None._

---

## Testing Requirements

### Repo-wide

_No testing guide available for Repo-wide tooling/configuration scope — layer testing requirements not applicable to CLAUDE.md authoring decisions._
