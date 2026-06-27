---
subproject: frontend
runner: playwright
scope: phase-02-user-auth
si: SI-02.15b
target_file: frontend/e2e/login.spec.ts
---

# User login (/login) Test Plan

## Application Overview

Tela de login com reactive form (e-mail, senha), link "Forgot password?" e footer "Sign up". Em sucesso, a sessão é ativada (cookie httpOnly setado pelo backend) e o usuário é redirecionado para `/`. Credenciais inválidas exibem um `MatSnackBar`.

## Test Scenarios

### 1. Login

**Setup:** `frontend/tests/fixtures.ts` (MSW network fixture auto-aplicada — mocka `POST /api/auth/login` e `GET /api/me`).

#### 1.1. login-bem-sucedido-redireciona-para-home

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário acessa `/login`, preenche credenciais válidas e clica em "Sign in" (MSW responde 200)
    - expect: URL muda para `/`
    - expect: sessão ativa (`/api/me` retorna o usuário)

#### 1.2. credenciais-invalidas-exibem-snackbar

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário submete credenciais inválidas (MSW responde 401 INVALID_CREDENTIALS)
    - expect: `MatSnackBar` visível com "Invalid credentials. Please try again."
    - expect: permanece em `/login`

### 2. Navegação

**Setup:** idem grupo 1.

#### 2.1. links-navegam-para-forgot-e-signup

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário clica em "Forgot password?"
    - expect: URL muda para `/forgot-password`
  2. Usuário (em `/login`) clica em "Sign up" no footer
    - expect: URL muda para `/signup`
