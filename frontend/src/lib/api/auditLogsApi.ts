import apiClient from './apiClient';

export interface AuditLog {
  id: string | number;
  userId?: string | number;
  username: string;
  action: string;
  entityType: string;
  entityId: string;
  details?: string;
  ipAddress?: string;
  timestamp?: string;
  createdAt?: string;
  [key: string]: any;
}

export const auditLogsApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/audit-logs');
    return res.data?.data?.content || res.data?.data || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/audit-logs/${id}`);
    return res.data?.data || res.data;
  }
};
