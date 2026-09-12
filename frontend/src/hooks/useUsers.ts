'use client';
import { useQuery } from '@tanstack/react-query';
import { usersApi } from '@/lib/api/usersApi';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { User } from '@/lib/api/mockData';
import { auditLogsApi } from '@/lib/api/auditLogsApi';

export function useUsers() {
  return useQuery({ queryKey: ['users'], queryFn: usersApi.list });
}
export function useUser(id: string) {
  return useQuery({ queryKey: ['users', id], queryFn: () => usersApi.get(id), enabled: !!id });
}
export function useCreateUser() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (data: Omit<User, 'id' | 'createdAt' | 'lastLogin'>) => usersApi.create(data), onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }) });
}
export function useUpdateUser() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: ({ id, data }: { id: string; data: Partial<User> }) => usersApi.update(id, data), onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }) });
}
export function useDeleteUser() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: (id: string) => usersApi.delete(id), onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }) });
}
export function useAuditLogs() {
  return useQuery({ queryKey: ['audit-logs'], queryFn: auditLogsApi.list });
}
