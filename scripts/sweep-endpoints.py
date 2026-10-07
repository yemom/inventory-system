"""
End-to-end sweep: every GET endpoint the app exposes, as every role.

The point is not to assert permissions - RolePermissionEnforcementIntegrationTest
already does that. The point is to find the class of bug where an endpoint
answers 500 for a role the tests never exercised, because the test suite drives
each endpoint as a Super Admin who holds every permission and a fresh schema.

A 403 here is usually correct. A 5xx never is.

Usage:  python sweep.py [base_url]
"""

import json
import sys
import time
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
API = f"{BASE}/api/v1"

ADMIN = ("12yemom@gmail.com", "Fekerte@zegeye1221")
ROLES = ["SUPER_ADMIN", "MANAGER", "SUPERVISOR", "CASHIER", "INVENTORY_STAFF"]

# Every GET endpoint, with the query the frontend actually sends.
GETS = [
    "/dashboard/health",
    "/dashboard/stats",
    "/products?page=0&size=100",
    "/categories",
    "/categories/roots",
    "/warehouses?page=0&size=100",
    "/inventory/movements?page=0&size=100",
    "/inventory/movements/pending?page=0&size=100",
    "/sales?page=0&size=100",
    "/sales/approvals?page=0&size=100",
    "/customers?page=0&size=100",
    "/suppliers?page=0&size=100",
    "/purchases?page=0&size=100",
    "/payments?page=0&size=100",
    "/expenses?page=0&size=100",
    "/staff?page=0&size=100",
    "/staff/stats",
    "/users?page=0&size=100",
    "/users/stats",
    "/audit-logs?page=0&size=100",
    "/reports/sales",
    "/reports/inventory",
    "/reports/inventory-summary",
    "/reports/inventory-value",
    "/reports/profit-loss",
]

PASSWORD = "SweepTest#2026"


def call(path, token=None, method="GET", body=None, retries=6):
    """
    Returns (status, parsed_body_or_text).

    Login and the report bucket are rate limited, and a sweep hits both hard.
    Backing off keeps the run honest instead of reporting a 429 as a finding:
    a 429 here is the limiter doing its job, not an endpoint being broken.
    """
    for attempt in range(retries):
        status, payload = _call_once(path, token, method, body)
        if status != 429:
            return status, payload
        time.sleep(3)
    return status, payload


def _call_once(path, token, method, body):
    url = f"{API}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read() or b"{}")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw or b"{}")
        except json.JSONDecodeError:
            return e.code, {"raw": raw.decode(errors="replace")[:300]}
    except Exception as e:  # connection refused, timeout
        return 0, {"raw": str(e)}


def login(identifier, password):
    status, body = call("/auth/login", method="POST",
                        body={"identifier": identifier, "password": password})
    if status != 200:
        raise SystemExit(f"cannot log in as {identifier}: {status} {body}")
    return (body.get("data") or body)["token"]


def ensure_staff(admin_token, role):
    """Creates the user if it does not exist; returns a fresh token for it."""
    email = f"{role.lower()}.sweep@stockflow.local"
    status, body = call("/auth/login", method="POST",
                        body={"identifier": email, "password": PASSWORD})
    if status == 200:
        return (body.get("data") or body)["token"]

    payload = {
        "username": f"{role.lower()}_sweep",
        "firstName": "Sweep", "lastName": "Tester",
        "email": email, "phone": "+251911000123",
        "employeeId": f"SWEEP-{role}",
        "dateJoined": "2026-01-05",
        "password": PASSWORD, "roleName": role,
    }
    status, body = call("/users", token=admin_token, method="POST", body=payload)
    if status not in (200, 201):
        raise SystemExit(f"cannot create {role}: {status} {body}")
    return login(email, PASSWORD)


def main():
    print(f"sweeping {BASE}\n")
    admin = login(*ADMIN)

    tokens = {}
    for role in ROLES:
        try:
            tokens[role] = admin if role == "SUPER_ADMIN" else ensure_staff(admin, role)
            print(f"  {role:16} ready")
        except SystemExit as e:
            print(f"  {role:16} FAILED: {e}")
    print()

    server_errors, unreachable = [], []
    rows = []
    for path in GETS:
        row = {"path": path}
        for role in ROLES:
            status, body = call(path, tokens.get(role))
            row[role] = status
            if status == 0:
                unreachable.append((path, role, body.get("raw")))
            elif status >= 500:
                server_errors.append((path, role, status, body))
        rows.append(row)

    width = max(len(r["path"]) for r in rows) + 2
    header = " " * width + "".join(f"{r[:11]:>13}" for r in ROLES)
    print(header)
    print("-" * len(header))
    for row in rows:
        line = f"{row['path']:<{width}}"
        for role in ROLES:
            status = row[role]
            mark = "*" if status >= 500 or status == 0 else " "
            line += f"{str(status) + mark:>13}"
        print(line)
    print("\n(* = server error or unreachable)")

    for path, role, raw in unreachable:
        print(f"UNREACHABLE {path} as {role}: {raw}")

    if server_errors:
        print(f"\n{len(server_errors)} SERVER ERROR(S):")
        for path, role, status, body in server_errors:
            msg = body.get("message") or body.get("raw", "")
            print(f"  {status} {path} as {role}: {msg}")
        return 1

    print("\nNo 5xx from any endpoint, for any role.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
