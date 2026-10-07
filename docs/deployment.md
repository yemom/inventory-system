# Deployment Guide

StockFlow runs as four containers: **PostgreSQL**, **Redis**, **Spring Boot API**, **Next.js frontend**.

```
                       ┌──────────────────────────┐
   Browser ──────────► │  Next.js  (3005)         │
                       └────────────┬─────────────┘
                                    │  /api/v1/*  (server-side)
                       ┌────────────▼─────────────┐
   API clients ──────► │  Spring Boot (8081)      │
                       └──────┬─────────────┬─────┘
                              │             │
                  ┌───────────▼──┐   ┌──────▼───────────────┐
                  │ PostgreSQL   │   │ Redis                │
                  │ (5434)       │   │ rate limit + cache   │
                  │ primary data │   └──────────────────────┘
                  └──────────────┘
```

---

## 1. Local development

```bash
git clone https://github.com/yemom/inventory-system.git
cd inventory-system
cp .env-example .env       # then edit .env with real local values
docker compose up --build
```

| Service    | URL                            |
|------------|--------------------------------|
| Frontend   | http://localhost:3005          |
| Backend    | http://localhost:8081          |
| Health     | http://localhost:8081/actuator/health |

Default super-admin comes from `.env` (`SUPER_ADMIN_EMAIL` / `SUPER_ADMIN_PASSWORD`).

### Useful commands

```bash
docker compose up -d --build backend     # rebuild just the API
docker compose logs -f backend           # tail API logs
docker compose down                      # stop (keeps data)
docker compose down -v                   # stop and DELETE all data
docker compose ps                        # container status
```

---

## 2. Environment variables

Copy `.env-example` → `.env`. **Never commit `.env`.**

### Required in production

| Variable | Purpose | Notes |
|----------|---------|-------|
| `DATABASE_URL` | PostgreSQL connection string | A `jdbc:postgresql://…` URL **or** a `postgres://user:pw@host:5432/db` URI — both work (see below) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials | Use a dedicated least-privilege role |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection | Rate limiting degrades gracefully if unset/unreachable |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins | **Required in production.** Comma-separated exact origins the frontend is served from |
| `JWT_SECRET` | JWT signing key | **≥ 32 chars, random.** `openssl rand -hex 32`. Rotating → invalidates all sessions |
| `SUPER_ADMIN_PASSWORD` | Bootstrap admin | **≥ 12 chars.** Change immediately after first login |

`JWT_SECRET` and `SUPER_ADMIN_PASSWORD` have **no built-in defaults**. The backend
refuses to start without them (`StartupConfigValidator`) and names the missing one.
That is deliberate: both values used to live in `application.properties`, which made
them public knowledge — a published JWT signing key lets anyone mint an admin token,
and a published admin password is a working backdoor on every fresh deployment.

#### CORS: `CORS_ALLOWED_ORIGINS`

The browser refuses any cross-origin API call whose origin the server has not
allowed, and it reports that refusal as an **opaque network error** — never as a
CORS message. A deployed frontend therefore looks exactly like a dead backend:
`fetch` fails and the UI reports the API as unavailable.

This was a real failure here. The allowed-origin list was hardcoded to localhost
and private-LAN patterns, while a comment claimed production would supply a real
domain "via an environment variable" — no such variable was ever read. A deployed
frontend received `403` on its preflight with no `Access-Control-Allow-Origin`
header, and the login screen told the user to run `docker compose up --build`.

Set it to the exact origins the frontend is served from, comma-separated:

```bash
CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app,https://www.your-domain.com
```

The backend logs a warning at startup when no non-local origin is configured.

**Do not use a wildcard** such as `https://*.vercel.app`. The API allows
credentials, so a wildcard would let *any* deployment on that host make
authenticated calls. List preview deployments individually.

Verify from the command line:

```bash
curl -i -X OPTIONS "$API/api/v1/auth/login" \
  -H "Origin: https://your-frontend.vercel.app" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type"
# expect: HTTP/1.1 200 and access-control-allow-origin: https://your-frontend.vercel.app
```

