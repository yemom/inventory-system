import apiClient from './apiClient';

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
    const response = await apiClient.get('/warehouses');

    const data = response.data?.data;

    // Backend returns:
    // {
    //   success: true,
    //   message: "...",
    //   data: [...]
    // }
    if (Array.isArray(data)) {
      return data;
    }

    // Defensive support if backend later returns pagination.
    if (Array.isArray(data?.content)) {
      return data.content;
    }

    return [];
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