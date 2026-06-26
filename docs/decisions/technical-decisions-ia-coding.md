---
scope_type: phase
related_phases: [1]
status: decided
date: 2026-06-25
scope_description: "Fundação de IA para coding: seleção da ferramenta de AI coding e granularidade do CLAUDE.md para o projeto"
covers_capabilities:
  - "Fundação de IA para coding."
---

# Technical Decisions — Fundação de IA para Coding

_Subprojects in scope:_

- `backend/` — sem decisão de código runtime neste documento; recebe `backend/CLAUDE.md` como artefato de configuração (dependente da decisão TD-02)
- `frontend/` — explicitamente adiado na Fase 01 ("será criado depois, não agora"); receberá `frontend/CLAUDE.md` apenas quando o subprojeto for inicializado
- Repo-wide — escopo primário: ambas as decisões são de ferramental de desenvolvimento e configuração do repositório, sem impacto em código runtime

---

## TD-01: Ferramenta de AI Coding

**Scope:** Repo-wide

**Capability:** "Fundação de IA para coding."

**Context:** A fundação de IA para coding define qual assistente de IA será adotado durante o desenvolvimento do projeto e, consequentemente, quais artefatos de configuração precisam ser criados no repositório. Ferramentas diferentes exigem arquivos de configuração diferentes (CLAUDE.md, .cursorrules, .github/copilot-instructions.md, etc.) e impactam o workflow de todos os desenvolvedores. O ambiente de desenvolvimento já possui configuração global extensa orientada ao Claude Code (`machine-environment.md`, `git-flow.md`, `docker-k8s.md`, `clean-architecture.md`, etc.), o que torna essa escolha relevante para o custo de onboarding.

**Options:**

### Option A: Claude Code (Anthropic CLI)
- CLI que usa o modelo Claude e integra com o terminal e IDEs via extensão. Configurado por arquivos `CLAUDE.md` em cascata: global (`~/.claude/CLAUDE.md`) → projeto raiz → subprojeto. O ambiente de desenvolvimento do projeto já possui configuração global madura com guides de Git, Docker, testing, Clean Architecture e DDD.
- **Pros:** Configuração global já existente e madura; mecanismo de `CLAUDE.md` por projeto/subprojeto é o padrão nativo; suporte a skills, hooks e memória persistente entre sessões; integração com IDEs (VS Code, JetBrains) via extensão; contexto de projeto granular por diretório de trabalho.
- **Cons:** Custo por uso da API Anthropic; ferramenta CLI (não IDE-nativa sem extensão).

### Option B: GitHub Copilot (VSCode / JetBrains)
- Plugin de IDE com autocomplete e chat inline. Configurado via `.github/copilot-instructions.md` (instruções globais do repositório) e configurações de IDE. Integrado ao ecossistema GitHub.
- **Pros:** Integração nativa no editor sem necessidade de CLI; familiar para desenvolvedores com workflow GitHub-centric; plano Teams/Enterprise disponível.
- **Cons:** Contexto de projeto limitado (um único arquivo de instruções sem hierarquia por subprojeto); sem mecanismo equivalente a skills, hooks ou memória; descartaria o investimento já feito na configuração global Claude Code.

### Option C: Cursor (IDE com AI integrada)
- IDE baseado em VS Code com AI integrada nativamente. Configurado via `.cursorrules` na raiz ou por diretório. Suporta Models como Claude, GPT-4, etc. via configuração.
- **Pros:** AI integrada deep no editor; `.cursorrules` por diretório provê granularidade similar ao CLAUDE.md; interface visual para revisão de mudanças.
- **Cons:** Requer adoção de um novo IDE (abandona IntelliJ/WebStorm para Java e WebStorm para Angular — IDEs documentados em `machine-environment.md`); `.cursorrules` é menos expressivo que CLAUDE.md para automações complexas; descartaria o investimento na configuração global Claude Code.

**Recommendation:** Option A (Claude Code) — o ambiente de desenvolvimento já possui configuração global extensa e matura (guides de arquitetura, testing, Git, Docker), eliminando overhead de configuração do zero; o mecanismo de `CLAUDE.md` por projeto/subprojeto é exatamente o padrão documentado no hub global; as Options B e C descartariam esse investimento e ofereceriam menor flexibilidade para automação de tarefas complexas.