#### `DATABASE_URL`: JDBC URL or URI — both are accepted

Managed platforms usually hand you a **URI**, not a JDBC URL:

```
postgres://user:password@host:5432/dbname      # Render, Heroku, Neon, Supabase, …
jdbc:postgresql://user:password@host:5432/db   # Docker Compose, local
```

`DataSourceUrlNormalizer` reconciles them at startup: it adds the `jdbc:` prefix the
PostgreSQL driver requires, and lifts any percent-encoded `user:password@` out of the
URI into `spring.datasource.username` / `spring.datasource.password`. Credentials
embedded in the connection string win over separately configured ones, because both
must belong to the same database — if you set `DATABASE_USERNAME` /
`DATABASE_PASSWORD` too, keep them in agreement or expect them to be overridden.

Without that translation the driver rejects the URL, Hibernate receives no JDBC
metadata, and the service dies with `Unable to determine Dialect without JDBC metadata
(please set 'jakarta.persistence.jdbc.url' …)` — a message that blames the URL setting
even though you configured one correctly.

If the service still will not boot, the log now names the real cause.
`DataSourceReadinessVerifier` opens one real connection before JPA starts and reports:

```
Unable to connect to PostgreSQL at jdbc:postgresql://host:5432/db.
Cause: java.net.ConnectException: Connection refused
```

followed by a short checklist (database asleep/expired, internal vs public hostname,
credentials, IP allow list, TLS). HikariCP already retries for 60 s
(`initialization-fail-timeout`) first, so a database that is merely still starting does
not trip it.

`DATABASE_URL` also carries `connectTimeout=10` and `socketTimeout=30`. The latter
matters more than it looks: a managed database that has stopped responding (a
suspended or expired instance still completes the TCP handshake, then never
replies) would otherwise block a request thread indefinitely. Enough hung requests
exhaust the connection pool, after which *every* endpoint stops answering —
including ones that never touch the database. With the socket timeout,
database-dependent requests fail within 30s instead of hanging.

#### Health checks and optional dependencies

`/actuator/health` decides whether the platform routes traffic to an instance. When
it reports `DOWN`, Render answers **503 to every request**, including ones that need
no database — so one sick dependency can take the whole site offline.

Only PostgreSQL is a hard dependency, and only it can mark the service `DOWN`. Redis
deliberately is not: the rate limiter and cache both fail open, so an unreachable
Redis is logged as a warning and reported as `UP` with `degraded: true`. Verified
with Redis unreachable:

```
GET /actuator/health  ->  200  {"status":"UP"}
WARN  Redis is unavailable (RedisConnectionFailureException) - rate limiting and
      caching are degraded. The API remains fully available.
```

Because `show-details=never` keeps the response body to `{"status":"DOWN"}`, both the
Redis and the PostgreSQL indicators log the concrete cause themselves — read the
service logs rather than the endpoint.

#### Super-admin bootstrap identity

`DataInitializer` refreshes the super-admin **password** on every start (that is how
it is rotated) but never overwrites an existing **email**. It used to overwrite both,
which meant any process starting the app without `SUPER_ADMIN_EMAIL` silently
repointed the administrator's login — two instances with different values would
fight, and a stray local run against a shared database could lock the real owner
out. A mismatch is now logged as a warning and ignored; move the address in the UI.

