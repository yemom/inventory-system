import apiClient, { fetchAllPages } from './apiClient';

export interface Warehouse {
  id: number;
  name: string;
  location?: string;
  managerName?: string;
  contactPhone?: string;
  active: boolean;
  createdAt?: string;
}

export interface CreateWarehouseRequest {
  name: string;
  location?: string;
  managerName?: string;
  contactPhone?: string;
}

export const warehousesApi = {
  list: async (): Promise<Warehouse[]> => {
    return fetchAllPages<Warehouse>('/warehouses');
  },

  get: async (
    id: string | number,
  ): Promise<Warehouse> => {
    const response = await apiClient.get(
      `/warehouses/${id}`,
    );

    return response.data?.data;
  },

  create: async (
    data: CreateWarehouseRequest,
  ): Promise<Warehouse> => {
    const response = await apiClient.post(
      '/warehouses',
      data,
    );

    return response.data?.data;
  },

  update: async (
    id: string | number,
    data: CreateWarehouseRequest,
  ): Promise<Warehouse> => {
    const response = await apiClient.put(
      `/warehouses/${id}`,
      data,
    );

    return response.data?.data;
  },

  delete: async (
    id: string | number,
  ): Promise<void> => {
    await apiClient.delete(`/warehouses/${id}`);
  },
};