import apiClient from './apiClient';

export interface Expense {
  id: string | number;
  reference?: string;
  category: string;
  amount: number;
  date?: string;
  description?: string;
  status: string;
  createdBy?: string;
  createdAt?: string;
  [key: string]: any;
}

export const expensesApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/expenses');
    return res.data?.data?.content || res.data?.data || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/expenses/${id}`);
    return res.data?.data || res.data;
  },
  create: async (data: any): Promise<any> => {
    const res = await apiClient.post('/expenses', data);
    return res.data?.data || res.data;
  },
  update: async (id: string | number, data: any): Promise<any> => {
    const res = await apiClient.put(`/expenses/${id}`, data);
    return res.data?.data || res.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/expenses/${id}`);
    return res.data;
  }
};
