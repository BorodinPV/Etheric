# Etheric

OAuth 2.0 / OIDC **Authorization Server** на Quarkus: Authorization Code + PKCE, refresh, introspection, revocation.

| Документ | Для кого |
|----------|----------|
| [docs/IntegrationGuide.md](docs/IntegrationGuide.md) | Интеграция клиентских приложений, OAuth-поток, Admin API |
| [docs/Etheric.md](docs/Etheric.md) | Архитектура и статус реализации |

---

## Быстрый старт

**Нужно:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) (PostgreSQL + Redis).

```bash
./mvnw -Pdev quarkus:dev          # Linux/macOS
.\scripts\dev.ps1                 # Windows
```

Профиль `-Pdev` поднимает `docker compose up -d --wait` автоматически. Сервер: **http://localhost:8080**

| Сервис | Адрес | Учётка |
|--------|-------|--------|
| PostgreSQL | `localhost:5432` | `etheric` / `etheric`, БД `etheric` |
| Redis | `localhost:6379` | без пароля |

При первом старте Flyway создаёт схему; `DevSeedService` (только `%dev`) сбрасывает dev-данные и создаёт клиента, пользователей и membership.

### Dev-учётки

| Назначение | Значение |
|------------|----------|
| OAuth-пользователь | `user` / `password` |
| Admin Console | `admin` / `admin` (роль `admin`) |
| OAuth-клиент | `test-client` / `secret` |
| Admin API key | `dev-admin-key` (`X-Admin-Api-Key`) |

> Пользователь должен быть **привязан к клиенту** (membership), иначе authorize/login вернёт `access_denied`. Dev seed привязывает `user` и `admin` к `test-client`.

---

## Admin Console

**http://localhost:8080/admin/console** — вход `admin` / `admin`.

- Клиенты и пользователи (CRUD, credentials, secret regeneration)
- **Membership** — какие пользователи могут авторизоваться через какого клиента
- **Server settings** — cookie OAuth-сессии, TTL токенов по умолчанию; переопределения — на вкладке Settings клиента
- **Язык:** English / Русский (cookie `ADMIN_LOCALE`)

JSON Admin API (`/admin/clients`, `/admin/users`) — заголовок `X-Admin-Api-Key` (в dev: `dev-admin-key`). Подробности — [IntegrationGuide §2](docs/IntegrationGuide.md).

---

## SPA Demo

React/Vite public client (PKCE): [examples/spa-demo](examples/spa-demo).

```powershell
.\scripts\dev.ps1          # терминал 1
.\scripts\spa-demo.ps1     # терминал 2 → http://localhost:5173
```

Вход: `user` / `password`. Dashboard: introspection, refresh, logout с revoke.

---

## Эндпоинты

| Группа | Пути |
|--------|------|
| OAuth / OIDC | `/authorize`, `/login`, `/consent`, `/token`, `/logout`, `/error` |
| Токены | `POST /introspect`, `POST /revoke`, `GET /.well-known/jwks.json` |
| Admin API | `/admin/clients`, `/admin/users` (+ `X-Admin-Api-Key`) |
| Admin Console | `/admin/console/**` (cookie `ADMIN_SESSION`, роль `admin`) |
| Health | `/health/live`, `/health/ready` (PostgreSQL + Redis) |

---

## Хранение и политики

| PostgreSQL | Redis |
|------------|-------|
| Клиенты, пользователи, membership, server settings, backup auth codes | Сессии, коды, токены, request state, consent, admin sessions |

TTL токенов и имя OAuth cookie — в **`server_settings`** (Admin Console → Server settings), с fallback на `etheric.jwt.*` / `etheric.session.*` в `application.properties`. Per-client overrides — в настройках клиента.

Прочее: PKCE (S256/plain), OIDC `id_token`, refresh rotation, remember consent, rate limit на `/authorize`/`/login`/`/token`/`/consent`, RS256 JWT + JWKS.

---

## Production

```bash
./mvnw package -Pprod -DskipTests
java -Dquarkus.profile=prod -jar target/quarkus-app/quarkus-run.jar
```

**Обязательные env** (см. [`.env.example`](.env.example)):

| Переменная | Назначение |
|------------|------------|
| `ETHERIC_DB_*` | PostgreSQL (reactive + JDBC для Flyway) |
| `ETHERIC_REDIS_URL` | Redis |
| `ETHERIC_ADMIN_API_KEY` | Admin API (≠ `change-me-admin-key`) |
| `ETHERIC_JWT_ISSUER` | Claim `iss` (публичный URL auth server) |

Опционально: `ETHERIC_CORS_ENABLED`, `ETHERIC_CORS_ORIGINS` (явные origins, не `*` с credentials).

`ProductionConfigValidator` (`prod`) завершит процесс при дефолтном admin key, localhost БД/Redis, слабом пароле БД или небезопасном CORS. Dev seed **не** работает в prod — регистрируйте клиентов/пользователей через Admin API или Console.

**Чеклист:** HTTPS, сильные секреты, JWT issuer = публичный URL, CORS только при необходимости, PEM-ключи (`keys/*.pem`) вместо эфемерной пары.

---

## Конфигурация

Основной файл: `src/main/resources/application.properties`.

| Свойство | Env | По умолчанию |
|----------|-----|--------------|
| `etheric.admin.api-key` | `ETHERIC_ADMIN_API_KEY` | `change-me-admin-key` |
| `etheric.jwt.*-lifetime` | — | access 3600 с, refresh 7 д, code 600 с, session 8 ч |
| `etheric.jwt.issuer` | `ETHERIC_JWT_ISSUER` | `etheric` |
| `etheric.session.cookie.secure` | — | `true` (`false` в `%dev`) |
| `etheric.rate-limit.*` | — | см. properties |
| `quarkus.http.cors*` | `ETHERIC_CORS_*` | выкл. в prod, localhost в `%dev` |

Native: `./mvnw package -Dnative` (GraalVM) → `target/Etheric-*-runner`.

---

## Тесты

Нужен запущенный Docker. `mvn test` поднимает compose автоматически; `-DskipDockerCompose=true` — если контainers уже up.

```bash
./mvnw test
```

Admin API key в `%test`: `test-admin-key`.
