'use client';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { usersApi, CreateUserPayload, StaffListParams, UpdateUserPayload } from '@/lib/api/usersApi';

export function useUsers() {
  return useQuery({ queryKey: ['users'], queryFn: () => usersApi.list() });
}
export function useStaff(params: StaffListParams) {
  return useQuery({ queryKey: ['staff', params], queryFn: () => usersApi.listStaff(params) });
}
export function useStaffStats() {
  return useQuery({ queryKey: ['staff-stats'], queryFn: () => usersApi.staffStats() });
}
export function useUser(id: string) {
  return useQuery({ queryKey: ['users', id], queryFn: () => usersApi.get(id), enabled: !!id });
}
export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateUserPayload) => usersApi.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['staff'] });
      qc.invalidateQueries({ queryKey: ['staff-stats'] });
    },
  });
}
export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateUserPayload }) => usersApi.update(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      qc.invalidateQueries({ queryKey: ['staff'] });
      qc.invalidateQueries({ queryKey: ['staff-stats'] });
    },
  });
}

import apiClient from '@/lib/api/apiClient';

export function useAuditLogs() {
  return { data: [], isLoading: false, error: null, refetch: () => {} };
}
