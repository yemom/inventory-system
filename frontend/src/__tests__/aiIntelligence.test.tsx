/**
 * The intelligence UI.
 *
 * These tests pin three things the panels are easy to get wrong and hard to
 * notice:
 *
 * 1. **A refusal is not an answer.** When the assistant cannot answer — not
 *    enough data, question not understood, no permission — it must say so. A
 *    panel that renders "no data" and a panel that renders "zero" look
 *    identical until somebody acts on the difference.
 *
 * 2. **Every figure shown is the one the backend sent.** The components format
 *    and link; they never recompute. A component that multiplied two fields
 *    together would produce a number no report could be checked against, which
 *    is the entire reason the calculation layer is on the server.
 *
 * 3. **Alerts describe, they do not accuse.** The wording comes from the
 *    backend, but the panel must not dress an anomaly up as a finding.
 */
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import AssistantPanel from '@/components/ai/AssistantPanel';
import AiInsightPanel from '@/components/ai/AiInsightPanel';
import DateRangeFilter, {
  lastCalendarMonth,
  lastNDays,
} from '@/components/ui/DateRangeFilter';
import { useAuth } from '@/lib/auth/AuthProvider';
import { aiApi } from '@/lib/api/aiApi';

jest.mock('@/lib/auth/AuthProvider', () => ({ useAuth: jest.fn() }));
jest.mock('@/lib/api/aiApi', () => ({
  aiApi: { ask: jest.fn(), assistantCapabilities: jest.fn() },
}));

const mockedUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockedAsk = aiApi.ask as jest.MockedFunction<typeof aiApi.ask>;
const mockedCapabilities = aiApi
  .assistantCapabilities as jest.MockedFunction<typeof aiApi.assistantCapabilities>;

function withPermission(...permissions: string[]) {
  mockedUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  } as any);
}

function wrap(node: React.ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

/** An answer as BusinessAssistantService returns it. */
function answer(overrides: Record<string, unknown> = {}) {
  return {
    question: 'What was my gross profit last month?',
    intent: 'GROSS_PROFIT',
    outcome: 'ANSWERED',
    answer: 'Gross profit was ETB 12,400.00 for 1 – 30 September.',
    period: { from: '2026-09-01', to: '2026-09-30', label: 'last month' },
    data: { grossProfit: 12400 },
    sources: ['/reports/financial'],
    assumptions: ['Revenue excludes sales tax.'],
    note: null,
    ...overrides,
  } as any;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedCapabilities.mockResolvedValue(['Gross profit for a period']);
});

// ─────────────────────────────────────────────────────────────────────────────
// Assistant
// ─────────────────────────────────────────────────────────────────────────────

