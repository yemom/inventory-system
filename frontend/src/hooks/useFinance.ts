'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { paymentsApi } from '@/lib/api/paymentsApi';
import { expensesApi } from '@/lib/api/expensesApi';

export function usePayments() {
  return useQuery({ queryKey: ['payments'], queryFn: () => paymentsApi.list() });
}

/**
 * Records money received against a sale order.
 *
 * <p>The mutation takes the document id and the amount, and nothing else. The
 * previous version posted a whole payment object with its own direction, party
 * and status, which meant a client could declare an amount received for an
 * invoice that had not been paid. Direction, party and status are now the
 * server's to decide, from the document.
 */
export function useCreatePayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      saleOrderId: string | number;
      amount: number;
      method?: string;
      reference?: string;
      notes?: string;
    }) => paymentsApi.recordForSale(data.saleOrderId, data),
    onSuccess: () => {
      // Balances move when a payment is recorded, so anything derived from
      // them is now stale — not just the payment list.
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['report'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['finance'] });
    },
  });
}

/** Records money paid to a supplier against a purchase order. */
export function useCreateSupplierPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      purchaseOrderId: string | number;
      amount: number;
      method?: string;
      reference?: string;
      notes?: string;
    }) => paymentsApi.recordForPurchase(data.purchaseOrderId, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['purchases'] });
      qc.invalidateQueries({ queryKey: ['report'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['finance'] });
    },
  });
}

/** Records a refund paid back to a customer. */
export function useCreateRefund() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: {
      saleOrderId?: string | number;
      amount: number;
      method?: string;
      notes?: string;
    }) => paymentsApi.refund(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['report'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
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
