---
libs:
  spring-security-oauth2-resource-server:
    version: "6.5.x"
    context7_id: "/websites/spring_io_spring-security_reference_6_5"
    fetched_at: "2026-06-26T23:04:25Z"
  "@angular/material":
    version: "22.x"
    context7_id: "/websites/material_angular_dev"
    fetched_at: "2026-06-26T23:04:25Z"
sources_mtime:
  docs/decisions/technical-decisions-user-auth.md: "2026-06-26T18:31:14-0300"
---

# phase-02-user-auth — Library Refs

---

## spring-security-oauth2-resource-server

**Decided in:** user-auth/TD-01 (Auth Strategy & Token Transport)
**Use in this phase:**
- Custom `BearerTokenResolver` to extract JWT from `HttpOnly` cookie instead of `Authorization` header
- `JwtDecoder` bean configuration (symmetric HMAC-SHA256 key)
- `SecurityFilterChain` with `.oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` + `SessionCreationPolicy.STATELESS`
- `SameSite=Strict` cookie via `ResponseCookie` on the login endpoint; `withCredentials: true` on Angular `HttpClient`

**Context7 docs:**

### SecurityFilterChain — JWT resource server (baseline)

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(authorize -> authorize
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
    return http.build();
}
```

### JwtDecoder with custom public key

```java
@Bean
public JwtDecoder jwtDecoder() {
    // For HMAC-SHA256 symmetric key use NimbusJwtDecoder.withSecretKey(secretKey).build()
    return NimbusJwtDecoder.withPublicKey(publicKey()).build();
}
```

### Custom BearerTokenResolver — extract JWT from httpOnly cookie

`BearerTokenResolver` is a strategy interface (`resolve(HttpServletRequest) → String`).  
For cookie-based extraction (user-auth/TD-01 Option B), implement as a lambda or dedicated class:

```java
@Bean
public BearerTokenResolver cookieBearerTokenResolver() {
    return request -> {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    };
}

// Wire into SecurityFilterChain:
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(Customizer.withDefaults())
    .bearerTokenResolver(cookieBearerTokenResolver())
);
```

Wire with `.bearerTokenResolver(cookieBearerTokenResolver())` inside `.oauth2ResourceServer(...)`.  
Source: https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/bearer-tokens.html

### SessionCreationPolicy.STATELESS

```java
http.sessionManagement(sm -> sm
    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
);
```

`STATELESS` — Spring Security never creates or uses an HTTP session for the SecurityContext.  
Source: https://docs.spring.io/spring-security/reference/6.5/api/java/org/springframework/security/config/http/SessionCreationPolicy.html

### ResponseCookie — set httpOnly + SameSite=Strict on login endpoint

```java
ResponseCookie cookie = ResponseCookie.from("access_token", jwtToken)
    .httpOnly(true)
    .secure(true)
    .sameSite("Strict")
    .path("/")
    .maxAge(Duration.ofHours(1))
    .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
```

---

## @angular/material

**Decided in:** user-auth/TD-05 (Angular UI Component Library)
**Use in this phase:**
- `MatFormFieldModule` + `MatInputModule` — email/password fields on all 4 auth screens
- `MatButtonModule` — submit buttons (`"Create account"`, `"Sign in"`, `"Send reset link"`, `"Reset password"`)
- `MatSnackBarModule` — error feedback (invalid credentials, expired token redirect)
- `MatIconModule` + `MatIconButton` — password visibility toggle (`matSuffix` pattern)
- All imports standalone (no `NgModule`); Angular 22 LTS

**Context7 docs:**

### MatFormField — appearance variants

```html
<!-- outline is the standard for auth screens -->
<mat-form-field appearance="outline">
  <mat-label>Email</mat-label>
  <input matInput type="email" formControlName="email" required>
  <mat-error>Please enter a valid email.</mat-error>
</mat-form-field>

<!-- Password field with visibility toggle (matSuffix pattern) -->
<mat-form-field appearance="outline">
  <mat-label>Password</mat-label>
  <input matInput [type]="hide ? 'password' : 'text'" formControlName="password">
  <button mat-icon-button matSuffix (click)="hide = !hide" type="button">
    <mat-icon>{{ hide ? 'visibility_off' : 'visibility' }}</mat-icon>
  </button>
</mat-form-field>
```

`matInput` is a directive that integrates native `<input>` with `<mat-form-field>`. Requires `MatInputModule` imported in the standalone component.  
Source: https://material.angular.dev/components/input/overview

### MatButton — standalone import

```typescript
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    ReactiveFormsModule,
  ],
})
export class SignupComponent { }
```

### MatSnackBar — programmatic usage for error feedback

```typescript
constructor(private snackBar: MatSnackBar) {}

showError(message: string) {
  this.snackBar.open(message, 'Close', {
    duration: 4000,
    panelClass: ['error-snackbar'],
  });
}

// Usage on auth error (e.g., 401 from /api/auth/login):
// this.showError('Invalid credentials. Please try again.');
// On expired token redirect from /reset-password:
// this.showError('Link expirado. Solicite um novo.');
```

Source: https://material.angular.dev/components/form-field/examples  
Source: https://material.angular.dev/components/button/api
