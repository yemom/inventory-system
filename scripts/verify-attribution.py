"""
End-to-end check of the cashier/branch attribution, against a running stack.

Creates a cashier with a branch, rings up a sale, and asserts the API returns
`cashier {id, name}` and `branch {name}` as objects. Then repeats for a cashier
with a blank branch, and for a cashier with a blank name, because those are the
cases the UI has to survive.

Usage:  python verify-attribution.py [base_url]
"""

import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081").rstrip("/")
API = f"{BASE}/api/v1"
ADMIN = ("12yemom@gmail.com", "Fekerte@zegeye1221")
PASSWORD = "AttributionE2E#2026"

failures = []


def call(path, token=None, method="GET", body=None):
    req = urllib.request.Request(
        f"{API}{path}",
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
    )
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


def check(label, condition, detail=""):
    mark = "PASS" if condition else "FAIL"
    print(f"  [{mark}] {label}" + (f"  -> {detail}" if detail and not condition else ""))
    if not condition:
        failures.append(label)


def login(identifier, password):
    status, body = call("/auth/login", method="POST",
                        body={"identifier": identifier, "password": password})
    if status != 200:
        raise SystemExit(f"login failed for {identifier}: {status} {body}")
    return body.get("data", body)


def ensure_cashier(admin_token, tag, first, last, branch):
    """Creates a CASHIER with the given profile, or reuses one."""
    tag_id = uuid.uuid4().hex[:6]
    email = f"e2e.{tag}.{tag_id}@stockflow.local"
    employee_id = f"E2E-{tag}-{tag_id.upper()}"
    status, body = call("/users", token=admin_token, method="POST", body={
        "username": f"e2e_{tag}_{uuid.uuid4().hex[:6]}",
        "firstName": first, "lastName": last, "email": email,
        "phone": "+251911000999", "employeeId": employee_id,
        "dateJoined": "2026-01-05", "password": PASSWORD, "roleName": "CASHIER",
    })
    if status not in (200, 201):
        raise SystemExit(f"cannot create cashier {tag}: {status} {body}")

    # The staff endpoint is what the UI uses; it is what carries `branch`.
    users = call("/users?page=0&size=100", token=admin_token)[1]
    content = (users.get("data") or {}).get("content") or []
    uid = next((u["id"] for u in content if u.get("email") == email), None)

    detail = call(f"/staff/{uid}", token=admin_token)[1].get("data", {}) if uid else {}
    if branch is not None and not detail.get("branch"):
        call(f"/staff/{uid}", token=admin_token, method="PUT", body={
            "firstName": first, "lastName": last, "email": email,
            "phone": "+251911000999", "employeeId": employee_id,
            "dateJoined": "2026-01-05", "roleName": "CASHIER", "branch": branch,
        })

    token = login(email, PASSWORD)["token"]
    stored = call(f"/staff/{uid}", token=admin_token)[1].get("data", {})
    return token, stored


def ensure_product(admin_token):
    cats = call("/categories", token=admin_token)[1]
    items = cats.get("data") or []
    category_id = items[0]["id"] if isinstance(items, list) and items else None
    if category_id is None:
        status, body = call("/categories", token=admin_token, method="POST",
                            body={"name": f"E2E Cat {uuid.uuid4().hex[:6]}", "description": "e2e"})
        category_id = (body.get("data") or body)["id"]

    sku = "E2E-" + uuid.uuid4().hex[:8].upper()
    status, body = call("/products", token=admin_token, method="POST", body={
        "sku": sku, "name": "E2E product", "categoryId": category_id,
        "sellingPrice": 100.0, "purchasePrice": 60.0,
        "reorderLevel": 5, "initialQuantity": 50,
    })
    if status not in (200, 201):
        raise SystemExit(f"cannot create product: {status} {body}")
    return (body.get("data") or body)["id"]


