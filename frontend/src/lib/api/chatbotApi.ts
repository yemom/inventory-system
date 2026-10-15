import axios from 'axios';
import apiClient, { getApiBaseUrl } from './apiClient';

/**
 * Client for the chatbot.
 *
 * <p>Two channels, two clients:
 *
 * <ul>
 *   <li><b>Staff</b> calls go through the shared {@link apiClient},
 *       which attaches the staff JWT. The assistant answers from the
 *       caller's own permissions, resolved on the server from the
 *       token — the same authorities the rest of the dashboard uses.</li>
 *   <li><b>Customer</b> calls go through a bare client. A customer
 *       has no staff JWT; they hold a short-lived session token that
 *       is sent with the request. That client deliberately has no
 *       session-expiry interceptor: a customer session expiring must
 *       not log a member of staff out of the dashboard, and the
 *       customer sign-in screen must not be torn down by an event it
 *       has nothing to do with.</li>
 * </ul>
 *
 * <p>Every figure in an answer comes from the backend's calculation
 * services. Nothing here computes, rounds or re-derives a number —
 * the browser only displays what the server produced.
 */

/** One capability the assistant may use, and what it costs to use it. */
export interface ChatCapability {
  name: string;
  description: string;
  requiresPermission: string;
}

/** An assistant reply, with the figures behind it. */
export interface ChatReply {
  conversationId: number | null;
  text: string;
  /** True when the answer was rendered from the tool results directly. */
  deterministic: boolean;
  /** True when a phrased answer was replaced for inventing a figure. */
  guardIntervened: boolean;
  note: string | null;
  /** The tool results the answer was built from, keyed by tool name. */
  toolResults: Record<string, unknown>;
}

/** A conversation in the caller's own list. */
export interface ChatConversationSummary {
  id: number;
  title: string | null;
  createdAt: string | null;
  lastMessageAt: string | null;
}

/** One message in a transcript. */
export interface ChatHistoryMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  toolName: string | null;
  deterministic: boolean;
  createdAt: string | null;
}

/** A customer session, as issued. The token is shown once. */
export interface CustomerSession {
  token: string;
  customerName: string;
  expiresAt: string;
  note: string;
}

/** The session as kept in the browser, so a reload keeps the chat. */
export interface StoredCustomerSession extends CustomerSession {
  /** The email the session was opened with, for the "signed in as" line. */
  email: string;
}

const unwrap = <T,>(response: { data?: { data?: T } | T }): T => {
  const payload = response.data as { data?: T };
  return (payload?.data ?? (response.data as T)) as T;
};

/** A customer's error, with the server's code. */
export class CustomerChatError extends Error {
  constructor(
    message: string,
    /** True when the session has expired and the customer must sign in again. */
    public readonly sessionExpired: boolean,
  ) {
    super(message);
  }
}

/**
 * The customer channel's HTTP client.
 *
 * <p>Bare on purpose: no interceptors, no shared state, no
 * side effects. Its only job is to carry the session token.
 */
const customerClient = axios.create({
  baseURL: getApiBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
  timeout: 60_000,
});

/** Reads a customer call's failure as the typed error the UI shows. */
function customerError(error: unknown, fallback: string): CustomerChatError {
  const status = (error as { response?: { status?: number } })?.response?.status;
  if (status === 401) {
    return new CustomerChatError(
      'This session has expired. Sign in again with your email and an order number.',
      true,
    );
  }
  const message =
    (error as { response?: { data?: { message?: string } } })?.response?.data
      ?.message;
  return new CustomerChatError(message ?? fallback, false);
}

