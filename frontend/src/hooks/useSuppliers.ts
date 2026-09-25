"use client";

import {
  useQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";

import {
  suppliersApi,
  CreateSupplierRequest,
} from "@/lib/api/suppliersApi";

/**
 * Convert an ID coming from a route, select, table, etc.
 * into the numeric ID expected by the backend API.
 */
function normalizeSupplierId(
  id: string | number,
): number {
  const numericId = Number(id);

  if (!Number.isFinite(numericId) || numericId <= 0) {
    throw new Error("Invalid supplier ID.");
  }

  return numericId;
}

/**
 * Get all suppliers.
 */
export function useSuppliers() {
  return useQuery({
    queryKey: ["suppliers"],
    queryFn: () => suppliersApi.list(),
  });
}

/**
 * Get one supplier by ID.
 *
 * Components can pass either:
 *   "5"
 * or:
 *   5
 *
 * The API always receives:
 *   5
 */
export function useSupplier(id: string | number) {
  const supplierId = Number(id);

  return useQuery({
    queryKey: ["suppliers", supplierId],

    queryFn: () =>
      suppliersApi.get(
        normalizeSupplierId(id),
      ),

    enabled:
      Number.isFinite(supplierId) &&
      supplierId > 0,
  });
}

/**
 * Create supplier.
 */
export function useCreateSupplier() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (
      data: CreateSupplierRequest,
    ) => suppliersApi.create(data),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["suppliers"],
      });
    },
  });
}

/**
 * Update supplier.
 */
export function useUpdateSupplier() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string | number;
      data: Partial<CreateSupplierRequest>;
    }) =>
      suppliersApi.update(
        normalizeSupplierId(id),
        data,
      ),

    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["suppliers"],
      });

      queryClient.invalidateQueries({
        queryKey: [
          "suppliers",
          Number(variables.id),
        ],
      });
    },
  });
}