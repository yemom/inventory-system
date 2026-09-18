'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { inventoryApi } from '@/lib/api/inventoryApi';

export function useInventory() {
  return useQuery({ queryKey: ['inventory'], queryFn: () => inventoryApi.listProducts() });
}
export function useStockMovements() {
  return useQuery({ queryKey: ['stock-movements'], queryFn: () => inventoryApi.listMovements() });
}
export function useLowStock() {
  return useQuery({ queryKey: ['low-stock'], queryFn: () => inventoryApi.getLowStock() });
}
export function useTransfers() {
  return useQuery({ queryKey: ['transfers'], queryFn: () => inventoryApi.listTransfers() });
}
export function useStockAdjust() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, qty, note }: { productId: string; qty: number; note: string }) => inventoryApi.adjust(productId, qty, note),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['inventory'] }); qc.invalidateQueries({ queryKey: ['stock-movements'] }); qc.invalidateQueries({ queryKey: ['low-stock'] }); },
  });
}
