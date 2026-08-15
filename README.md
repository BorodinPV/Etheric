# Etheric

Высокопроизводительный **OAuth 2.0 Authorization Server** на Quarkus (Authorization Code Grant по [RFC 6749](https://www.rfc-editor.org/info/rfc6749)).

## Быстрый старт

### 1. Инфраструктура (PostgreSQL + Redis)

Требуется [Docker Desktop](https://www.docker.com/products/docker-desktop/) (или совместимый runtime).

```bash
docker compose up -d
```

Сервисы:

| Сервис | Порт | Учётные данные |
|--------|------|----------------|
| PostgreSQL | 5432 | `etheric` / `etheric`, БД `etheric` |
| Redis | 6379 | без пароля |

Проверка: `docker compose ps` — оба контейнера в статусе `healthy`.

### 2. Запуск сервера

```bash
./mvnw quarkus:dev
```

Сервер: `http://localhost:8080`

При первом старте Flyway создаёт схему, `DevSeedService` добавляет тестового клиента и пользователя (если таблицы пусты).

Тестовые учётные данные (dev seed):

| Тип | Значение |
|-----|----------|
| Пользователь | `user` / `password` |
| Клиент | `test-client` / `secret` |
| Admin API key (dev) | `dev-admin-key` |

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
| `client_logo` | нет | URL логотипа |
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
  "client_logo": null,
  "client_description": null
}
```

Поля `client_logo` и `client_description` опциональны (могут отсутствовать или быть `null`).

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
| `client_secret` | да |
| `code_verifier` | да*, если на `/authorize` был `code_challenge` |

\* Без PKCE на authorize — `code_verifier` не требуется.

```bash
curl -s -X POST http://localhost:8080/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=AUTH_CODE" \
  -d "redirect_uri=http://localhost:8080/callback" \
  -d "client_id=test-client" \
  -d "client_secret=secret" \
  -d "code_verifier=YOUR_CODE_VERIFIER"
```

При неверном `client_id` или `client_secret` → `401` с `error=invalid_client`.  
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
2. На **`/token`** (grant `authorization_code`) **`client_secret` обязателен** и проверяется; неверные credentials → `401 invalid_client`.
3. Храните **`client_secret`** только на сервере клиента; не логируйте его.
4. В production используйте **HTTPS**; cookie сессии с `Secure` (`etheric.session.cookie.secure=true`).
5. Смените **`etheric.admin.api-key`** — не оставляйте значение по умолчанию.
6. Не коммитьте секреты; `client_secret` из Admin API показывается один раз.

---

## Конфигурация

Ключевые свойства в `src/main/resources/application.properties`:

| Свойство | Описание | По умолчанию |
|----------|----------|--------------|
| `etheric.admin.api-key` | Ключ Admin API | `change-me-admin-key` |
| `etheric.session.cookie.secure` | Флаг Secure у cookie `SESSIONID` | `true` |
| `etheric.jwt.access-token-lifetime` | TTL access-токена (с) | `3600` |
| `etheric.jwt.refresh-token-lifetime` | TTL refresh-токена (с) | `604800` |
| `etheric.jwt.authorization-code-lifetime` | TTL auth code (с) | `600` |
| `etheric.jwt.session-lifetime` | TTL сессии (с) | `28800` |
| `etheric.jwt.request-state-lifetime` | TTL `auth:request:{state}` (с) | `600` |
| `etheric.jwt.issuer` | Claim `iss` в JWT | `etheric` |
| `etheric.jwt.algorithm` | Алгоритм подписи JWT (JWKS `alg`) | `RS256` |
| `etheric.jwt.private-key-location` | Путь к PEM приватного ключа | `keys/private.pem` |
| `etheric.jwt.public-key-location` | Путь к PEM публичного ключа | `keys/public.pem` |
| `etheric.rate-limit.enabled` | Включить rate limiting | `true` |
| `etheric.rate-limit.window-seconds` | Окно rate limit (с) | `60` |
| `etheric.rate-limit.authorize-max` | Макс. запросов `/authorize` за окно | `60` |
| `etheric.rate-limit.login-max` | Макс. запросов `/login` за окно | `20` |
| `etheric.rate-limit.token-max` | Макс. запросов `/token` за окно | `30` |
| `etheric.rate-limit.consent-max` | Макс. POST `/consent` за окно | `20` |
| `etheric.cache.client-ttl-seconds` | TTL локального кэша клиентов (Caffeine) | `60` |
| `etheric.cache.consent-ttl-seconds` | TTL remember-consent (с) | `2592000` (30 дней) |
| `quarkus.datasource.*` | PostgreSQL (клиенты, пользователи) | см. файл |
| `quarkus.redis.hosts` | Redis (сессии, коды, токены) | `redis://localhost:6379` |
| `quarkus.shutdown.timeout` | Graceful shutdown — ожидание завершения HTTP-запросов | `PT30S` |

Архитектурный документ: [`docs/Etheric.md`](docs/Etheric.md).

### Native build (GraalVM)

```bash
./mvnw package -Dnative
```

Требует установленный GraalVM. Результат: `target/Etheric-1.0-SNAPSHOT-runner`.

---

## Тесты

Требуется **Docker Desktop** (демон Docker должен быть запущен).

При `mvn test` / `mvn package` Maven **автоматически** выполняет `docker compose up -d --wait` перед тестами (см. `exec-maven-plugin` в `pom.xml`). Отключить: `-DskipDockerCompose=true` (если контейнеры уже подняты вручную).

```bash
./mvnw test
```

Тесты подключаются к PostgreSQL (`localhost:5432`, `etheric` / `etheric`) и Redis (`localhost:6379`). Admin API key: `test-admin-key`. В тестовом профиле `%test` cookie сессии принудительно с `Secure`: `%test.etheric.session.cookie.secure=true` (см. `src/test/resources/application.properties`).

**Типичная ошибка:** `Connection refused: localhost:5432` — Docker Desktop не запущен, либо контейнеры не успели подняться. Проверка: `docker compose ps` (оба сервиса `healthy`). Вручную: `docker compose up -d --wait`.
