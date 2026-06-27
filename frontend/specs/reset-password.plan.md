---
subproject: frontend
runner: playwright
scope: phase-02-user-auth
si: SI-02.17b
target_file: frontend/e2e/reset-password.spec.ts
---

# Set new password (/reset-password) Test Plan

## Application Overview

Tela de redefinição de senha, acessada pelo link do e-mail com `token` no query param. Lê o token no init; em sucesso redireciona para `/login`. Token inválido/expirado/ausente redireciona para `/forgot-password` com um `MatSnackBar`. Sem footer e sem back link.

## Test Scenarios

### 1. Redefinição

**Setup:** `frontend/tests/fixtures.ts` (MSW network fixture auto-aplicada — mocka `POST /api/auth/reset-password`).

#### 1.1. reset-com-token-valido-redireciona-para-login

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário acessa `/reset-password?token=<válido>`, preenche nova senha e confirmação iguais e válidas
    - expect: botão "Reset password" habilitado
  2. Usuário clica em "Reset password" (MSW responde 204)
    - expect: URL muda para `/login`

#### 1.2. submit-desabilitado-enquanto-invalido

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário deixa nova senha e confirmação divergentes ou inválidas
    - expect: botão "Reset password" permanece desabilitado

### 2. Token inválido

**Setup:** idem grupo 1; MSW responde 410 INVALID_TOKEN para token inválido.

#### 2.1. token-invalido-redireciona-para-forgot-com-snackbar

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário acessa `/reset-password?token=<inválido>` e submete (MSW responde 410), OU acessa sem `token`
    - expect: URL muda para `/forgot-password`
    - expect: `MatSnackBar` visível com "Link expirado. Solicite um novo."
