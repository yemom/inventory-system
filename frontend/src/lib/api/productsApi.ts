import apiClient from './apiClient';

export interface Product {
  id: number | string;
  name: string;
  sku: string;
  categoryId: number;
  categoryName?: string;
  quantity: number;
  unit: string;
  costPrice: number;
  sellingPrice: number;
  minStockLevel?: number;
  description?: string;
  [key: string]: any;
}

export const productsApi = {
  list: async (): Promise<any[]> => {
    const res = await apiClient.get('/products');
    return res.data.data.content || [];
  },
  get: async (id: string | number): Promise<any> => {
    const res = await apiClient.get(`/products/${id}`);
    return res.data.data;
  },
  create: async (data: Partial<Product>): Promise<any> => {
    const res = await apiClient.post('/products', data);
    return res.data.data;
  },
  update: async (id: string | number, data: Partial<Product>): Promise<any> => {
    const res = await apiClient.put(`/products/${id}`, data);
    return res.data.data;
  },
  delete: async (id: string | number): Promise<any> => {
    const res = await apiClient.delete(`/products/${id}`);
    return res.data;
  }
};
