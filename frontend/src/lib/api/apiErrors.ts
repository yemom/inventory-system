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

        const detail =
            fromBody?.trim() ||
            error.message ||
            `Request failed with status ${status}`;

        return {
            status,
            isNetworkError: false,
            message: `${status} — ${detail}`,
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