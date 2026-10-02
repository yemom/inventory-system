/**
 * ============================================================
 * AUTH ERROR HELPERS
 * ============================================================
 *
 * AuthController returns a JSON body for both success and failure:
 *
 *   401 -> { "success": false, "message": "Invalid credentials. ..." }
 *   400 -> { "success": false, "message": "<validation reason>" }
 *
 * These helpers read the backend's real reason out of an Axios
 * rejection so the login form never has to guess, and never
 * replaces a specific backend message with a generic one.
 */

/**
 * Returns the backend-provided message, or null when the response
 * carried none (for example a transport failure or a non-JSON body).
 */
export function extractBackendMessage(error: unknown): string | null {
    if (!error || typeof error !== 'object') return null;

    const body = (error as { response?: { data?: unknown } }).response?.data;

    if (typeof body === 'string') {
        const trimmed = body.trim();
        return trimmed.length > 0 ? trimmed : null;
    }

    if (body && typeof body === 'object') {
        const candidate = body as { message?: unknown; error?: unknown };

        for (const value of [candidate.message, candidate.error]) {
            if (typeof value === 'string' && value.trim().length > 0) {
                return value.trim();
            }
        }
    }

    return null;
}