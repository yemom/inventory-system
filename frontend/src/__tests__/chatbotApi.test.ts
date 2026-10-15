/**
 * The chatbot's client, both channels.
 *
 * These tests pin the properties the chatbot UI depends on and that are
 * invisible once the screens are rendered:
 *
 * 1. **Two channels, two clients.** Staff calls ride the shared client that
 *    carries the staff JWT; customer calls ride a bare client that carries
 *    only the short-lived session token. A channel mix-up would either send
 *    a customer's token into the staff auth stack or answer a staff question
 *    without the caller's authorities.
 *
 * 2. **No provider secret, anywhere, ever.** The browser holds no LLM API
 *    key. Every request this module can build is scanned for one.
 *
 * 3. **A customer's failure is typed.** An expired session (401) must be
 *    distinguishable from a refusal or a rate limit, because the support
 *    page signs the customer back in for the first and shows the server's
 *    sentence for the rest — a 429 must surface its message, not vanish.
 *
 * 4. **A stored session expires by itself.** A token left in localStorage
 *    past its TTL must not be replayed on the next visit.
 */
import axios from 'axios';
import apiClient from '../lib/api/apiClient';
import {
  chatbotApi,
  customerChatbotApi,
  loadCustomerSession,
  saveCustomerSession,
  clearCustomerSession,
  CustomerChatError,
  CUSTOMER_SESSION_KEY,
} from '../lib/api/chatbotApi';

jest.mock('../lib/api/apiClient', () => ({
  __esModule: true,
  default: { get: jest.fn(), post: jest.fn(), delete: jest.fn() },
  getApiBaseUrl: jest.fn(() => 'http://localhost:8081/api/v1'),
}));

// The customer channel creates its own axios instance at module load, so the
// factory hands back one shared, inspectable client.
jest.mock('axios', () => {
  const client = { get: jest.fn(), post: jest.fn(), delete: jest.fn() };
  return {
    __esModule: true,
    default: { create: jest.fn(() => client), __customerClient: client },
  };
});

type MockClient = { get: jest.Mock; post: jest.Mock; delete: jest.Mock };
const staff = apiClient as unknown as MockClient;
const customer = (
  axios as unknown as { __customerClient: MockClient }
).__customerClient;

const reply = {
  conversationId: 4,
  text: 'Net profit was ETB 1,000.00.',
  deterministic: true,
  guardIntervened: false,
  note: null,
  toolResults: { financial_summary: { netProfit: 1000 } },
};

beforeEach(() => {
  jest.clearAllMocks();
  localStorage.clear();
});

// ─────────────────────────────────────────────────────────────────────────────
// Staff channel
// ─────────────────────────────────────────────────────────────────────────────

