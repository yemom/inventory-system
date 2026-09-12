import { delay, auditLogs as mockLogs, type AuditLog } from './mockData';
export const auditLogsApi = {
  list: async (): Promise<AuditLog[]> => { await delay(300); return [...mockLogs].sort((a, b) => b.timestamp.localeCompare(a.timestamp)); },
};
