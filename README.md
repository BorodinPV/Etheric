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
| `GET` | `/logout` | Выход (удаление сессии) |
| `GET` | `/.well-known/jwks.json` | Публичные ключи JWT |
| `POST` | `/admin/clients` | Регистрация клиента |
| `GET` | `/admin/clients` | Список клиентов |
| `GET` | `/admin/clients/{client_id}` | Клиент по id |
| `GET` | `/health/live`, `/health/ready` | Health checks |

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
  "enabled": true
}
```

Ошибки: `401` (нет/неверный ключ), `400` (валидация), `409` (`client_id` уже занят).

### `GET /admin/clients` / `GET /admin/clients/{client_id}`

Возвращают метаданные **без** `client_secret`.

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
| `nonce` | нет | Зарезервировано под OIDC |

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

При ошибке (если `redirect_uri` валиден):

```
{redirect_uri}?error=invalid_request&error_description=…&state=xyz
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
  "scope": "openid profile"
}
```

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

Опционально: `?redirect_uri=…`. Удаляет сессию и очищает cookie.

### 6. JWKS — `GET /.well-known/jwks.json`

Публичный RSA-ключ для проверки подписи JWT (алгоритм **RS256**).

---

## Формат JWT (access / refresh)

| Claim | Описание |
|-------|----------|
| `sub` | Id пользователя (UUID) |
| `groups` | Роли из PostgreSQL (`users.roles`) |
| `scopes` | Список scope |
| `iat` / `exp` | Время выдачи / истечения |
| `token_type` | Только у refresh: `refresh` |

Подпись **RS256**. Ключи загружаются из PEM (`etheric.jwt.private-key-location` / `public-key-location`); при отсутствии файлов — эфемерная пара (WARN в логе, только dev). Заголовок JWT содержит стабильный `kid` (хеш публичного ключа).

TTL берётся из конфига (`etheric.jwt.access-token-lifetime`, `refresh-token-lifetime`) и совпадает с TTL записей в Redis. По умолчанию: access **1 ч**, refresh **7 дней**, auth code **10 мин**.

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

- `etheric.admin.api-key` — ключ Admin API  
- `etheric.session.cookie.secure` — флаг Secure у `SESSIONID`  
- `etheric.jwt.*` — TTL токенов, issuer, пути ключей  
- PostgreSQL — `quarkus.datasource.*` (клиенты, пользователи)  
- Redis — `quarkus.redis.hosts` (сессии, коды, токены)

Архитектурный документ: [`docs/Etheric.md`](docs/Etheric.md).

---

## Тесты

Требуется **Docker**. Dev Services **отключены** в `src/test/resources/application.properties` — перед запуском тестов поднимите инфраструктуру:

```bash
docker compose up -d
./mvnw test
```

Тесты подключаются к PostgreSQL (`localhost:5432`, `etheric` / `etheric`) и Redis (`localhost:6379`). Admin API key в тестовом профиле: `test-admin-key` (`%test.etheric.admin.api-key`).
