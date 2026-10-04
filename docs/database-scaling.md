# Database Scaling

## Overview

This document describes the PostgreSQL scaling strategy: indexing, connection pooling, read replicas, high availability, and backups.

## Indexing Strategy

Indexes are created via Flyway migration `V1__performance_indexes.sql`. They are based on **actual query patterns** in the application — not speculative.

### Query Pattern Analysis

| Query Pattern | Index | Endpoint |
|--------------|-------|----------|
| Product name search (case-insensitive) | `idx_products_name` | `GET /products?search=xxx` |
| SKU search | `idx_products_sku` | `GET /products?search=xxx` |
| Active + stock level filter | `idx_products_active_quantity` | Dashboard stock alerts |
| Sale orders by status | `idx_sale_orders_status` | `GET /sales?status=PAID` |
| Sale orders by customer | `idx_sale_orders_customer` | Sales report filter |
| Sale orders by date | `idx_sale_orders_created_at` | Sales listing (sorted) |
| Stock movements by product | `idx_stock_movements_product` | Stock history |
| Users by tenant | `idx_users_tenant` | Multi-tenant filtering |
| Payments by order reference | `idx_payments_order_reference` | Payment lookup |

### Index Maintenance

- Indexes are created with `CREATE INDEX IF NOT EXISTS` — safe to re-run
- Monitor index usage with `pg_stat_user_indexes`
- Remove unused indexes to reduce write overhead

## Connection Pool (HikariCP)

### Configuration

| Setting | Default | Env Var |
|---------|---------|---------|
| maximum-pool-size | 10 | `DB_POOL_MAX` |
| minimum-idle | 2 | `DB_POOL_MIN` |
| connection-timeout | 10000ms | — |
| idle-timeout | 300000ms | — |
| max-lifetime | 900000ms | — |
| leak-detection-threshold | 60000ms | — |

### Sizing

```
Total connections = N API instances × DB_POOL_MAX
```

- 3 instances × 10 = 30 connections
- PostgreSQL default `max_connections` = 100
- Leaves 70 connections for migrations, admin tools, etc.

### Monitoring

Expose HikariCP metrics via Micrometer:
- `hikaricp.connections.active` — currently active connections
- `hikaricp.connections.idle` — idle connections
- `hikaricp.connections.pending` — pending connection requests
- `hikaricp.connections.max` — maximum pool size
- `hikaricp.connections.min` — minimum pool size

## Read Replicas

### Architecture

```
                  ┌──────────────┐
                  │ PostgreSQL   │
                  │   PRIMARY    │
                  └──────┬───────┘
                         │
                  streaming replication
                         │
             ┌───────────┴───────────┐
             ↓                       ↓
      PostgreSQL Replica 1    PostgreSQL Replica 2
```

### Routing Strategy

The `ReadRoutingDataSource` routes queries based on transaction read-only status:

| Operation | Routing | Reason |
|-----------|---------|--------|
| INSERT/UPDATE/DELETE | Primary | Writes must go to primary |
| `@Transactional(readOnly = true)` | Replica | Read-heavy queries |
| `@Transactional` (write) | Primary | Read-after-write consistency |
| Non-transactional read | Primary | Safety default |

### Configuration

Set `READ_REPLICA_URL` to enable replica routing:
```
READ_REPLICA_URL=jdbc:postgresql://replica-host:5432/stockflow_db
READ_REPLICA_USERNAME=stockflow
READ_REPLICA_PASSWORD=secret
```

If not set, all traffic goes to primary (single-node mode).

### Read-After-Write Consistency

**Critical:** Some reads require immediate consistency after a write.

| Operation | Correctness Requirement | Routing |
|-----------|------------------------|---------|
| POST sale → GET sale | Must see the just-written sale | Primary (not readOnly) |
| POST purchase → GET purchase | Must see the just-written purchase | Primary |
| POST stock adjust → GET product | Must see updated stock | Primary |
| GET dashboard stats | Can tolerate 1-30s lag | Replica |
| GET product list | Can tolerate 1-2min lag | Replica |
| GET sales report | Can tolerate replication lag | Replica |

**Rule of thumb:** If a method writes data and then reads it back, do NOT mark it `readOnly`. Only mark read-only methods that don't follow a write.

### Replica Lag

- Monitor replication lag with `pg_stat_replication`
- If lag exceeds a threshold (e.g., 5 seconds), route reads to primary
- The application can check `pg_last_xact_replay_timestamp()` for lag

### Fallback

If the replica is unavailable:
- The routing data source falls back to primary
- Reads continue to work (with primary load increase)
- Log a warning for operational awareness

## High Availability

### Read Scaling vs High Availability

**Important:** Read replicas provide **read scaling**, NOT automatic failover.

| Feature | Read Replica | HA (Standby + Failover) |
|---------|-------------|------------------------|
| Read scaling | Yes | No |
| Automatic failover | No | Yes |
| Data redundancy | Yes (async) | Yes (sync/async) |
| Zero-downtime failover | No | Yes (with Patroni/repmgr) |

### Production HA Architecture

For production high availability, use **Patroni** or **repmgr**:

```
                    ┌──────────┐
                    │  etcd    │ (distributed consensus)
                    └────┬─────┘
                         │
          ┌──────────────┼──────────────┐
          ↓              ↓              ↓
     ┌─────────┐   ┌─────────┐   ┌─────────┐
     │ Primary │──→│ Standby │   │ Replica │
     └─────────┘   └─────────┘   └─────────┘
          │              │
          └──────────────┘
              streaming replication
```

- **Primary:** Handles all writes
- **Standby:** Synchronous replication, automatic failover via Patroni
- **Replica:** Async replication, read scaling

## Backups

### Automated Backups

```bash
# Daily full backup
pg_dump -U stockflow -d stockflow_db -F c -f /backups/stockflow_$(date +%Y%m%d).dump

# Continuous archiving (WAL-G or pgBackRest)
# Enable in postgresql.conf:
# archive_mode = on
# archive_command = 'wal-g wal-push %p'
```

### Backup Retention

| Backup Type | Retention | Storage |
|-------------|-----------|---------|
| Daily full | 30 days | S3 / NFS |
| WAL archives | 7 days | S3 |
| Weekly full | 12 weeks | S3 |

### Point-in-Time Recovery (PITR)

With WAL archiving enabled, PITR allows restoring to any point in time:
```bash
# Restore base backup
pg_restore -d stockflow_db /backups/stockflow_20261004.dump

# Replay WAL to target time
recovery_target_time = '2026-10-04 14:30:00'
```

### Restore Testing

**Critical:** Test restores regularly. A backup that hasn't been tested is not a backup.

```bash
# Monthly restore test
createdb stockflow_restore_test
pg_restore -d stockflow_restore_test /backups/stockflow_20260904.dump
# Verify row counts, run sanity checks
dropdb stockflow_restore_test
```

## Monitoring

### Key Metrics

| Metric | Warning Threshold | Critical Threshold |
|--------|------------------|-------------------|
| Active connections | 80% of max | 95% of max |
| Replication lag | 5 seconds | 30 seconds |
| Cache hit ratio | 90% | 80% |
| Disk usage | 80% | 90% |
| Long-running queries | 10 seconds | 60 seconds |

### Prometheus Queries

```promql
# Connection pool usage
hikaricp_connections_active / hikaricp_connections_max

# Replication lag (seconds)
pg_replication_lag_seconds

# Cache hit ratio
pg_stat_database_blks_hit / (pg_stat_database_blks_hit + pg_stat_database_blks_read)
```
