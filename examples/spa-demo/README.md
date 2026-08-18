# Etheric SPA Demo

Minimal React + Vite single-page application demonstrating **Authorization Code + PKCE (S256)** against Etheric as a **public client** (no `client_secret` in the browser).

## Prerequisites

- Etheric running at `http://localhost:8080` (dev profile seeds client `test-client`)
- Node.js 18+

## Run

Из **корня репозитория**:

```powershell
# Terminal 1 — Etheric
.\scripts\dev.ps1
```

```powershell
# Terminal 2 — SPA demo
.\scripts\spa-demo.ps1
```

Или вручную из `examples/spa-demo` (используйте `npm.cmd`, если PowerShell блокирует `npm`):

```powershell
npm.cmd install
npm.cmd approve-scripts --allow-scripts-pending   # if npm warns about esbuild scripts
npm.cmd install
npm.cmd run dev
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
| Secret | Random hash in DB — **not used in browser** (PKCE public client) |

## CORS

Dev profile enables CORS for `http://localhost:5173` (see `application.properties` `%dev.quarkus.http.cors.origins`).  
In production, set `ETHERIC_CORS_ENABLED=true` and `ETHERIC_CORS_ORIGINS` to your SPA origin.

## Test user (dev seed)

| Field | Value |
|-------|-------|
| Username | `user` |
| Password | `password` |

## Flow

1. **Login** — redirects to Etheric `/authorize` with PKCE challenge.
2. **Callback** — validates `state`, exchanges `code` + `code_verifier` at `/token` (no secret).
3. **Dashboard** — shows decoded `id_token` claims; auto-introspects the access token via a dev-only Vite proxy; **automatically refreshes** the access token 60s before expiry (and on load if already expired); **Logout** revokes tokens, clears `sessionStorage`, redirects to Etheric `/logout?redirect_uri=http://localhost:5173/` (home page, not `/callback`). If the refresh token is expired or revoked, tokens are cleared and the user is sent back to the home page to log in again.

Tokens are stored in `sessionStorage` only.

### Dev-only introspection / revocation proxy

The public SPA cannot call `/introspect` or `/revoke` directly (both require `client_secret`). During `npm run dev`, Vite middleware exposes:

| Route | Forwards to | Auth |
|-------|-------------|------|
| `POST /api/demo/introspect` | `POST http://localhost:8080/introspect` | Basic `test-client:secret` |
| `POST /api/demo/revoke` | `POST http://localhost:8080/revoke` | Basic `test-client:secret` |

Request body (JSON): `{ "token": "...", "token_type_hint": "access_token" }` (hint optional).

The secret lives only in `vite.config.js` (Node dev server) — never in browser bundles. Production builds have no proxy; use a backend for introspection/revocation in real deployments.

## Build

```powershell
npm run build
npm run preview
```

> Dev proxy (`/api/demo/*`) работает **только** при `npm run dev`. В `preview` и production-сборке introspection/revoke из SPA недоступны — нужен backend/BFF.
