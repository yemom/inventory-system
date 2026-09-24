import apiClient from './apiClient';

export interface Payment {
  id: string | number;
  reference?: string;
  orderReference?: string;
  type: string;
  amount: number;
  paymentMethod: string;
  status: string;
  date?: string;
  createdBy?: string;
  createdAt?: string;
  [key: string]: any;
}

export const paymentsApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/payments');
    return res.data?.data?.content || res.data?.data || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/payments/${id}`);
    return res.data?.data || res.data;
  },
  create: async (data: any): Promise<any> => {
    const res = await apiClient.post('/payments', data);
    return res.data?.data || res.data;
  },
  update: async (id: string | number, data: any): Promise<any> => {
    const res = await apiClient.put(`/payments/${id}`, data);
    return res.data?.data || res.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/payments/${id}`);
    return res.data;
  }
};
