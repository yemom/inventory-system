import apiClient from './apiClient';

export interface SaleOrder {
  id: string | number;
  orderNumber?: string;
  reference?: string;
  customerName: string;
  totalAmount?: number;
  total?: number; // fallback mapping
  status: string;
  paymentStatus?: string;
  paid?: number;
  date?: string;
  createdAt?: string;
  [key: string]: any;
}

export const salesApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/sales');
    return res.data?.data?.content || res.data?.data || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/sales/${id}`);
    return res.data?.data || res.data;
  },
  create: async (data: any): Promise<any> => {
    const res = await apiClient.post('/sales', data);
    return res.data?.data || res.data;
  },
  update: async (id: string | number, data: any): Promise<any> => {
    const res = await apiClient.put(`/sales/${id}`, data);
    return res.data?.data || res.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/sales/${id}`);
    return res.data;
  }
};
