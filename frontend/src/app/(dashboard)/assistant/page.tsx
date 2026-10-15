'use client';
import React, { useCallback, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  MessageSquare,
  Plus,
  Trash2,
} from 'lucide-react';
import { useAuth } from '@/lib/auth/AuthProvider';
import {
  chatbotApi,
  ChatCapability,
  ChatConversationSummary,
} from '@/lib/api/chatbotApi';
import ChatThread, { ChatMessage } from '@/components/chat/ChatThread';
import Badge from '@/components/ui/Badge';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * The staff assistant.
 *
 * <p>A chat, not a search box: questions continue a
 * conversation, and the conversation belongs to the signed-in
 * user alone. What the assistant may answer is derived from
 * that user's own backend permissions on every question, so
 * the suggestion chips, the answers and the permission checks
 * cannot drift apart — a promotion changes what the assistant
 * says without a code change, and a demotion changes it just
 * as fast.
 *
 * <p>What it deliberately cannot do: place an order, record a
 * payment, move stock, approve anything. There is no code path
 * here that writes, which is why no prompt can make it write.
 */

/** Questions a member of staff might ask, and what each needs. */
const STAFF_SUGGESTIONS: Array<{
  question: string;
  anyOf: string[];
}> = [
  {
    question: 'What was our net profit this month?',
    anyOf: ['PROFIT_LOSS_READ'],
  },
  {
    question: 'Compare sales this month with last month.',
    anyOf: ['FINANCIAL_REPORT_VIEW'],
  },
  {
    question: 'What were today\u2019s cash inflows and outflows?',
    anyOf: ['FINANCIAL_REPORT_VIEW'],
  },
  {
    question: 'Show purchases and supplier payments that remain outstanding.',
    anyOf: ['PAYABLE_READ'],
  },
  {
    question: 'Which customers have outstanding balances?',
    anyOf: ['RECEIVABLE_READ'],
  },
  {
    question: 'Which products are likely to run out in the next seven days?',
    anyOf: ['INVENTORY_READ'],
  },
  {
    question: 'Which products are selling slowly?',
    anyOf: ['INVENTORY_READ'],
  },
  {
    question: 'What is our inventory worth at cost?',
    anyOf: ['INVENTORY_VALUE_VIEW'],
  },
  {
    question: 'Which warehouse has the most stock discrepancies?',
    anyOf: ['STOCK_MOVEMENT_READ', 'INVENTORY_READ'],
  },
  {
    question: 'Do you sell anything like a phone charger?',
    anyOf: ['PRODUCT_READ', 'INVENTORY_READ'],
  },
];

/** Reporting windows, so a question covers the window the reader meant. */
const PERIODS = [
  { key: 'today', label: 'Today' },
  { key: 'week', label: 'Last 7 days' },
  { key: 'month', label: 'This month' },
  { key: 'lastMonth', label: 'Last month' },
  { key: 'all', label: 'All recorded' },
] as const;

type PeriodKey = (typeof PERIODS)[number]['key'];

/** The ISO dates a preset covers. */
function periodRange(key: PeriodKey): { from?: string; to?: string } {
  const today = new Date();
  const iso = (date: Date) => date.toISOString().slice(0, 10);
  switch (key) {
    case 'today':
      return { from: iso(today), to: iso(today) };
    case 'week':
      return {
        from: iso(new Date(today.getTime() - 6 * 86_400_000)),
        to: iso(today),
      };
    case 'month':
      return {
        from: iso(new Date(today.getFullYear(), today.getMonth(), 1)),
        to: iso(today),
      };
    case 'lastMonth': {
      const first = new Date(today.getFullYear(), today.getMonth() - 1, 1);
      return {
        from: iso(first),
        to: iso(new Date(today.getFullYear(), today.getMonth(), 0)),
      };
    }
    case 'all':
      return {};
  }
}

