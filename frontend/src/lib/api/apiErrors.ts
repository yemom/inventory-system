/**
 * ============================================================
 * API ERROR HELPERS
 * ============================================================
 *
 * Every dashboard/API consumer needs to report failures the same
 * way: identify WHICH request failed and WHY.
 *
 * The most common failure in this stack is not an HTTP error but a
 * transport error, because the browser cannot reach the Spring Boot
 * backend at all. Axios surfaces that as `ERR_NETWORK` with the
 * unhelpful message "Network Error", so every failing call would
 * otherwise render the identical string — which both hides the
 * real cause and produces duplicate React keys.
 *
 * These helpers keep the backend's real error messages when the
 * backend responded, and clearly distinguish an unreachable
 * backend from a rejected request.
 */

import axios from 'axios';

/** Stable, human-readable message for an unreachable backend. */
export const BACKEND_UNAVAILABLE = 'Backend unavailable';

/**
 * Explanation shown with {@link BACKEND_UNAVAILABLE}.
 *
 * Every caller must use this rather than inventing its own text. It previously
 * appended "Start the API with 'docker compose up --build' and try again",
 * which is meaningless on a deployed instance and actively misled whoever was
 * diagnosing it — the real cause was a CORS origin the API had not been told
 * about, and the instruction sent them to run Docker locally.
 *
 * The browser deliberately hides the underlying reason: a CORS preflight
 * rejection, a DNS failure, a refused connection and a 503 from an unhealthy
 * instance all surface as the identical opaque "Network Error". So the message
 * names the URL that was actually contacted (which is the one thing the operator
 * can check first) and lists the causes that are indistinguishable from here.
 */
export function backendUnavailableMessage(apiBaseUrl?: string): string {
    const target = apiBaseUrl ? ` at ${apiBaseUrl}` : '';
    return (
        `${BACKEND_UNAVAILABLE}${target}. The API did not respond. ` +
        'Check that the API service is running and healthy, that this frontend is ' +
        'configured with the correct API URL, and that this page’s origin is listed ' +
        'in the API’s CORS_ALLOWED_ORIGINS setting.'
    );
}

export interface ApiError {
    /** HTTP status code, or null when the request never completed. */
    status: number | null;
    /** True when the request failed before the backend responded. */
    isNetworkError: boolean;
    /** Human-readable description, safe to render. */
    message: string;
    /** Method + URL that failed, for diagnostics. */
    endpoint?: string;
}

function describeEndpoint(config?: {
    method?: string;
    url?: string;
}): string | undefined {
    if (!config) return undefined;
    const method = config.method ? config.method.toUpperCase() : '';
    return [method, config.url].filter(Boolean).join(' ') || undefined;
}

/**
 * ------------------------------------------------------------
 * Normalise any thrown value into an ApiError
 * ------------------------------------------------------------
 */
export function toApiError(error: unknown): ApiError {
    if (axios.isAxiosError(error)) {
        const status = error.response?.status ?? null;

        // No response at all: DNS failure, connection refused, CORS
        // preflight failure, or the container simply is not up yet.
        if (!error.response) {
            return {
                status: null,
                isNetworkError: true,
                message: BACKEND_UNAVAILABLE,
                endpoint: describeEndpoint(error.config),
            };
        }

        // The backend replied with a real status and possibly a
        // message — surface it instead of a generic string.
        const body = error.response.data as
            | { message?: string; error?: string }
            | string
            | undefined;

        const fromBody =
            typeof body === 'string'
                ? body
                : body?.message ?? body?.error;

        let detail =
            fromBody?.trim() ||
            error.message ||
            `Request failed with status ${status}`;

        // Translate log-speak HTTP jargon into plain-language messages so
        // every page renders something a non-technical user understands.
        const fallbackDetail = (error.message || '').toLowerCase();
        if (
            status === 403 ||
            detail === 'Forbidden' ||
            detail === 'Access denied. You do not have permission.'
        ) {
            detail =
                "You don't have permission to do that. Ask an administrator to grant the required role.";
        } else if (status === 401 || detail.toLowerCase().includes('authentication required')) {
            detail = 'Please log in again to continue.';
        } else if (status === 404) {
            detail = 'Not found — it may have been removed.';
        } else if (status != null && status >= 500) {
            detail =
                detail && !fallbackDetail.includes('network') && !detail.startsWith('Request failed')
                    ? detail
                    : 'Something went wrong on the server. Please try again.';
        } else if (
            detail.toLowerCase().includes('network error') ||
            detail.toLowerCase().includes('err_network')
        ) {
            detail = 'Cannot reach the server. Please check your connection and try again.';
        }

        return {
            status,
            isNetworkError: false,
            message: detail,
            endpoint: describeEndpoint(error.config),
        };
    }

    if (error instanceof Error) {
        return {
            status: null,
            isNetworkError: false,
            message: error.message,
        };
    }

    return {
        status: null,
        isNetworkError: false,
        message: 'Unexpected error',
    };
}

/**
 * ------------------------------------------------------------
 * Build a labelled message such as:
 *   "Products: Backend unavailable"
 *   "Sales: 403 — Missing authority INVENTORY_READ"
 *
 * `label` must be stable and unique per request (never an error
 * message) so it can safely double as a React key.
 * ------------------------------------------------------------
 */
export function labelApiError(
    label: string,
    error: unknown,
): ApiError & { label: string } {
    return {
        label,
        ...toApiError(error),
    };
}