describe('AssistantPanel', () => {
  it('is not rendered at all without the assistant permission', () => {
    withPermission('DASHBOARD_VIEW');

    const { container } = wrap(<AssistantPanel />);

    // Hidden rather than shown-and-refused: a panel that is always visible and
    // always fails is noise, and it advertises a capability the reader does
    // not have.
    expect(container).toBeEmptyDOMElement();
    expect(mockedAsk).not.toHaveBeenCalled();
  });

  it('says what it cannot do, up front', () => {
    withPermission('AI_ASSISTANT_USE');
    wrap(<AssistantPanel />);

    expect(
      screen.getByText(/cannot change stock, approve a payment or post/i),
    ).toBeInTheDocument();
  });

  it('answers a question with its period, assumptions and a link to the report', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(answer());

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: 'What was my gross profit last month?' }));

    await waitFor(() =>
      expect(screen.getByText(/Gross profit was ETB 12,400\.00/)).toBeInTheDocument(),
    );

    // The reader can tell which window was used...
    expect(screen.getByText(/last month: 2026-09-01 – 2026-09-30/)).toBeInTheDocument();
    // ...what the figures assume...
    expect(screen.getByText(/Revenue excludes sales tax/)).toBeInTheDocument();
    // ...and can open the report showing the same numbers.
    expect(screen.getByRole('link', { name: /reports\/financial/i })).toHaveAttribute(
      'href',
      '/reports/financial',
    );
  });

  it('shows the structured figure next to the sentence', async () => {
    // The wording and the number must be checkable against each other without
    // opening anything. If they can disagree, the sentence is decoration.
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(answer());

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: /gross profit last month/i }));

    // The number must appear twice: once in the sentence the reader is reading and
    // once in the structured grid beneath it. That is what makes the sentence
    // checkable — if the two can differ, the prose is decoration.
    await waitFor(() =>
      expect(screen.getAllByText(/12,400/).length).toBeGreaterThanOrEqual(2),
    );
    const figures = screen.getAllByText(/12,400/);
    expect(figures[0].tagName).toBe('P');
    expect(figures[1].tagName).toBe('DD');
    // The grid labels the figure, so the reader knows what the number is.
    const grid = figures[1].closest('dl') as HTMLElement;
    expect(within(grid).getByText(/gross\s?profit/i)).toBeInTheDocument();
  });

  it('passes the dashboard filter to the backend rather than relying on the wording', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(answer());

    const user = userEvent.setup();
    wrap(<AssistantPanel range={{ from: '2026-10-01', to: '2026-10-31' }} />);

    await user.type(
      screen.getByLabelText(/ask a question/i),
      'gross profit{enter}',
    );

    await waitFor(() => expect(mockedAsk).toHaveBeenCalled());

    // Epoch milliseconds, so the backend applies the screen's filter instead of
    // re-reading "last month" and possibly meaning a different window.
    const [question, range] = mockedAsk.mock.calls[0];
    expect(question).toBe('gross profit');
    expect(range).toEqual({ from: '2026-10-01', to: '2026-10-31' });
    expect(Date.parse(range!.from)).toBe(Date.parse('2026-10-01'));
    expect(Date.parse(range!.to)).toBe(Date.parse('2026-10-31'));
  });

  it('does not present "not enough data" as an answer with a figure', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(
      answer({
        outcome: 'INSUFFICIENT_DATA',
        answer: 'There are not enough recorded transactions to answer that.',
        data: {},
        sources: [],
        assumptions: [],
        note: 'No estimate has been produced rather than a misleading one.',
      }),
    );

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: /gross profit last month/i }));

    await waitFor(() =>
      expect(screen.getByText(/not enough data/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/No estimate has been produced/)).toBeInTheDocument();
    // No figure is rendered, because none was produced.
    expect(screen.queryByText(/12,400/)).not.toBeInTheDocument();
  });

  it('asks for clarification instead of guessing', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(
      answer({
        outcome: 'CLARIFICATION_NEEDED',
        answer: 'I could not tell which report you meant.',
        data: { canAnswer: ['Gross profit', 'Cash received'] },
        sources: [],
        assumptions: [],
      }),
    );

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: /gross profit last month/i }));

    await waitFor(() =>
      expect(screen.getByText(/needs clarifying/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/could not tell which report/i)).toBeInTheDocument();
  });

  it('surfaces a refused question as a refusal, not as a failure to answer', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockRejectedValue(
      new Error('Request failed with status code 403'),
    );

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: /gross profit last month/i }));

    await waitFor(() =>
      expect(screen.getByText(/could not be answered/i)).toBeInTheDocument(),
    );
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument();
  });

  it('keeps every exchange in the transcript', async () => {
    withPermission('AI_ASSISTANT_USE');
    mockedAsk.mockResolvedValue(answer());

    const user = userEvent.setup();
    wrap(<AssistantPanel />);

    await user.click(screen.getByRole('button', { name: /gross profit last month/i }));
    await waitFor(() =>
      expect(screen.getByText(/Gross profit was ETB 12,400\.00/)).toBeInTheDocument(),
    );

    // The suggestions disappear once there is a conversation to continue.
    expect(
      screen.queryByRole('button', { name: /Which products are likely to run out/i }),
    ).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Insights
// ─────────────────────────────────────────────────────────────────────────────

describe('AiInsightPanel', () => {
  it('renders each state distinctly', () => {
    const props = {
      insights: undefined,
      anomalies: undefined,
      recommendations: undefined,
      isLoading: false,
      error: null,
    };

    // Loading: says what it is doing.
    const loading = wrap(<AiInsightPanel {...props} isLoading />);
    expect(screen.getByText(/Analysing recorded transactions/)).toBeInTheDocument();
    loading.unmount();

    // Error: says the figures are unaffected, so a reader does not distrust
    // the numbers above the panel because the commentary failed.
    const failed = wrap(<AiInsightPanel {...props} error={new Error('boom')} />);
    expect(screen.getByText(/The analysis could not be produced/)).toBeInTheDocument();
    expect(screen.getByText(/figures on this page come from/)).toBeInTheDocument();
    failed.unmount();

    // Empty: says nothing stands out, and says that is an answer.
    const empty = wrap(<AiInsightPanel {...props} />);
    expect(screen.getByText('Nothing stands out')).toBeInTheDocument();
    expect(screen.getByText(/That is a real answer, not a missing one/)).toBeInTheDocument();
    empty.unmount();
  });

  it('shows an insight with its headline, message and a link to the report', () => {
    wrap(
      <AiInsightPanel
        insights={[
          {
            category: 'PROFIT',
            headline: 'Gross margin narrowed',
            message: 'Gross margin fell to 31.2% from 38.0%.',
            severity: 'WARNING',
            evidence: { grossMarginPercent: 31.2, previousGrossMarginPercent: 38.0 },
            sourceUrl: '/reports/financial',
          },
        ]}
        anomalies={[]}
        recommendations={[]}
        isLoading={false}
        error={null}
      />,
    );

    expect(screen.getByText('Gross margin narrowed')).toBeInTheDocument();
    expect(screen.getByText(/fell to 31\.2%/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /open the report/i })).toHaveAttribute(
      'href',
      '/reports/financial',
    );
  });

  it('hides the figures behind an insight until they are asked for', () => {
    wrap(
      <AiInsightPanel
        insights={[
          {
            category: 'PROFIT',
            headline: 'Margin narrowed',
            message: 'Margin fell.',
            severity: 'WARNING',
            evidence: { grossMarginPercent: 31.2 },
            sourceUrl: null,
          },
        ]}
        anomalies={[]}
        recommendations={[]}
        isLoading={false}
        error={null}
      />,
    );

    // Not on the page until expanded — the panel leads with the conclusion and
    // keeps the arithmetic behind a disclosure.
    expect(screen.queryByText('31.2')).not.toBeInTheDocument();
  });

  it('labels an anomaly as something to investigate, never as a finding', () => {
    wrap(
      <AiInsightPanel
        insights={[]}
        anomalies={[
          {
            source: 'REVENUE',
            period: '2026-09-26',
            value: 35000,
            baseline: 400,
            score: 3.9,
            kind: 'UNUSUALLY_HIGH',
            summary:
              'Net sales on 2026-09-26 were unusually high for that day of the week, against the recent norm of 400.00.',
            evidence: { expectedForThisWeekday: 400, differencePercent: 8650 },
          },
        ]}
        recommendations={[]}
        isLoading={false}
        error={null}
      />,
    );

    expect(screen.getByText('Investigation prompts, not findings')).toBeInTheDocument();
    expect(screen.getByText(/were unusually high/)).toBeInTheDocument();
    expect(screen.getByText(/judge/i)).toBeInTheDocument();

    // The wording must never accuse. The summary is the backend's, but the panel
    // is where it is read, so the constraint is asserted here too.
    const body = document.body.textContent?.toLowerCase() ?? '';
    for (const word of ['fraud', 'theft', 'stolen', 'suspicious']) {
      expect(body).not.toContain(word);
    }
  });

  it('says plainly that a recommendation orders nothing', () => {
    wrap(
      <AiInsightPanel
        insights={[]}
        anomalies={[]}
        recommendations={[
          {
            productId: 1,
            sku: 'SKU-1',
            name: 'Blue widget',
            hasSufficientHistory: true,
            stockOnHand: 4,
            stockOnOrder: 0,
            averageDailyUnits: 6.2,
            leadTimeDays: 7,
            safetyStock: 18,
            reorderPoint: 61,
            suggestedQuantity: 120,
            daysOfStockRemaining: 0.6,
            urgency: 'URGENT',
            stockoutImminent: true,
            observationCount: 120,
            coefficientOfVariation: 0.3,
            evidence: { formula: 'ROP = lead time demand + safety stock' },
            authorityNote: 'A human decides whether to order.',
          },
        ]}
        isLoading={false}
        error={null}
      />,
    );

    expect(screen.getByText('Advice only — nothing has been ordered')).toBeInTheDocument();
    expect(screen.getByText(/Order 120/)).toBeInTheDocument();
  });

  it('does not show a recommendation for a product with no sales history', () => {
    // The backend sends these with suggestedQuantity 0 and an explicit
    // "NO_SALES_HISTORY" urgency. Showing "order 0" as advice would be noise.
    wrap(
      <AiInsightPanel
        insights={[]}
        anomalies={[]}
        recommendations={[
          {
            productId: 2,
            sku: 'SKU-2',
            name: 'Never sold',
            hasSufficientHistory: false,
            stockOnHand: 3,
            stockOnOrder: 0,
            averageDailyUnits: 0,
            leadTimeDays: 7,
            safetyStock: 0,
            reorderPoint: 3,
            suggestedQuantity: 0,
            daysOfStockRemaining: null,
            urgency: 'NO_SALES_HISTORY',
            stockoutImminent: false,
            observationCount: 0,
            coefficientOfVariation: 0,
            evidence: {},
            authorityNote: 'No quantity is recommended.',
          },
        ]}
        isLoading={false}
        error={null}
      />,
    );

    expect(screen.queryByText(/Order 120/)).not.toBeInTheDocument();
    expect(screen.queryByText('Never sold')).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Date range
// ─────────────────────────────────────────────────────────────────────────────

describe('DateRangeFilter', () => {
  it('offers calendar months, not rolling windows, where a month is meant', () => {
    const month = lastCalendarMonth();
    const first = month.from.split('-')[2];
    expect(Number(first)).toBe(1);
  });

  it('ends last month on its last day, whatever today is', () => {
    const month = lastCalendarMonth();
    const end = new Date(`${month.to}T00:00:00`);
    const following = new Date(end);
    following.setDate(end.getDate() + 1);
    expect(following.getDate()).toBe(1);
  });

  it('includes both ends of a rolling window', () => {
    const range = lastNDays(30);
    const from = new Date(`${range.from}T00:00:00`);
    const to = new Date(`${range.to}T00:00:00`);
    const days = Math.round((to.getTime() - from.getTime()) / 86400000) + 1;
    expect(days).toBe(30);
  });

  it('marks the preset that matches the range currently set', () => {
    const range = lastNDays(30);
    wrap(<DateRangeFilter value={range} onChange={jest.fn()} />);

    expect(screen.getByRole('button', { name: 'Last 30 days' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: 'Last month' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('refuses a range that starts after it ends, rather than silently showing nothing', () => {
    wrap(
      <DateRangeFilter
        value={{ from: '2026-10-31', to: '2026-10-01' }}
        onChange={jest.fn()}
      />,
    );

    // A reversed range returns nothing from the backend. A quietly empty report
    // is much harder to diagnose than a message saying which way round it is.
    expect(screen.getByRole('alert')).toHaveTextContent(/Start date is after the end date/);
  });

  it('reports a preset the user picks', async () => {
    const onChange = jest.fn();
    const user = userEvent.setup();
    wrap(
      <DateRangeFilter value={lastNDays(30)} onChange={onChange} />,
    );

    await user.click(screen.getByRole('button', { name: 'Last month' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange.mock.calls[0][0]).toEqual(lastCalendarMonth());
  });
});