export default function AssistantPage() {
  const { hasPermission, user } = useAuth();
  const { toast } = useToast();
  const permitted = hasPermission('AI_ASSISTANT_USE');

  const [conversationId, setConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastQuestion, setLastQuestion] = useState<string | null>(null);
  const [period, setPeriod] = useState<PeriodKey>('all');
  const [listOpen, setListOpen] = useState(false);

  const range = useMemo(() => periodRange(period), [period]);

  const suggestions = useMemo(
    () =>
      STAFF_SUGGESTIONS.filter((suggestion) =>
        suggestion.anyOf.some((permission) => hasPermission(permission)),
      ).map((suggestion) => suggestion.question),
    [hasPermission],
  );

  const capabilities = useQuery({
    queryKey: ['chatbot', 'capabilities'],
    queryFn: () => chatbotApi.capabilities(),
    enabled: permitted,
    staleTime: 10 * 60 * 1000,
    retry: false,
  });

  const conversations = useQuery({
    queryKey: ['chatbot', 'conversations'],
    queryFn: () => chatbotApi.conversations(),
    enabled: permitted,
    staleTime: 30 * 1000,
    retry: false,
  });

  const send = useCallback(
    async (question: string) => {
      if (loading) return;
      setLoading(true);
      setError(null);
      setLastQuestion(question);

      const userMessage: ChatMessage = {
        id: `user-${Date.now()}`,
        role: 'user',
        content: question,
      };
      setMessages((previous) => [...previous, userMessage]);

      try {
        const reply = await chatbotApi.ask(
          question,
          conversationId,
          Object.keys(range).length > 0 ? range : undefined,
        );
        if (reply.conversationId != null && conversationId == null) {
          setConversationId(reply.conversationId);
        }
        const assistantMessage: ChatMessage = {
          id: `assistant-${Date.now()}`,
          role: 'assistant',
          content: reply.text,
          conversationId: reply.conversationId,
          deterministic: reply.deterministic,
          guardIntervened: reply.guardIntervened,
          note: reply.note,
          toolResults: reply.toolResults,
          createdAt: new Date().toISOString(),
        };
        setMessages((previous) => [...previous, assistantMessage]);
        // The list gains a conversation the moment the first
        // question is answered, not when the page is reloaded.
        conversations.refetch();
      } catch (e) {
        const status = (e as { response?: { status?: number } })?.response
          ?.status;
        const message =
          (e as { response?: { data?: { message?: string } } })?.response
            ?.data?.message;
        setError(
          status === 403
            ? 'Your role does not have access to the information that answer needs. Ask a manager.'
            : message ??
                'The assistant could not be reached. Check the connection and try again.',
        );
      } finally {
        setLoading(false);
      }
    },
    [loading, conversationId, range, conversations],
  );

  const retry = useCallback(() => {
    if (lastQuestion) void send(lastQuestion);
  }, [lastQuestion, send]);

  const openConversation = useCallback(
    async (id: number) => {
      setConversationId(id);
      setError(null);
      setListOpen(false);
      try {
        const history = await chatbotApi.history(id);
        setMessages(
          history.map((message) => ({
            id: String(message.id),
            role: message.role,
            content: message.content,
            deterministic: message.deterministic,
            createdAt: message.createdAt,
          })),
        );
      } catch {
        toast('error', 'Could not load this conversation');
      }
    },
    [toast],
  );

  const newConversation = useCallback(() => {
    setConversationId(null);
    setMessages([]);
    setError(null);
    setListOpen(false);
  }, []);

  const removeConversation = useCallback(
    async (id: number) => {
      try {
        await chatbotApi.deleteConversation(id);
        conversations.refetch();
        if (id === conversationId) {
          newConversation();
        }
        toast('success', 'Conversation deleted');
      } catch {
        toast('error', 'Could not delete this conversation');
      }
    },
    [conversationId, conversations, newConversation, toast],
  );

  if (!permitted) {
    return (
      <div className="rounded-lg border border-gray-200 p-8 text-center dark:border-gray-700">
        <AlertTriangle className="mx-auto text-amber-500" size={24} />
        <h1 className="mt-3 text-lg font-semibold text-gray-800 dark:text-gray-200">
          The assistant is not available to your role
        </h1>
        <p className="mx-auto mt-1 max-w-md text-sm text-gray-500 dark:text-gray-400">
          The AI Assistant needs the AI_ASSISTANT_USE permission. Ask a Super
          Admin or Manager to grant it to your role.
        </p>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100vh-9rem)] min-h-[26rem] flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100">
            AI Assistant
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            Answers from live records, for {user?.fullName ?? user?.username}
            . Read-only: it cannot place orders, take payments or move stock.
          </p>
        </div>
        <div className="flex items-center gap-2">
          {/* The reporting window the answers cover. */}
          <div className="flex items-center gap-1 rounded-lg border border-gray-200 p-0.5 dark:border-gray-700">
            {PERIODS.map((preset) => (
              <button
                key={preset.key}
                type="button"
                onClick={() => setPeriod(preset.key)}
                aria-pressed={period === preset.key}
                className={`rounded-md px-2 py-1 text-xs ${
                  period === preset.key
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-500 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700/50'
                }`}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <button
            type="button"
            onClick={newConversation}
            className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs text-white hover:bg-blue-700"
          >
            <Plus size={14} /> New chat
          </button>
          {/* Conversation list, on mobile behind a button. */}
          <button
            type="button"
            onClick={() => setListOpen((open) => !open)}
            aria-label="Open conversation list"
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs text-gray-600 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-700/50 sm:hidden"
          >
            <MessageSquare size={14} /> History
          </button>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 gap-4">
        {/* ── Conversation list ─────────────────────────────── */}
        <aside
          className={`w-full shrink-0 flex-col gap-1.5 overflow-y-auto rounded-lg border border-gray-200 p-2 dark:border-gray-700 sm:flex sm:w-56 ${
            listOpen ? 'flex' : 'hidden'
          }`}
          aria-label="Your conversations"
        >
          <p className="px-1.5 py-1 text-[10px] font-medium uppercase tracking-wide text-gray-400">
            Conversations
          </p>
          {(conversations.data ?? []).length === 0 && (
            <p className="px-1.5 py-1 text-xs text-gray-400">
              No conversations yet. Ask a question to start one.
            </p>
          )}
          {(conversations.data ?? []).map(
            (conversation: ChatConversationSummary) => (
              <div
                key={conversation.id}
                className={`group flex items-center gap-1 rounded-lg px-2 py-1.5 text-xs ${
                  conversation.id === conversationId
                    ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
                    : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700/50'
                }`}
              >
                <button
                  type="button"
                  onClick={() => void openConversation(conversation.id)}
                  className="min-w-0 flex-1 truncate text-left"
                  title={conversation.title ?? undefined}
                >
                  {conversation.title ??
                    `Conversation ${conversation.id}`}
                </button>
                <button
                  type="button"
                  onClick={() => void removeConversation(conversation.id)}
                  aria-label={`Delete conversation ${conversation.id}`}
                  className="rounded p-0.5 text-gray-400 hover:text-red-600 group-hover:visible invisible"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            ),
          )}
          {capabilities.data && (
            <details className="mt-2 text-xs">
              <summary className="cursor-pointer px-1.5 text-gray-500 hover:text-gray-700 dark:hover:text-gray-300">
                What this can answer
              </summary>
              <ul className="mt-1 space-y-1 px-1.5 text-gray-500 dark:text-gray-400">
                {(capabilities.data as ChatCapability[]).map((capability) => (
                  <li key={capability.name} className="text-[11px]">
                    {capability.description}
                  </li>
                ))}
              </ul>
            </details>
          )}
        </aside>

        {/* ── The chat ──────────────────────────────────────── */}
        <main className="min-w-0 flex-1 rounded-lg border border-gray-200 p-3 dark:border-gray-700">
          <div className="mb-2 flex items-center gap-2">
            <Badge variant="default">Read-only</Badge>
            {Object.keys(range).length > 0 && (
              <span className="text-xs text-gray-400">
                Answers cover {range.from} to {range.to}
              </span>
            )}
          </div>
          <ChatThread
            messages={messages}
            loading={loading}
            error={error}
            suggestions={suggestions}
            showSuggestions
            onSend={(question) => void send(question)}
            onRetry={retry}
            placeholder="Ask about sales, profit, stock, balances…"
            ariaLabel="Staff assistant"
          />
        </main>
      </div>
    </div>
  );
}