### Performance tuning

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_POOL_MAX` / `DB_POOL_MIN` | `10` / `2` | **Total connections = instances × `DB_POOL_MAX`.** Keep `instances × max` well under PostgreSQL `max_connections` |
| `RATE_LIMIT_LOGIN` / `_WINDOW` | `5` / `60` | Login attempts per IP per window |
| `RATE_LIMIT_SIGNUP` / `_WINDOW` | `3` / `3600` | Signups per IP per hour |
| `RATE_LIMIT_API` / `_WINDOW` | `120` / `60` | Authenticated requests per user |
| `RATE_LIMIT_REPORTS` / `_WINDOW` | `20` / `60` | Report queries per user (DB-heavy) |
| `RATE_LIMIT_ENABLED` | `true` | Set `false` to disable globally |
| `CACHE_PRODUCT_TTL` | `120` | Product cache seconds |
| `CACHE_CATEGORY_TTL` / `CACHE_WAREHOUSE_TTL` | `300` | Reference-data cache seconds |
| `CACHE_DASHBOARD_TTL` | `30` | Dashboard summary seconds |
| `APP_PERFORMANCE_INDEXES_ENABLED` | `true` | Apply indexes at startup |

### Optional — read replicas

Leave `READ_REPLICA_URL` **unset** for a single-node setup. When set, read-only
transactions are routed to the replica and everything else to the primary.

| Variable | Default | Purpose |
|----------|---------|---------|
| `READ_REPLICA_URL` | *(unset)* | Enabling this activates replica routing |
| `READ_REPLICA_USERNAME` / `READ_REPLICA_PASSWORD` | DB creds | Replica credentials |

---

## 3. Production deployment

### 3.1 Prepare the host

```bash
# Install Docker Engine + Compose v2
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker "$USER" && newgrp docker

sudo mkdir -p /opt/stockflow && sudo chown "$USER" /opt/stockflow
cd /opt/stockflow
git clone https://github.com/yemom/inventory-system.git .
```

### 3.2 Configure secrets

```bash
cp .env-example .env
chmod 600 .env            # contains DB/Redis/JWT secrets
$EDITOR .env              # set production passwords + a strong JWT_SECRET
```

### 3.3 Start the stack

```bash
docker compose up -d
docker compose ps
curl -fsS http://localhost:8081/actuator/health
```

### 3.4 Put a TLS reverse proxy in front

Run Nginx (or Caddy) on `80`/`443` and terminate TLS there.

**nginx.conf**

```nginx
upstream stockflow_api   { server 127.0.0.1:8081; }
upstream stockflow_front { server 127.0.0.1:3005; }

