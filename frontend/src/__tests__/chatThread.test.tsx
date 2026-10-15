/**
 * The thread both chatbot channels render.
 *
 * The thread is dumb on purpose — it receives messages and renders them —
 * which is exactly why its behaviour can be pinned here without a server:
 *
 * 1. **Loading, empty, error, retry.** Each state a reader must be able to
 *    tell apart: "looking up", "nothing asked yet", "could not answer" —
 *    and a refusal (rate limit included) shown with the sentence the server
 *    sent, with retry offered only when retrying can help.
 *
 * 2. **Server text renders as text.** An answer is untrusted prose that may
 *    contain anything a record contains; it must land in the DOM as
 *    characters, never as markup the browser acts on.
 *
 * 3. **Figures are displayed, not derived.** The cards show the numbers the
 *    backend sent — a figure the UI rounded or re-multiplied would no longer
 *    match the report it links to.
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import ChatThread, { ChatMessage } from '@/components/chat/ChatThread';

type ThreadProps = React.ComponentProps<typeof ChatThread>;

const onSend = jest.fn();
const onRetry = jest.fn();

function thread(overrides: Partial<ThreadProps> = {}) {
  const props: ThreadProps = {
    messages: [],
    loading: false,
    error: null,
    suggestions: ['What was our net profit this month?'],
    showSuggestions: true,
    onSend,
    onRetry,
    ...overrides,
  };
  return <ChatThread {...props} />;
}

/** A transcript as history would load it. */
const transcript: ChatMessage[] = [
  { id: 'u1', role: 'user', content: 'Where is my order SO-000123?' },
  {
    id: 'a1',
    role: 'assistant',
    content: 'Order SO-000123 was placed on 12 September.',
    note: 'From your own order history.',
    guardIntervened: false,
  },
];

beforeEach(() => {
  jest.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// States
// ─────────────────────────────────────────────────────────────────────────────

describe('states', () => {
  it('says what the assistant is for when nothing has been asked yet', () => {
    render(thread());
    expect(screen.getByText(/built from live records/)).toBeInTheDocument();
  });

  it('shows the lookup state and closes the input while a question runs', () => {
    render(thread({ loading: true }));

    expect(screen.getByText(/Looking up the records/)).toBeInTheDocument();
    expect(screen.getByLabelText('Ask a question')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Send question' })).toBeDisabled();
  });

  it('shows a failure with the server sentence and offers a retry that is called', async () => {
    const user = userEvent.setup();
    render(thread({ error: 'Too many messages. Please wait a minute.' }));

    expect(screen.getByText('That question could not be answered.')).toBeInTheDocument();
    expect(screen.getByText('Too many messages. Please wait a minute.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Try again/ }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('offers no retry when the caller passed none — nothing would happen', () => {
    render(thread({ error: 'No permission', onRetry: undefined }));

    expect(screen.getByText('No permission')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Try again/ })).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Asking
// ─────────────────────────────────────────────────────────────────────────────

describe('asking', () => {
  it('sends what was typed, trimmed, and clears the box', async () => {
    const user = userEvent.setup();
    render(thread());

    await user.type(screen.getByLabelText('Ask a question'), '  net profit?  ');
    await user.click(screen.getByRole('button', { name: 'Send question' }));

    expect(onSend).toHaveBeenCalledWith('net profit?');
    expect(screen.getByLabelText('Ask a question')).toHaveValue('');
  });

  it('sends nothing for a blank question', async () => {
    const user = userEvent.setup();
    render(thread());

    await user.type(screen.getByLabelText('Ask a question'), '   ');
    await user.keyboard('{Enter}');

    expect(onSend).not.toHaveBeenCalled();
  });

  it('asks a suggestion directly, and only while the transcript is empty', async () => {
    const user = userEvent.setup();
    const { rerender } = render(thread());

    await user.click(screen.getByRole('button', { name: 'What was our net profit this month?' }));
    expect(onSend).toHaveBeenCalledWith('What was our net profit this month?');

    rerender(thread({ messages: transcript }));
    expect(
      screen.queryByRole('button', { name: 'What was our net profit this month?' }),
    ).not.toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Rendering untrusted server text
// ─────────────────────────────────────────────────────────────────────────────

describe('safe rendering', () => {
  it('treats answer text as characters, never as markup the browser acts on', () => {
    const hostile = '<script>alert("steal")</script> and <img src=x onerror="alert(1)">';
    render(
      thread({
        messages: [{ id: 'a1', role: 'assistant', content: hostile }],
        showSuggestions: false,
      }),
    );

    expect(screen.getByText(hostile)).toBeInTheDocument();
    expect(document.querySelector('script')).toBeNull();
    expect(document.querySelectorAll('img').length).toBe(0);
  });

  it('renders a loaded transcript: both sides, the note, and a corrected figure badge', () => {
    render(
      thread({
        messages: [
          ...transcript,
          {
            id: 'a2',
            role: 'assistant',
            content: 'Net profit was ETB 900.00.',
            guardIntervened: true,
            note: 'The phrased figure was replaced with the computed one.',
          },
        ],
        showSuggestions: false,
      }),
    );

    expect(screen.getByText('Where is my order SO-000123?')).toBeInTheDocument();
    expect(screen.getByText('Order SO-000123 was placed on 12 September.')).toBeInTheDocument();
    expect(screen.getByText('From your own order history.')).toBeInTheDocument();
    expect(screen.getByText('Figure corrected')).toBeInTheDocument();
    expect(
      screen.getByText('The phrased figure was replaced with the computed one.'),
    ).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Figures and their sources
// ─────────────────────────────────────────────────────────────────────────────

describe('data behind an answer', () => {
  it('shows the backend figure as sent, with the period and the report that holds it', () => {
    render(
      thread({
        messages: [
          {
            id: 'a1',
            role: 'assistant',
            content: 'Net profit for the selected date range was ETB 12,400.00.',
            toolResults: {
              financial_summary: {
                netProfit: 12400,
                period: '1 – 30 September 2026',
                from: '2026-09-01',
                to: '2026-09-30',
                reportUrl: '/reports/financial',
              },
            },
          },
        ],
        showSuggestions: false,
      }),
    );

    expect(screen.getByText('ETB 12,400.00')).toBeInTheDocument();
    expect(screen.getByText('Period: 1 – 30 September 2026')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /See the report/ });
    expect(link).toHaveAttribute('href', '/reports/financial');
  });

  it('shows an order as a reference a customer can quote back', () => {
    render(
      thread({
        messages: [
          {
            id: 'a1',
            role: 'assistant',
            content: 'Order SO-000123 was placed on 12 September.',
            toolResults: {
              customer_order_status: {
                found: true,
                orderNumber: 'SO-000123',
                status: 'CONFIRMED',
              },
            },
          },
        ],
        showSuggestions: false,
      }),
    );

    expect(screen.getByText('SO-000123')).toBeInTheDocument();
    expect(screen.getByText('CONFIRMED')).toBeInTheDocument();
  });
});
