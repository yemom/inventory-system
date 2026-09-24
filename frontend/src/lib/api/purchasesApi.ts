import apiClient from './apiClient';

export interface PurchaseOrder {
  id: string | number;
  orderNumber?: string;
  reference?: string;
  supplierName: string;
  totalAmount?: number;
  total?: number;
  status: string;
  paymentStatus?: string;
  paid?: number;
  date?: string;
  createdAt?: string;
  [key: string]: any;
}

export const purchasesApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/purchases');
    return res.data?.data?.content || res.data?.data || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/purchases/${id}`);
    return res.data?.data || res.data;
  },
  create: async (data: any): Promise<any> => {
    const res = await apiClient.post('/purchases', data);
    return res.data?.data || res.data;
  },
  update: async (id: string | number, data: any): Promise<any> => {
    const res = await apiClient.put(`/purchases/${id}`, data);
    return res.data?.data || res.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/purchases/${id}`);
    return res.data;
  }
};
