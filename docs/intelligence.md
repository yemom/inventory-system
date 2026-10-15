# How the intelligence service works

This document describes the forecasting, anomaly detection, inventory advice and
business assistant in `backend/src/main/java/com/inventory/backend/ai`.

## What it is, and what it is not

The intelligence service is ordinary Java in the same process as the rest of the
application. It reads the same tables, through the same repositories, under the
same permissions, as the reports.

**There is no external AI provider.** No API key is read, no outbound request is
made, and nothing is sent to any third party. There is deliberately no
`AI_PROVIDER_API_KEY` configuration variable — a variable nothing reads is a
variable that eventually gets a real secret pasted into it and committed.

The reason is not squeamishness about cloud models. For this problem — a few
hundred observations, a handful of well-understood models, figures that must tie
back to a ledger to the cent — a dependency would add cost, latency, a second
failure mode and a data-egress question, in exchange for capability the task does
not need. The models here are Holt exponential smoothing with weekly
seasonality, a robust z-score, and a service-level safety-stock formula. They are
about a thousand lines, they are auditable, and they are tested against held-out
history.

## Read-only, and structurally so

The AI package has no write methods. It cannot approve a payment, issue a
refund, change stock, receive goods or post an accounting entry, because there
is no code path in it that writes. That is a stronger guarantee than a policy
statement, and it is why there are no AI write permissions to grant.

What it produces is read: forecasts, alerts, recommendations, explanations, and
a log of the questions asked of the assistant.

## The models

### Forecasting — `TimeSeries`

Candidates, each fitted and scored by backtest:

| Model | Chosen when |
|---|---|
| Naive (last value) | Very little history; nothing more complex is supportable. |
| Moving average | A flat level with little trend. |
| Holt (level + trend) | A clear trend in the series. |
| Holt + weekly seasonality | A weekly cycle — retail trades harder at weekends. |

**Selection is by measured accuracy, not by assumption.** Every candidate is
fitted on an earlier part of the series and scored on a later part it never saw,
in chronological order. Random splits are not used: shuffling a time series lets
a model learn from its own future, which inflates its apparent accuracy and
tells you nothing about deployment.

Reported alongside every forecast:

- **MAE** — mean absolute error, in currency, on held-out data.
- **RMSE** — penalises large misses more.
- **MAPE** — a percentage, shown only when every held-out value was non-zero.
  With a day of zero sales the percentage is undefined, and printing a number
  anyway would be worse than printing none.

**Prediction intervals** widen with the horizon. Being further ahead must not be
more certain. The interval width comes from the model's measured residual spread
scaled by horizon, and the UI says so in words.

**Insufficient data refuses.** A window with fewer than
`MIN_OBSERVATIONS_DAILY` observations, or with no recorded sales at all,
produces no forecast and an explanation of why. A series of all zeros will
otherwise produce a technically valid model of a flat line at zero and therefore
a confident "next 14 days: 0.00" — which reads as a prediction about a trading
business when the truth is that it has no history to predict from. The same rule
applies to per-product demand.

### Anomaly detection — `AnomalyDetectionService`

An alert is a prompt to look at something. It is never a finding of wrongdoing,
and the wording is constrained: a test asserts that no alert text contains
"fraud", "theft" or "stolen".

**Median and MAD, not mean and standard deviation.** An outlier inflates the very
statistic used to judge it: one enormous day raises the standard deviation enough
to make that day look ordinary. The median absolute deviation is computed around
the median, so the outlier contributes one deviation and cannot widen its own
yardstick.

**The weekly cycle is removed first.** A shop that is busy on Saturday is not
unusual for being busy on Saturday, and judging Saturdays against a yardstick
built from all days produces an alert every weekend. Residuals are taken around
each weekday's own median, then anomalies are found in the residuals.

**Two conditions must both hold.** A day is raised only when it is:

1. **Statistically unusual** — modified z-score at or above
   `AI_ANOMALY_THRESHOLD` (3.5 by default) against the trailing window of
   residuals, excluding the day itself so a spike cannot widen its own yardstick.
2. **Materially different in money** — at least
   `AI_ANOMALY_MIN_RELATIVE_DEVIATION` (30% by default) from what that weekday
   normally trades at.

