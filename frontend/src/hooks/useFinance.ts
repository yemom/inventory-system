'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paymentsApi } from '@/lib/api/paymentsApi';
import { expensesApi } from '@/lib/api/expensesApi';

export function usePayments() {
  return useQuery({ queryKey: ['payments'], queryFn: () => paymentsApi.list() });
}
export function useCreatePayment() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (data: any) => paymentsApi.create(data), onSuccess: () => qc.invalidateQueries({ queryKey: ['payments'] }) });
}
export function useExpenses() {
  return useQuery({ queryKey: ['expenses'], queryFn: () => expensesApi.list() });
}
export function useCreateExpense() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (data: any) => expensesApi.create(data), onSuccess: () => qc.invalidateQueries({ queryKey: ['expenses'] }) });
}
export function useDeleteExpense() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => expensesApi.delete(id), onSuccess: () => qc.invalidateQueries({ queryKey: ['expenses'] }) });
}
