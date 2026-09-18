'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { purchasesApi } from '@/lib/api/purchasesApi';

export function usePurchases() {
  return useQuery({ queryKey: ['purchases'], queryFn: () => purchasesApi.list() });
}
export function usePurchase(id: string) {
  return useQuery({ queryKey: ['purchases', id], queryFn: () => purchasesApi.get(id), enabled: !!id });
}
export function useCreatePurchase() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (data: any) => purchasesApi.create(data), onSuccess: () => qc.invalidateQueries({ queryKey: ['purchases'] }) });
}
export function useUpdatePurchase() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: ({ id, data }: { id: string; data: any }) => purchasesApi.update(id, data), onSuccess: () => qc.invalidateQueries({ queryKey: ['purchases'] }) });
}

export function useDeletePurchase() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => purchasesApi.delete(id), onSuccess: () => qc.invalidateQueries({ queryKey: ['purchases'] }) });
}