The second test is the important one, and it was added because the suite caught
a real defect. A business trading eight units a day has a small spread, so
ordinary variation scores high on a robust z-score. Without the floor, a normal
Saturday landing at the top of the Saturday range raised an alert — and a
business whose alerts fire on ordinary days has its alerts switched off, at
which point the one real spike goes unnoticed too. Statistical significance is
not the same as practical significance.

**The last seven days are left unjudged.** A day at the end of a series has no
following history to distinguish a one-day spike from the start of a trend.

Every alert carries the evidence that produced it: the value, the level that
weekday normally trades at, the difference as a percentage, the median and MAD
of the residual history, the method, and the floor. The evidence describes the
calculation that actually produced the score — the residuals, not the raw series,
because the raw series still contains the weekly swing that was removed
precisely because a busy Saturday is not unusual.

Other signals: a large adjustment with no stated reason; two identical postings
of the same size in one period (a prompt to confirm it was not entered twice,
nothing more — two deliveries of the same size in one day is normal warehouse
work); recurring shrinkage on one SKU, which is a process problem rather than an
event; expense categories moving more than 25% against the previous period,
assessed per category because only one line moving is a different conversation
from everything moving at once.

### Inventory optimisation — `InventoryOptimizationService`

```
average daily units (ADU)     = mean of observed daily units sold
coefficient of variation (CV) = σ / ADU
lead time demand              = ADU × assumed lead time
safety stock                  = z(service level) × σ(daily) × √lead time
reorder point (ROP)           = lead time demand + safety stock
suggested quantity            = max(0, ROP + review-period demand − stock on hand − stock on order)
days of stock remaining       = stock on hand ÷ ADU
```

**The lead time is a default, not a measurement.** Until supplier lead times are
recorded, every recommendation states that it assumed
`AI_DEFAULT_LEAD_TIME_DAYS`, and the evidence dictionary carries
`leadTimeSource` so the distinction is machine-readable rather than a footnote.

**A product with no sales history gets no quantity.** Not a zero, and not a
guess: `hasSufficientHistory: false`, `suggestedQuantity: 0`, urgency
`NO_SALES_HISTORY`, and a note saying its configured reorder level applies
instead. The same rule excludes such products from stock-out projection —
"we cannot say when this runs out" and "it needs nothing" are different claims,
and only the first is true.

Every recommendation states that it decides nothing on its own. The UI says the
same thing above the list: *advice only, nothing has been ordered*.

### Financial insights — `FinancialInsightService`

Explanations, not a second set of numbers. Each insight reads its figures from
`FinancialSummary` and quotes them; the test
`explanationIsGroundedInTheSummary` asserts that every figure an explanation
cites equals the one the calculation service reports, for the same period. If
these can disagree, the dashboard and the commentary can disagree, which is the
entire thing this layer exists to prevent.

Every insight carries a `sourceUrl` linking to the report showing the same
numbers.

An empty period produces an insight saying no conclusion can be drawn — an
absent finding and a missing one are not the same thing.

### The assistant — `BusinessAssistantService`

A question is classified into an intent and answered by calling a **trusted
service query**. There is no free-text generation in the answer path.

That is the central design decision. The assistant's job is to let somebody ask
a question in words and receive a figure that already exists in the system. It
resolves the intent, calls the same service the report uses, and formats the
result. It cannot invent a calculation, and it cannot produce a number no report
can produce — so the sentence a reader is shown and the report they open will
always agree.

Every answer carries:

- the **period** it covers, in words and as dates;
- its **assumptions** — what the figures count and what they exclude;
- **source links** to the reports showing the same numbers;
- the **structured data** alongside the prose, so the two can be checked against
  each other.

Four outcomes, each distinct and each rendered differently:

| Outcome | Meaning |
|---|---|
| `ANSWERED` | The question was understood and answered from records. |
| `INSUFFICIENT_DATA` | Understood, but too little data. Says so; produces no estimate. |
| `CLARIFICATION_NEEDED` | Not understood well enough. Asks, and lists what it can answer. |
| `DENIED` | The caller lacks the permission the answer needs. |