**Decision:** Option A

---

## TD-02: Granularidade do CLAUDE.md do Projeto

**Scope:** Repo-wide

**Capability:** "Fundação de IA para coding."

**Context:** Dado que a ferramenta de AI coding foi selecionada (TD-01), é necessário definir quantos e quais arquivos `CLAUDE.md` criar para o projeto fc-streamtube. O hub global (`~/.claude/CLAUDE.md`) e o hub de projetos (`~/Projetos/claude-projects/CLAUDE.md`) já existem. A questão é qual granularidade de contexto de IA criar dentro do repositório: um único arquivo raiz ou também arquivos por subprojeto. A escolha determina os artefatos a serem criados na Fase 01 e como o contexto de projeto é dividido entre backend (Java/Spring) e frontend (Angular), que têm stacks, comandos e convenções independentes.

**Options:**

### Option A: CLAUDE.md único na raiz do repositório
- Um único `fc-streamtube/CLAUDE.md` com contexto do projeto: stack, subprojetos, comandos de build, convenções e arquitetura. Claude Code carrega esse arquivo ao operar em qualquer parte do repositório.
- **Pros:** Manutenção simplificada (um arquivo); contexto unificado independente do subprojeto; adequado para o estágio inicial onde `backend/` e `frontend/` ainda não existem na Fase 01.
- **Cons:** Contexto menos focado por subprojeto; conforme o projeto cresce (fases 02–07), o arquivo pode carregar contexto irrelevante para tarefas específicas de um subprojeto.

### Option B: CLAUDE.md raiz + CLAUDE.md por subprojeto
- `fc-streamtube/CLAUDE.md` (hub do projeto: stack, arquitetura, decisões técnicas chave) + `backend/CLAUDE.md` (stack Java 25, Gradle, arquitetura hexagonal, comandos Maven/Gradle) + `frontend/CLAUDE.md` (stack Angular LTS, comandos `ng`, estrutura de módulos). Claude Code carrega automaticamente o arquivo mais específico para o diretório de trabalho.
- **Pros:** Contexto focado por subprojeto — ao trabalhar em `backend/`, o assistente carrega apenas contexto Java/Spring sem ruído Angular; alinha com o padrão documentado no hub global (que descreve `ecommerce-project/CLAUDE.md` e `estudos-agentes/CLAUDE.md` como "Local Context: Use Java 21, Micronaut, Virtual Threads" e "Use Python, static typing, LangChain"); os arquivos por subprojeto crescem organicamente com cada fase de implementação.
- **Cons:** Três arquivos para manter; na Fase 01 `backend/` e `frontend/` ainda não existem — os arquivos seriam criados com conteúdo mínimo e enriquecidos gradualmente.

### Option C: Sem CLAUDE.md de projeto (uso somente do hub global até implementação)
- Não criar nenhum `CLAUDE.md` na Fase 01; os arquivos seriam criados junto com os subprojetos nas fases de implementação. O projeto usa apenas o hub global e o hub de projetos até lá.
- **Pros:** Evita criar arquivos com conteúdo mínimo/prematuro.
- **Cons:** A própria Fase 01 (criação dos subprojetos, Docker Compose, inicialização Spring Boot) se beneficia de contexto de projeto documentado durante a implementação — adiar é circular; o contexto do projeto (stack, decisões TD-01–TD-05, arquitetura hexagonal) precisa estar disponível para o assistente já na execução da Fase 01.

**Recommendation:** Option B (raiz + por subprojeto) — alinha com o padrão de três níveis documentado no hub global; `backend/CLAUDE.md` e `frontend/CLAUDE.md` podem conter contexto mínimo (stack, comandos, referência à arquitetura) na Fase 01 e crescer organicamente com cada fase; Claude Code usa o arquivo mais próximo automaticamente, tornando o contexto relevante para a tarefa em execução.

**Decision:** Option B

---

## Decisions Summary

| ID | Scope | Decision | Recommendation | Choice |
|----|-------|----------|----------------|--------|
| TD-01 | Repo-wide | Ferramenta de AI Coding | Claude Code (Option A) | Option A |
| TD-02 | Repo-wide | Granularidade do CLAUDE.md | Raiz + por subprojeto (Option B) | Option B |
