import apiClient from './apiClient';

export interface Category {
  id: number | string;
  name: string;
  description?: string;
  productCount?: number;
  parentId?: number | string | null;
  [key: string]: any;
}

export const categoriesApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/categories');
    return res.data.data.content || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/categories/${id}`);
    return res.data.data;
  },
  create: async (data: Partial<Category>): Promise<any> => {
    const res = await apiClient.post('/categories', data);
    return res.data.data;
  },
  update: async (id: string | number, data: Partial<Category>): Promise<any> => {
    const res = await apiClient.put(`/categories/${id}`, data);
    return res.data.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/categories/${id}`);
    return res.data;
  }
};
