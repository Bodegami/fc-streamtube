---
subproject: backend
runner: junit+mockmvc+testcontainers
scope: phase-02-user-auth
si: SI-02.6
target_file: backend/src/test/java/com/fcstreamtube/auth/AuthRegisterE2ETest.java
---

# POST /api/auth/register Test Plan

## Application Overview

Endpoint de cadastro de usuário. Recebe nome, e-mail e senha; persiste o usuário com a senha hasheada (BCrypt), cria a conta com `email_confirmed = false`, emite um token opaco de confirmação e dispara o e-mail de confirmação. Erros seguem RFC 7807 `ProblemDetail`.

## Test Scenarios

### 1. Cadastro de usuário

**Setup:** `beforeEach` truncar `users` e `user_tokens`; subir o módulo Spring com PostgreSQL via Testcontainers; mock/spy do `EmailService`.

#### 1.1. cadastro-com-corpo-valido-retorna-201

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/register com body `{ name, email, password }` válido
    - expect: status 201
    - expect: body contém `{ id (uuid), email, name }` e NÃO contém senha/hash
    - expect: registro em `users` com `password_hash` diferente da senha em texto puro (prefixo `{bcrypt}`)
    - expect: `users.email_confirmed = false`

#### 1.2. cadastro-dispara-token-e-email-de-confirmacao

**Covers AC:** #4
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/register com body válido
    - expect: existe um registro em `user_tokens` com `type = EMAIL_CONFIRMATION` para o `user_id` criado
    - expect: `EmailService.sendConfirmationEmail` é invocado com o e-mail do usuário e o token emitido

### 2. Erros de cadastro

**Setup:** idem grupo 1.

#### 2.1. cadastro-com-email-duplicado-retorna-409

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/register com um e-mail já existente
    - expect: status 409
    - expect: `ProblemDetail` com `detail`/code correspondente a `EMAIL_ALREADY_EXISTS`
    - expect: nenhum segundo registro em `users` para o mesmo e-mail

#### 2.2. cadastro-com-corpo-invalido-retorna-400

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/register com `email` inválido e `password` curta
    - expect: status 400
    - expect: `Content-Type: application/problem+json`
    - expect: `ProblemDetail` com extensão `invalidFields` contendo entradas para `email` e `password`
