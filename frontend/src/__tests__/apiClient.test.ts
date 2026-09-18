/**
 * @jest-environment node
 *
 * Unit tests for the Axios apiClient interceptor behaviors.
 * Uses node environment with mock window and localStorage for clean, reliable assertions.
 */

// Setup mock window and localStorage in node environment
const storage: Record<string, string> = {};
const mockLocalStorage = {
  getItem: jest.fn((key: string) => storage[key] ?? null),
  setItem: jest.fn((key: string, val: string) => {
    storage[key] = val;
  }),
  removeItem: jest.fn((key: string) => {
    delete storage[key];
  }),
  clear: jest.fn(() => {
    Object.keys(storage).forEach((k) => delete storage[k]);
  }),
};

const mockWindow = {
  location: { href: '' },
};

(global as any).window = mockWindow;
(global as any).localStorage = mockLocalStorage;

import apiClient from '../lib/api/apiClient';
import type { InternalAxiosRequestConfig } from 'axios';

// Extract the registered handlers from the real Axios instance
const requestInterceptor = (apiClient.interceptors.request as any).handlers[0]?.fulfilled;
const responseErrorInterceptor = (apiClient.interceptors.response as any).handlers[0]?.rejected;

beforeEach(() => {
  mockWindow.location.href = '';
  mockLocalStorage.clear();
  jest.clearAllMocks();
});

describe('apiClient — request interceptor', () => {
  it('attaches Bearer token when auth_token exists in localStorage', () => {
    mockLocalStorage.setItem('auth_token', 'test-jwt-token-abc');

    const config = { headers: {} } as InternalAxiosRequestConfig;
    const result = requestInterceptor(config);

    expect(result.headers.Authorization).toBe('Bearer test-jwt-token-abc');
  });

  it('does NOT set Authorization header when no token in localStorage', () => {
    mockLocalStorage.removeItem('auth_token');

    const config = { headers: {} } as InternalAxiosRequestConfig;
    const result = requestInterceptor(config);

    expect(result.headers.Authorization).toBeUndefined();
  });

  it('overwrites stale token if localStorage has a new one', () => {
    mockLocalStorage.setItem('auth_token', 'new-token-xyz');

    const config = { headers: { Authorization: 'Bearer old-token' } } as unknown as InternalAxiosRequestConfig;
    const result = requestInterceptor(config);

    expect(result.headers.Authorization).toBe('Bearer new-token-xyz');
  });
});

describe('apiClient — response error interceptor (401)', () => {
  it('clears auth_token and auth_user from localStorage on 401', async () => {
    mockLocalStorage.setItem('auth_token', 'expired-token');
    mockLocalStorage.setItem('auth_user', JSON.stringify({ username: 'admin' }));

    const error = { response: { status: 401 } };

    await expect(responseErrorInterceptor(error)).rejects.toEqual(error);

    expect(mockLocalStorage.getItem('auth_token')).toBeNull();
    expect(mockLocalStorage.getItem('auth_user')).toBeNull();
  });

  it('redirects to /login on 401', async () => {
    const error = { response: { status: 401 } };

    await expect(responseErrorInterceptor(error)).rejects.toEqual(error);

    expect(mockWindow.location.href).toBe('/login');
  });

  it('does NOT redirect or clear storage on 403 errors', async () => {
    mockLocalStorage.setItem('auth_token', 'valid-token');

    const error = { response: { status: 403 } };

    await expect(responseErrorInterceptor(error)).rejects.toEqual(error);

    expect(mockLocalStorage.getItem('auth_token')).toBe('valid-token');
    expect(mockWindow.location.href).toBe('');
  });

  it('does NOT redirect on 500 server errors', async () => {
    const error = { response: { status: 500 } };

    await expect(responseErrorInterceptor(error)).rejects.toEqual(error);

    expect(mockWindow.location.href).toBe('');
  });

  it('does NOT crash when error has no response (network error)', async () => {
    const error = new Error('Network Error');

    await expect(responseErrorInterceptor(error)).rejects.toEqual(error);
    expect(mockWindow.location.href).toBe('');
  });
});
