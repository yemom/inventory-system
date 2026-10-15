'use client';
import React, { useCallback, useMemo, useState } from 'react';
import {
  AlertTriangle,
  LogOut,
  MessageSquare,
  Plus,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import Link from 'next/link';
import {
  clearCustomerSession,
  CustomerChatError,
  customerChatbotApi,
  loadCustomerSession,
  saveCustomerSession,
  StoredCustomerSession,
} from '@/lib/api/chatbotApi';
import ChatThread, { ChatMessage } from '@/components/chat/ChatThread';
import Badge from '@/components/ui/Badge';
import { useToast } from '@/components/ui/ToastProvider';

/**
 * Customer support chat.
 *
 * <p>A customer has no staff account, so there is no login
 * page for them and no role to check. Instead, a shopper
 * proves they can see one order — by naming the email and
 * the order number printed on their receipt — and is handed
 * a short-lived token that stands for "this customer".
 *
 * <p>Every question after that is answered from their own
 * rows alone, in the database query itself. Another
 * customer's orders are not hidden by a filter that could
 * be forgotten; they are unreachable, because the query
 * cannot be written without the caller's own customer id.
 *
 * <p>The assistant cannot take an order or issue a refund,
 * and says so: those need a person at a till. What it can
 * do is answer from the catalogue and the customer's own
 * order history, and hand the conversation to a human.
 */

/** What a shopper can ask about. No permission needed: the session is the scope. */
const CUSTOMER_SUGGESTIONS = [
  'What phones do you sell?',
  'Do you have any phone chargers in stock?',
  'Where is my order SO-000123?',
  'Show my recent orders.',
  'How do I return something?',
  'How can I pay?',
  'I would like to speak to someone.',
];

export default function CustomerSupportPage() {
  const { toast } = useToast();

  const [session, setSession] = useState<StoredCustomerSession | null>(
    () => loadCustomerSession(),
  );
  const [email, setEmail] = useState('');
  const [orderNumber, setOrderNumber] = useState('');
  const [signingIn, setSigningIn] = useState(false);
  const [signInError, setSignInError] = useState<string | null>(null);

  const [conversationId, setConversationId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastQuestion, setLastQuestion] = useState<string | null>(null);
  const [listOpen, setListOpen] = useState(false);
  const [conversations, setConversations] = useState<
    Array<{ id: number; title: string | null }>
  >([]);

  // A conversation list for this customer, loaded once the
  // session exists and refreshed after each question.
  React.useEffect(() => {
    if (!session) return;
    customerChatbotApi
      .conversations(session.token)
      .then((rows) =>
        setConversations(
          rows.map((row) => ({ id: row.id, title: row.title })),
        ),
      )
      .catch(() => setConversations([]));
  }, [session, messages.length]);

  const signIn = useCallback(
    async (event: React.FormEvent) => {
      event.preventDefault();
      if (signingIn) return;
      setSigningIn(true);
      setSignInError(null);
      try {
        const opened = await customerChatbotApi.openSession(
          email,
          orderNumber,
        );
        const stored = saveCustomerSession(opened, email);
        setSession(stored);
        setMessages([]);
        setConversationId(null);
        setConversations([]);
      } catch (e) {
        // The message is the same whether the email is wrong,
        // the order number is wrong, or the account is closed.
        setSignInError(
          e instanceof Error ? e.message : 'Could not open a session.',
        );
      } finally {
        setSigningIn(false);
      }
    },
    [email, orderNumber, signingIn],
  );

  const signOut = useCallback(() => {
    if (session) {
      void customerChatbotApi.closeSession(session.token);
    }
    clearCustomerSession();
    setSession(null);
    setMessages([]);
    setConversationId(null);
    setConversations([]);
  }, [session]);

  const send = useCallback(
    async (question: string) => {
      if (loading || !session) return;
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
        const reply = await customerChatbotApi.ask(
          session.token,
          question,
          conversationId,
        );
        if (reply.conversationId != null && conversationId == null) {
          setConversationId(reply.conversationId);
        }
        setMessages((previous) => [
          ...previous,
          {
            id: `assistant-${Date.now()}`,
            role: 'assistant',
            content: reply.text,
            conversationId: reply.conversationId,
            deterministic: reply.deterministic,
            guardIntervened: reply.guardIntervened,
            note: reply.note,
            toolResults: reply.toolResults,
            createdAt: new Date().toISOString(),
          },
        ]);
      } catch (e) {
        const expired =
          e instanceof CustomerChatError && e.sessionExpired;
        if (expired) {
          // The token stopped working. The only correct move
          // is to prove who you are again.
          setError(
            'Your session has expired. Sign in again with your email and an order number.',
          );
          signOut();
        } else {
          setError(
            e instanceof Error
              ? e.message
              : 'That question could not be answered. Please try again.',
          );
        }
      } finally {
        setLoading(false);
      }
    },
    [loading, session, conversationId, signOut],
  );

  const retry = useCallback(() => {
    if (lastQuestion) void send(lastQuestion);
  }, [lastQuestion, send]);

  const openConversation = useCallback(
    async (id: number) => {
      if (!session) return;
      setConversationId(id);
      setError(null);
      setListOpen(false);
      try {
        const history = await customerChatbotApi.history(session.token, id);
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
    [session, toast],
  );

  const newConversation = useCallback(() => {
    setConversationId(null);
    setMessages([]);
    setError(null);
    setListOpen(false);
  }, []);

  const removeConversation = useCallback(
    async (id: number) => {
      if (!session) return;
      try {
        await customerChatbotApi.deleteConversation(session.token, id);
        setConversations((previous) =>
          previous.filter((row) => row.id !== id),
        );
        if (id === conversationId) {
          newConversation();
        }
        toast('success', 'Conversation deleted');
      } catch {
        toast('error', 'Could not delete this conversation');
      }
    },
    [session, conversationId, newConversation, toast],
  );

  // ── Not signed in: prove it is your order ────────────────
  if (!session) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
        <div className="w-full max-w-md">
          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
              StockFlow Support
            </h1>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
              Ask about products, your orders, returns and payments.
            </p>
          </div>

          <form
            onSubmit={signIn}
            className="mt-6 rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800"
          >
            <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <ShieldCheck size={14} className="text-blue-600" />
              Sign in with the email and order number on your receipt.
              No password is needed or stored.
            </div>

            <label
              htmlFor="customer-email"
              className="mt-4 block text-xs font-medium text-gray-600 dark:text-gray-400"
            >
              Email address
            </label>
            <input
              id="customer-email"
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            />

            <label
              htmlFor="customer-order"
              className="mt-3 block text-xs font-medium text-gray-600 dark:text-gray-400"
            >
              Order number
            </label>
            <input
              id="customer-order"
              required
              value={orderNumber}
              onChange={(event) => setOrderNumber(event.target.value)}
              placeholder="SO-000123"
              className="mt-1 w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
            />

            {signInError && (
              <p
                role="alert"
                className="mt-3 rounded-lg bg-red-50 p-2.5 text-xs text-red-700 dark:bg-red-900/20 dark:text-red-400"
              >
                {signInError}
              </p>
            )}

            <button
              type="submit"
              disabled={signingIn || email.trim().length === 0 || orderNumber.trim().length === 0}
              className="mt-4 w-full rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-40"
            >
              {signingIn ? 'Checking…' : 'Continue to the assistant'}
            </button>

            <p className="mt-3 text-center text-[11px] text-gray-400">
              The token this creates works for this conversation and
              expires. It is never used for anything else.
            </p>
          </form>

          <p className="mt-4 text-center text-xs text-gray-400">
            Are you a member of staff?{' '}
            <Link href="/login" className="text-blue-600 hover:underline dark:text-blue-400">
              Sign in to the dashboard
            </Link>
          </p>
        </div>
      </div>
    );
  }

  // ── Signed in: the chat ──────────────────────────────────
  return (
    <div className="flex min-h-screen flex-col bg-gray-50 dark:bg-gray-900">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800">
        <div>
          <h1 className="text-lg font-bold text-gray-900 dark:text-gray-100">
            StockFlow Support
          </h1>
          <p className="text-xs text-gray-500 dark:text-gray-400">
            Signed in as {session.customerName} ·{' '}
            {session.email}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge variant="success">Verified order</Badge>
          <button
            type="button"
            onClick={signOut}
            className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-400 dark:hover:bg-gray-700"
          >
            <LogOut size={13} /> Sign out
          </button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={newConversation}
              className="inline-flex items-center gap-1.5 rounded-lg bg-blue-600 px-3 py-1.5 text-xs text-white hover:bg-blue-700"
            >
              <Plus size={14} /> New chat
            </button>
            <button
              type="button"
              onClick={() => setListOpen((open) => !open)}
              aria-label="Open conversation list"
              className="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 px-2.5 py-1.5 text-xs text-gray-600 hover:bg-gray-50 dark:border-gray-600 dark:text-gray-400 dark:hover:bg-gray-700/50"
            >
              <MessageSquare size={14} /> History ({conversations.length})
            </button>
          </div>
          <span className="text-[11px] text-gray-400">
            Read-only · cannot place orders or issue refunds
          </span>
        </div>

        {listOpen && (
          <aside
            className="flex flex-col gap-1.5 rounded-lg border border-gray-200 bg-white p-2 dark:border-gray-700 dark:bg-gray-800"
            aria-label="Your conversations"
          >
            {conversations.length === 0 && (
              <p className="px-1.5 py-1 text-xs text-gray-400">
                No conversations yet. Ask a question to start one.
              </p>
            )}
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                className="group flex items-center gap-1 rounded-lg px-2 py-1.5 text-xs text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-700/50"
              >
                <button
                  type="button"
                  onClick={() => void openConversation(conversation.id)}
                  className="min-w-0 flex-1 truncate text-left"
                >
                  {conversation.title ?? `Conversation ${conversation.id}`}
                </button>
                <button
                  type="button"
                  onClick={() => void removeConversation(conversation.id)}
                  aria-label={`Delete conversation ${conversation.id}`}
                  className="rounded p-0.5 text-gray-400 hover:text-red-600"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            ))}
          </aside>
        )}

        <section
          className="flex min-h-[60vh] flex-1 flex-col rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-700 dark:bg-gray-800"
          aria-label="Assistant"
        >
          <div className="mb-2">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              I can answer from the product catalogue and your own orders.
              I cannot see other customers, and I cannot take an order —
              for that, ask a member of staff.
            </p>
          </div>
          <ChatThread
            messages={messages}
            loading={loading}
            error={error}
            suggestions={CUSTOMER_SUGGESTIONS}
            showSuggestions
            onSend={(question) => void send(question)}
            onRetry={retry}
            placeholder="Ask about a product or your order…"
            ariaLabel="Customer assistant"
          />
        </section>

        {error && error.includes('expired') && (
          <p role="alert" className="flex items-center gap-2 rounded-lg bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
            <AlertTriangle size={14} /> {error}
          </p>
        )}
      </main>
    </div>
  );
}