def sell(token, product_id, qty=5):
    status, body = call("/sales", token=token, method="POST", body={
        "items": [{"productId": product_id, "quantity": qty, "unitPrice": 100.0}],
        "paymentMethod": "CASH", "paymentStatus": "PAID",
        "idempotencyKey": uuid.uuid4().hex,
    })
    if status not in (200, 201):
        raise SystemExit(f"sale failed: {status} {body}")
    return body.get("data", body)


def main():
    print(f"verifying attribution against {BASE}\n")
    admin = login(*ADMIN)["token"]
    product_id = ensure_product(admin)

    print("cashier with a name and a branch")
    token, stored = ensure_cashier(admin, "full", "Eyas", "Sentayew", "Main Street")
    order = sell(token, product_id)

    cashier = order.get("cashier")
    branch = order.get("branch")
    check("cashier is an object, not an id or null", isinstance(cashier, dict), repr(cashier))
    check("cashier has a name", bool(cashier and cashier.get("name")), repr(cashier))
    check(
        "cashier name is the person's name, not the username",
        (cashier or {}).get("name") == "Eyas Sentayew",
        repr((cashier or {}).get("name")),
    )
    check("cashier carries an id", bool(cashier and cashier.get("id")), repr(cashier))
    check("branch is an object", isinstance(branch, dict), repr(branch))
    check("branch has a name", bool(branch and branch.get("name")), repr(branch))
    check(
        "branch id is null because branch is free text, not an entity",
        branch is not None and branch.get("id", "missing") is None,
        repr(branch),
    )
    check("createdBy is still returned for existing callers", bool(order.get("createdBy")),
          repr(order.get("createdBy")))

    detail = call(f"/sales/{order['id']}", token=admin)[1].get("data", {})
    check("detail view returns cashier too", (detail.get("cashier") or {}).get("name") == "Eyas Sentayew",
          repr(detail.get("cashier")))
    check("detail view returns branch too", (detail.get("branch") or {}).get("name") == "Main Street",
          repr(detail.get("branch")))

    listing = call("/sales?page=0&size=50", token=admin)[1]
    first = ((listing.get("data") or {}).get("content") or [{}])[0]
    check("list returns cashier as an object", isinstance(first.get("cashier"), dict), repr(first.get("cashier")))
    check("list returns branch as an object", isinstance(first.get("branch"), dict), repr(first.get("branch")))

    print("\ncashier with a blank branch")
    token2, _ = ensure_cashier(admin, "nobranch", "No", "Branch", "   ")
    order2 = sell(token2, product_id)
    check("no crash when the branch is blank", isinstance(order2, dict))
    check("branch is absent or null", not order2.get("branch"), repr(order2.get("branch")))
    check("cashier is still present", bool((order2.get("cashier") or {}).get("name")),
          repr(order2.get("cashier")))

    print("\na cashier with a blank name")
    # Cannot be created through the API: firstName/lastName are @NotBlank. That
    # is worth asserting, because it means the blank-name fallback in
    # displayName() only has to cope with legacy rows that predate the
    # validation -- which the database permits, since the columns are NOT NULL
    # but were not always guarded at the boundary.
    status, body = call("/users", token=admin, method="POST", body={
        "username": f"e2e_noname_{uuid.uuid4().hex[:6]}",
        "firstName": "", "lastName": "", "email": f"noname.{uuid.uuid4().hex[:6]}@stockflow.local",
        "phone": "+251911000998", "employeeId": f"E2E-NONAME-{uuid.uuid4().hex[:6].upper()}",
        "dateJoined": "2026-01-05", "password": PASSWORD, "roleName": "CASHIER",
    })
    check(
        "the API refuses a cashier with a blank name",
        status == 400,
        f"status {status}: {body}",
    )

    print()
    if failures:
        print(f"{len(failures)} FAILURE(S):")
        for f in failures:
            print(f"  - {f}")
        return 1

    print("All attribution checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
