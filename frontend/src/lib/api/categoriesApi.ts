import apiClient from './apiClient';

/**
 * ============================================================
 * CATEGORY TYPES
 * ============================================================
 */

export interface Category {
  id: number;
  name: string;
  description?: string | null;
  parentId?: number | null;
  subCategories?: Category[];
  productCount?: number;
}

/**
 * Backend API response wrapper.
 */
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

/**
 * ============================================================
 * RESPONSE NORMALIZER
 * ============================================================
 *
 * The backend currently returns:
 *
 * {
 *   success: true,
 *   message: "...",
 *   data: [...]
 * }
 *
 * NOT:
 *
 * {
 *   data: {
 *     content: [...]
 *   }
 * }
 *
 * This function also supports both formats so the frontend
 * remains safe if pagination is introduced later.
 */

function normalizeCategoryList(
  data: unknown
): Category[] {
  if (Array.isArray(data)) {
    return data as Category[];
  }

  if (
    data &&
    typeof data === 'object' &&
    'content' in data
  ) {
    const content = (
      data as {
        content?: unknown;
      }
    ).content;

    if (Array.isArray(content)) {
      return content as Category[];
    }
  }

  return [];
}

/**
 * ============================================================
 * CATEGORIES API
 * ============================================================
 */

export const categoriesApi = {

  /**
   * ----------------------------------------------------------
   * GET ALL CATEGORIES
   * ----------------------------------------------------------
   *
   * GET /api/v1/categories
   */
  list: async (): Promise<Category[]> => {
    const response =
      await apiClient.get<
        ApiResponse<Category[]>
      >('/categories');

    return normalizeCategoryList(
      response.data?.data
    );
  },

  /**
   * ----------------------------------------------------------
   * GET ROOT CATEGORIES
   * ----------------------------------------------------------
   *
   * GET /api/v1/categories/roots
   */
  listRoots: async (): Promise<Category[]> => {
    const response =
      await apiClient.get<
        ApiResponse<Category[]>
      >('/categories/roots');

    return normalizeCategoryList(
      response.data?.data
    );
  },

  /**
   * ----------------------------------------------------------
   * GET CATEGORY BY ID
   * ----------------------------------------------------------
   *
   * GET /api/v1/categories/{id}
   */
  get: async (
    id: number | string
  ): Promise<Category> => {
    const response =
      await apiClient.get<
        ApiResponse<Category>
      >(`/categories/${id}`);

    return response.data.data;
  },

  /**
   * ----------------------------------------------------------
   * CREATE CATEGORY
   * ----------------------------------------------------------
   *
   * POST /api/v1/categories
   */
  create: async (
    data: {
      name: string;
      description?: string;
      parentId?: number | null;
    }
  ): Promise<Category> => {
    const response =
      await apiClient.post<
        ApiResponse<Category>
      >('/categories', data);

    return response.data.data;
  },

  /**
   * ----------------------------------------------------------
   * UPDATE CATEGORY
   * ----------------------------------------------------------
   *
   * PUT /api/v1/categories/{id}
   */
  update: async (
    id: number | string,
    data: {
      name?: string;
      description?: string;
      parentId?: number | null;
    }
  ): Promise<Category> => {
    const response =
      await apiClient.put<
        ApiResponse<Category>
      >(`/categories/${id}`, data);

    return response.data.data;
  },

  /**
   * ----------------------------------------------------------
   * DELETE CATEGORY
   * ----------------------------------------------------------
   *
   * DELETE /api/v1/categories/{id}
   */
  delete: async (
    id: number | string
  ): Promise<void> => {
    await apiClient.delete<
      ApiResponse<null>
    >(`/categories/${id}`);
  },
};