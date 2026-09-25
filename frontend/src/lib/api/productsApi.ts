import apiClient from './apiClient';
import {
  extractItem,
  extractList,
} from './response';

export interface Product {
  id: number;

  name: string;

  sku: string;

  barcode?: string;

  unit?: string;

  description?: string;

  categoryId?: number | null;

  categoryName?: string;

  purchasePrice?: number;

  sellingPrice: number;

  minSellingPrice?: number;

  minStockLevel?: number;

  maxStockLevel?: number;

  reorderLevel?: number;

  quantity: number;

  batchTracked?: boolean;

  expiryTracked?: boolean;

  active: boolean;

  createdAt?: string;

  updatedAt?: string;
}

export interface CreateProductRequest {
  name: string;

  sku: string;

  barcode?: string;

  unit?: string;

  description?: string;

  categoryId?: number | null;

  purchasePrice?: number;

  sellingPrice: number;

  minSellingPrice?: number;

  minStockLevel?: number;

  maxStockLevel?: number;

  reorderLevel?: number;

  quantity?: number;

  batchTracked?: boolean;

  expiryTracked?: boolean;
}

export const productsApi = {

  async list(): Promise<Product[]> {

    const response =
      await apiClient.get(
        '/products'
      );

    return extractList<Product>(
      response.data
    );
  },

  async get(
    id: number
  ): Promise<Product> {

    const response =
      await apiClient.get(
        `/products/${id}`
      );

    const product =
      extractItem<Product>(
        response.data
      );

    if (!product) {
      throw new Error(
        'Product not found'
      );
    }

    return product;
  },

  async create(
    data: CreateProductRequest
  ): Promise<Product> {

    const response =
      await apiClient.post(
        '/products',
        data
      );

    const product =
      extractItem<Product>(
        response.data
      );

    if (!product) {
      throw new Error(
        'Product was created but no product was returned'
      );
    }

    return product;
  },

  async update(
    id: number,
    data: Partial<CreateProductRequest>
  ): Promise<Product> {

    const response =
      await apiClient.put(
        `/products/${id}`,
        data
      );

    const product =
      extractItem<Product>(
        response.data
      );

    if (!product) {
      throw new Error(
        'Product was updated but no product was returned'
      );
    }

    return product;
  },

  async delete(
    id: number
  ): Promise<void> {

    await apiClient.delete(
      `/products/${id}`
    );
  },
};