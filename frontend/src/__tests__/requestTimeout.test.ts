/**
 * The client must not abandon a slow request and then blame CORS for it.
 *
 * Regression context: `timeout` was 15s. The deployed backend scales to zero when
 * idle, so the first request after a pause waits for the instance to boot, which
 * routinely takes 30-60s. The client gave up at 15s, the browser showed the
 * request "cancelled" with no response, and `toApiError` mapped "no response" to
 * "Backend unavailable ... check that this page's origin is listed in the API's
 * CORS_ALLOWED_ORIGINS setting".
 *
 * That advice was wrong. Nothing about CORS was wrong; the server was merely slow
 * to answer, and it answered fine once waited for.
 *
 * @jest-environment node
 */

import axios from 'axios';
import { toApiError, describeFailure } from '@/lib/api/apiErrors';

describe('request timeout', () => {
  const originalCreate = axios.create;

  afterEach(() => {
    jest.restoreAllMocks();
    (axios as unknown as { create: typeof originalCreate }).create = originalCreate;
  });

  /** Loads the module fresh so we can read the timeout it configures. */
  async function loadClient() {
    jest.resetModules();
    const { default: client } = await import('@/lib/api/apiClient');
    return client;
  }

  it('waits long enough for a backend that is booting from cold', async () => {
    const client = await loadClient();

    // 60s. A cold start is 30-60s, so anything under that reports a healthy
    // server as unavailable.
    expect(client.defaults.timeout).toBeGreaterThanOrEqual(60_000);
  });

  it('was previously 15s, which is what produced the cancelled login', async () => {
    const client = await loadClient();
    expect(client.defaults.timeout).not.toBe(15_000);
  });
});

describe('retry policy', () => {
  it('retries a read that produced no response', async () => {
    const { default: client } = await import('@/lib/api/apiClient');

    // `config` is included because axios always attaches it to a transport
    // failure; the interceptor needs it to know the request was a safe method.
    // Without it the retry is correctly skipped, which would make this test
    // pass for the wrong reason.
    const adapter = jest
      .fn()
      .mockRejectedValueOnce(
        Object.assign(new Error('timeout'), { code: 'ECONNABORTED', config: { method: 'get' } }),
      )
      .mockResolvedValue({ data: { ok: true }, status: 200, statusText: 'OK', headers: {}, config: {} });

    client.defaults.adapter = adapter as any;

    const response = await client.get('/products');

    expect(adapter).toHaveBeenCalledTimes(2);
    expect(response.data).toEqual({ ok: true });
  });

  it('never retries a write, which could double-commit it', async () => {
    const { default: client } = await import('@/lib/api/apiClient');

    const adapter = jest.fn().mockRejectedValue(
      Object.assign(new Error('timeout'), { code: 'ECONNABORTED' }),
    );
    client.defaults.adapter = adapter as any;

    // A POST /sales that timed out may already have been recorded. Re-sending it
    // would sell the basket twice, so this must fail on the first attempt.
    await expect(client.post('/sales', { items: [] })).rejects.toThrow();
    expect(adapter).toHaveBeenCalledTimes(1);
  });

  it('does not retry when the server actually answered', async () => {
    const { default: client } = await import('@/lib/api/apiClient');

    const adapter = jest.fn().mockRejectedValue(
      Object.assign(new Error('Request failed with status code 500'), {
        response: { status: 500, data: {} },
      }),
    );
    client.defaults.adapter = adapter as any;

    await expect(client.get('/products')).rejects.toThrow();
    expect(adapter).toHaveBeenCalledTimes(1);
  });
});

describe('a timeout is reported as a timeout', () => {
  it('does not tell the reader to check CORS', () => {
    const timeoutError = Object.assign(new Error('timeout of 60000ms exceeded'), {
      code: 'ECONNABORTED',
      config: { method: 'post', url: '/auth/login', timeout: 60_000 },
      isAxiosError: true,
    });
    Object.setPrototypeOf(timeoutError, axios.AxiosError.prototype);

    const result = toApiError(timeoutError);

    expect(result.message).not.toMatch(/CORS/i);
    expect(result.message).toMatch(/did not respond|starting up/i);
  });

  it('still mentions CORS for a genuine unreachable backend', () => {
    // No response and not a timeout: DNS failure, refused connection, or a
    // rejected preflight. CORS genuinely is one of the candidates here.
    const networkError = Object.assign(new Error('Network Error'), {
      config: { method: 'get', url: '/products' },
      isAxiosError: true,
    });
    Object.setPrototypeOf(networkError, axios.AxiosError.prototype);

    const result = toApiError(networkError);
    expect(result.isNetworkError).toBe(true);

    // toApiError returns the short sentinel; describeFailure expands it, and
    // only then is the list of causes — CORS among them — appropriate.
    expect(result.message).toBe('Backend unavailable');
    expect(describeFailure(networkError, 'http://localhost:8081/api/v1')).toMatch(/CORS/i);
  });

  it('expands the sentinel into the full explanation with the URL', () => {
    const networkError = Object.assign(new Error('Network Error'), {
      config: { method: 'get', url: '/products' },
      isAxiosError: true,
    });
    Object.setPrototypeOf(networkError, axios.AxiosError.prototype);

    const message = describeFailure(networkError, 'https://api.example.com/api/v1');
    expect(message).toContain('https://api.example.com/api/v1');
  });

  it('names the timeout it waited, so the delay is visible', () => {
    const timeoutError = Object.assign(new Error('timeout of 45000ms exceeded'), {
      code: 'ECONNABORTED',
      config: { method: 'get', url: '/sales', timeout: 45_000, __timedOut: true },
      isAxiosError: true,
    });
    Object.setPrototypeOf(timeoutError, axios.AxiosError.prototype);

    expect(describeFailure(timeoutError, undefined)).toContain('45');
  });

  it('marks a timeout as a network-class failure so pages still render an error', () => {
    const timeoutError = Object.assign(new Error('timeout of 60000ms exceeded'), {
      code: 'ECONNABORTED',
      config: { method: 'get', url: '/sales', timeout: 60_000 },
      isAxiosError: true,
    });
    Object.setPrototypeOf(timeoutError, axios.AxiosError.prototype);

    const result = toApiError(timeoutError);
    expect(result.isNetworkError).toBe(true);
    expect(result.status).toBeNull();
    expect(result.endpoint).toContain('/sales');
  });

  it('does not mistake a 500 for a timeout', () => {
    const serverError = Object.assign(new Error('Request failed with status code 500'), {
      response: { status: 500, data: { message: 'Boom' } },
      config: { method: 'get', url: '/sales' },
      isAxiosError: true,
    });
    Object.setPrototypeOf(serverError, axios.AxiosError.prototype);

    const result = toApiError(serverError);
    expect(result.status).toBe(500);
    expect(result.isNetworkError).toBe(false);
  });
});
