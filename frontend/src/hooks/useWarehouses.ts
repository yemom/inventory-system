'use client';

import {
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import { warehousesApi } from '@/lib/api/warehousesApi';

export function useWarehouses() {
  return useQuery({
    queryKey: ['warehouses'],
    queryFn: () => warehousesApi.list(),
  });
}

export function useWarehouse(id: string | number) {
  return useQuery({
    queryKey: ['warehouses', id],
    queryFn: () => warehousesApi.get(id),
    enabled: !!id,
  });
}

export function useCreateWarehouse() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      location?: string;
      managerName?: string;
      contactPhone?: string;
    }) => warehousesApi.create(data),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['warehouses'],
      });
    },
  });
}

export function useUpdateWarehouse() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      data,
    }: {
      id: string | number;
      data: {
        name: string;
        location?: string;
        managerName?: string;
        contactPhone?: string;
      };
    }) => warehousesApi.update(id, data),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['warehouses'],
      });
    },
  });
}

export function useDeleteWarehouse() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string | number) =>
      warehousesApi.delete(id),

    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['warehouses'],
      });
    },
  });
}