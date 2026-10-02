import axios from 'axios';

/**
 * ------------------------------------------------------------
 * BASE URL RESOLUTION
 * ------------------------------------------------------------
 * Two distinct URLs are involved and must not be conflated:
 *
 * 1. Browser / public URL — `NEXT_PUBLIC_API_BASE_URL`
 *    e.g. http://localhost:8081/api/v1
 *    Used for every request the browser makes. The host reaches
 *    the Spring Boot container through the 8081 -> 8080 mapping.
 *
 * 2. Docker-internal / server-side URL — `API_INTERNAL_BASE_URL`
 *    e.g. http://backend:8080/api/v1
 *    Only resolvable from inside the compose network. It is used
 *    when server-side Next.js code has to call the backend.
 *
 * The `backend` hostname is NOT resolvable by the user's browser,
 * so it is never used as the public URL.
 */
const PUBLIC_API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8081/api/v1';

const SERVER_API_BASE_URL =
  process.env.API_INTERNAL_BASE_URL ?? PUBLIC_API_BASE_URL;

export const getApiBaseUrl = (): string =>
  typeof window === 'undefined' ? SERVER_API_BASE_URL : PUBLIC_API_BASE_URL;

/**
 * ------------------------------------------------------------
 * Session expiry notification
 * ------------------------------------------------------------
 * On a 401 the axios interceptor clears the stored token/user and
 * broadcasts this event. AuthProvider listens for it and clears the
 * in-app user state, which makes the dashboard layout soft-redirect
 * to /login. Using an event + router navigation (instead of
 * window.location) avoids aborting the in-flight request, which
 * surfaced as a confusing "AxiosError: Network Error".
 */
export const SESSION_EXPIRED_EVENT = 'stockflow:session-expired';

const apiClient = axios.create({
  // Resolved per environment; `undefined` keeps axios' default
  // same-origin behaviour for the (currently unused) server path.
  baseURL: getApiBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
  // Distinguishes a genuine transport failure from an HTTP error,
  // so the UI can say "Backend unavailable" instead of "Network Error".
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('auth_token');
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      // Never swallow the error — it propagates so callers can label
      // it with the resource they were fetching. Clear the stale token
      // and notify AuthProvider so the in-app session state is dropped;
      // the dashboard layout then performs a soft router redirect to
      // /login. (A hard window.location here would abort the
      // request that triggered it and surface a misleading
      // "AxiosError: Network Error" instead of the real 401.)
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  }
);

/**
 * Fetch EVERY page of a paginated list endpoint (Spring Page or plain
 * array). The first request asks for a large page; when the backend
 * reports totalPages > 1 we pull the remaining pages too. This is what
 * guarantees "list all real data" in the UI instead of silently
 * showing only the first page.
 */
export async function fetchAllPages<T = any>(
  url: string,
  params: Record<string, any> = {},
): Promise<T[]> {
  const PAGE_SIZE = 200;
  const all: T[] = [];
  let page = 0;
  let totalPages = 1;

  while (page < totalPages) {
    const res = await apiClient.get(url, {
      params: { ...params, page, size: PAGE_SIZE },
    });
    const payload = res.data?.data ?? res.data;
    const content: T[] = Array.isArray(payload)
      ? payload
      : (payload?.content ?? []);
    all.push(...content);
    totalPages = Array.isArray(payload)
      ? 1
      : Math.max(1, Number(payload?.totalPages ?? 1));
    page += 1;
  }

  return all;
}

export default apiClient;
