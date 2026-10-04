# Load Testing

## Overview

This document describes the load testing strategy for the StockFlow API. Load tests measure actual performance under concurrent load and identify bottlenecks.

## Tooling

Use **k6** (Grafana k6) for load testing. It's scriptable in JavaScript and produces detailed metrics.

```bash
# Install k6
brew install k6  # macOS
choco install k6  # Windows

# Run a test
k6 run tests/load/login.js
```

## Test Scenarios

### 1. Login Load Test

**File:** `tests/load/login.js`

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up to 10 users
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '1m', target: 100 },   // Ramp up to 100 users
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],    // Error rate under 1%
  },
};

export default function () {
  const payload = JSON.stringify({
    identifier: `user_${__VU}@test.com`,
    password: 'TestPass123!',
  });
  const res = http.post('http://localhost:8081/api/v1/auth/login', payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, {
    'status is 200 or 401': (r) => r.status === 200 || r.status === 401,
  });
  sleep(1);
}
```

### 2. Product Listing Load Test

**File:** `tests/load/products.js`

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get('http://localhost:8081/api/v1/products?size=25');
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  sleep(1);
}
```

### 3. Dashboard Load Test

**File:** `tests/load/dashboard.js`

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get('http://localhost:8081/api/v1/dashboard/stats');
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
  sleep(1);
}
```

### 4. Concurrent Sales Load Test

**File:** `tests/load/sales.js`

Tests that concurrent sales don't oversell stock.

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.05'],  // Allow some failures (insufficient stock)
  },
};

export default function () {
  const payload = JSON.stringify({
    customerName: 'Load Test Customer',
    discount: 0,
    tax: 0,
    paymentMethod: 'CASH',
    paymentStatus: 'PAID',
    idempotencyKey: `load-${__VU}-${__ITER}`,
    items: [{ productId: 1, quantity: 1, unitPrice: 100, discount: 0 }],
  });
  const res = http.post('http://localhost:8081/api/v1/sales', payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, {
    'status is 201 or 400': (r) => r.status === 201 || r.status === 400,
  });
  sleep(1);
}
```

## Metrics to Report

For every load test, report:

| Metric | Description |
|--------|-------------|
| Requests/sec | Throughput |
| Average latency | Mean response time |
| p50 latency | Median response time |
| p95 latency | 95th percentile |
| p99 latency | 99th percentile |
| Error rate | % of failed requests |
| 429 rate | % of rate-limited requests |
| DB CPU | Database CPU usage |
| DB connections | Active database connections |
| Cache hit ratio | Redis cache hit rate |

## Running Load Tests

```bash
# 10 users
k6 run --vus 10 --duration 30s tests/load/login.js

# 50 users
k6 run --vus 50 --duration 1m tests/load/products.js

# 100 users
k6 run --vus 100 --duration 1m tests/load/dashboard.js

# 250 users
k6 run --vus 250 --duration 2m tests/load/sales.js

# 500 users
k6 run --vus 500 --duration 3m tests/load/products.js
```

## Interpreting Results

### Good Results
- p95 latency under target
- Error rate < 1%
- No 429s (rate limiting not triggered)
- DB connections stable

### Warning Signs
- p95 latency degrading with load → bottleneck
- Error rate > 1% → investigate errors
- 429s appearing → rate limiting working, but may need tuning
- DB connections maxed out → increase pool or optimize queries
- Cache hit ratio < 80% → review cache strategy

## Continuous Load Testing

Integrate load tests into CI:

```yaml
# .github/workflows/load-test.yml
name: Load Test
on:
  schedule:
    - cron: '0 2 * * *'  # Daily at 2 AM
jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Start services
        run: docker compose up -d --build
      - name: Wait for health
        run: sleep 60
      - name: Run k6
        run: |
          docker run --rm -v $(pwd)/tests/load:/tests \
            grafana/k6 run /tests/login.js
```
