'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { warehousesApi } from '@/lib/api/warehousesApi';

export function useWarehouses() {
  return useQuery({ queryKey: ['warehouses'], queryFn: () => warehousesApi.list() });
}

export function useWarehouse(id: string) {
  return useQuery({ queryKey: ['warehouses', id], queryFn: () => warehousesApi.get(id), enabled: !!id });
}

export function useCreateWarehouse() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (data: any) => warehousesApi.create(data), onSuccess: () => qc.invalidateQueries({ queryKey: ['warehouses'] }) });
}

export function useUpdateWarehouse() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: ({ id, data }: { id: string; data: any }) => warehousesApi.update(id, data), onSuccess: () => qc.invalidateQueries({ queryKey: ['warehouses'] }) });
}

export function useDeleteWarehouse() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => warehousesApi.delete(id), onSuccess: () => qc.invalidateQueries({ queryKey: ['warehouses'] }) });
}
