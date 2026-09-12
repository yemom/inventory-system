'use client';
import React from 'react';
import PageHeader from '@/components/ui/PageHeader';
import DataTable, { Column } from '@/components/ui/DataTable';
import Badge from '@/components/ui/Badge';
import ErrorState from '@/components/ui/ErrorState';
import { useAuditLogs } from '@/hooks/useUsers';

export default function AuditLogsPage() {
  const { data: logs = [], isLoading, error, refetch } = useAuditLogs();

  const actionColors: Record<string, 'success' | 'info' | 'warning' | 'danger' | 'default'> = {
    CREATE: 'success', UPDATE: 'info', DELETE: 'danger', LOGIN: 'default', APPROVE: 'warning',
  };

  const columns: Column<Record<string, unknown>>[] = [
    { key: 'timestamp', label: 'Timestamp', render: v => new Date(String(v)).toLocaleString() },
    { key: 'username', label: 'User', render: v => <span className="font-medium">{String(v)}</span> },
    { key: 'action', label: 'Action', render: v => <Badge variant={actionColors[String(v)] ?? 'default'}>{String(v)}</Badge> },
    { key: 'resource', label: 'Resource', render: (v, row) => <span className="font-mono text-xs">{String(v)} {row.resourceId ? `#${row.resourceId}` : ''}</span> },
    { key: 'details', label: 'Details', render: v => v ? <span className="text-gray-600 dark:text-gray-400">{String(v)}</span> : '-' },
    { key: 'ip', label: 'IP Address', render: v => <span className="text-xs text-gray-500">{String(v)}</span> },
  ];

  if (error) return <ErrorState retry={refetch} />;

  return (
    <div>
      <PageHeader title="Audit Logs" subtitle="System activity and security trail" />
      <DataTable columns={columns} data={logs as unknown as Record<string, unknown>[]} loading={isLoading} searchable searchPlaceholder="Search logs..." />
    </div>
  );
}
