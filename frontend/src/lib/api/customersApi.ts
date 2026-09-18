import apiClient from './apiClient';

export interface CreateCustomerPayload {
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  customerType?: string;
  creditLimit?: number;
}

export const customersApi = {
  list: async (page = 0, size = 20, search = '') => {
    const res = await apiClient.get('/customers', { params: { page, size, search } });
    return res.data;
  },
  get: async (id: number | string) => {
    const res = await apiClient.get(`/customers/${id}`);
    return res.data;
  },
  create: async (data: CreateCustomerPayload) => {
    const res = await apiClient.post('/customers', data);
    return res.data;
  },
  update: async (id: number | string, data: Partial<CreateCustomerPayload & { status: string }>) => {
    const res = await apiClient.put(`/customers/${id}`, data);
    return res.data;
  },
};
