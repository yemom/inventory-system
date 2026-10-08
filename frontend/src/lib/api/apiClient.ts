import axios, { AxiosRequestConfig } from 'axios';

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

/**
 * How long a single request may take before it is abandoned.
 *
 * This was 15s, which is fine locally and wrong in production. The deployed
 * backend runs on a platform that scales instances to zero when idle, so the
 * first request after a pause waits for the instance to boot, which routinely
 * takes 30-60s. At 15s the client gave up and cancelled the request — exactly
 * what the browser showed: a login "cancelled" at 15.1s with no response at all,
 * reported as "Backend unavailable … check CORS_ALLOWED_ORIGINS".
 *
 * The connection was fine; we simply stopped waiting for it.
 */
const REQUEST_TIMEOUT_MS = 60_000;

/**
 * Methods that may be sent again after a failed attempt.
 *
 * Only requests that change nothing. A timed-out `POST /sales` may well have
 * been committed before the response was lost, and re-sending it would sell the
 * customer's basket twice — so writes are never retried automatically, however
 * tempting it looks.
 */
function isRetryable(config?: { method?: string }): boolean {
  const method = (config?.method ?? 'get').toLowerCase();
  return method === 'get' || method === 'head' || method === 'options';
}

const apiClient = axios.create({
  // Resolved per environment; `undefined` keeps axios' default
  // same-origin behaviour for the (currently unused) server path.
  baseURL: getApiBaseUrl(),
  headers: { 'Content-Type': 'application/json' },
  // Distinguishes a genuine transport failure from an HTTP error,
  // so the UI can say "Backend unavailable" instead of "Network Error".
  timeout: REQUEST_TIMEOUT_MS,
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
  async (error) => {
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

    // Typed as an Axios config plus our two flags, rather than as an object
    // carrying only the flags — the latter has no overlap with AxiosRequestConfig
    // and so cannot be passed back to `apiClient.request`.
    const config = error.config as
        | (AxiosRequestConfig & { __retried?: boolean; __timedOut?: boolean })
        | undefined;

    // A timeout is not the same as "the backend is down", and the two used to
    // share one message that pointed the operator at CORS. Tagged here so the
    // error layer can say what actually happened.
    if ((error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') && config) {
      config.__timedOut = true;
    }

    // One retry for a safe request that produced no response. Either the
    // connection never established or it was dropped — which is what a platform
    // waking a cold instance looks like from here. The second attempt lands on a
    // server that is up by then, so the user is not shown an error for something
    // that has already recovered on its own.
    if (config && !config.__retried && !error.response && isRetryable(config)) {
      config.__retried = true;
      return apiClient.request(config);
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
  // Matches the backend's max page size (WebConfig.MAX_PAGE_SIZE).
  // The loop below still walks every page, so no rows are ever lost.
  const PAGE_SIZE = 100;
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
