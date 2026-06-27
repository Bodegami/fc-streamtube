---
status: Validated
phase: 02
slug: user-auth
generated: 2026-06-26
---

# Screen Inventory — Phase 02: User Auth

## Summary

| # | Screen | Route | Figma node |
|---|--------|-------|------------|
| 1 | Create user account | `/signup` | `140:333` |
| 2 | User login | `/login` | `138:179` |
| 3 | Reset password | `/forgot-password` | `140:289` |
| 4 | Set new password | `/reset-password` | `58987:2044` |

---

## Screen: Create user account

**Route:** `/signup`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=140-333

### Component inventory

| Component | Type | In DS? | Reuse? |
|-----------|------|--------|--------|
| Auth page layout (full-page dark bg + centering) | Presentational | ✗ | `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts (new)` |
| Auth card (white card container) | Presentational | ✗ | `frontend/src/app/shared/components/auth-card/auth-card.component.ts (new)` |
| Brand logo (icon + "StreamTube" wordmark) | Presentational | ✗ | `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts (new)` |
| Back navigation button (←) | Local-interactive | ✗ | `new` |
| Screen title ("Create account") | Presentational | ✗ | `new` |
| Screen subtitle ("Join the community and start sharing.") | Presentational | ✗ | `new` |
| Full Name field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| Email address field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| Password field + visibility toggle (MatFormField + MatInput + MatIconButton) | Local-interactive | ✗ | `new` |
| Password strength indicator | Local-interactive | ✗ | `frontend/src/app/shared/components/password-strength/password-strength.component.ts (new)` |
| Confirm Password field + visibility toggle (MatFormField + MatInput + MatIconButton) | Local-interactive | ✗ | `new` |
| Terms of Service checkbox (MatCheckbox) | Local-interactive | ✗ | `new` |
| "Create account" submit button (MatButton) | Server-connected | ✗ | `new` |
| Auth footer ("Already have an account?" + "Sign in" link) | Presentational | ✗ | `frontend/src/app/shared/components/auth-footer/auth-footer.component.ts (new)` |

### Verbs of intent

| Verb | Capability |
|------|------------|
| registrar novo usuário com nome, email e senha | "Cadastro de usuário com e-mail e senha" |

### Observations

- Password strength indicator shown inline below the password field ("Weak password: Add numbers and symbols.").
- "Terms of Service" and "Privacy Policy" are inline links. Their destinations are out of scope for Phase 02.
- On success, user is redirected directly to `/` (Home — Catalog Show) already logged in. No email confirmation screen exists (confirmed by user).
- Back arrow (←) navigates to `/login`.
- The `AuthFooterComponent` is configurable — it receives the message text and link target as inputs (reused across Login, Reset password screens with different copy).

---

## Screen: User login

**Route:** `/login`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=138-179

### Component inventory

| Component | Type | In DS? | Reuse? |
|-----------|------|--------|--------|
| Auth page layout | Presentational | ✗ | `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts (new)` |
| Auth card | Presentational | ✗ | `frontend/src/app/shared/components/auth-card/auth-card.component.ts (new)` |
| Brand logo | Presentational | ✗ | `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts (new)` |
| Screen title ("Sign in") | Presentational | ✗ | `new` |
| Email address field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| Password field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| "Forgot password?" inline link | Local-interactive | ✗ | `new` |
| "Sign in" submit button (MatButton) | Server-connected | ✗ | `new` |
| Auth footer ("Don't have an account?" + "Sign up" link) | Presentational | ✗ | `frontend/src/app/shared/components/auth-footer/auth-footer.component.ts (new)` |

### Verbs of intent

| Verb | Capability |
|------|------------|
| autenticar usuário com email e senha | "Login e controle de sessão do usuário" |

### Observations

- ✓ ~~OQ-04~~ Password field placeholder fixed in Figma on 2026-06-26: now reads "Enter your password".
- JWT stored as httpOnly cookie (TD-01, Option B). Angular holds no token client-side — auth state is determined via a `/api/me` call on app init (returns user data or 401).
- "Forgot password?" link appears right-aligned, inline with the "Password" label row. Navigates to `/forgot-password`.
- Auth footer "Sign up" link navigates to `/signup`.

---

## Screen: Reset password

**Route:** `/forgot-password`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=140-289

### Component inventory

| Component | Type | In DS? | Reuse? |
|-----------|------|--------|--------|
| Auth page layout | Presentational | ✗ | `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts (new)` |
| Auth card | Presentational | ✗ | `frontend/src/app/shared/components/auth-card/auth-card.component.ts (new)` |
| Brand logo | Presentational | ✗ | `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts (new)` |
| Back navigation button (←) | Local-interactive | ✗ | `new` |
| Screen title ("Reset password") | Presentational | ✗ | `new` |
| Screen subtitle ("Enter your email and we'll send you a reset link") | Presentational | ✗ | `new` |
| Email address field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| "Send reset link" submit button (MatButton) | Server-connected | ✗ | `new` |
| Auth footer ("Remember your password?" + link) | Presentational | ✗ | `frontend/src/app/shared/components/auth-footer/auth-footer.component.ts (new)` |
| Success state body (inline swap — replaces form area) | Presentational | ✗ | `new` |

### Verbs of intent

| Verb | Capability |
|------|------------|
| solicitar link de reset de senha por email | "Recuperação de senha: solicitação via e-mail → link com token → redefinição" |

### Observations

