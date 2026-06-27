---
subproject: frontend
runner: playwright
scope: phase-02-user-auth
si: SI-02.14b
target_file: frontend/e2e/signup.spec.ts
---

# Create user account (/signup) Test Plan

## Application Overview

Tela de cadastro com reactive form (nome, e-mail, senha + confirmação + indicador de força, termos). Em sucesso, redireciona para `/` já autenticado. Erros de validação e e-mail duplicado aparecem inline; o botão fica desabilitado enquanto o formulário é inválido.

## Test Scenarios

### 1. Cadastro

**Setup:** `frontend/tests/fixtures.ts` (MSW network fixture auto-aplicada — mocka `POST /api/auth/register`).

#### 1.1. cadastro-bem-sucedido-redireciona-para-home

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário acessa `/signup` e preenche nome, e-mail, senha e confirmação válidos, marca os termos
    - expect: botão "Create account" habilitado
  2. Usuário clica em "Create account" (MSW responde 201)
    - expect: URL muda para `/`
    - expect: estado autenticado (sessão ativa)

#### 1.2. email-duplicado-exibe-erro-inline

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário submete o formulário com e-mail já cadastrado (MSW responde 409 EMAIL_ALREADY_EXISTS)
    - expect: erro inline (`mat-error`) no campo Email
    - expect: permanece em `/signup`

### 2. Validação client-side

**Setup:** idem grupo 1.

#### 2.1. submit-desabilitado-enquanto-invalido

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. Usuário deixa senha e confirmação divergentes e/ou termos desmarcados
    - expect: botão "Create account" permanece desabilitado
  2. Usuário corrige os campos (senhas iguais, termos marcados, e-mail/senha válidos)
    - expect: botão "Create account" habilitado
