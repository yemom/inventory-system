import apiClient from './apiClient';

export interface Product {
  id: number;          // always a number from the backend (Long)
  name: string;
  sku: string;
  barcode?: string;
  unit?: string;
  description?: string;
  categoryId?: number;
  categoryName?: string;
  purchasePrice?: number;
  sellingPrice: number;
  minSellingPrice?: number;
  minStockLevel?: number;
  maxStockLevel?: number;
  reorderLevel?: number;
  quantity: number;    // current stock on hand
  batchTracked?: boolean;
  expiryTracked?: boolean;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
  [key: string]: any;
}

export const productsApi = {
  list: async (): Promise<Product[]> => {
    const res = await apiClient.get('/products');
    return res.data?.data?.content || [];
  },
  get: async (id: number | string): Promise<Product> => {
    const res = await apiClient.get(`/products/${id}`);
    return res.data?.data;
  },
  create: async (data: Partial<Product>): Promise<Product> => {
    const res = await apiClient.post('/products', data);
    return res.data?.data;
  },
  update: async (id: number | string, data: Partial<Product>): Promise<Product> => {
    const res = await apiClient.put(`/products/${id}`, data);
    return res.data?.data;
  },
  delete: async (id: number | string): Promise<any> => {
    const res = await apiClient.delete(`/products/${id}`);
    return res.data;
  }
};
