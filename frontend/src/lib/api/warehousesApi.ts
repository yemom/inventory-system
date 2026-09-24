import apiClient from './apiClient';

export interface Warehouse {
  id: number | string;
  name: string;
  code: string;
  location?: string;
  manager?: string;
  capacity?: number;
  status?: string;
  [key: string]: any;
}

export const warehousesApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/warehouses');
    return res.data.data.content || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/warehouses/${id}`);
    return res.data.data;
  },
  create: async (data: Partial<Warehouse>): Promise<any> => {
    const res = await apiClient.post('/warehouses', data);
    return res.data.data;
  },
  update: async (id: string | number, data: Partial<Warehouse>): Promise<any> => {
    const res = await apiClient.put(`/warehouses/${id}`, data);
    return res.data.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/warehouses/${id}`);
    return res.data;
  }
};
