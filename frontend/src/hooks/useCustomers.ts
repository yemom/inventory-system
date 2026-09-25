"use client";

import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { customersApi } from "@/lib/api/customersApi";

export function useCustomers() {
  return useQuery({
    queryKey: ["customers"],
    queryFn: async () => {
      const customers = await customersApi.list();
      return customers ?? [];
    },
  });
}

export function useCustomer(id: string | number | undefined) {
  return useQuery({
    queryKey: ["customers", id],
    queryFn: () => customersApi.get(id as string | number),
    enabled: id !== undefined && id !== null && String(id).length > 0,
  });
}

export function useCreateCustomer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: customersApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ["customers"],
      });
    },
  });
}

export function useUpdateCustomer() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string | number;
      data: any;
    }) => customersApi.update(id, data),

    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: ["customers"],
      });

      queryClient.invalidateQueries({
        queryKey: ["customers", variables.id],
      });
    },
  });
}