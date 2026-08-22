# Etheric SPA Demo

Minimal React + Vite single-page application demonstrating **Authorization Code + PKCE (S256)** against Etheric as a **public client** (no `client_secret` in the browser).

For the opposite pattern — secret on a backend — see [confidential-demo](../confidential-demo) (`http://localhost:5174`).

## Prerequisites

- Etheric running at `http://localhost:8080` (dev profile seeds client `test-client`)
- Node.js 18+

## Run

Из **корня репозитория**:

```bash
# Terminal 1 — Etheric (macOS/Linux)
./scripts/macos/dev.sh
```

```bash
# Terminal 2 — SPA demo (macOS/Linux)
./scripts/macos/spa-demo.sh
```

```powershell
# Terminal 1 — Etheric (Windows)
.\scripts\windows\dev.ps1
```

```powershell
# Terminal 2 — SPA demo (Windows)
.\scripts\windows\spa-demo.ps1
```

Или вручную из `examples/spa-demo` (на Windows используйте `npm.cmd`, если PowerShell блокирует `npm`):

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

## Client configuration (dev seed)

| Setting | Value |
|---------|-------|
| Auth server | `http://localhost:8080` |
| `client_id` | `test-client` |
| `redirect_uri` | `http://localhost:5173/callback` |
| Post-logout redirect | `http://localhost:5173/` |
| Scopes | `openid`, `profile`, `email` |
| Grant types | `authorization_code`, `refresh_token` |
| Secret | `secret` in dev seed (server-side only; PKCE login does not send it from the browser) |

## CORS

Dev profile enables CORS for `http://localhost:5173` (see `application.properties` `%dev.quarkus.http.cors.origins`).  
In production, set `ETHERIC_CORS_ENABLED=true` and `ETHERIC_CORS_ORIGINS` to your SPA origin.

## Test user (dev seed)

| Field | Value |
|-------|-------|
| Username | `user` |
| Password | `password` |

## Flow

1. **Login** — redirects to Etheric `/authorize` with PKCE challenge and OIDC `nonce`.
2. **Create account** — opens Etheric `/register` for client `test-client`; after signup the user is returned to the SPA home page with `?registered=1` and can log in.
3. **Callback** — validates `state`, exchanges `code` + `code_verifier` at `/token` (no secret), verifies `nonce` in `id_token`.
4. **Dashboard** — shows decoded `id_token` claims; auto-introspects via **RFC 7662** `POST /introspect` (through dev BFF); **automatically refreshes** the access token 60s before expiry; **Logout** revokes tokens via **RFC 7009** `POST /revoke` (through dev BFF), clears `sessionStorage`, redirects to Etheric `/logout?redirect_uri=http://localhost:5173/`. Logout is synchronized across duplicated tabs via `localStorage`; reloading a stale tab re-validates the session server-side.

Tokens are stored in `sessionStorage` only. Access-token refresh timing uses the OAuth `expires_in` value from `/token` (stored as `expires_at` ms), not the JWT `exp` claim — refresh is scheduled 60 seconds before that deadline.

### redirect_uri consistency

The same `redirect_uri` must be sent to `/authorize` and to `/token` (authorization code exchange). Etheric validates this server-side; a mismatch returns `invalid_grant`.

### PKCE

Only **S256** is accepted (`code_challenge_method=S256`). Plain PKCE is rejected.

### Rate limiting

Etheric rate-limits sensitive endpoints (defaults in `application.properties`): `/authorize`, `/login`, `/token` (and `/consent` on POST). Repeated failed logins or token exchanges may return `temporarily_unavailable` — the SPA maps these to user-friendly messages via `mapOAuthError`.

### Redis restart and introspection

Token/session state lives in **Redis**. If Redis is restarted (e.g. dev Docker volume wiped), active tokens may disappear from the server while the SPA still holds them in `sessionStorage`. Introspection will report `active: false` until the user logs in again. **Production should use persistent Redis** (AOF/RDB, managed service) so restarts do not invalidate sessions unexpectedly.

### Introspection / revocation (RFC 7662 / RFC 7009)

Standard Etheric endpoints:

| Endpoint | RFC | Client auth |
|----------|-----|-------------|
| `POST http://localhost:8080/introspect` | 7662 | HTTP Basic (`client_id:client_secret`) or form credentials |
| `POST http://localhost:8080/revoke` | 7009 | HTTP Basic (`client_id:client_secret`) or form credentials |

Request body (form-urlencoded): `token=...&token_type_hint=access_token` (hint optional).

The public SPA **must not** send `client_secret` from the browser. During `npm run dev` / `npm run preview`, Vite BFF proxies same-origin routes and adds Basic Auth server-side:

| SPA route (same origin) | Forwards to | Auth added by BFF |
|-------------------------|-------------|-------------------|
| `POST /api/oauth/introspect` | `POST /introspect` | Basic `test-client:secret` |
| `POST /api/oauth/revoke` | `POST /revoke` | Basic `test-client:secret` |

SPA sends only `token` (+ optional `token_type_hint`) in the body. In production, replace the Vite BFF with your backend.

## Build

```powershell
npm run build
npm run preview
```

> BFF (`/api/oauth/*`) работает при `npm run dev` и `npm run preview`. Нужен запущенный Etheric на `:8080`.
