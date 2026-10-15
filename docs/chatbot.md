# How the business assistant works

## What it is, and what it is not

Every answer the assistant gives has two parts: the **facts** and the **words**.

The facts come from the chatbot's tools — small, read-only functions in
`com.inventory.backend.chatbot` that call the *same services the reports use*.
"Net profit" in the assistant is `FinancialCalculationService.summarise`, the
identical call the Profit & Loss screen makes. The words are presentation.

By default the words are also deterministic: a template renders the tool
results and nothing leaves the process. That is `CHATBOT_PROVIDER=deterministic`,
the shipped default, and it needs no key, no network and no outbound request.

Optionally, a model — any OpenAI-compatible chat-completions endpoint — may
**re-phrase text that has already been computed**. It computes nothing, looks
up nothing, and decides nothing. Its output is checked number-by-number against
the tool results before a human sees it (see [Number invention](#number-invention)).
If anything at all goes wrong, the deterministic rendering is used instead and
the answer is unaffected.

What this system is not:

- **Not a write channel.** The single exception is `raise_support_request`,
  which files a support ticket saying "a human should look" — no order, no
  payment, no stock movement, no record a customer could not already see.
- **Not an authority.** Authorization is enforced in Java, server-side, on
  every tool call, from the JWT's authorities or the customer session's id.
  The model is never asked whether a caller may see something; a prompt cannot
  grant a permission the caller does not hold.
- **Not a query generator.** Tools read through repositories and services with
  fixed queries. Nothing in the pipeline accepts SQL, an arbitrary endpoint or
  a code fragment from a question or from a model response.

## The configuration that matters

| Variable | Default | Meaning |
|---|---|---|
| `CHATBOT_ENABLED` | `true` | Master switch for the assistant endpoints |
| `CHATBOT_PROVIDER` | `deterministic` | `deterministic` (no network) or `openai-compatible` |
| `CHATBOT_API_URL` | *(empty)* | Full endpoint **or** base URL (see below) |
| `CHATBOT_API_KEY` | *(empty)* | The secret; used first, with `OPENAI_API_KEY` as the fallback when empty |
| `CHATBOT_MODEL` | `gpt-4o-mini` | Must be a model the key's account can use |
| `CHATBOT_LLM_PHRASED` | `true` | `false` guarantees **zero** outbound LLM requests |
| `CHATBOT_TIMEOUT_MS` | `15000` | Provider deadline; a hung model cannot hang the chatbot |
| `CHATBOT_MAX_TOKENS` | `900` | Completion cap |
| `CHATBOT_MIN_CONFIDENCE` | `0.45` | Below this, the question is treated as unrecognised |
| `CHATBOT_MESSAGES_PER_WINDOW` | `30` | Rate limit per actor per window (fails **closed**) |
| `CHATBOT_WINDOW_SECONDS` | `60` | Rate-limit window |
| `CHATBOT_MAX_MESSAGES` | `100` | Message cap per conversation |
| `CHATBOT_SESSION_TTL_HOURS` | `12` | Customer chat session lifetime |
| `CHATBOT_STAFF_RETENTION_DAYS` | `90` | Staff conversation retention |
| `CHATBOT_CUSTOMER_RETENTION_DAYS` | `30` | Customer conversation retention |
| `CHATBOT_MAX_CONTEXT_CHARS` | `6000` | Tool output sent to the model, capped |

`CHATBOT_API_KEY` is read first; when it is absent **or empty** the key is
taken from `OPENAI_API_KEY`, so either name works — including deployments
that pass both variables with one of them blank (a `${VAR:-}` pass-through
supplies an empty string, not an absent variable, so a single nested
placeholder would never reach its fallback). Both unset → deterministic.

### `CHATBOT_API_URL` — both shapes accepted

```
CHATBOT_API_URL=https://api.openai.com/v1/chat/completions   # the full endpoint
CHATBOT_API_URL=https://api.openai.com/v1                    # a base URL
```

The suffix `/chat/completions` is appended **only if the URL does not already
end with it**, so neither shape doubles the path. The startup model check uses
the sibling `GET {base}/models` derived the same way.

### Creating the key

1. Create a key at <https://platform.openai.com/api-keys> (or your provider's
   equivalent). Treat it as a password.
2. Put it in `.env` (git-ignored) as `OPENAI_API_KEY=sk-…` or
   `CHATBOT_API_KEY=sk-…`. `.env-example` documents the names with no value.
3. The key is read **server-side only**. It is never sent to the browser,
   never included in an API response, and never written to a log — failure
   messages are redacted (query strings and bearer credentials stripped)
   before logging. The frontend has no chatbot secret and makes no direct
   provider calls; every request goes through the backend.

### Local configuration

```bash
cp .env-example .env          # then edit
# .env:
CHATBOT_PROVIDER=openai-compatible
CHATBOT_API_URL=https://api.openai.com/v1/chat/completions
CHATBOT_MODEL=gpt-4o-mini
OPENAI_API_KEY=sk-...         # never commit this file
```

Leave `CHATBOT_PROVIDER=deterministic` and the key empty and everything works
offline: tests, development and CI all run in deterministic mode with **zero
outbound requests**. That is the default and the supported offline path.

### Deployed configuration

- **docker-compose** passes `CHATBOT_*` and `OPENAI_API_KEY` through from your
  environment; the values live in your `.env`, which is git-ignored.
- **Render (`render.yaml`)** ships the non-secret chatbot defaults
  (`CHATBOT_PROVIDER=deterministic`, the API URL, model, timeout). Set
  `CHATBOT_API_KEY` (or `OPENAI_API_KEY`) in the Render dashboard as a
  secret — it is intentionally absent from the file, as with `JWT_SECRET`.
  `.gitignore` blocks `.env`, `.env.*` and permits only `.env-example`.

## Model availability is verified, not assumed

On startup, with phrasing enabled and a key present, the backend calls
`GET {base}/models` and checks that the configured model is on the account:

- **Model present** → the provider is available.
- **Model absent** → the provider is marked unavailable, a configuration error
  naming the problem is logged (`Model '…' is not available to this API
  account…`), and every answer stays deterministic. No chat request is made
  for a model that cannot answer.
- **List unavailable** (no endpoint, auth failure, network down) → treated as
  unverifiable and therefore unusable: deterministic mode is retained and the
  reason is logged. The assistant keeps working either way.

## `CHATBOT_LLM_PHRASED=false` makes zero outbound requests

Setting `CHATBOT_LLM_PHRASED=false` guarantees that the backend performs **no
outbound LLM requests at all**, even with a key and a provider configured:

1. The startup model check does not run (no `GET /models`).
2. `ask()` never calls a provider.
3. The provider itself refuses: `available()` returns false and `generate()`
   returns unusable without touching the network.

Three separate gates, because a guarantee enforced in one place is a
preference. This is covered by a test that records every request the mock
server receives and asserts the count is **zero**.

## Number invention

Before a phrased answer is shown, every number in it is checked against the
numbers the tools actually returned (`NumberGuard`). A figure the tools did
not produce — a wrong total, a hallucinated percentage — causes an immediate
fall back to the deterministic rendering, with a `CHATBOT_GUARD_INTERVENED`
audit entry recording that it happened. The customer or staff member never
sees the invented number.

## Failure behaviour — the answer degrades, it does not disappear

| Failure | Result |
|---|---|
| Timeout | Deterministic reply; provider call bounded by `CHATBOT_TIMEOUT_MS` |
| HTTP 4xx / 429 rate limit / 5xx | Deterministic reply |
| Malformed JSON, empty content, no choices | Deterministic reply |
| Model unavailable / unverifiable at startup | Deterministic reply, configuration error logged |
| Model not on the account | Deterministic reply, no chat request attempted |
| Unreachable endpoint | Deterministic reply |
| Phrasing disabled | Deterministic reply, zero requests |
| Invented number | Deterministic reply, guard audit entry |
| Question unrecognised (below confidence) | Honest "I could not tell" plus the caller's capability list — never a guess |
| Rate-limited or conversation over the cap | Explicit refusal; state never half-changed |

## Authorization

**Staff** authenticate with the normal JWT. The chatbot endpoints require
`AI_ASSISTANT_USE`, and each tool additionally requires the *same permission
the corresponding report requires* — a cashier asking about net profit is
refused by `PROFIT_LOSS_READ`, exactly as on the reports screen. Capabilities
offered to a caller are computed from that caller's own authorities, so the
menu a person sees contains only what they can actually run.

**Customers** use a chat session opened with their email plus an order number
(`X-Chatbot-Token`, TTL `CHATBOT_SESSION_TTL_HOURS`). Every customer tool
scopes its query to the session's customer id in addition to the order number
given — an order number alone grants nothing, and one customer's conversation
history is unreadable by another (and by staff, who have their own thread).
Conversations are checked for ownership before they are read or appended to.

**Prompt injection** is answered structurally, not by instruction. Questions
are stripped of credential-shaped strings and instruction-shaped passages
before classification; the model's output cannot choose a tool, a parameter or
an owner — entities such as order numbers are extracted from the caller's own
words by a fixed pattern, and the model never sees, let alone decides, authorisation.

## What is logged, and what is not

- **Logged:** intent, resolved parameters, tools run, guard interventions,
  support-ticket creation — the same audit service the rest of the system uses.
- **Not logged:** API keys, authorization headers, or conversation contents.
  Provider failure messages are redacted before logging.
- **Stored:** messages in the database, purged after `CHATBOT_STAFF_RETENTION_DAYS`
  (staff) or `CHATBOT_CUSTOMER_RETENTION_DAYS` (customers); expired customer
  sessions stop resolving at their TTL.

## Endpoints

| Caller | Endpoint | Auth |
|---|---|---|
| Staff | `POST /api/v1/chatbot/ask`, `GET /api/v1/chatbot/conversations…` | JWT + `AI_ASSISTANT_USE` |
| Customer | `POST /api/v1/chatbot/customer/ask`, history | `X-Chatbot-Token` |

The frontend assistant page and the public support page call only these; the
browser never holds a provider key and never talks to an LLM directly.

## Testing

- **`OpenAiCompatibleProviderTest`** (18 tests) runs a local recording HTTP
  server that answers both `GET /v1/models` and `POST /v1/chat/completions`,
  and asserts: neither URL shape doubles the path; the request carries the
  bearer header and the configured model; the startup model check accepts,
  rejects and declines-on-unverifiable; disabled mode records **zero** requests;
  timeout, unreachable, malformed, empty, 401, 429 and 500 all return
  unusable rather than throwing; and a failure message never contains the key.
- **`ChatbotServiceIntegrationTest`** (22 tests) runs against a real database
  and covers the permission matrix per role, customer isolation both
  directions, figures matching the report service, no-write guarantees for
  order/payment/adjustment requests, the audited support path, injection
  attempts, rate limiting, retention and expiry.
- **`chatbotApi.test.ts` + `chatThread.test.tsx`** (30 tests, frontend) pin
  the two channels — staff JWT client vs bare customer client with the
  `X-Chatbot-Token` header — that **no request carries a provider key**, that
  a 401 reads as an expired session while a 429 surfaces its server message,
  that a stored session expires by its TTL, and that the thread renders its
  loading, empty, error and retry states with answer text as text.
- All provider HTTP is mocked; the suite makes **no paid calls**. Live-API
  behaviour is therefore *not* claimed from this environment: no
  `OPENAI_API_KEY` was available where these tests ran. The code paths a live
  call exercises (URL construction, auth header, response parsing, every
  failure mode) are the ones the mock server verifies.

## Offline and CI mode

Nothing above requires a key. With the shipped defaults
(`CHATBOT_PROVIDER=deterministic`) the assistant answers from the reporting
layer with no outbound request — the mode tests run in and the mode to keep in
development until a key is deliberately added.