export const chatbotApi = {
  /**
   * What this caller may ask about.
   *
   * <p>Derived from the caller's own authorities on the server, so
   * the suggestion chips can never offer a question the assistant
   * would refuse to answer.
   */
  capabilities: async (): Promise<ChatCapability[]> => {
    const response = await apiClient.get('/chatbot/capabilities');
    return unwrap<ChatCapability[]>(response);
  },

  /**
   * Ask a question.
   *
   * <p>The optional period is sent as ISO dates so the answer covers
   * the window the reader chose, not whichever one the question's
   * wording happens to imply.
   */
  ask: async (
    question: string,
    conversationId: number | null,
    range?: { from?: string; to?: string },
  ): Promise<ChatReply> => {
    const response = await apiClient.post('/chatbot/ask', {
      question,
      conversationId,
      from: range?.from,
      to: range?.to,
    });
    return unwrap<ChatReply>(response);
  },

  conversations: async (): Promise<ChatConversationSummary[]> => {
    const response = await apiClient.get('/chatbot/conversations');
    return unwrap<ChatConversationSummary[]>(response);
  },

  startConversation: async (title?: string): Promise<ChatConversationSummary> => {
    const response = await apiClient.post('/chatbot/conversations', { title });
    return unwrap<ChatConversationSummary>(response);
  },

  history: async (conversationId: number): Promise<ChatHistoryMessage[]> => {
    const response = await apiClient.get(`/chatbot/conversations/${conversationId}`);
    return unwrap<ChatHistoryMessage[]>(response);
  },

  /**
   * Delete a conversation, messages and all.
   *
   * <p>The server checks ownership, so an id alone deletes nothing.
   */
  deleteConversation: async (conversationId: number): Promise<void> => {
    await apiClient.delete(`/chatbot/conversations/${conversationId}`);
  },
};

export const customerChatbotApi = {
  /**
   * Open a session with the email and order number on a receipt.
   *
   * <p>The pair is the proof: both are printed on paperwork the
   * customer already holds. A wrong pair and a right pair for a
   * closed account fail identically, so neither can be probed for
   * the other.
   */
  openSession: async (email: string, orderNumber: string): Promise<CustomerSession> => {
    try {
      const response = await customerClient.post('/chatbot/customer/session', {
        email,
        orderNumber,
      });
      return unwrap<CustomerSession>(response);
    } catch (error) {
      throw customerError(error, 'Could not open a session. Check the email and order number and try again.');
    }
  },

  ask: async (
    token: string,
    question: string,
    conversationId: number | null,
  ): Promise<ChatReply> => {
    try {
      const response = await customerClient.post('/chatbot/customer/ask', {
        token,
        question,
        conversationId,
      });
      return unwrap<ChatReply>(response);
    } catch (error) {
      throw customerError(error, 'That question could not be answered. Please try again.');
    }
  },

  conversations: async (token: string): Promise<ChatConversationSummary[]> => {
    try {
      const response = await customerClient.get('/chatbot/customer/conversations', {
        headers: { 'X-Chatbot-Token': token },
      });
      return unwrap<ChatConversationSummary[]>(response);
    } catch (error) {
      throw customerError(error, 'Could not load your conversations.');
    }
  },

  history: async (token: string, conversationId: number): Promise<ChatHistoryMessage[]> => {
    try {
      const response = await customerClient.get(
        `/chatbot/customer/conversations/${conversationId}`,
        { headers: { 'X-Chatbot-Token': token } },
      );
      return unwrap<ChatHistoryMessage[]>(response);
    } catch (error) {
      throw customerError(error, 'Could not load this conversation.');
    }
  },

  deleteConversation: async (token: string, conversationId: number): Promise<void> => {
    try {
      await customerClient.delete(`/chatbot/customer/conversations/${conversationId}`, {
        headers: { 'X-Chatbot-Token': token },
      });
    } catch (error) {
      throw customerError(error, 'Could not delete this conversation.');
    }
  },

  /** Ends the session. The token stops working immediately. */
  closeSession: async (token: string): Promise<void> => {
    try {
      await customerClient.delete('/chatbot/customer/session', {
        headers: { 'X-Chatbot-Token': token },
      });
    } catch {
      // Signing out must never fail loudly: an expired token is
      // already signed out, which is the state being asked for.
    }
  },
};

/** Where the customer session is kept in the browser. */
export const CUSTOMER_SESSION_KEY = 'chatbot_customer_session';

/** The stored session, or null when there is none (or it expired). */
export function loadCustomerSession(): StoredCustomerSession | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = localStorage.getItem(CUSTOMER_SESSION_KEY);
    if (!raw) return null;
    const session = JSON.parse(raw) as StoredCustomerSession;
    if (!session?.token) return null;
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(CUSTOMER_SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

/** Keeps the session across reloads. */
export function saveCustomerSession(
  session: CustomerSession,
  email: string,
): StoredCustomerSession {
  const stored: StoredCustomerSession = { ...session, email };
  localStorage.setItem(CUSTOMER_SESSION_KEY, JSON.stringify(stored));
  return stored;
}

/** Forgets the session. */
export function clearCustomerSession(): void {
  localStorage.removeItem(CUSTOMER_SESSION_KEY);
}
