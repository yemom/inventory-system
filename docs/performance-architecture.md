# Performance Architecture

## Overview

This document describes the performance architecture of the StockFlow Inventory & Business Transaction System. It covers caching, rate limiting, database optimization, connection pooling, read replicas, and horizontal scaling.

## Architecture Diagram

```
CLIENT
  ↓
NEXT.JS (React Query cache)
  ↓
RATE LIMITING (Redis token bucket)
  ↓
LOAD BALANCER / REVERSE PROXY
  ↓
SPRING BOOT API (stateless, horizontally scalable)
  ↓
CACHE (Redis, cache-aside)
  ↓
POSTGRESQL PRIMARY (writes + transactional reads)
  ↓
POSTGRESQL READ REPLICAS (read-only queries)
```

## Components

### 1. Rate Limiting (Redis Token Bucket)

**Location:** `com.inventory.backend.ratelimit.RateLimiterService`

**Algorithm:** Token bucket with atomic Lua script execution in Redis.

**Key features:**
- Distributed across all API instances via Redis
- Different limits for different endpoint categories
- Fails open when Redis is unavailable (availability over strict enforcement)
- Returns standard `Retry-After` header

**Endpoint categories:**

| Category | Default Capacity | Default Window | Env Var |
|----------|-----------------|----------------|---------|
| Login | 5 | 60s | `RATE_LIMIT_LOGIN`, `RATE_LIMIT_LOGIN_WINDOW` |
| Signup | 3 | 3600s | `RATE_LIMIT_SIGNUP`, `RATE_LIMIT_SIGNUP_WINDOW` |
| API (general) | 120 | 60s | `RATE_LIMIT_API`, `RATE_LIMIT_API_WINDOW` |
| Reports | 20 | 60s | `RATE_LIMIT_REPORTS`, `RATE_LIMIT_REPORTS_WINDOW` |

**Rate limit key format:**
- Login/Signup: `rate:{type}:ip:{clientIp}`
- API/Reports: `rate:{type}:user:{username}` (falls back to IP if unauthenticated)