- This screen has **2 UI states** under the same `/forgot-password` route:
  1. **Form state** (default): email input + "Send reset link" button.
  2. **Success state**: card body swaps to a confirmation message ("You should receive the email shortly."). The form is hidden; card shell (logo + back arrow) remains visible.
  State is toggled via a local boolean in the Angular component — no route change.
- ✓ ~~OQ-03~~ Auth footer link fixed in Figma on 2026-06-26: now reads "Remember your password? **Sign in**".
- Back arrow (←) navigates to `/login`.
- After the success state appears, no automatic redirect occurs — user must click "Sign in" in the footer or use the back arrow.

---

## Screen: Set new password

**Route:** `/reset-password`
**Figma:** https://www.figma.com/design/zqPFL1161k3PlcEjtNrJzS/FC-Tube?node-id=58987-2044

### Component inventory

| Component | Type | In DS? | Reuse? |
|-----------|------|--------|--------|
| Auth page layout | Presentational | ✗ | `frontend/src/app/shared/components/auth-layout/auth-layout.component.ts (new)` |
| Auth card | Presentational | ✗ | `frontend/src/app/shared/components/auth-card/auth-card.component.ts (new)` |
| Brand logo | Presentational | ✗ | `frontend/src/app/shared/components/brand-logo/brand-logo.component.ts (new)` |
| Screen title ("Set new password") | Presentational | ✗ | `new` |
| New password field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| Confirm password field (MatFormField + MatInput) | Local-interactive | ✗ | `new` |
| "Reset password" submit button (MatButton) | Server-connected | ✗ | `new` |

### Verbs of intent

| Verb | Capability |
|------|------------|
| redefinir senha usando token recebido por email | "Recuperação de senha: solicitação via e-mail → link com token → redefinição" |

### Observations

- Screen is reached via the password-reset email link, which carries a one-time token as a query param (e.g., `/reset-password?token=abc123`). The Angular component must read and validate the token on init (TD-03: opaque token stored in DB).
- ✓ ~~OQ-02~~ Invalid/expired token: redirect to `/forgot-password` + `MatSnackBar` "Link expirado. Solicite um novo." No additional Figma frame required.
- ✓ ~~OQ-05~~ Placeholders fixed manually in Figma on 2026-06-26: "New password" → "Enter new password", "Confirm password" → "Confirm new password".
- ✓ ~~OQ-01~~ Visibility toggle deferred to implementation: both fields will use `MatIconButton matSuffix` following the "Create account" pattern. No Figma update required.
- No footer and no back link by design — this screen is an entry point from email.

---

## Validation

| Check | Status |
|-------|--------|
| 1. Every screen has `**Route:**` below `## Screen:` | ✓ |
| 2. Every screen has `**Figma:**` with full URL + `node-id` | ✓ |
| 3. All `Type` cells filled | ✓ |
| 4. All `Reuse?` values follow the 3 canonical forms | ✓ |
| 5. Verbs of intent tables present on all screens | ✓ |
| 6. `### Observations` exists on all screens | ✓ |
| 7. `## Open questions` heading exists | ✓ |

---

## Reconciliation summary

| Phase 02 capability | Screen | Verb |
|--------------------|--------|------|
| Cadastro de usuário com e-mail e senha | Create user account (`/signup`) | registrar novo usuário com nome, email e senha |
| Login e controle de sessão do usuário | User login (`/login`) | autenticar usuário com email e senha |
| Logout | — (no dedicated screen; header action, Phase 07 scope) | — |
| Recuperação de senha — solicitação | Reset password (`/forgot-password`) | solicitar link de reset de senha por email |
| Recuperação de senha — redefinição | Set new password (`/reset-password`) | redefinir senha usando token recebido por email |
| Serviço de envio de e-mails transacionais | — (backend only, no screen) | — |
| Confirmação de conta via e-mail | — (no screen; direct redirect to Home on signup) | — |
| Criação automática do canal do usuário | — (backend only, no screen) | — |

**Screens out of scope (confirmed):**

- Logout: no dedicated screen. Logout is a header/nav action (Phase 07 scope).
- Email confirmation: user confirmed no dedicated screen — on successful signup the user lands on Home already authenticated.

---

## Open questions

- ~~**OQ-01:** "Set new password" — should password fields include a visibility toggle (eye icon)?~~ ✓ **Resolved** — deferred to implementation. The "Create account" screen already establishes the pattern (`MatIconButton matSuffix`). Implementer adds the toggle to both fields without a separate Figma frame.
- ~~**OQ-02:** "Set new password" — what does the UI show when the reset token is expired or invalid?~~ ✓ **Resolved** — on invalid/expired token, redirect to `/forgot-password` and display a `MatSnackBar` with the message "Link expirado. Solicite um novo." No additional Figma frame required.
- ~~**OQ-03:** "Reset password" — auth footer reads "Sign up" instead of "Sign in".~~ ✓ **Resolved** — fixed manually in Figma on 2026-06-26.
- ~~**OQ-04:** "User login" — password field placeholder reads "Enter your email".~~ ✓ **Resolved** — fixed manually in Figma on 2026-06-26 (corrected to "Enter your password").
- ~~**OQ-05:** Figma MCP rate limit hit before updating "Set new password" placeholders. Both fields still show "Enter your email" in Figma. Intended: "Enter new password" / "Confirm new password".~~ ✓ **Resolved** — placeholders fixed manually in Figma on 2026-06-26.
