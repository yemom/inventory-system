# Rate Limiting

## Overview

Rate limiting protects the backend from abuse, accidental request storms, and brute-force attacks. It uses a **distributed token-bucket algorithm** backed by Redis, so all API instances share the same rate-limit state.

## Architecture

```
Request → RateLimitFilter → Redis (Lua script) → Allow/Deny
```

The `RateLimitFilter` runs before the JWT authentication filter. It resolves the rate-limit category from the request path, builds a rate-limit key, and checks the token bucket in Redis.

## Algorithm: Token Bucket

Each rate-limit key has a bucket that:
- Has a **maximum capacity** (e.g., 5 tokens for login)
- Refills at a **constant rate** (e.g., 1 token per 12 seconds for login)
- Is consumed by each request (1 token per request)

When the bucket is empty, the request is denied with HTTP 429 and a `Retry-After` header.

The check-and-consume operation is **atomic** — it runs as a Lua script in Redis, so concurrent requests from multiple API instances are handled correctly.

## Rate Limit Categories

### Login (`POST /api/v1/auth/login`)
- **Default:** 5 requests per 60 seconds per IP
- **Purpose:** Prevent brute-force password attacks
- **Key:** `rate:login:ip:{clientIp}`

### Signup (`POST /api/v1/auth/register`)
- **Default:** 3 requests per 3600 seconds per IP
- **Purpose:** Prevent account creation abuse
- **Key:** `rate:signup:ip:{clientIp}`

### General API (all authenticated endpoints)
- **Default:** 120 requests per 60 seconds per user
- **Purpose:** Prevent one user from overwhelming the API
- **Key:** `rate:api:user:{username}` (falls back to IP if unauthenticated)

### Reports (`/api/v1/reports/**`)
- **Default:** 20 requests per 60 seconds per user
- **Purpose:** Protect database-heavy report queries
- **Key:** `rate:reports:user:{username}`

## Configuration

All limits are configurable via environment variables:

| Env Var | Default | Description |
|---------|---------|-------------|
| `RATE_LIMIT_LOGIN` | 5 | Login requests per window |
| `RATE_LIMIT_LOGIN_WINDOW` | 60 | Login window in seconds |
| `RATE_LIMIT_SIGNUP` | 3 | Signup requests per window |
| `RATE_LIMIT_SIGNUP_WINDOW` | 3600 | Signup window in seconds |
| `RATE_LIMIT_API` | 120 | API requests per window |
| `RATE_LIMIT_API_WINDOW` | 60 | API window in seconds |
| `RATE_LIMIT_REPORTS` | 20 | Report requests per window |
| `RATE_LIMIT_REPORTS_WINDOW` | 60 | Report window in seconds |

## Response When Limited

**HTTP Status:** `429 Too Many Requests`

**Headers:**
- `Retry-After: 30` — seconds to wait before retrying
- `X-RateLimit-Limit: 5` — the limit capacity
- `X-RateLimit-Remaining: 0` — remaining tokens

**Body:**
```json
{
  "success": false,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests. Please try again in 30 seconds."
}
```

## Multi-Tenancy

Rate limiting supports multiple dimensions:
- **IP-based** (login, signup) — protects infrastructure
- **User-based** (API, reports) — prevents one user from abusing the API
- **Tenant-aware** — cache keys include tenant ID, preventing cross-tenant interference

## Graceful Degradation

If Redis is unavailable, the rate limiter **fails open** — it logs a warning and allows the request. This ensures that rate limiting never blocks legitimate traffic when Redis is down.

## Testing

See `RateLimitIntegrationTest` for integration tests that verify:
- Login rate limit returns 429 after exceeding the limit
- `Retry-After` header is present
- Authenticated API requests are allowed under the limit
