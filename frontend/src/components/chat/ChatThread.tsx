'use client';
import React, { useRef } from 'react';
import { AlertTriangle, Bot, ExternalLink, Loader2, RefreshCw, Send } from 'lucide-react';
import Link from 'next/link';
import Badge from '@/components/ui/Badge';

/**
 * The chat thread both channels share.
 *
 * <p>One component, two callers: the staff dashboard page and the
 * customer support page. They differ in who is asking and how the
 * question is authorised, which is a property of the page, not of
 * the thread — so the thread knows nothing about tokens or
 * permissions and only renders what it is given.
 *
 * <p>Every assistant message carries the data it was built from.
 * A financial answer shows its period and a link to the report with
 * the same figures; a product answer shows the products as cards;
 * an order answer shows the order as a reference card. The sentence
 * is never the only place a figure appears, because a sentence that
 * cannot be checked is a sentence that has to be trusted.
 */

/** One message as the thread renders it. */
export interface ChatMessage {
  /** Client-assigned; unique within the thread. */
  id: string;
  role: 'user' | 'assistant';
  content: string;
  /** The conversation the server filed this under, once known. */
  conversationId?: number | null;
  /** True when the answer was rendered from the tool results. */
  deterministic?: boolean;
  /** True when a phrased answer was replaced for inventing a figure. */
  guardIntervened?: boolean;
  note?: string | null;
  /** The tool results behind the answer, keyed by tool name. */
  toolResults?: Record<string, unknown>;
  createdAt?: string | null;
}

interface ChatThreadProps {
  messages: ChatMessage[];
  loading: boolean;
  error: string | null;
  /** Questions the caller may ask, from the server's capability list. */
  suggestions: string[];
  /** Usually only when the transcript is empty. */
  showSuggestions: boolean;
  onSend: (question: string) => void;
  /** Resend the last question. */
  onRetry?: () => void;
  placeholder?: string;
  ariaLabel?: string;
}

/** Money-ish keys in a tool result, for the key-figures grid. */
const MONEY_KEY =
  /(amount|profit|sales|expense|cash|total|outstanding|balance|value|received|paid|refund)/i;

