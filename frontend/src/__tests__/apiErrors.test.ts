/**
 * @jest-environment node
 *
 * Unit tests for API error normalisation and labelling.
 *
 * The dashboard renders one alert per failed request, keyed by a stable
 * label. These tests cover the root cause of the duplicate-key warning:
 * several failing requests must never collapse into one identical message.
 */

import { AxiosError, AxiosHeaders } from 'axios';
import {
  BACKEND_UNAVAILABLE,
  labelApiError,
  toApiError,
} from '../lib/api/apiErrors';

function networkFailure(url = '/products'): AxiosError {
  const error = new AxiosError('Network Error');
  error.config = {
    method: 'get',
    url,
    headers: new AxiosHeaders(),
  } as never;
  error.request = {};
  return error;
}

function httpFailure(status: number, data: unknown, url = '/products'): AxiosError {
  const error = new AxiosError(`Request failed with status code ${status}`);
  error.config = {
    method: 'get',
    url,
    headers: new AxiosHeaders(),
  } as never;
  error.response = {
    status,
    statusText: '',
    data,
    headers: {},
    config: error.config,
  } as never;
  return error;
}

describe('toApiError — transport failures', () => {
  it('reports an unreachable backend instead of the vague "Network Error"', () => {
    const result = toApiError(networkFailure());

    expect(result.isNetworkError).toBe(true);
    expect(result.status).toBeNull();
    expect(result.message).toBe(BACKEND_UNAVAILABLE);
    expect(result.message).not.toBe('Network Error');
  });

  it('includes the failing method and URL for diagnostics', () => {
    const result = toApiError(networkFailure('/sales'));

    expect(result.endpoint).toBe('GET /sales');
  });
});

describe('toApiError — responses from the backend', () => {
  it('surfaces the backend status and message', () => {
    const result = toApiError(httpFailure(403, { message: 'Missing authority INVENTORY_READ' }));

    expect(result.isNetworkError).toBe(false);
    expect(result.status).toBe(403);
    expect(result.message).toBe('403 — Missing authority INVENTORY_READ');
  });

  it('falls back to a status-based message when the body has no message', () => {
    const result = toApiError(httpFailure(500, {}));

    expect(result.status).toBe(500);
    expect(result.message).toContain('500');
  });
});

describe('toApiError — non-axios failures', () => {
  it('passes through a plain Error message', () => {
    expect(toApiError(new Error('Product not found')).message).toBe('Product not found');
  });

  it('handles thrown primitives', () => {
    expect(toApiError('boom').message).toBe('Unexpected error');
  });
});

describe('labelApiError — unique identity per request', () => {
  /**
   * Regression test for:
   * "Encountered two children with the same key, 'Network Error'"
   */
  it('gives three identical backend failures distinct labels and keys', () => {
    const errors = [
      labelApiError('Products', networkFailure('/products')),
      labelApiError('Sales', networkFailure('/sales')),
      labelApiError('Purchases', networkFailure('/purchases')),
    ];

    const keys = errors.map((e) => e.label);

    expect(new Set(keys).size).toBe(keys.length);
    expect(keys).toEqual(['Products', 'Sales', 'Purchases']);

    // Same underlying reason, but each message identifies its resource.
    expect(errors.map((e) => `${e.label}: ${e.message}`)).toEqual([
      `Products: ${BACKEND_UNAVAILABLE}`,
      `Sales: ${BACKEND_UNAVAILABLE}`,
      `Purchases: ${BACKEND_UNAVAILABLE}`,
    ]);
  });

  it('produces a message usable as a React key candidate only via the label', () => {
    const a = labelApiError('Products', networkFailure('/products'));
    const b = labelApiError('Sales', networkFailure('/sales'));

    expect(a.message).toBe(b.message);
    expect(a.label).not.toBe(b.label);
  });
});