**Response when limited:**
```json
{
  "success": false,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again in 30 seconds."
}
```
Headers: `Retry-After: 30`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`

### 2. Redis Cache (Cache-Aside)

**Location:** `com.inventory.backend.cache.CacheService`

**Pattern:** Cache-aside (lazy loading). The application checks Redis first; on miss, it fetches from PostgreSQL and stores the result.

**Cache TTLs (configurable via env vars):**

| Data Type | Default TTL | Env Var |
|-----------|-------------|---------|
| Categories | 300s (5min) | `CACHE_CATEGORY_TTL` |
| Warehouses | 300s (5min) | `CACHE_WAREHOUSE_TTL` |
| Dashboard stats | 30s | `CACHE_DASHBOARD_TTL` |
| Products | 120s (2min) | `CACHE_PRODUCT_TTL` |

**Tenant-safe keys:** All cache keys are prefixed with `tenant:{tenantId}:` to prevent cross-tenant data leakage.

**What is cached:**
- Categories (reference data, rarely changes)
- Warehouses (reference data, rarely changes)
- Dashboard summary (short TTL, frequently accessed)
- Product metadata (medium TTL)

**What is NOT cached:**
- Inventory quantities (transactional, must be accurate)
- Financial transactions (payments, sales, purchases)
- Stock movements (transactional)
- Sales/purchase orders (transactional)

**Cache invalidation:**
- Product update → `invalidateProduct(id)`
- Category update → `invalidateCategory(id)`
- Warehouse update → `invalidateWarehouse(id)`
- Dashboard → `invalidateDashboard()` (pattern-based)

**Graceful degradation:** If Redis is unavailable, the cache service logs a warning and fetches directly from the database. Redis is never a hard dependency for transactional operations.

### 3. Database Indexing

**Migration:** `V1__performance_indexes.sql` (Flyway)

Indexes are based on actual query patterns:

| Table | Index | Query Pattern Supported |
|-------|-------|------------------------|
| products | `idx_products_name` | Case-insensitive name search |
| products | `idx_products_sku` | Case-insensitive SKU search |
| products | `idx_products_active_quantity` | Dashboard stock alerts |
| products | `idx_products_category` | Filter by category |
| sale_orders | `idx_sale_orders_status` | Filter by status |
| sale_orders | `idx_sale_orders_customer` | Filter by customer |
| sale_orders | `idx_sale_orders_created_by` | Filter by seller |
| sale_orders | `idx_sale_orders_created_at` | Sort by date (DESC) |
| sale_order_items | `idx_sale_order_items_sale_order` | Join with sale orders |
| sale_order_items | `idx_sale_order_items_product` | Join with products |
| purchase_orders | `idx_purchase_orders_status` | Filter by status |
| purchase_orders | `idx_purchase_orders_created_at` | Sort by date |
| stock_movements | `idx_stock_movements_product` | Stock history by product |
| stock_movements | `idx_stock_movements_warehouse` | Filter by warehouse |
| stock_movements | `idx_stock_movements_type` | Filter by type |
| customers | `idx_customers_name` | Case-insensitive name search |
| suppliers | `idx_suppliers_company_name` | Case-insensitive company search |
| users | `idx_users_role` | Filter by role |
| users | `idx_users_status` | Filter by status |
| users | `idx_users_tenant` | Filter by tenant |
| payments | `idx_payments_order_reference` | Lookup by order reference |
| audit_logs | `idx_audit_logs_user` | Filter by user |
| audit_logs | `idx_audit_logs_timestamp` | Sort by timestamp |

### 4. Connection Pool (HikariCP)

**Configuration (env vars):**

| Setting | Default | Env Var | Description |
|---------|---------|---------|-------------|
| maximum-pool-size | 10 | `DB_POOL_MAX` | Max connections per instance |
| minimum-idle | 2 | `DB_POOL_MIN` | Min idle connections |
| connection-timeout | 10000ms | — | Wait time for connection |
| idle-timeout | 300000ms | — | Idle connection timeout |
| max-lifetime | 900000ms | — | Connection max lifetime |
| leak-detection-threshold | 60000ms | — | Leak detection |

**Sizing formula:**
```
Total connections = N API instances × DB_POOL_MAX
```
Example: 3 instances × 10 = 30 connections. PostgreSQL default `max_connections` is 100, so this leaves room for other clients.

### 5. Read Replicas

**Location:** `com.inventory.backend.config.ReadReplicaRoutingConfig`

**Strategy:** `AbstractRoutingDataSource` routes based on transaction read-only status.

- `@Transactional(readOnly = true)` → replica
- `@Transactional` (write) → primary
- Non-transactional → primary

**Configuration:**
- Set `READ_REPLICA_URL` to enable replica routing
- If not set, all traffic goes to primary (single-node mode)

**Read-after-write consistency:**
- Methods that write then read are NOT marked `readOnly`, so they read from primary
- Dashboard/reports can use `readOnly = true` for replica reads
- Transaction-critical reads (e.g., stock check after sale) always use primary

### 6. Multi-Tenancy

**Location:** `com.inventory.backend.tenant.TenantContext`, `TenantFilter`

**Model:** Tenant ID stored on `User` entity. Resolved from JWT by `TenantFilter`.

**Isolation:**
- Cache keys prefixed with `tenant:{tenantId}:`
- Rate limit keys include user/tenant context
- Database queries can be extended with `tenant_id` filter

**Default tenant:** `"default"` (single-tenant mode when no tenant ID is set)

### 7. Request ID / Correlation

**Location:** `com.inventory.backend.filter.RequestIdFilter`

Every request gets a unique `X-Request-ID` (16-char hex). Stored in MDC for structured logging and returned in response headers.

### 8. Observability

**Metrics (Micrometer + Prometheus):**
- `http.server.requests` — request count, rate, latency (p50/p95/p99)
- `hikaricp.connections` — pool usage
- `jvm.memory.used` — JVM memory
- `cache.hit/miss` — cache statistics (custom)

**Health checks:**
- `/actuator/health` — liveness + readiness
- `/actuator/health/liveness` — process running
- `/actuator/health/readiness` — database connected
- `/actuator/metrics` — Micrometer metrics
- `/actuator/prometheus` — Prometheus scrape endpoint

### 9. HTTP Compression

Enabled in `application.properties`:
```
server.compression.enabled=true
server.compression.min-response-size=1024
```

Compresses JSON responses ≥ 1KB with gzip.

### 10. Pagination

**Maximum page size:** 100 (enforced by `WebConfig`)
**Default page size:** 20

All collection endpoints support `?page=0&size=25` and return:
```json
{
  "content": [],
  "page": 0,
  "size": 25,
  "totalElements": 1000,
  "totalPages": 40
}
```

## Performance Targets

| Endpoint | Target | Baseline (measured) |
|----------|--------|---------------------|
| GET /products?size=25 | <200ms | 170ms |
| GET /dashboard/stats | <300ms | 338ms |
| GET /sales?size=25 | <300ms | 111ms |
| GET /purchases?size=25 | <300ms | 67ms |

## Environment Variables

See `.env-example` for the full list of configurable environment variables.
