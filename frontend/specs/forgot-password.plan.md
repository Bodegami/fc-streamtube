---
subproject: frontend
runner: playwright
scope: phase-02-user-auth
si: SI-02.16b
target_file: frontend/e2e/forgot-password.spec.ts
---

# Reset password request (/forgot-password) Test Plan

## Application Overview

Tela de solicitação de reset de senha com reactive form (e-mail). Após submeter, o card troca inline para um success state ("You should receive the email shortly.") sem mudança de rota e sem redirect automático. O shell do card (logo + back arrow) permanece.

## Test Scenarios

### 1. Solicitação de reset

**Setup:** `frontend/tests/fixtures.ts` (MSW network fixture auto-aplicada — mocka `POST /api/auth/forgot-password` → 202).

#### 1.1. submit-troca-para-success-state

**Covers AC:** #1, #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário acessa `/forgot-password`, preenche um e-mail válido e clica em "Send reset link" (MSW responde 202)
    - expect: card exibe a mensagem de sucesso ("You should receive the email shortly.")
    - expect: o formulário fica oculto, mas logo e back arrow permanecem visíveis
    - expect: URL permanece `/forgot-password` (sem redirect)

### 2. Navegação

**Setup:** idem grupo 1.

#### 2.1. back-e-sign-in-navegam-para-login

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário clica no back arrow (←)
    - expect: URL muda para `/login`
  2. Usuário (no success state) clica em "Sign in" no footer
    - expect: URL muda para `/login`