describe('staff channel', () => {
  it('reads capabilities from the server, unwrapped from the envelope', async () => {
    staff.get.mockResolvedValueOnce({
      data: { data: [{ name: 'profit_loss', description: 'x', requiresPermission: 'PROFIT_LOSS_READ' }] },
    });

    const result = await chatbotApi.capabilities();

    expect(staff.get).toHaveBeenCalledWith('/chatbot/capabilities');
    expect(result).toHaveLength(1);
    expect(result[0].requiresPermission).toBe('PROFIT_LOSS_READ');
  });

  it('sends the chosen period as ISO dates so the answer covers the filtered window', async () => {
    staff.post.mockResolvedValueOnce({ data: { data: reply } });

    await chatbotApi.ask('net profit?', 7, { from: '2026-09-01', to: '2026-09-30' });

    // Exact body: an extra field here (an injected key, a leaked token)
    // would fail this assertion, not merely look odd.
    expect(staff.post).toHaveBeenCalledWith('/chatbot/ask', {
      question: 'net profit?',
      conversationId: 7,
      from: '2026-09-01',
      to: '2026-09-30',
    });
  });

  it('omits the period when the reader did not choose one', async () => {
    staff.post.mockResolvedValueOnce({ data: { data: reply } });

    await chatbotApi.ask('net profit?', null);

    expect(staff.post).toHaveBeenCalledWith('/chatbot/ask', {
      question: 'net profit?',
      conversationId: null,
      from: undefined,
      to: undefined,
    });
  });

  it('starts, reads and deletes conversations on the conversation endpoints', async () => {
    staff.post.mockResolvedValueOnce({ data: { data: { id: 9, title: null } } });
    staff.get.mockResolvedValueOnce({ data: { data: [{ id: 1, content: 'hi' }] } });
    staff.delete.mockResolvedValueOnce({ data: {} });

    await chatbotApi.startConversation('Weekly');
    await chatbotApi.history(9);
    await chatbotApi.deleteConversation(9);

    expect(staff.post).toHaveBeenCalledWith('/chatbot/conversations', { title: 'Weekly' });
    expect(staff.get).toHaveBeenCalledWith('/chatbot/conversations/9');
    expect(staff.delete).toHaveBeenCalledWith('/chatbot/conversations/9');
  });

  it('never sends a staff call down the customer channel', async () => {
    staff.get.mockResolvedValueOnce({ data: { data: [] } });

    await chatbotApi.capabilities();

    expect(customer.get).not.toHaveBeenCalled();
    expect(customer.post).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Customer channel
// ─────────────────────────────────────────────────────────────────────────────

describe('customer channel', () => {
  it('opens a session with the receipt pair and never touches the staff client', async () => {
    customer.post.mockResolvedValueOnce({
      data: { data: { token: 'sess_1', customerName: 'Abebe', expiresAt: '2026-10-15T10:00:00Z', note: '' } },
    });

    const session = await customerChatbotApi.openSession('a@example.com', 'SO-000123');

    expect(customer.post).toHaveBeenCalledWith('/chatbot/customer/session', {
      email: 'a@example.com',
      orderNumber: 'SO-000123',
    });
    expect(staff.post).not.toHaveBeenCalled();
    expect(session.token).toBe('sess_1');
  });

  it('sends the session token in the request body when asking', async () => {
    customer.post.mockResolvedValueOnce({ data: { data: reply } });

    await customerChatbotApi.ask('sess_1', 'where is my order?', 3);

    expect(customer.post).toHaveBeenCalledWith('/chatbot/customer/ask', {
      token: 'sess_1',
      question: 'where is my order?',
      conversationId: 3,
    });
  });

  it('carries the session token as the X-Chatbot-Token header on reads and deletes', async () => {
    customer.get.mockResolvedValueOnce({ data: { data: [] } });
    customer.get.mockResolvedValueOnce({ data: { data: [] } });
    customer.delete.mockResolvedValueOnce({ data: {} });

    await customerChatbotApi.conversations('sess_1');
    await customerChatbotApi.history('sess_1', 5);
    await customerChatbotApi.deleteConversation('sess_1', 5);

    expect(customer.get).toHaveBeenCalledWith('/chatbot/customer/conversations', {
      headers: { 'X-Chatbot-Token': 'sess_1' },
    });
    expect(customer.get).toHaveBeenCalledWith('/chatbot/customer/conversations/5', {
      headers: { 'X-Chatbot-Token': 'sess_1' },
    });
    expect(customer.delete).toHaveBeenCalledWith('/chatbot/customer/conversations/5', {
      headers: { 'X-Chatbot-Token': 'sess_1' },
    });
  });

  it('marks a 401 as an expired session so the page can ask the customer back in', async () => {
    customer.post.mockRejectedValueOnce({ response: { status: 401 } });

    const error = await customerChatbotApi.ask('expired', 'q', null).catch((e) => e);

    expect(error).toBeInstanceOf(CustomerChatError);
    expect(error.sessionExpired).toBe(true);
    expect(error.message).toContain('expired');
  });

  it('surfaces a rate-limit refusal as the server worded it, not as an expired session', async () => {
    customer.post.mockRejectedValueOnce({
      response: { status: 429, data: { message: 'Too many messages. Please wait a minute.' } },
    });

    const error = await customerChatbotApi.ask('sess_1', 'q', null).catch((e) => e);

    expect(error).toBeInstanceOf(CustomerChatError);
    expect(error.sessionExpired).toBe(false);
    expect(error.message).toBe('Too many messages. Please wait a minute.');
  });

  it('reports a network failure with the channel fallback instead of throwing raw', async () => {
    customer.post.mockRejectedValueOnce(new Error('timeout of 60000ms exceeded'));

    const error = await customerChatbotApi.ask('sess_1', 'q', null).catch((e) => e);

    expect(error).toBeInstanceOf(CustomerChatError);
    expect(error.sessionExpired).toBe(false);
    expect(error.message).toBe('That question could not be answered. Please try again.');
  });

  it('lets a session open fail with the server message, for a wrong pair', async () => {
    customer.post.mockRejectedValueOnce({
      response: { status: 400, data: { message: 'No order matches that email.' } },
    });

    const error = await customerChatbotApi
      .openSession('a@example.com', 'SO-000001')
      .catch((e) => e);

    expect(error.message).toBe('No order matches that email.');
    expect(error.sessionExpired).toBe(false);
  });

  it('closes a session without failing loudly when the token is already dead', async () => {
    customer.delete.mockRejectedValueOnce({ response: { status: 401 } });

    await expect(customerChatbotApi.closeSession('dead')).resolves.toBeUndefined();
    expect(customer.delete).toHaveBeenCalledWith('/chatbot/customer/session', {
      headers: { 'X-Chatbot-Token': 'dead' },
    });
  });

  it('never sends a customer call down the staff client', async () => {
    customer.post.mockResolvedValueOnce({ data: { data: reply } });

    await customerChatbotApi.ask('sess_1', 'q', null);

    expect(staff.post).not.toHaveBeenCalled();
    expect(staff.get).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// The provider key stays on the server
// ─────────────────────────────────────────────────────────────────────────────

describe('no provider secret reaches the browser', () => {
  it('builds no request carrying an LLM key', async () => {
    staff.post.mockResolvedValue({ data: { data: reply } });
    staff.get.mockResolvedValue({ data: { data: [] } });
    customer.post.mockResolvedValue({ data: { data: reply } });
    customer.get.mockResolvedValue({ data: { data: [] } });
    customer.delete.mockResolvedValue({ data: {} });

    await chatbotApi.capabilities();
    await chatbotApi.ask('net profit?', 1, { from: '2026-09-01', to: '2026-09-30' });
    await customerChatbotApi.openSession('a@example.com', 'SO-000123');
    await customerChatbotApi.ask('sess_1', 'q', null);
    await customerChatbotApi.conversations('sess_1');
    await customerChatbotApi.closeSession('sess_1');

    const everything = JSON.stringify([
      ...staff.get.mock.calls,
      ...staff.post.mock.calls,
      ...staff.delete.mock.calls,
      ...customer.get.mock.calls,
      ...customer.post.mock.calls,
      ...customer.delete.mock.calls,
    ]);
    expect(everything).not.toMatch(/sk-/);
    expect(everything).not.toMatch(/apiKey/);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Stored customer session
// ─────────────────────────────────────────────────────────────────────────────

describe('stored customer session', () => {
  const future = () => new Date(Date.now() + 60 * 60 * 1000).toISOString();
  const past = () => new Date(Date.now() - 60 * 1000).toISOString();

  it('round-trips a session with the email it was opened with', () => {
    saveCustomerSession(
      { token: 'sess_1', customerName: 'Abebe', expiresAt: future(), note: '' },
      'a@example.com',
    );

    const loaded = loadCustomerSession();

    expect(loaded?.token).toBe('sess_1');
    expect(loaded?.email).toBe('a@example.com');
  });

  it('expires by itself: a past TTL is treated as no session and cleared', () => {
    saveCustomerSession(
      { token: 'sess_1', customerName: 'Abebe', expiresAt: past(), note: '' },
      'a@example.com',
    );

    expect(loadCustomerSession()).toBeNull();
    expect(localStorage.getItem(CUSTOMER_SESSION_KEY)).toBeNull();
  });

  it('returns null for absent, malformed or token-less storage', () => {
    expect(loadCustomerSession()).toBeNull();

    localStorage.setItem(CUSTOMER_SESSION_KEY, 'not json');
    expect(loadCustomerSession()).toBeNull();

    localStorage.setItem(CUSTOMER_SESSION_KEY, JSON.stringify({ expiresAt: future() }));
    expect(loadCustomerSession()).toBeNull();
  });

  it('forgets a session on demand', () => {
    saveCustomerSession(
      { token: 'sess_1', customerName: 'Abebe', expiresAt: future(), note: '' },
      'a@example.com',
    );

    clearCustomerSession();

    expect(localStorage.getItem(CUSTOMER_SESSION_KEY)).toBeNull();
  });
});
