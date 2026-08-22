# Etheric Confidential Demo

Node BFF + static UI that uses Etheric as a **confidential client**.  
`client_secret` lives only in the backend process. The browser never sees it and never calls `/token`, `/introspect`, or `/revoke`.

Compare with the [SPA demo](../spa-demo) (`:5173`): that app is a **public client** (Authorization Code + PKCE, no secret in the browser).

## Prerequisites

- Etheric running at `http://localhost:8080` (dev seed creates client `confidential-demo`)
- Node.js 18+

## Run

From the **repository root**:

```powershell
.\scripts\windows\confidential-demo.ps1
```

```bash
./scripts/macos/confidential-demo.sh
```

Or from this directory:

```bash
node server.js
```

Open [http://localhost:5174](http://localhost:5174).

## Client configuration (dev seed)

| Setting | Value |
|---------|-------|
| Auth server | `http://localhost:8080` |
| `client_id` | `confidential-demo` |
| `client_secret` | `confidential-secret` (BFF env only) |
| `redirect_uri` | `http://localhost:5174/callback` |
| Post-logout redirect | `http://localhost:5174/` |
| Scopes | `openid`, `profile`, `email` |
| Grant types | `authorization_code`, `refresh_token` |

Override with env: `ETHERIC_URL`, `CLIENT_ID`, `CLIENT_SECRET`, `APP_ORIGIN`, `PORT`.

## Test user (dev seed)

| Field | Value |
|-------|-------|
| Username | `user` |
| Password | `password` |

## Flow

1. **Login** — browser goes to this BFF `/login`. The BFF stores `state`/`nonce` in an HttpOnly session and redirects to Etheric `/authorize` **without PKCE**.
2. **Callback** — Etheric returns to BFF `/callback`. The BFF exchanges `code` + `client_secret` at `POST /token` (Basic or form). Tokens stay in the server session.
3. **Dashboard** — UI calls same-origin `/api/session`. Claims are decoded on the BFF; raw tokens are not sent to the page.
4. **Introspect / refresh / logout** — BFF uses the secret for RFC 7662 / RFC 7009 and refresh-token grant, then (on logout) redirects to Etheric `/logout`.

Why a BFF: a secret in JavaScript can be copied from DevTools. Confidential clients authenticate the *application*, so the secret belongs on a server you control.

PKCE is still allowed for confidential clients (defense in depth). This demo omits it on purpose so `/token` **requires** `client_secret`.