server {
    listen 80;
    server_name stockflow.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name stockflow.example.com;

    ssl_certificate     /etc/letsencrypt/live/stockflow.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/stockflow.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;

    # Rate-limit abuse at the edge too (defence in depth).
    limit_req_zone $binary_remote_addr zone=api:10m rate=20r/s;

    client_max_body_size 10m;

    location /api/ {
        limit_req zone=api burst=40 nodelay;
        proxy_pass http://stockflow_api;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    location / {
        proxy_pass http://stockflow_front;
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

> `X-Forwarded-For` matters: the application uses it to derive the client IP for
> per-IP rate limiting. Without it, all users share the proxy's IP.

The backend must also allow your public origin in CORS (`SecurityConfig.corsConfigurationSource()`).

---

## 4. CI/CD

Two workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `ci.yml` | push / PR to `main`, `master`, `develop` | Backend compile + `mvn verify` (Testcontainers), frontend `npm ci` + type-check + lint + build, Docker image builds, stack smoke test |
| `cd.yml` | tag `v*.*.*`, push to `main`/`master`, manual | Builds images, pushes to **GHCR** (+ Docker Hub if configured), deploys over SSH and health-checks |

### Required GitHub configuration

**Secrets** (Settings → Secrets → Actions):

| Secret | Needed for |
|--------|-----------|
| `SUPER_ADMIN_PASSWORD` | Smoke-test login |
| `GITHUB_TOKEN` | Push to GHCR (auto-provided) |
| `DOCKERHUB_TOKEN` | Optional Docker Hub push |
| `DEPLOY_SSH_KEY` | SSH deploy |

**Variables** (Settings → Secrets and variables → Actions → *Variables* tab):

| Variable | Example | Purpose |
|----------|---------|---------|
| `DEPLOY_HOST` | `203.0.113.10` | Remote host. **Leave blank to skip SSH deploy** |
| `DEPLOY_PATH` | `/opt/stockflow` | Clone location on the host |
| `IMAGE_REPO` | `ghcr.io/yemom` | Image namespace used by the deploy step |
| `DOCKERHUB_USERNAME` | `yemom` | Enable Docker Hub pushes |
| `ENABLE_GHCR` | `true` | Push to GHCR |

If `DEPLOY_HOST` is unset the deploy job is skipped and only images are published — safe on forks.

### Deploying a release

```bash
git tag v1.0.0
git push origin v1.0.0      # triggers cd.yml
```

### Verifying a deployment

```bash
curl -fsS https://stockflow.example.com/actuator/health
curl -fsS https://stockflow.example.com/api/v1/dashboard/health
```

---

## 5. Post-deployment checklist

- [ ] Change the super-admin password; delete any bootstrap accounts you don't need
- [ ] `JWT_SECRET` is long, random, and stored only in `.env` (or a secret manager)
- [ ] `.env` is `chmod 600` and not in version control
- [ ] TLS terminates correctly; `X-Forwarded-For` reaches the app
- [ ] DB user is **not** `superuser`
- [ ] Backups configured and a restore actually tested — see `docs/database-scaling.md`
- [ ] `DB_POOL_MAX × instance count` < PostgreSQL `max_connections`
- [ ] Monitoring wired to `/actuator/prometheus`

---

## 6. Monitoring & health

| Endpoint | Use |
|----------|-----|
| `/actuator/health` | Overall health (used by Docker + load balancer) |
| `/actuator/health/liveness` | Liveness — restart if this fails |
| `/actuator/health/readiness` | Readiness — remove from rotation if this fails |
| `/actuator/metrics` | Micrometer metrics |
| `/actuator/prometheus` | Prometheus scrape target |

Key signals: `hikaricp_connections_active / hikaricp_connections_max` (pool saturation),
`http_server_requests_seconds` (latency percentiles), and any rise in `429` responses.

---

## 7. Backups

```bash
# Nightly logical backup
docker compose exec -T db pg_dump -U stockflow stockflow_db | gzip > /backups/stockflow-$(date +%F).sql.gz

# Restore
gunzip -c /backups/stockflow-2026-10-04.sql.gz | docker compose exec -T db psql -U stockflow stockflow_db
```

For point-in-time recovery, configure WAL archiving (`pgBackRest` / WAL-G) — details
and retention guidance in `docs/database-scaling.md`.

---

## 8. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Unable to determine Dialect without JDBC metadata` | The DataSource could not open a connection, so Hibernate got no JDBC metadata. A **misleading symptom**, not a missing URL setting | Scroll up in the log for `Unable to connect to PostgreSQL at …` from `DataSourceReadinessVerifier` — it names the host and the real cause. Check: DB running, internal vs public hostname, credentials, IP allow list, TLS |
| `Unable to connect to PostgreSQL at jdbc:postgresql://db:5432… / UnknownHostException: db` | Hostname `db` is the Docker Compose container name, which only exists locally and cannot be resolved on Render | On Render, set `DATABASE_URL` in your Web Service's **Environment** tab to your Render PostgreSQL database's **Internal Database URL** (`postgres://stockflow:pw@dpg-…:5432/stockflow_db`). When using `render.yaml` Blueprint, `DATABASE_URL` is now linked automatically via `fromDatabase: connectionString` |
| `Driver org.postgresql.Driver claims to not accept jdbcUrl, postgres://…` | A platform URI was used verbatim as a JDBC URL | Should be fixed automatically by `DataSourceUrlNormalizer`. If it still appears, the running image predates that fix — redeploy |
| `Refusing to start: required secrets are missing or unsafe` | `JWT_SECRET` and/or `SUPER_ADMIN_PASSWORD` unset, too short, or still the old committed placeholder | `openssl rand -hex 32` for the signing key; set an admin password ≥ 12 chars |
| Login shows "Backend unavailable" and DevTools shows no response / "Network Error" | Almost always CORS: the browser got a 403 preflight with no `Access-Control-Allow-Origin`. The browser deliberately hides this | Add this frontend's exact origin to `CORS_ALLOWED_ORIGINS` and restart. Verify with the `curl -i -X OPTIONS` command in §2 |
| Every request returns 503, empty body | `/actuator/health` is `DOWN`, so the platform pulled the instance from rotation | Read the service logs — the failing component names itself. Most often the database is suspended/expired |
| Requests hang, then the whole API stops responding | A database that completed the TCP handshake but never replies; each hung request pinned a pool thread | Fixed by `socketTimeout=30`. Check the database is running |
| Admin login email changed unexpectedly | Older builds overwrote the super-admin's email on every start from `SUPER_ADMIN_EMAIL` | Now logged as a warning and ignored. Set the address in the UI |
| `relation "users" does not exist` | Schema never created | `docker compose restart backend`; Hibernate uses `ddl-auto=update` |
| One endpoint returns **500** while every other screen works, and the service reports healthy | A column added by `ddl-auto=update` to a table that **already had rows**, declared `NOT NULL`. PostgreSQL refuses the `ALTER`, Hibernate logs it as a **warning and boots anyway**, so the column silently does not exist and only the endpoints that read it fail | Search the startup log for `contains null values`. New columns on live tables must be nullable, with the legacy rows backfilled — see [Adding a column to a live table](#adding-a-column-to-a-live-table) |
| All logins fail with 429 | Per-IP login limit hit | Expected behaviour. Raise `RATE_LIMIT_LOGIN` or investigate brute force |
| Data disappeared | Someone ran `docker compose down -v` | Restore from backup; **that flag deletes the volume** |
| Frontend 404s | Backend unhealthy | `docker compose logs backend`; check DB connectivity |
| CORS errors in browser | Origin not listed in `CORS_ALLOWED_ORIGINS` | Set the exact frontend origin in the environment; do not edit `SecurityConfig` |
| Rate limits not shared across instances | Redis unreachable | Check `REDIS_*`; the limiter fails open by design |

---

## Adding a column to a live table

The schema is owned by `spring.jpa.hibernate.ddl-auto=update`. That is fine for
adding tables and nullable columns. It fails in a specific, quiet way the moment
you add a **`NOT NULL`** column to a table that already holds rows:

```sql
ALTER TABLE stock_movements ADD COLUMN status varchar(16) NOT NULL;
-- ERROR: column "status" of relation "stock_movements" contains null values
```

Hibernate treats that as a warning, not a fatal error. **The application starts,
passes its health check, and looks completely healthy** — but the column was
never created, so every endpoint that reads or writes it returns 500 at request
time and nothing else in the app is affected. It is genuinely hard to diagnose
from the outside, because the health check is green.

This is not hypothetical: it is what broke `/inventory/movements/pending` on the
deployed instance.

### The rule

1. Declare the new column **nullable**. Never `nullable = false` on a column
   added after the table already exists.
2. Decide what the legacy rows mean and **backfill them**. A column that is
   `NULL` on rows nobody will ever revisit is a silent hole — invisible to every
   filter built on that column, while the rows still exist.
3. Backfill with a `@EventListener(ApplicationReadyEvent.class)` component using
   `JdbcTemplate`, as `StockMovementStatusBackfill` does. `ApplicationReadyEvent`
   rather than `@PostConstruct`, because `ddl-auto=update` runs while the
   `SessionFactory` is built — the column may not exist yet at construction time.
4. Make the backfill idempotent, and let it log and continue on failure. It
   should never be the reason the service refuses to start.

### Why the tests did not catch it

The test profile uses `ddl-auto=create-drop`, which builds the schema **from
scratch**. A `NOT NULL` column is then created *with* the table instead of added
to a populated one, so the failing `ALTER` never runs. A green suite says
nothing about migrations.

The only way to catch this class of bug is to assert the migration itself —
`StockMovementStatusBackfillTest` checks the column is nullable and that a row
with a `NULL` status is backfilled — or to run against a database that already
has the old shape.