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
| `DATABASE_URL` | JDBC URL | e.g. `jdbc:postgresql://db:5432/stockflow_db` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials | Use a dedicated least-privilege role |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis connection | Rate limiting degrades gracefully if unset/unreachable |
| `JWT_SECRET` | JWT signing key | **Long random string.** Rotate → invalidates all sessions |
| `SUPER_ADMIN_PASSWORD` | Bootstrap admin | Change immediately after first login |

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
| `relation "users" does not exist` | Schema never created | `docker compose restart backend`; Hibernate uses `ddl-auto=update` |
| All logins fail with 429 | Per-IP login limit hit | Expected behaviour. Raise `RATE_LIMIT_LOGIN` or investigate brute force |
| Data disappeared | Someone ran `docker compose down -v` | Restore from backup; **that flag deletes the volume** |
| Frontend 404s | Backend unhealthy | `docker compose logs backend`; check DB connectivity |
| CORS errors in browser | Origin not allowed | Add the public origin to `SecurityConfig.corsConfigurationSource()` |
| Rate limits not shared across instances | Redis unreachable | Check `REDIS_*`; the limiter fails open by design |