'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { suppliersApi, CreateSupplierPayload } from '@/lib/api/suppliersApi';

export function useSuppliers() {
  return useQuery({ queryKey: ['suppliers'], queryFn: () => suppliersApi.list() });
}

export function useSupplier(id: string) {
  return useQuery({ queryKey: ['suppliers', id], queryFn: () => suppliersApi.get(id), enabled: !!id });
}

export function useCreateSupplier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateSupplierPayload) => suppliersApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suppliers'] }),
  });
}

export function useUpdateSupplier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: any }) => suppliersApi.update(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suppliers'] }),
  });
}