export default function ChatThread({
  messages,
  loading,
  error,
  suggestions,
  showSuggestions,
  onSend,
  onRetry,
  placeholder = 'Ask a question…',
  ariaLabel = 'Chat with the assistant',
}: ChatThreadProps) {
  const [input, setInput] = React.useState('');
  const transcriptRef = useRef<HTMLDivElement>(null);

  // Keep the newest message in view. Guarded because `scrollTo` is
  // not implemented everywhere the component is rendered — a test
  // environment throws on it, and an unhandled throw here would take
  // the answer down with it.
  React.useEffect(() => {
    const id = window.setTimeout(() => {
      const transcript = transcriptRef.current;
      if (typeof transcript?.scrollTo === 'function') {
        transcript.scrollTo({ top: transcript.scrollHeight });
      }
    }, 0);
    return () => window.clearTimeout(id);
  }, [messages.length, loading]);

  const submit = (question: string) => {
    const trimmed = question.trim();
    if (trimmed.length === 0 || loading) return;
    onSend(trimmed);
    setInput('');
  };

  return (
    <div className="flex h-full min-h-0 flex-col" aria-label={ariaLabel}>
      {/* ── Transcript ─────────────────────────────────────────── */}
      <div
        ref={transcriptRef}
        className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1"
        aria-live="polite"
      >
        {messages.length === 0 && !loading && (
          <div className="rounded-lg bg-gray-50 p-4 dark:bg-gray-700/30">
            <p className="text-sm text-gray-600 dark:text-gray-400">
              Ask a question. The answer is built from live records and
              states the period it covers.
            </p>
          </div>
        )}

        {messages.map((message) =>
          message.role === 'user' ? (
            <div key={message.id} className="flex justify-end">
              <p className="max-w-[85%] rounded-lg bg-blue-600 px-3 py-2 text-sm text-white sm:max-w-[75%]">
                {message.content}
              </p>
            </div>
          ) : (
            <AssistantMessage key={message.id} message={message} />
          ),
        )}

        {loading && (
          <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <Loader2 size={14} className="animate-spin" /> Looking up the
            records…
          </div>
        )}

        {error && (
          <div className="rounded-lg bg-red-50 p-3 dark:bg-red-900/20">
            <p className="flex items-center gap-2 text-sm text-red-700 dark:text-red-400">
              <AlertTriangle size={14} /> That question could not be answered.
            </p>
            <p className="mt-1 text-xs text-red-600 dark:text-red-400">
              {error}
            </p>
            {onRetry && (
              <button
                type="button"
                onClick={onRetry}
                disabled={loading}
                className="mt-2 inline-flex items-center gap-1.5 rounded-lg border border-red-300 px-2.5 py-1 text-xs text-red-700 hover:bg-red-100 disabled:opacity-50 dark:border-red-700 dark:text-red-300 dark:hover:bg-red-900/30"
              >
                <RefreshCw size={12} /> Try again
              </button>
            )}
          </div>
        )}
      </div>

      {/* ── Suggestions ────────────────────────────────────────── */}
      {showSuggestions && suggestions.length > 0 && messages.length === 0 && (
        <div className="flex flex-wrap gap-1.5 pt-2">
          {suggestions.map((question) => (
            <button
              key={question}
              type="button"
              onClick={() => submit(question)}
              disabled={loading}
              className="rounded-full border border-gray-200 px-3 py-1 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-gray-600 dark:text-gray-400 dark:hover:bg-gray-700/50"
            >
              {question}
            </button>
          ))}
        </div>
      )}

      {/* ── Input ──────────────────────────────────────────────── */}
      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit(input);
        }}
        className="flex items-center gap-2 pt-3"
      >
        <label htmlFor="chat-question" className="sr-only">
          Ask a question
        </label>
        <input
          id="chat-question"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder={placeholder}
          disabled={loading}
          className="flex-1 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
        />
        <button
          type="submit"
          disabled={loading || input.trim().length === 0}
          aria-label="Send question"
          className="rounded-lg bg-blue-600 p-2 text-white hover:bg-blue-700 disabled:opacity-40"
        >
          <Send size={16} />
        </button>
      </form>
    </div>
  );
}

/** An assistant message: the answer, its basis, and its data. */
function AssistantMessage({ message }: { message: ChatMessage }) {
  return (
    <div className="flex items-start gap-2">
      <span
        className="mt-1 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-100 text-blue-600 dark:bg-blue-900/40 dark:text-blue-300"
        aria-hidden="true"
      >
        <Bot size={15} />
      </span>
      <div className="min-w-0 max-w-[95%] rounded-lg border border-gray-200 p-3 dark:border-gray-700 sm:max-w-[85%]">
        <div className="mb-1.5 flex flex-wrap items-center gap-2">
          <span className="text-xs font-medium text-gray-400">Assistant</span>
          {message.guardIntervened && (
            <Badge variant="warning">Figure corrected</Badge>
          )}
          {message.createdAt && (
            <span className="text-[10px] text-gray-400">
              {new Date(message.createdAt).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              })}
            </span>
          )}
        </div>

        <p className="whitespace-pre-line text-sm text-gray-700 dark:text-gray-300">
          {message.content}
        </p>

        {message.note && (
          <p className="mt-2 border-l-2 border-gray-300 pl-2 text-xs text-gray-500 dark:border-gray-600 dark:text-gray-400">
            {message.note}
          </p>
        )}

        {message.toolResults && Object.keys(message.toolResults).length > 0 && (
          <ResultCards toolResults={message.toolResults} />
        )}
      </div>
    </div>
  );
}

/**
 * The structured data behind an answer.
 *
 * <p>Switches on the tool names present, because the shape of a
 * product search and the shape of a profit figure have nothing in
 * common worth generalising over. A tool with no known card shape
 * still gets its period and source link shown, so every answer can
 * be traced somewhere.
 */
