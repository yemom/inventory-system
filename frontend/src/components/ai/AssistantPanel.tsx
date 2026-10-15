'use client';
import React, { useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  Bot,
  ExternalLink,
  Loader2,
  Send,
} from 'lucide-react';
import Link from 'next/link';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Badge from '@/components/ui/Badge';
import { useAuth } from '@/lib/auth/AuthProvider';
import { aiApi, AssistantAnswer } from '@/lib/api/aiApi';
import { formatCurrency } from '@/lib/utils';

/**
 * The business assistant.
 *
 * <p>Questions a person might actually ask, answered from figures the backend
 * calculated. There is no free-text generation in the answer path: a question is
 * resolved to a structured query against a trusted service, and the reply is
 * written from that result. Every answer carries the period it covers, what its
 * figures assume, and a link to the report showing the same numbers — so a
 * reader can check it rather than take it on trust.
 *
 * <p>When the assistant cannot answer, it says so and says why: "not enough
 * data", "I could not tell which report you meant", or a refusal when the
 * question needs a permission the caller does not hold. A refusal here is a
 * deliberate feature, not an error path.
 */

interface AssistantPanelProps {
  /** The dashboard's active date filter, applied when it is set. */
  range?: { from: string; to: string };
  className?: string;
}

const SUGGESTED_QUESTIONS = [
  'What was my gross profit last month?',
  'Which five products made the highest gross profit?',
  'Which products are likely to run out in the next 14 days?',
  'Show purchases received but not fully paid.',
  'Which customers have overdue balances?',
  'Explain why net profit decreased this month.',
];

interface Exchange {
  question: string;
  answer: AssistantAnswer;
}

export default function AssistantPanel({
  range,
  className,
}: AssistantPanelProps) {
  const { hasPermission } = useAuth();
  const permitted = hasPermission('AI_ASSISTANT_USE');

  const [input, setInput] = useState('');
  const [exchanges, setExchanges] = useState<Exchange[]>([]);
  const transcriptRef = useRef<HTMLDivElement>(null);

  const capabilities = useQuery({
    queryKey: ['ai', 'assistant', 'capabilities'],
    queryFn: () => aiApi.assistantCapabilities(),
    enabled: permitted,
    staleTime: 10 * 60 * 1000,
    retry: false,
  });

  const ask = useMutation({
    mutationFn: (question: string) => aiApi.ask(question, range),
    onSuccess: (answer, question) => {
      setExchanges((previous) => [...previous, { question, answer }]);
      setInput('');
      // Scroll after the DOM has the new turn in it. Guarded because
      // `scrollTo` is not implemented everywhere the component is rendered —
      // a test environment throws on it, and an unhandled throw here would take
      // the answer down with it.
      window.setTimeout(() => {
        const transcript = transcriptRef.current;
        if (typeof transcript?.scrollTo === 'function') {
          transcript.scrollTo({
            top: transcript.scrollHeight,
            behavior: 'smooth',
          });
        }
      }, 0);
    },
  });

  if (!permitted) {
    return null;
  }

  const submit = (question: string) => {
    const trimmed = question.trim();
    if (trimmed.length === 0 || ask.isPending) return;
    ask.mutate(trimmed);
  };

  return (
    <Card className={className}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-gray-800 dark:text-gray-200 flex items-center gap-2">
            <Bot size={16} className="text-blue-500" /> Ask about the business
          </h3>
          <span className="text-xs text-gray-400">Read-only</span>
        </div>
      </CardHeader>

      <CardContent className="space-y-4">
        <p className="text-xs text-gray-500 dark:text-gray-400">
          Answers are computed from your recorded transactions and always state the
          period they cover. This assistant cannot change stock, approve a payment
          or post an accounting entry.
        </p>

        {/* ── Transcript ─────────────────────────────────────────────────── */}
        <div
          ref={transcriptRef}
          className="space-y-3 max-h-[420px] overflow-y-auto pr-1"
          aria-live="polite"
        >
          {exchanges.length === 0 && (
            <div className="rounded-lg bg-gray-50 dark:bg-gray-700/30 p-4">
              <p className="text-sm text-gray-600 dark:text-gray-400">
                Ask a question and the answer will show the figures it used.
              </p>
              {range && (
                <p className="mt-1 text-xs text-gray-400">
                  Your dashboard filter ({range.from} to {range.to}) will be
                  applied unless the question names a different period.
                </p>
              )}
            </div>
          )}

          {exchanges.map((exchange, index) => (
            <div key={index} className="space-y-2">
              <div className="flex justify-end">
                <p className="rounded-lg bg-blue-600 px-3 py-2 text-sm text-white max-w-[85%]">
                  {exchange.question}
                </p>
              </div>
              <AnswerBlock answer={exchange.answer} />
            </div>
          ))}

          {ask.isPending && (
            <div className="flex items-center gap-2 text-sm text-gray-500">
              <Loader2 size={14} className="animate-spin" /> Looking…
            </div>
          )}

          {ask.isError && (
            <div className="rounded-lg bg-red-50 dark:bg-red-900/20 p-3">
              <p className="text-sm text-red-700 dark:text-red-400 flex items-center gap-2">
                <AlertTriangle size={14} /> That question could not be answered.
              </p>
              <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                If you do not have permission for this information, ask a manager.
              </p>
            </div>
          )}
        </div>

        {/* ── Suggestions ─────────────────────────────────────────────────── */}
        {exchanges.length === 0 && (
          <div className="flex flex-wrap gap-1.5">
            {SUGGESTED_QUESTIONS.map((question) => (
              <button
                key={question}
                type="button"
                onClick={() => submit(question)}
                disabled={ask.isPending}
                className="rounded-full border border-gray-200 dark:border-gray-600 px-3 py-1 text-xs text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700/50 disabled:opacity-50"
              >
                {question}
              </button>
            ))}
          </div>
        )}

        {/* ── Input ───────────────────────────────────────────────────────── */}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            submit(input);
          }}
          className="flex items-center gap-2"
        >
          <label htmlFor="assistant-question" className="sr-only">
            Ask a question about the business
          </label>
          <input
            id="assistant-question"
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ask about sales, profit, stock or balances…"
            disabled={ask.isPending}
            className="flex-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 text-sm text-gray-800 dark:text-gray-200 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50"
          />
          <button
            type="submit"
            disabled={ask.isPending || input.trim().length === 0}
            aria-label="Send question"
            className="rounded-lg bg-blue-600 p-2 text-white hover:bg-blue-700 disabled:opacity-40"
          >
            <Send size={16} />
          </button>
        </form>

        {capabilities.data && capabilities.data.length > 0 && (
          <details className="text-xs">
            <summary className="cursor-pointer text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
              What this can answer
            </summary>
            <ul className="mt-2 space-y-0.5 text-gray-500 dark:text-gray-400 list-disc pl-4">
              {capabilities.data.map((capability) => (
                <li key={capability}>{capability}</li>
              ))}
            </ul>
          </details>
        )}
      </CardContent>
    </Card>
  );
}

