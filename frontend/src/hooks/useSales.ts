'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { salesApi } from '@/lib/api/salesApi';
import type { SaleOrder } from '@/lib/api/mockData';

export function useSales() {
  return useQuery({ queryKey: ['sales'], queryFn: salesApi.list });
}

export function useSale(id: string) {
  return useQuery({ queryKey: ['sales', id], queryFn: () => salesApi.get(id), enabled: !!id });
}

export function useCreateSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: Omit<SaleOrder, 'id'>) => salesApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sales'] }),
  });
}

export function useUpdateSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<SaleOrder> }) => salesApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sales'] }),
  });
}

export function useDeleteSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => salesApi.delete(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['sales'] }),
  });
}
