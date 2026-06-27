---
subproject: backend
runner: junit+mockmvc+testcontainers
scope: phase-02-user-auth
si: SI-02.9
target_file: backend/src/test/java/com/fcstreamtube/auth/AuthPasswordRecoveryE2ETest.java
---

# POST /api/auth/forgot-password + reset-password Test Plan

## Application Overview

Fluxo de recuperação de senha: solicitação neutra (anti-enumeração) que emite token `PASSWORD_RESET` e dispara e-mail quando o usuário existe; e redefinição da senha via token, com re-hash BCrypt e consumo do token.

## Test Scenarios

### 1. Solicitação de reset

**Setup:** `beforeEach` truncar `users`/`user_tokens`; subir módulo Spring + PostgreSQL Testcontainers; mock/spy do `EmailService`; seed de um usuário conhecido.

#### 1.1. forgot-password-retorna-202-neutro

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/forgot-password com e-mail de usuário existente
    - expect: status 202
  2. POST /api/auth/forgot-password com e-mail inexistente
    - expect: status 202 (resposta indistinguível do caso existente — não revela existência)

#### 1.2. forgot-password-emite-token-e-email-quando-existe

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/forgot-password com e-mail existente
    - expect: existe um `user_tokens` com `type = PASSWORD_RESET` para o usuário
    - expect: `EmailService.sendPasswordResetEmail` invocado com o link de reset

### 2. Redefinição de senha

**Setup:** idem grupo 1; seed de um token `PASSWORD_RESET` válido.

#### 2.1. reset-password-com-token-valido-retorna-204

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/reset-password com `{ token válido, password nova }`
    - expect: status 204
    - expect: token marcado como usado (`used_at` preenchido)
  2. POST /api/auth/login com a nova senha
    - expect: status 200 (login bem-sucedido com a senha redefinida)

#### 2.2. reset-password-com-token-invalido-retorna-410

**Covers AC:** #4
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/reset-password com token expirado/já-usado
    - expect: status 410
    - expect: `ProblemDetail` com code `INVALID_TOKEN`
