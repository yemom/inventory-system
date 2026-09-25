/**
 * ============================================================
 * API RESPONSE HELPERS
 * ============================================================
 *
 * Supports all of these backend response formats:
 *
 * 1. Plain array
 *
 * {
 *   success: true,
 *   data: [...]
 * }
 *
 * 2. Spring Page
 *
 * {
 *   success: true,
 *   data: {
 *     content: [...]
 *   }
 * }
 *
 * 3. Direct array
 *
 * [...]
 *
 * This prevents every API service from having to guess
 * whether the backend returned data or data.content.
 */

export interface ApiResponse<T> {
    success?: boolean;
    message?: string;
    data?: T;
    timestamp?: string;
}

export interface PageResponse<T> {
    content?: T[];
    totalElements?: number;
    totalPages?: number;
    number?: number;
    size?: number;
    first?: boolean;
    last?: boolean;
}

/**
 * ------------------------------------------------------------
 * Extract list
 * ------------------------------------------------------------
 */

export function extractList<T>(
    responseData: unknown
): T[] {

    /**
     * Backend returned:
     *
     * [...]
     */
    if (Array.isArray(responseData)) {
        return responseData as T[];
    }

    /**
     * Backend returned:
     *
     * {
     *   data: [...]
     * }
     */
    if (
        responseData &&
        typeof responseData === 'object'
    ) {

        const response =
            responseData as {
                data?: unknown;
            };

        if (
            Array.isArray(response.data)
        ) {
            return response.data as T[];
        }

        /**
         * Backend returned:
         *
         * {
         *   data: {
         *     content: [...]
         *   }
         * }
         */
        if (
            response.data &&
            typeof response.data === 'object'
        ) {

            const data =
                response.data as {
                    content?: unknown;
                };

            if (
                Array.isArray(data.content)
            ) {
                return data.content as T[];
            }
        }

        /**
         * Direct Spring Page:
         *
         * {
         *   content: [...]
         * }
         */
        const direct =
            responseData as {
                content?: unknown;
            };

        if (
            Array.isArray(direct.content)
        ) {
            return direct.content as T[];
        }
    }

    return [];
}

/**
 * ------------------------------------------------------------
 * Extract single object
 * ------------------------------------------------------------
 */

export function extractItem<T>(
    responseData: unknown
): T | null {

    if (
        responseData == null
    ) {
        return null;
    }

    if (
        typeof responseData === 'object' &&
        'data' in responseData
    ) {

        const response =
            responseData as {
                data?: T;
            };

        return response.data ?? null;
    }

    return responseData as T;
}

/**
 * ------------------------------------------------------------
 * Extract API error message
 * ------------------------------------------------------------
 */

export function extractErrorMessage(
    error: unknown,
    fallback = 'Something went wrong'
): string {

    if (
        error &&
        typeof error === 'object'
    ) {

        const axiosError =
            error as {
                message?: string;
                response?: {
                    data?: {
                        message?: string;
                        error?: string;
                    };
                };
            };

        return (
            axiosError.response?.data?.message ??
            axiosError.response?.data?.error ??
            axiosError.message ??
            fallback
        );
    }

    return fallback;
}