# Etheric — Integration Guide

Руководство для разработчиков клиентских приложений, интегрирующихся с **Etheric OAuth 2.0 / OIDC Authorization Server**.

> Краткий справочник по запуску и конфигурации сервера: [README.md](../README.md)  
> Архитектура и статус реализации: [Etheric.md](Etheric.md)

---

## 1. Обзор

Etheric реализует **Authorization Code Grant** ([RFC 6749](https://www.rfc-editor.org/info/rfc6749)) с поддержкой:

- **PKCE** ([RFC 7636](https://www.rfc-editor.org/info/rfc7636)) — рекомендуется для public clients
- **OpenID Connect** — выдача `id_token` при scope `openid`
- **Refresh tokens** — с ротацией при каждом обновлении
- **Token Introspection** ([RFC 7662](https://www.rfc-editor.org/info/rfc7662))
- **Token Revocation** ([RFC 7009](https://www.rfc-editor.org/info/rfc7009))

Базовый URL сервера в dev: `http://localhost:8080`

---

## 2. Регистрация клиента

### 2.1. Admin API

Клиенты регистрируются через Admin API (требуется ключ администратора):

```http
POST /admin/clients
X-Admin-Api-Key: <admin-key>
Content-Type: application/json
```

```json
{
  "client_name": "My Application",
  "redirect_uris": ["https://app.example.com/callback"],
  "scopes": ["openid", "profile", "email"],
  "grant_types": ["authorization_code", "refresh_token"]
}
```

**Ответ `201`:**

```json
{
  "client_id": "client-…",
  "client_secret": "…",
  "client_name": "My Application",
  "redirect_uris": ["https://app.example.com/callback"],
  "scopes": ["openid", "profile", "email"],
  "grant_types": ["authorization_code", "refresh_token"],
  "enabled": true
}
```

`client_secret` возвращается **один раз** — сохраните его на стороне клиента (secret manager, env).

### 2.2. Обязательные поля клиента

| Поле | Описание |
|------|----------|
| `client_name` | Отображаемое имя на экране consent |
| `redirect_uris` | Whitelist URI для редиректов после authorize/logout |
| `scopes` | Разрешённые scope (по умолчанию `openid`, `profile`, `email`) |
| `grant_types` | По умолчанию `authorization_code`, `refresh_token` |

---

## 3. Authorization Code Flow

### 3.1. Шаг 1 — перенаправление пользователя

```http
GET /authorize?response_type=code
    &client_id={client_id}
    &redirect_uri={encoded_redirect_uri}
    &state={random_state}
    &scope=openid&scope=profile
    &code_challenge={pkce_challenge}
    &code_challenge_method=S256
    &nonce={random_nonce}
```

| Параметр | Обязательно | Описание |
|----------|-------------|----------|
| `response_type` | да | Только `code` |
| `client_id` | да | Id зарегистрированного клиента |
| `redirect_uri` | да | Должен быть в whitelist клиента |
| `state` | да | Случайная строка; проверьте при callback |
| `scope` | нет | Повторяемые query-параметры |
| `code_challenge` | рекомендуется | PKCE challenge |
| `code_challenge_method` | нет | `S256` (default) или `plain` |
| `nonce` | для OIDC | Включается в `id_token` при scope `openid` |

**Успех:** редирект на `{redirect_uri}?code=…&state=…`

**Ошибка (до выдачи кода):** редирект на `{redirect_uri}?error=…&error_description=…&state=…`

### 3.2. Шаг 2 — login и consent

Браузер проходит HTML-страницы `/login` и `/consent` на том же origin, что и authorization server.  
При повторном входе consent может быть пропущен («remember consent»).

### 3.3. Шаг 3 — обмен кода на токены

```http
POST /token
Content-Type: application/x-www-form-urlencoded
```

**Confidential client** (server-side, без PKCE на `/authorize`):

```
grant_type=authorization_code
&code={authorization_code}
&redirect_uri={same_as_authorize}
&client_id={client_id}
&client_secret={client_secret}
```

**Public client** (SPA, mobile — PKCE обязателен; `client_secret` **не передаётся**):

```
grant_type=authorization_code
&code={authorization_code}
&redirect_uri={same_as_authorize}
&client_id={client_id}
&code_verifier={pkce_verifier}
```

> Etheric определяет режим по auth code: если при `/authorize` был передан `code_challenge`, секрет **не требуется** (как public client в Keycloak). Без PKCE — `client_secret` **обязателен**.

**Аутентификация confidential client** — form-параметры **или** Basic Auth:

```http
Authorization: Basic base64(client_id:client_secret)
```

**Ответ `200`:**

```json
{
  "access_token": "eyJ…",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJ…",
  "scope": "openid profile",
  "id_token": "eyJ…"
}
```

`id_token` — только при scope `openid`.

### 3.4. PKCE (рекомендуется)

1. Сгенерируйте `code_verifier` (43–128 unreserved chars)
2. `code_challenge = BASE64URL(SHA256(code_verifier))` для `S256`
3. Передайте `code_challenge` на `/authorize`
4. Передайте `code_verifier` на `/token`

Public clients (SPA, mobile) **должны** использовать PKCE и не хранить `client_secret` в клиентском коде.

---

## 4. Refresh Token

```http
POST /token
Content-Type: application/x-www-form-urlencoded
```

```
grant_type=refresh_token
&refresh_token={refresh_token}
&client_id={client_id}
```

`client_secret` **не обязателен** (если передан — проверяется). Public clients (SPA) обновляют токены только с `client_id`, как в Keycloak.

Старый refresh-токен **отзывается** при выдаче нового (rotation).  
При scope `openid` в ответе также возвращается новый `id_token`.

---

## 5. JWT Access Token

Access-токены — подписанные JWT (RS256 по умолчанию, алгоритм из `etheric.jwt.algorithm`).

### 5.1. Публичные ключи

```http
GET /.well-known/jwks.json
```

Используйте для offline-верификации подписи на resource server.

### 5.2. Типичные claims

| Claim | Описание |
|-------|----------|
| `iss` | Issuer (`etheric.jwt.issuer`) |
| `sub` | User id (UUID) |
| `aud` | `client_id` |
| `exp` / `iat` | Время истечения / выдачи |
| `scope` | Пробел-разделённые scope |
| `roles` | Роли пользователя из PostgreSQL |

При scope `profile` может присутствовать `preferred_username`.

### 5.3. ID Token (OIDC)

При scope `openid` claim `nonce` из authorize попадает в `id_token`.  
Проверяйте `iss`, `aud`, `exp` и `nonce` на клиенте.

---

## 6. Introspection и Revocation

### 6.1. Introspection — `POST /introspect`

Проверка активности токена (RFC 7662). Требуется client auth.

```
token={access_or_refresh_token}
&token_type_hint=access_token
```

**Активный токен:**

```json
{
  "active": true,
  "scope": "openid profile",
  "client_id": "test-client",
  "username": "user",
  "token_type": "Bearer",
  "exp": 1735689600,
  "sub": "b0000000-0000-0000-0000-000000000001"
}
```

**Неактивный:** `{ "active": false }`

### 6.2. Revocation — `POST /revoke`

Отзыв access или refresh токена (RFC 7009):

```
token={token}
&token_type_hint=refresh_token
```

Успех: `200` (даже если токен уже недействителен).

> **Public clients (SPA):** `/introspect` и `/revoke` нельзя вызывать из браузера — нужен `client_secret`. В dev см. [`examples/spa-demo`](../examples/spa-demo/README.md#dev-only-introspection--revocation-proxy): Vite middleware проксирует запросы с `test-client:secret` на сервере. В production — backend/BFF.

---

## 7. Logout

```http
GET /logout?redirect_uri={optional}
```

Удаляет сессию (cookie `SESSIONID`).  
`redirect_uri` принимается **только** если зарегистрирован у клиента; иначе редирект на `/`.

---

## 8. Формат ошибок

### Token / Admin / Introspection / Revocation (JSON)

```json
{
  "error": "invalid_grant",
  "error_description": "Authorization code expired or invalid"
}
```

| `error` | Типичная причина |
|---------|------------------|
| `invalid_request` | Отсутствует обязательный параметр |
| `invalid_client` | Неверные client credentials |
| `invalid_grant` | Неверный/истёкший code или refresh token |
| `unsupported_grant_type` | Неподдерживаемый `grant_type` |
| `access_denied` | Пользователь отклонил consent |
| `unauthorized` | Admin API без ключа |
| `not_found` | Admin: ресурс не найден |
| `conflict` | Admin: дубликат username/client_id |

### Authorize (redirect)

Ошибки передаются query-параметрами `error` и `error_description` на `redirect_uri`.

---

## 9. Безопасность — рекомендации интегратору

1. **Всегда используйте `state`** — проверяйте совпадение при callback.
2. **PKCE** — обязателен для public clients (SPA, native apps).
3. **`client_secret`** — только на server-side; никогда в браузере или mobile bundle.
4. **HTTPS** — все redirect URI и token requests только по TLS в production.
5. **Короткий TTL access token** — используйте refresh для продления сессии.
6. **JWKS caching** — кешируйте ключи, но учитывайте будущую ротацию ключей.
7. **Не логируйте** tokens, codes, secrets.

---

## 10. Пример полного потока (curl)

### Confidential client (`test-client`)

```bash
# 1. Authorize (откройте в браузере после login/consent)
open "http://localhost:8080/authorize?response_type=code&client_id=test-client&redirect_uri=http://localhost:8080/callback&state=xyz&scope=openid"

# 2. Token exchange (client_secret обязателен — PKCE не использовался)
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE_FROM_CALLBACK" \
  -d "redirect_uri=http://localhost:8080/callback" \
  -d "client_id=test-client" \
  -d "client_secret=secret"
```

### Public client + PKCE (`test-client`, SPA demo redirect)

```bash
# 1. Authorize с PKCE (code_challenge — BASE64URL(SHA256(code_verifier)))
open "http://localhost:8080/authorize?response_type=code&client_id=test-client&redirect_uri=http://localhost:5173/callback&state=xyz&scope=openid&code_challenge=CHALLENGE&code_challenge_method=S256"

# 2. Token exchange (без client_secret)
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE_FROM_CALLBACK" \
  -d "redirect_uri=http://localhost:5173/callback" \
  -d "client_id=test-client" \
  -d "code_verifier=YOUR_CODE_VERIFIER"

# 3. Refresh token (public client — без client_secret)
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=REFRESH_TOKEN_FROM_STEP_2" \
  -d "client_id=test-client"
```

Introspection и revoke для public client — только server-side. См. [SPA demo README](../examples/spa-demo/README.md#dev-only-introspection--revocation-proxy) или curl ниже (confidential client).

### Общие шаги после получения access token (confidential client)

```bash
# 3. Introspect (требует client_secret — server-side only)
curl -s -X POST http://localhost:8080/introspect \
  -u "test-client:secret" \
  -d "token=ACCESS_TOKEN"

# 4. Revoke refresh token (требует client_secret — server-side only)
curl -s -X POST http://localhost:8080/revoke \
  -u "test-client:secret" \
  -d "token=REFRESH_TOKEN"
```

---

## 11. Нагрузочное тестирование (k6)

Это **не** `./mvnw test` (юнит/интеграционные тесты). Нагрузка — отдельный прогон **k6** против уже запущенного Etheric.

Сценарий: [`scripts/loadtest/etheric.k6.js`](../scripts/loadtest/etheric.k6.js). Обёртки: [`scripts/macos/loadtest.sh`](../scripts/macos/loadtest.sh), [`scripts/windows/loadtest.ps1`](../scripts/windows/loadtest.ps1). Нужен **Docker Desktop** (образ `grafana/k6`).

Запускайте скрипты **из корня репозитория** (`Etheric`), не из `examples/spa-demo`. Файла `scripts/windows/run.ps1` нет: Windows-скрипт называется `loadtest.ps1`.

### 11.1. Подготовка

Нужно:

1. **Docker Desktop** запущен.
2. **Etheric** слушает `http://localhost:8080` (dev seed: клиент `test-client` / `secret`, пользователь `user` / `password`).
3. Rate limit **выключен** — иначе цифры будут про лимиты, а не про производительность.

**Терминал 1 — сервер:**

```bash
# macOS / Linux
./scripts/macos/dev.sh --no-rate-limit
```

```powershell
# Windows — из корня репозитория
.\scripts\windows\dev.ps1 -DisableRateLimit
```

Дождитесь старта. Проверка:

```bash
curl http://localhost:8080/health/live
```

```powershell
Invoke-WebRequest http://localhost:8080/health/live -UseBasicParsing
```

### 11.2. Запуск

**Терминал 2 — нагрузка** (Etheric уже работает). По умолчанию: сценарий `all`, 10 VU, 30 секунд.

```bash
# macOS / Linux — из корня репозитория
./scripts/macos/loadtest.sh
./scripts/macos/loadtest.sh --scenario refresh --vus 20 --duration 1m
./scripts/macos/loadtest.sh --scenario public --vus 30 --duration 30s
```

```powershell
# Windows — из корня репозитория
.\scripts\windows\loadtest.ps1
.\scripts\windows\loadtest.ps1 -Scenario refresh -Vus 20 -Duration 1m
.\scripts\windows\loadtest.ps1 -Scenario public -Vus 30 -Duration 30s
.\scripts\windows\loadtest.ps1 -Scenario introspect
```

| Параметр (Windows / macOS) | По умолчанию | Смысл |
|----------------------------|--------------|--------|
| `-Scenario` / `--scenario` | `all` | См. §11.3 |
| `-Vus` / `--vus` | `10` | Виртуальные пользователи |
| `-Duration` / `--duration` | `30s` | Длительность (`30s`, `1m`, …) |
| `-BaseUrl` / `--base-url` | `http://host.docker.internal:8080` | URL Etheric **из контейнера k6** |

k6 без Docker (скрипт установлен на хосте, Etheric на localhost):

```bash
k6 run scripts/loadtest/etheric.k6.js -e BASE_URL=http://localhost:8080
```

```powershell
k6 run scripts\loadtest\etheric.k6.js -e BASE_URL=http://localhost:8080
```

### 11.3. Сценарии

| `SCENARIO` | Эндпоинты | Назначение |
|------------|-----------|------------|
| `all` | refresh, introspect, authorize, jwks, health | Stress-mix |
| `refresh` | `POST /token` (grant_type=refresh_token) | Token endpoint + rotation |
| `introspect` | `POST /introspect` | RFC 7662 |
| `authorize` | `GET /authorize` | Старт OAuth (Redis state) |
| `public` | `GET /.well-known/jwks.json`, `GET /health/live` | Baseline без OAuth |

Перед тестами скрипт проходит полный OAuth flow (dev seed: `test-client` / `secret`, `user` / `password`) и выдаёт **отдельные** refresh-токены каждому VU.

### 11.4. Переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `BASE_URL` | `http://host.docker.internal:8080` | URL сервера из Docker |
| `VUS` | `10` | Виртуальные пользователи |
| `DURATION` | `30s` | Длительность |
| `SCENARIO` | `all` | См. таблицу выше |
| `CLIENT_ID`, `CLIENT_SECRET` | `test-client`, `secret` | Dev OAuth client |
| `USERNAME`, `PASSWORD` | `user`, `password` | Dev-пользователь для setup OAuth flow |

### 11.5. Типичные ошибки

| Симптом | Причина | Что сделать |
|---------|---------|-------------|
| `Имя ".\scripts\windows\run.ps1" не распознано` | Такого файла нет; команда из `spa-demo` или неверное имя | Из корня Etheric: `.\scripts\windows\loadtest.ps1` |
| `WARN: http://localhost:8080 not reachable` | Сервер не запущен | Терминал 1: `.\scripts\windows\dev.ps1 -DisableRateLimit` |
| `ERROR: Docker cannot reach …` | Docker не видит хост | Docker Desktop должен работать; на Windows k6 ходит на `http://host.docker.internal:8080` |
| Много `429` | Rate limit включён | Перезапустите сервер с `-DisableRateLimit` / `--no-rate-limit` |

### 11.6. Интерпретация результатов

Пример хорошего dev-прогона (`SCENARIO=all`, VUs=10, 30s):

| Метрика | Типичное значение |
|---------|-------------------|
| `failed` | < 5% |
| `429 hits` | 0 (при `-DisableRateLimit`) |
| `token_refresh` failed | < 5% |
| `p95` | 0.5–2 s в `quarkus:dev`, 2–4 s под refresh-only на 20 VU |

**Важно:**

- С включённым rate limit (`etheric.rate-limit.enabled=true`) `/token` и `/authorize` быстро получают **429** — это ожидаемо, не показатель производительности.
- `quarkus:dev` даёт **завышенный p95**; для prod-like цифр используйте packaged JAR (`-Pprod`).
- `SCENARIO=all` — stress-mix; для сравнимых метрик refresh используйте `-Scenario refresh`.
- Exit code k6 ≠ 0 может означать превышение порога **latency** (`p95 > 3s`), а не падение сервера.

---

## 12. Rate limiting

На `/authorize`, `/login`, `/token`, POST `/consent` действует rate limiting (Redis).  
При превышении лимита: `429` с `error=temporarily_unavailable`.

---

## 13. Связанные документы

| Документ | Содержание |
|----------|------------|
| [README.md](../README.md) | Запуск, конфигурация, Admin API, production |
| [Etheric.md](Etheric.md) | Архитектура, статус реализации |
| [scripts/loadtest/](../scripts/loadtest/) | k6-сценарий (`etheric.k6.js`) |
| [scripts/macos/](../scripts/macos/) | Запуск и load test (macOS/Linux) |
| [scripts/windows/](../scripts/windows/) | Запуск и load test (Windows) |
| [.env.example](../.env.example) | Переменные окружения для deployment |