**Authorisation is checked before any data is read**, per intent, against the
requesting user's own authorities — not the role's name. The check fails closed
with no authentication at all, which is what makes holding the assistant
permission useless without the permission the *answer* needs. It throws
`AccessDeniedException` rather than returning a refusal in the body, so the
controller produces 403: a 200 carrying "not allowed" would be indistinguishable
from an answer to a client that only checks the status code.

## The business chatbot — `com.inventory.backend.chatbot`

Distinct from `BusinessAssistantService` above, the business chatbot is the
conversational channel (staff assistant page, public support page). It follows
the same doctrine — figures come from trusted service queries, never from text
generation — but adds an **optional phrasing layer**: an OpenAI-compatible model
may re-word results that have already been computed, while
`CHATBOT_PROVIDER=deterministic` (the default) makes no outbound request at all.

Guarantees that make the two compatible:

- The provider's wording is checked number-by-number against the tool results
  (`NumberGuard`); an invented figure falls back to the deterministic reply.
- The model is verified against the account at startup
  (`GET {base}/models`); an unavailable model yields a logged configuration
  error and the deterministic path is retained.
- `CHATBOT_LLM_PHRASED=false` guarantees zero outbound LLM requests, even with
  a key configured.
- Authorisation is enforced per tool, server-side, from the caller's JWT
  authorities or customer session — never by prompt. Customer tools are
  additionally scoped to the session's own customer id.

Configuration, key creation (local and deployed) and the full failure matrix
are documented in **[docs/chatbot.md](chatbot.md)**.

## Traceability

### Model versions — `AiModelVersion`

Every training run records the model key, algorithm, version, training period
start and end, sample size, MAE, RMSE, MAPE, the candidates that were considered
and their scores, and the timestamp. **Versions are superseded, not
overwritten** — the test `retrainingKeepsHistory` asserts that a second training
run leaves the first readable, because overwriting it would leave a figure
already reported unreproducible.

One version is active at a time; it answers, and the others remain as evidence.

### Query log — `AiQueryLog`

Every question asked of the assistant is recorded: the question as typed, the
intent, the resolved parameters, the date range, the outcome, the structured
result, the duration and the timestamp. Refusals are logged too — a repeated
attempt to ask for something the caller is not entitled to see is exactly what
this log is for.

The log holds figures and questions. It holds no credentials, because the
assistant is never given any: the test `queriesExposeNoCredentials` asserts
that no password, token or secret string can appear in an answer.

## Evaluation

`AiServiceIntegrationTest` (31 tests) evaluates the layer against recorded
transactions written by `TradingHistoryFixture` — a test-only component that
stamps `TESTDATA-` on every order number it writes, and exists only under
`src/test/java`.

It asserts that:

- a forecast is built from recorded sales, not from a pattern written into the
  code;
- the forecast tracks the level of the data it was trained on — a model that
  ignored its input would return the same numbers whatever history it was given;
- the interval widens with the horizon;
- a trained model is recorded with its period and its measured error;
- retraining supersedes rather than overwrites;
- insufficient and empty history refuses with a reason, and produces nothing;
- an ordinary period with a weekly pattern raises no alerts;
- a genuine spike is raised;
- an alert never claims wrongdoing;
- an explanation's figures equal the calculation service's figures;
- authorisation boundaries hold: a cashier gets nothing from the AI endpoints, a
  supervisor may ask operational questions but is refused profit figures, and a
  caller with no authorities is refused outright with nothing computed.

`TimeSeriesTest` (44 tests) covers the statistics directly — the models, the
model selection, the backtest, the intervals, the robust z-score and the
deseasonalisation.

One real defect was found this way. Deseasonalising weekly retail data and then
flagging anomalies against the raw residuals produced a false alarm on a healthy
weekend. The fix is in the code and the case is pinned by
`ordinaryPeriodRaisesNoAlerts` and `statisticallyOddButTrivialIsNotRaised`.

## Configuration

Every setting is in `application.properties` under `app.ai.*`, overridable by
environment variable. See `.env-example`. All are documented inline in that
file; none is a secret.

`AI_ENABLED=false` disables every `/api/v1/ai` endpoint. Reports, dashboards
and financial figures continue to work, because they are computed by the
reporting layer and do not depend on these models.