/** One answer, with its period, figures, sources and assumptions. */
function AnswerBlock({ answer }: { answer: AssistantAnswer }) {
  const outcomeVariant =
    answer.outcome === 'ANSWERED'
      ? 'success'
      : answer.outcome === 'INSUFFICIENT_DATA'
        ? 'warning'
        : 'info';

  const outcomeLabel =
    answer.outcome === 'ANSWERED'
      ? null
      : answer.outcome === 'INSUFFICIENT_DATA'
        ? 'Not enough data'
        : answer.outcome === 'CLARIFICATION_NEEDED'
          ? 'Needs clarifying'
          : 'Not permitted';

  return (
    <div className="rounded-lg border border-gray-200 dark:border-gray-700 p-3 max-w-[95%]">
      <div className="flex items-center gap-2 flex-wrap mb-2">
        {outcomeLabel && <Badge variant={outcomeVariant}>{outcomeLabel}</Badge>}
        {answer.period && (
          <span className="text-xs text-gray-400">
            {answer.period.label}: {answer.period.from} – {answer.period.to}
          </span>
        )}
      </div>

      <p className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-line">
        {answer.answer}
      </p>

      {answer.note && (
        <p className="mt-2 text-xs text-gray-500 dark:text-gray-400 border-l-2 border-gray-300 dark:border-gray-600 pl-2">
          {answer.note}
        </p>
      )}

      {answer.sources.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-3 text-xs">
          <span className="text-gray-400">Verify:</span>
          {answer.sources.map((source) => (
            <Link
              key={source}
              href={source}
              className="text-blue-600 hover:underline flex items-center gap-1"
            >
              {source.replace(/^\//, '').replace(/-/g, ' ')}
              <ExternalLink size={11} />
            </Link>
          ))}
        </div>
      )}

      {answer.assumptions.length > 0 && (
        <details className="mt-2 text-xs">
          <summary className="cursor-pointer text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
            What these figures assume
          </summary>
          <ul className="mt-1.5 space-y-1 text-gray-500 dark:text-gray-400 list-disc pl-4">
            {answer.assumptions.map((assumption) => (
              <li key={assumption}>{assumption}</li>
            ))}
          </ul>
        </details>
      )}

      <KeyFigures data={answer.data} />
    </div>
  );
}

/**
 * The figures behind the sentence, when they are simple enough to show.
 *
 * <p>Only one level deep, and only scalar values: a nested structure here would
 * be a report in miniature, and the reader has a link for that. This exists so
 * the number in the sentence can be checked against the number in the data
 * without opening anything.
 */
function KeyFigures({ data }: { data: Record<string, unknown> }) {
  const entries = Object.entries(data).filter(
    ([key, value]) =>
      typeof value === 'number' &&
      !/count|Count|version|sampleSize|days|observations/i.test(key),
  );
  if (entries.length === 0) {
    return null;
  }

  return (
    <dl className="mt-3 grid grid-cols-2 sm:grid-cols-3 gap-x-4 gap-y-2 rounded bg-gray-50 dark:bg-gray-700/40 p-2.5">
      {entries.slice(0, 9).map(([key, value]) => (
        <div key={key} className="min-w-0">
          <dt className="text-[10px] uppercase tracking-wide text-gray-400 truncate">
            {key.replace(/([A-Z])/g, ' $1')}
          </dt>
          <dd className="text-xs font-semibold text-gray-700 dark:text-gray-300 truncate">
            {formatCurrency(value as number)}
          </dd>
        </div>
      ))}
    </dl>
  );
}