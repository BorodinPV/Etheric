# Etheric

Высокопроизводительный **OAuth 2.0 Authorization Server** на Quarkus (Authorization Code Grant по [RFC 6749](https://www.rfc-editor.org/info/rfc6749)).

> **Интеграция клиентских приложений:** [docs/IntegrationGuide.md](docs/IntegrationGuide.md)

## Быстрый старт

### 1. Инфраструктура (PostgreSQL + Redis)

Требуется [Docker Desktop](https://www.docker.com/products/docker-desktop/) (или совместимый runtime).

**Рекомендуемый способ (dev-профиль — docker compose стартует автоматически):**

```bash
./mvnw -Pdev quarkus:dev
```

Windows (PowerShell):

```powershell
.\scripts\dev.ps1
```

Linux/macOS:

```bash
./scripts/dev.sh
```

Maven-профиль `-Pdev` выполняет `docker compose up -d --wait` на фазе `initialize` перед запуском Quarkus.

**Альтернатива — поднять контейнеры вручную:**

```bash
docker compose up -d
./mvnw quarkus:dev
```

Сервисы:

| Сервис | Порт | Учётные данные |
|--------|------|----------------|
| PostgreSQL | 5432 | `etheric` / `etheric`, БД `etheric` |
| Redis | 6379 | без пароля |

Проверка: `docker compose ps` — оба контейнера в статусе `healthy`.

### 2. Запуск сервера

```bash
./mvnw -Pdev quarkus:dev
```

Сервер: `http://localhost:8080`

При первом старте Flyway создаёт схему, `DevSeedService` добавляет dev seed (клиенты `test-client`, `spa-demo`, пользователи `user`, `admin` — если таблицы пусты).

Тестовые учётные данные (dev seed):

| Тип | Значение |
|-----|----------|
| Пользователь | `user` / `password` |
| Admin Console | `admin` / `admin` (роль `admin`) |
| Клиент | `test-client` / `secret` |
| SPA Demo клиент | `spa-demo` (public, PKCE — без secret в браузере) |
| Admin API key (dev) | `dev-admin-key` |

### SPA Demo

Пример public-клиента (Authorization Code + PKCE) на React/Vite: [examples/spa-demo](examples/spa-demo).

**Два терминала:**

```powershell
# Terminal 1 — Etheric
.\scripts\dev.ps1
```

```powershell
# Terminal 2 — SPA demo
.\scripts\spa-demo.ps1
```

Или вручную (используйте `npm.cmd`, если PowerShell блокирует `npm`):

```powershell
cd examples/spa-demo
npm.cmd install
npm.cmd approve-scripts --allow-scripts-pending   # если npm предупреждает про esbuild
npm.cmd install
npm.cmd run dev
```

Откройте [http://localhost:5173](http://localhost:5173). Вход: `user` / `password`.

### Admin Console

Keycloak-подобная веб-админка для управления клиентами и пользователями:

- URL: [http://localhost:8080/admin/console](http://localhost:8080/admin/console)
- Вход: username/password пользователя с ролью `admin` (dev seed: `admin` / `admin`)
- JSON Admin API (`/admin/clients`, `/admin/users`) по-прежнему требует заголовок `X-Admin-Api-Key`

> Клиенты и пользователи хранятся в **PostgreSQL**, сессии, коды и токены — в **Redis**.

---

## Эндпоинты

| Метод | Путь | Назначение |
|-------|------|------------|
| `GET` | `/authorize` | Authorization Endpoint |
| `GET`/`POST` | `/login` | Страница входа |
| `GET`/`POST` | `/consent` | Экран согласия |
| `POST` | `/token` | Token Endpoint |
| `POST` | `/introspect` | Token Introspection (RFC 7662) |
| `POST` | `/revoke` | Token Revocation (RFC 7009) |
| `GET` | `/logout` | Выход (удаление сессии) |
| `GET` | `/error` | Страница ошибки (HTML) |
| `GET` | `/.well-known/jwks.json` | Публичные ключи JWT |
| `POST` | `/admin/clients` | Регистрация клиента |
| `GET` | `/admin/clients` | Список клиентов |
| `GET` | `/admin/clients/{client_id}` | Клиент по id |
| `PUT` | `/admin/clients/{client_id}` | Обновление настроек клиента |
| `PUT` | `/admin/clients/{client_id}/secret` | Регенерация client secret |
| `POST` | `/admin/users` | Создание пользователя |
| `GET` | `/admin/users` | Список пользователей |
| `GET` | `/admin/users/{user_id}` | Пользователь по id |
| `PUT` | `/admin/users/{user_id}` | Обновление email/roles/enabled |
| `PUT` | `/admin/users/{user_id}/password` | Смена пароля |
| `GET` | `/admin/console` | Admin Console (redirect → Clients) |
| `GET`/`POST` | `/admin/console/login` | Вход в Admin Console |
| `GET` | `/admin/console/clients` | Список клиентов (HTML) |
| `GET` | `/admin/console/users` | Список пользователей (HTML) |
| `GET` | `/health/live`, `/health/ready` | Health checks |

### Health

| Путь | Назначение |
|------|------------|
| `/health/live` | **Liveness** — процесс жив (без проверки зависимостей) |
| `/health/ready` | **Readiness** — готов принимать трафик |

Readiness-проверки на `/health/ready`:

| Имя | Зависимость |
|-----|-------------|
| `postgresql` | PostgreSQL (запрос к БД) |
| `redis` | Redis (ping/set) |

Ответ `200` — все проверки `UP`; иначе `503` с деталями упавших проверок.

---

## Регистрация клиента (Admin API)

Все `/admin/**` запросы требуют заголовок:

```http
X-Admin-Api-Key: <etheric.admin.api-key>
```

Ключ задаётся в `application.properties` (`etheric.admin.api-key`).  
В профиле `%dev` по умолчанию: `dev-admin-key`.

### `POST /admin/clients`

Создаёт клиента, генерирует `client_id` и `client_secret`. **Секрет возвращается только в этом ответе** — сохраните его.

**Request body (JSON):**

| Поле | Обязательно | Описание |
|------|-------------|----------|
| `client_name` | да | Отображаемое имя |
| `redirect_uris` | да | Список разрешённых redirect URI |
| `scopes` | нет | По умолчанию `openid`, `profile`, `email` |
| `grant_types` | нет | По умолчанию `authorization_code`, `refresh_token` |
| `client_id` | нет | Свой id (иначе UUID с префиксом `client-`) |
| `client_description` | нет | Описание для экрана согласия |

**Пример:**

```bash
curl -s -X POST http://localhost:8080/admin/clients \
  -H "Content-Type: application/json" \
  -H "X-Admin-Api-Key: dev-admin-key" \
  -d '{
    "client_name": "My App",
    "redirect_uris": ["http://localhost:3000/callback"],
    "scopes": ["openid", "profile"],
    "grant_types": ["authorization_code", "refresh_token"]
  }'
```

**Ответ `201`:**

```json
{
  "client_id": "client-…",
  "client_secret": "…",
  "client_name": "My App",
  "redirect_uris": ["http://localhost:3000/callback"],
  "scopes": ["openid", "profile"],
  "grant_types": ["authorization_code", "refresh_token"],
  "enabled": true,
  "client_description": null
}
```

Поле `client_description` опционально (может отсутствовать или быть `null`).

**Ошибки Admin API:**

| HTTP | `error` | Когда |
|------|---------|-------|
| `401` | `unauthorized` | Нет или неверный `X-Admin-Api-Key` |
| `400` | `invalid_request` | Ошибка валидации тела запроса |
| `409` | `conflict` | `client_id` уже занят |

Пример `401`:

```json
{
  "error": "unauthorized",
  "error_description": "Missing or invalid X-Admin-Api-Key"
}
```

Пример `409`:

```json
{
  "error": "conflict",
  "error_description": "client_id already exists"
}
```

### `GET /admin/clients` / `GET /admin/clients/{client_id}`

Возвращают метаданные **без** `client_secret`.

`GET /admin/clients/{client_id}` при неизвестном id → `404`:

```json
{
  "error": "not_found",
  "error_description": "Client not found"
}
```

### `PUT /admin/clients/{client_id}`

Обновляет настройки клиента в PostgreSQL. Все поля опциональны, но хотя бы одно должно быть указано.

| Поле | Описание |
|------|----------|
| `client_name` | Отображаемое имя |
| `redirect_uris` | Список redirect URI |
| `scopes` | Разрешённые scopes |
| `grant_types` | Поддерживаемые grant types |
| `enabled` | Активен ли клиент |
| `client_description` | Описание для экрана согласия |

Ответ `200` — объект клиента **без** `client_secret`.

### `PUT /admin/clients/{client_id}/secret`

Генерирует новый `client_secret`, сохраняет bcrypt-хеш в БД. Ответ `200` содержит **новый секрет** (единственный раз, когда он виден). Старый секрет перестаёт работать сразу.

---

## Управление пользователями (Admin API)

Эндпоинты `/admin/users/**` используют тот же заголовок `X-Admin-Api-Key`.  
Пароли **никогда** не возвращаются в ответах.

### `POST /admin/users`

| Поле | Обязательно | Описание |
|------|-------------|----------|
| `username` | да | Уникальный логин |
| `password` | да | Минимум 8 символов |
| `email` | нет | Email |
| `roles` | нет | По умолчанию `["user"]` |
| `enabled` | нет | По умолчанию `true` |

**Ответ `201`:** `id`, `username`, `email`, `roles`, `enabled`, `created_at`.

### `GET /admin/users` / `GET /admin/users/{user_id}`

Список или один пользователь без password hash. Неизвестный id → `404`.

### `PUT /admin/users/{user_id}`

Обновление `email`, `roles`, `enabled` (хотя бы одно поле обязательно).

### `PUT /admin/users/{user_id}/password`

```json
{ "new_password": "new-secret123" }
```

Успех: `204 No Content`. После смены пароля старый пароль не принимается при login.

Подробнее о OAuth-потоке для клиентов: [docs/IntegrationGuide.md](docs/IntegrationGuide.md).

---

## Authorization Code Flow

### 1. Authorization — `GET /authorize`

Параметры query:

| Параметр | Обязательно | Описание |
|----------|-------------|----------|
| `response_type` | да | Только `code` |
| `client_id` | да | Id клиента |
| `redirect_uri` | да | Должен быть в whitelist клиента |
| `state` | да | CSRF-защита (обязателен) |
| `scope` | нет | Повторяемые query-параметры, напр. `scope=openid&scope=profile` |
| `code_challenge` | нет* | PKCE challenge ([RFC 7636](https://www.rfc-editor.org/info/rfc7636)) |
| `code_challenge_method` | нет | `S256` (по умолчанию) или `plain`; только вместе с `code_challenge` |
| `nonce` | нет | OIDC nonce (включается в `id_token` при scope `openid`) |

\* Если передан `code_challenge`, на `/token` обязателен `code_verifier`.

**Пример с PKCE (S256):**

Сгенерируйте `code_verifier` (43–128 символов) и `code_challenge = BASE64URL(SHA256(code_verifier))`, затем:

```
http://localhost:8080/authorize?response_type=code&client_id=test-client&redirect_uri=http://localhost:8080/callback&state=xyz&code_challenge=CHALLENGE&code_challenge_method=S256
```

На `/token` передайте тот же `code_verifier`.

**Пример без PKCE:**

```
http://localhost:8080/authorize?response_type=code&client_id=test-client&redirect_uri=http://localhost:8080/callback&state=xyz&scope=openid&scope=profile
```

При успехе браузер уходит на `/login` или `/consent`, затем редирект:

```
{redirect_uri}?code=…&state=xyz
```

При ошибке (если `redirect_uri` передан и валиден):

```
{redirect_uri}?error=invalid_request&error_description=…&state=xyz
```

Если `redirect_uri` **отсутствует или пуст**, редирект невозможен — сервер возвращает JSON `400`:

```json
{ "error": "invalid_request", "error_description": "…" }
```

### 2. Login — `GET` / `POST /login`

HTML-форма. POST: `username`, `password`, `state`, `csrf_token` (+ cookie `SESSIONID`).  
Неверный CSRF → `403`.

### 3. Consent — `GET` / `POST /consent`

HTML. POST: `action=approve|deny`, `state`, `csrf_token`.

### 4. Token — `POST /token`

`Content-Type: application/x-www-form-urlencoded`

#### `grant_type=authorization_code`

| Параметр | Обязательно |
|----------|-------------|
| `code` | да |
| `redirect_uri` | да (должен совпасть с сохранённым) |
| `client_id` | да |
| `client_secret` | да*, если на `/authorize` **не** был `code_challenge` |
| `code_verifier` | да*, если на `/authorize` был `code_challenge` |

\* Режим как в Keycloak: PKCE на authorize → public client, секрет не нужен; без PKCE → confidential client, секрет обязателен.

**Confidential client** (без PKCE):

```bash
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=http://localhost:8080/callback" \
  -d "client_id=test-client" \
  -d "client_secret=secret"
```

**Public client + PKCE** (dev client `spa-demo`):

```bash
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=http://localhost:5173/callback" \
  -d "client_id=spa-demo" \
  -d "code_verifier=YOUR_CODE_VERIFIER"
```

При неверном `client_id` или `client_secret` (когда секрет требуется) → `401` с `error=invalid_client`.  
Если `client_id` не совпадает с клиентом, выдавшим auth code → `400` с `error=invalid_grant`.  
При неверном или отсутствующем `code_verifier` (если был PKCE) → `400 invalid_grant`.

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

Поле `id_token` присутствует **только** при наличии scope `openid` (OIDC). При refresh с scope `openid` также выдаётся новый `id_token`.

Клиент может аутентифицироваться через form-параметры `client_id`/`client_secret` **или** заголовок `Authorization: Basic base64(client_id:client_secret)`.

Код одноразовый: повторный обмен → `invalid_grant`.

#### `grant_type=refresh_token`

| Параметр | Обязательно |
|----------|-------------|
| `refresh_token` | да |
| `client_id` | да (должен совпасть с клиентом refresh-токена) |
| `client_secret` | нет (если передан — проверяется) |
| `scope` | нет |

Старый refresh-токен отзывается (ротация). Неизвестный `client_id` → `401 invalid_client`.

### 5. Logout — `GET /logout`

Опционально: `?redirect_uri=…`. Удаляет сессию и очищает cookie `SESSIONID`.

| `redirect_uri` | Поведение |
|----------------|-----------|
| не передан или пуст | редирект `303` на `/` |
| зарегистрирован у клиента | редирект `303` на указанный URI |
| не зарегистрирован | редирект `303` на `/` (open redirect не допускается) |

Проверка: URI должен присутствовать в `redirect_uris` хотя бы одного клиента в PostgreSQL (`ClientRepository.isRegisteredRedirectUri()`).

### 6. JWKS — `GET /.well-known/jwks.json`

Публичный RSA-ключ для проверки подписи JWT. Алгоритм подписи задаётся в `etheric.jwt.algorithm` (по умолчанию **RS256**).

### 7. Token Introspection — `POST /introspect`

RFC 7662. Form: `token`, опционально `token_type_hint` (`access_token` / `refresh_token`). Требуется аутентификация клиента (form или Basic Auth).

**Ответ `200` (активный токен):**

```json
{
  "active": true,
  "scope": "openid profile",
  "client_id": "test-client",
  "sub": "…",
  "token_type": "Bearer",
  "exp": 1234567890,
  "iss": "etheric"
}
```

Неизвестный или истёкший токен: `{ "active": false }`.

### 8. Token Revocation — `POST /revoke`

RFC 7009. Form: `token`, опционально `token_type_hint`. Требуется аутентификация клиента. Всегда возвращает `200` (даже если токен не найден).

### 9. Error page — `GET /error`

HTML-страница ошибки. Query: `error`, `description` (опционально).

---

## Rate limiting

Эндпоинты `/authorize`, `/login`, `/token`, `/consent` (POST) защищены Redis-based rate limiter. При превышении лимита — `429`:

```json
{ "error": "temporarily_unavailable", "error_description": "Rate limit exceeded. Please try again later." }
```

Настройки: `etheric.rate-limit.*` (см. §Конfigурация).

---

## Remember consent

После одобрения consent сохраняется в Redis (`auth:consent:{userId}:{clientId}`) на срок `etheric.cache.consent-ttl-seconds` (по умолчанию 30 дней). При повторной авторизации с теми же или меньшими scope consent-страница пропускается.

---

## Формат JWT (access / refresh)

| Claim | Описание |
|-------|----------|
| `sub` | Id пользователя (UUID) |
| `groups` | Роли из PostgreSQL (`users.roles`) |
| `scopes` | Список scope |
| `iat` / `exp` | Время выдачи / истечения |
| `token_type` | Только у refresh: `refresh` |

Подпись берётся из `etheric.jwt.algorithm` (по умолчанию **RS256**). Ключи загружаются из PEM (`etheric.jwt.private-key-location` / `public-key-location`); при отсутствии файлов — эфемерная пара (WARN в логе, только dev). Заголовок JWT содержит стабильный `kid` (хеш публичного ключа).

TTL берётся из конфига (`etheric.jwt.*-lifetime`, см. §Конфигурация) и совпадает с TTL записей в Redis. По умолчанию: access **1 ч**, refresh **7 дней**, auth code **10 мин**, сессия **8 ч**, request state **10 мин**.

---

## Ошибки OAuth

JSON на Token Endpoint / admin:

```json
{ "error": "invalid_grant", "error_description": "…" }
```

Типичные коды: `invalid_request`, `unauthorized_client`, `access_denied`, `unsupported_response_type`, `invalid_scope`, `invalid_grant`, `unsupported_grant_type`, `invalid_client`, `server_error`.

На `/authorize` ошибки обычно уходят редиректом на `redirect_uri` с теми же параметрами.

---

## Безопасность (рекомендации)

1. Всегда передавайте и проверяйте **`state`**.
2. **Public clients (SPA, mobile)** — используйте **PKCE**; на **`/token`** `client_secret` **не передаётся** (если на `/authorize` был `code_challenge`).
3. **Confidential clients** — на **`/token`** `client_secret` **обязателен** (если PKCE не использовался); неверные credentials → `401 invalid_client`.
4. Храните **`client_secret`** только на server-side; не логируйте и не встраивайте в frontend bundle.
5. В production используйте **HTTPS**; cookie сессии с `Secure` (`etheric.session.cookie.secure=true`).
6. Смените **`etheric.admin.api-key`** — не оставляйте значение по умолчанию.
7. Не коммитьте секреты; `client_secret` из Admin API показывается один раз.

---

## Production

Запуск в production-режиме (внешние PostgreSQL и Redis через `ETHERIC_*`, **без** docker-compose):

```bash
./mvnw package -Pprod -DskipTests
java -Dquarkus.profile=prod -jar target/quarkus-app/quarkus-run.jar
```

У packaged JAR профиль **`prod` включён по умолчанию** (если не задан `-Dquarkus.profile`).  
Для dev используйте `./mvnw -Pdev quarkus:dev` или `./scripts/dev.ps1` / `./scripts/dev.sh` (профиль `%dev`, auto-start docker-compose).

### Обязательные переменные окружения

| Переменная | Свойство | Описание |
|------------|----------|----------|
| `ETHERIC_DB_USER` | `quarkus.datasource.username` | Пользователь PostgreSQL |
| `ETHERIC_DB_PASSWORD` | `quarkus.datasource.password` | Пароль PostgreSQL (используйте сильный пароль) |
| `ETHERIC_DB_REACTIVE_URL` | `quarkus.datasource.reactive.url` | Reactive URL PostgreSQL |
| `ETHERIC_DB_JDBC_URL` | `quarkus.datasource.jdbc.url` | JDBC URL PostgreSQL (Flyway) |
| `ETHERIC_REDIS_URL` | `quarkus.redis.hosts` | URL Redis |
| `ETHERIC_ADMIN_API_KEY` | `etheric.admin.api-key` | Ключ Admin API (**не** оставляйте `change-me-admin-key`) |
| `ETHERIC_JWT_ISSUER` | `etheric.jwt.issuer` | Claim `iss` в JWT (например `https://auth.example.com`) |

### Опциональные переменные

| Переменная | Свойство | По умолчанию | Описание |
|------------|----------|--------------|----------|
| `ETHERIC_CORS_ENABLED` | `quarkus.http.cors` | `false` | Включить CORS |
| `ETHERIC_CORS_ORIGINS` | `quarkus.http.cors.origins` | *(пусто)* | Разрешённые origins через запятую |

Пример `.env` — см. [`.env.example`](.env.example). **Не коммитьте `.env`.**

### CORS для SPA-клиентов

По умолчанию CORS **выключен**. Для SPA на другом origin (например `https://app.example.com`):

```bash
export ETHERIC_CORS_ENABLED=true
export ETHERIC_CORS_ORIGINS=https://app.example.com
```

**Никогда** не используйте `origins=*` вместе с `access-control-allow-credentials=true`.  
В профиле `%dev` CORS включён с явными localhost-origins (`8080`, `127.0.0.1:8080`, `3000`, `5173` для SPA demo).

При старте с `-Dquarkus.profile=prod` `ProductionConfigValidator` проверяет конфигурацию и **завершает процесс**, если:

- admin API key равен `change-me-admin-key`;
- CORS включён, а origins пусты или содержат `*`;
- PostgreSQL reactive URL указывает на `localhost` / `127.0.0.1`;
- Redis URL указывает на `localhost` / `127.0.0.1`;
- пароль БД равен dev-значению `etheric`.

`DevSeedService` **не выполняется** в production — клиентов и пользователей регистрируйте через Admin API или миграции.

### Чеклист перед деплоем

- [ ] Задать `ETHERIC_ADMIN_API_KEY` (длинный случайный секрет)
- [ ] Задать сильный `ETHERIC_DB_PASSWORD`
- [ ] Настроить `ETHERIC_JWT_ISSUER` на публичный URL authorization server
- [ ] Развернуть за **HTTPS**; `etheric.session.cookie.secure=true` (по умолчанию)
- [ ] Настроить CORS только при необходимости, с явными origins
- [ ] Убедиться, что dev seed не активен (профиль `prod`, без `@IfBuildProfile dev/test`)
- [ ] Хранить секреты в secret manager / env, не в репозитории

---

## Конфигурация

Ключевые свойства в `src/main/resources/application.properties`:

| Свойство | Env var | Описание | По умолчанию |
|----------|---------|----------|--------------|
| `etheric.admin.api-key` | `ETHERIC_ADMIN_API_KEY` | Ключ Admin API | `change-me-admin-key` |
| `etheric.session.cookie.secure` | — | Флаг Secure у cookie `SESSIONID` | `true` |
| `etheric.jwt.access-token-lifetime` | — | TTL access-токена (с) | `3600` |
| `etheric.jwt.refresh-token-lifetime` | — | TTL refresh-токена (с) | `604800` |
| `etheric.jwt.authorization-code-lifetime` | — | TTL auth code (с) | `600` |
| `etheric.jwt.session-lifetime` | — | TTL сессии (с) | `28800` |
| `etheric.jwt.request-state-lifetime` | — | TTL `auth:request:{state}` (с) | `600` |
| `etheric.jwt.issuer` | `ETHERIC_JWT_ISSUER` | Claim `iss` в JWT | `etheric` |
| `etheric.jwt.algorithm` | — | Алгоритм подписи JWT (JWKS `alg`) | `RS256` |
| `etheric.jwt.private-key-location` | — | Путь к PEM приватного ключа | `keys/private.pem` |
| `etheric.jwt.public-key-location` | — | Путь к PEM публичного ключа | `keys/public.pem` |
| `etheric.rate-limit.enabled` | — | Включить rate limiting | `true` |
| `etheric.rate-limit.window-seconds` | — | Окно rate limit (с) | `60` |
| `etheric.rate-limit.authorize-max` | — | Макс. запросов `/authorize` за окно | `60` |
| `etheric.rate-limit.login-max` | — | Макс. запросов `/login` за окно | `20` |
| `etheric.rate-limit.token-max` | — | Макс. запросов `/token` за окно | `30` |
| `etheric.rate-limit.consent-max` | — | Макс. POST `/consent` за окно | `20` |
| `etheric.cache.client-ttl-seconds` | — | TTL локального кэша клиентов (Caffeine) | `60` |
| `etheric.cache.consent-ttl-seconds` | — | TTL remember-consent (с) | `2592000` (30 дней) |
| `quarkus.datasource.username` | `ETHERIC_DB_USER` | PostgreSQL user | `etheric` |
| `quarkus.datasource.password` | `ETHERIC_DB_PASSWORD` | PostgreSQL password | `etheric` |
| `quarkus.datasource.reactive.url` | `ETHERIC_DB_REACTIVE_URL` | Reactive PostgreSQL URL | `postgresql://localhost:5432/etheric` |
| `quarkus.datasource.jdbc.url` | `ETHERIC_DB_JDBC_URL` | JDBC PostgreSQL URL | `jdbc:postgresql://localhost:5432/etheric` |
| `quarkus.redis.hosts` | `ETHERIC_REDIS_URL` | Redis URL | `redis://localhost:6379` |
| `quarkus.http.cors` | `ETHERIC_CORS_ENABLED` | CORS | `false` |
| `quarkus.http.cors.origins` | `ETHERIC_CORS_ORIGINS` | Allowed origins (comma-separated) | *(пусто)* |
| `quarkus.shutdown.timeout` | — | Graceful shutdown — ожидание завершения HTTP-запросов | `PT30S` |

Архитектурный документ: [`docs/Etheric.md`](docs/Etheric.md).

### Native build (GraalVM)

```bash
./mvnw package -Dnative
```

Требует установленный GraalVM. Результат: `target/Etheric-1.0-SNAPSHOT-runner`.

---

## Тесты

Требуется **Docker Desktop** (демон Docker должен быть запущен).

При `mvn test` / `mvn package` Maven **автоматически** выполняет `docker compose up -d --wait` перед тестами и **останавливает контейнеры после завершения** тестового прогона (`DockerComposeShutdownListener`, в том числе при падении тестов). Отключить: `-DskipDockerCompose=true` (если контейнеры уже подняты вручную).

```bash
./mvnw test
```

Тесты подключаются к PostgreSQL (`localhost:5432`, `etheric` / `etheric`) и Redis (`localhost:6379`). Admin API key: `test-admin-key`. В тестовом профиле `%test` cookie сессии принудительно с `Secure`: `%test.etheric.session.cookie.secure=true` (см. `src/test/resources/application.properties`).

**Типичная ошибка:** `Connection refused: localhost:5432` — Docker Desktop не запущен, либо контейнеры не успели подняться. Проверка: `docker compose ps` (оба сервиса `healthy`). Вручную: `docker compose up -d --wait`.
