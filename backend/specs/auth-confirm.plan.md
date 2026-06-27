---
subproject: backend
runner: junit+mockmvc+testcontainers
scope: phase-02-user-auth
si: SI-02.7
target_file: backend/src/test/java/com/fcstreamtube/auth/AuthConfirmE2ETest.java
---

# GET /api/auth/confirm + POST /api/auth/resend-confirmation Test Plan

## Application Overview

Confirmação de conta via token opaco recebido por e-mail e reenvio do e-mail de confirmação. Confirmar marca `email_confirmed = true` e consome o token; reenviar invalida tokens anteriores e emite um novo, com resposta neutra.

## Test Scenarios

### 1. Confirmação de conta

**Setup:** `beforeEach` truncar `users`/`user_tokens`; subir módulo Spring + PostgreSQL Testcontainers; seed de um usuário com token `EMAIL_CONFIRMATION` válido.

#### 1.1. confirmar-com-token-valido-retorna-204

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. GET /api/auth/confirm?token=<válido>
    - expect: status 204
    - expect: `users.email_confirmed = true` para o usuário do token
    - expect: `user_tokens.used_at` preenchido para o token consumido

#### 1.2. confirmar-com-token-invalido-retorna-410

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. GET /api/auth/confirm?token=<expirado|já-usado|inexistente>
    - expect: status 410
    - expect: `ProblemDetail` com code `INVALID_TOKEN`
    - expect: `users.email_confirmed` permanece `false`

### 2. Reenvio de confirmação

**Setup:** idem grupo 1; mock/spy do `EmailService`.

#### 2.1. reenviar-confirmacao-invalida-anteriores-e-retorna-202

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/resend-confirmation com `{ email }` de usuário não confirmado
    - expect: status 202
    - expect: tokens `EMAIL_CONFIRMATION` anteriores do usuário ficam inválidos (consumidos/expirados)
    - expect: existe um novo token `EMAIL_CONFIRMATION` válido
    - expect: `EmailService.sendConfirmationEmail` invocado novamente
