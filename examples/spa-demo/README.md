# Etheric SPA Demo

Minimal React + Vite single-page application demonstrating **Authorization Code + PKCE (S256)** against Etheric as a **public client** (no `client_secret` in the browser).

## Prerequisites

- Etheric running at `http://localhost:8080` (dev profile seeds client `spa-demo`)
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
| `client_id` | `spa-demo` |
| `redirect_uri` | `http://localhost:5173/callback` |
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
3. **Dashboard** — shows decoded `id_token` claims; **Refresh token** rotates tokens (no `client_secret`); **Logout** clears session storage and redirects to Etheric `/logout?redirect_uri=http://localhost:5173/callback`.

Tokens are stored in `sessionStorage` only.

## Build

```powershell
npm run build
npm run preview
```