function ResultCards({ toolResults }: { toolResults: Record<string, unknown> }) {
  const entries = Object.entries(toolResults);
  const [primaryTool, primary] = entries[0] ?? [];
  if (!primaryTool || typeof primary !== 'object' || primary === null) {
    return null;
  }
  const result = primary as Record<string, unknown>;

  return (
    <div className="mt-3 space-y-3">
      {/* Products, as cards a reader can act on. */}
      {(primaryTool.includes('product_search') ||
        primaryTool.includes('alternatives')) && (
        <ProductCards
          rows={
            (Array.isArray(result.results)
              ? result.results
              : Array.isArray(result.sameCategory)
                ? result.sameCategory
                : Array.isArray(result.withinBudget)
                  ? result.withinBudget
                  : []) as Array<Record<string, unknown>>
          }
        />
      )}

      {/* A transaction reference: an order a customer can name. */}
      {(primaryTool.includes('order_status') || primaryTool.includes('sale_lookup')) && (
        <TransactionCard result={result} />
      )}

      {/* Tabular lists: receivables, payables, stockout risk, slow movers. */}
      {(Array.isArray(result.accounts) ||
        Array.isArray(result.orders) ||
        Array.isArray(result.risks) ||
        Array.isArray(result.products)) && (
        <ResultTable
          rows={
            (result.accounts ??
              result.orders ??
              result.risks ??
              result.products) as Array<Record<string, unknown>>
          }
        />
      )}

      {/* The figures themselves, when the answer is about money. */}
      <KeyFigures result={result} />

      {/* Period and source, for anything that has them. */}
      <SourceLine result={result} />
    </div>
  );
}

