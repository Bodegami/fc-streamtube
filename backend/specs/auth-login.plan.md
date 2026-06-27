---
subproject: backend
runner: junit+mockmvc+testcontainers
scope: phase-02-user-auth
si: SI-02.8
target_file: backend/src/test/java/com/fcstreamtube/auth/AuthSessionE2ETest.java
---

# POST /api/auth/login + logout + GET /api/me Test Plan

## Application Overview

Autenticação com emissão de JWT em cookie `httpOnly` (`access_token`, `Secure`, `SameSite=Strict`), encerramento de sessão (expiração do cookie) e endpoint de sessão atual `/api/me`. Sessão stateless — o token é lido do cookie, nunca do header `Authorization`.

## Test Scenarios

### 1. Login

**Setup:** `beforeEach` truncar `users`; subir módulo Spring + PostgreSQL Testcontainers; seed de um usuário com senha conhecida (hash BCrypt).

#### 1.1. login-com-credenciais-validas-seta-cookie

**Covers AC:** #1
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/login com `{ email, password }` corretos
    - expect: status 200
    - expect: body `{ id, email, name }`
    - expect: header `Set-Cookie` para `access_token` com atributos `HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/`

#### 1.2. login-com-senha-incorreta-retorna-401

**Covers AC:** #2
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/login com senha incorreta
    - expect: status 401
    - expect: `ProblemDetail` com code `INVALID_CREDENTIALS`
    - expect: nenhum cookie `access_token` setado

### 2. Sessão e logout

**Setup:** idem grupo 1; obter o cookie `access_token` via login válido.

#### 2.1. me-com-cookie-valido-retorna-usuario

**Covers AC:** #3
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. GET /api/me enviando o cookie `access_token` válido
    - expect: status 200 com `{ id, email, name }`
  2. GET /api/me sem cookie
    - expect: status 401 com code `UNAUTHENTICATED`

#### 2.2. logout-expira-cookie

**Covers AC:** #4
**Source:** auto
**Last sync:** 2026-06-27T00:18:26Z

**Steps:**
  1. POST /api/auth/logout autenticado (cookie válido)
    - expect: status 204
    - expect: header `Set-Cookie` para `access_token` com `Max-Age=0` (expiração)
