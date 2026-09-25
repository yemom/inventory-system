'use client';

import {
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import { productsApi } from '@/lib/api/productsApi';

export function useProducts() {
  return useQuery({
    queryKey: ['products'],
    queryFn: () => productsApi.list(),
  });
}

export function useProduct(id: string | number) {
  return useQuery({
    queryKey: ['products', id],
    queryFn: () => productsApi.get(id),
    enabled: !!id,
  });
}

export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: any) => productsApi.create(data),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['products'],
      });

      queryClient.invalidateQueries({
        queryKey: ['inventory'],
      });
    },
  });
}

export function useUpdateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string | number;
      data: any;
    }) => productsApi.update(id, data),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['products'],
      });

      queryClient.invalidateQueries({
        queryKey: ['inventory'],
      });
    },
  });
}

export function useDeleteProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string | number) =>
      productsApi.delete(id),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['products'],
      });

      queryClient.invalidateQueries({
        queryKey: ['inventory'],
      });
    },
  });
}