/** Products, with the one thing a shopper checks first: is it in stock. */
function ProductCards({ rows }: { rows: Array<Record<string, unknown>> }) {
  if (rows.length === 0) return null;
  return (
    <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
      {rows.slice(0, 6).map((row) => {
        const inStock =
          row.inStock === true || Number(row.unitsOnHand ?? 0) > 0;
        return (
          <li
            key={String(row.productId ?? row.name)}
            className="rounded-lg bg-gray-50 p-2.5 dark:bg-gray-700/40"
          >
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-gray-800 dark:text-gray-200">
                  {String(row.name ?? '—')}
                </p>
                <p className="truncate text-[10px] text-gray-400">
                  {String(row.sku ?? '')}
                  {row.category ? ` · ${String(row.category)}` : ''}
                </p>
              </div>
              <Badge variant={inStock ? 'success' : 'default'}>
                {inStock ? 'In stock' : 'Out of stock'}
              </Badge>
            </div>
            <div className="mt-1.5 flex items-baseline justify-between">
              <span className="text-sm font-semibold text-gray-800 dark:text-gray-200">
                {typeof row.price === 'number'
                  ? `ETB ${row.price.toLocaleString(undefined, { minimumFractionDigits: 2 })}`
                  : '—'}
              </span>
              {typeof row.unit === 'string' && (
                <span className="text-[10px] text-gray-400">per {row.unit}</span>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}

/** An order or sale, as a reference a reader can quote back. */
function TransactionCard({ result }: { result: Record<string, unknown> }) {
  const fields = (
    [
      ['Order', result.orderNumber],
      ['Date', result.orderDate ?? result.date],
      ['Status', result.status],
      ['Payment', result.paymentStatus ?? result.refundStatus],
    ] as Array<[string, unknown]>
  ).filter(([, value]) => value != null);

  return (
    <div className="rounded-lg bg-gray-50 p-2.5 dark:bg-gray-700/40">
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 sm:grid-cols-4">
        {fields.map(([label, value]) => (
          <div key={label}>
            <dt className="text-[10px] uppercase tracking-wide text-gray-400">
              {label}
            </dt>
            <dd className="truncate text-xs font-medium text-gray-700 dark:text-gray-300">
              {String(value)}
            </dd>
          </div>
        ))}
        {typeof result.total === 'number' && (
          <div>
            <dt className="text-[10px] uppercase tracking-wide text-gray-400">
              Total
            </dt>
            <dd className="text-xs font-semibold text-gray-800 dark:text-gray-200">
              ETB {result.total.toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </dd>
          </div>
        )}
        {typeof result.outstanding === 'number' && result.outstanding > 0 && (
          <div>
            <dt className="text-[10px] uppercase tracking-wide text-gray-400">
              Outstanding
            </dt>
            <dd className="text-xs font-semibold text-amber-700 dark:text-amber-400">
              ETB {result.outstanding.toLocaleString(undefined, { minimumFractionDigits: 2 })}
            </dd>
          </div>
        )}
      </dl>
    </div>
  );
}

/** A short table for list-shaped results. */
function ResultTable({ rows }: { rows: Array<Record<string, unknown>> }) {
  if (rows.length === 0) return null;
  const columns = Object.keys(rows[0]).filter(
    (key) => !['productId', 'evidence'].includes(key),
  );
  return (
    <div className="overflow-x-auto rounded-lg bg-gray-50 dark:bg-gray-700/40">
      <table className="w-full min-w-[320px] text-left text-xs">
        <thead>
          <tr className="border-b border-gray-200 dark:border-gray-600">
            {columns.slice(0, 4).map((column) => (
              <th
                key={column}
                className="px-2.5 py-1.5 text-[10px] font-medium uppercase tracking-wide text-gray-400"
              >
                {column.replace(/([A-Z])/g, ' $1')}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.slice(0, 5).map((row, index) => (
            <tr key={index} className="border-b border-gray-100 last:border-0 dark:border-gray-700">
              {columns.slice(0, 4).map((column) => {
                const value = row[column];
                const money =
                  typeof value === 'number' && MONEY_KEY.test(column);
                return (
                  <td
                    key={column}
                    className={`max-w-[12rem] truncate px-2.5 py-1.5 ${
                      money ? 'font-medium text-gray-700 dark:text-gray-300' : 'text-gray-600 dark:text-gray-400'
                    }`}
                  >
                    {money
                      ? `ETB ${value.toLocaleString(undefined, { minimumFractionDigits: 2 })}`
                      : value == null
                        ? '—'
                        : String(value)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * The headline figures, when the result is about money.
 *
 * <p>Scalars only, one level deep: the number in the sentence next
 * to the number in the data, so a reader can compare them without
 * opening anything. A nested structure is a report, and the answer
 * already links to that.
 */
function KeyFigures({ result }: { result: Record<string, unknown> }) {
  const entries = Object.entries(result).filter(
    ([key, value]) =>
      typeof value === 'number' && MONEY_KEY.test(key) && value !== 0,
  );
  if (entries.length === 0) return null;

  return (
    <dl className="grid grid-cols-2 gap-x-4 gap-y-2 rounded-lg bg-gray-50 p-2.5 dark:bg-gray-700/40 sm:grid-cols-3">
      {entries.slice(0, 9).map(([key, value]) => (
        <div key={key} className="min-w-0">
          <dt className="truncate text-[10px] uppercase tracking-wide text-gray-400">
            {key.replace(/([A-Z])/g, ' $1')}
          </dt>
          <dd className="truncate text-xs font-semibold text-gray-800 dark:text-gray-200">
            ETB {(value as number).toLocaleString(undefined, {
              minimumFractionDigits: 2,
            })}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** The period the answer covers and the report that shows the same numbers. */
function SourceLine({ result }: { result: Record<string, unknown> }) {
  const period =
    typeof result.period === 'string'
      ? result.period
      : null;
  const from = typeof result.from === 'string' ? result.from : null;
  const to = typeof result.to === 'string' ? result.to : null;
  const url = typeof result.reportUrl === 'string' ? result.reportUrl : null;

  if (!period && !from && !url) return null;

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-400">
      {period && <span>Period: {period}</span>}
      {from && to && (
        <span>
          {from} – {to}
        </span>
      )}
      {url && (
        <Link
          href={url}
          className="inline-flex items-center gap-1 text-blue-600 hover:underline dark:text-blue-400"
        >
          See the report
          <ExternalLink size={11} />
        </Link>
      )}
    </div>
  );
}
