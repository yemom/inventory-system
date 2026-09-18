import apiClient from './apiClient';

export interface CreateSupplierPayload {
  companyName: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  address?: string;
  taxNumber?: string;
  paymentTerms?: string;
}

export const suppliersApi = {
  list: async (page = 0, size = 20, search = '') => {
    const res = await apiClient.get('/suppliers', { params: { page, size, search } });
    return res.data;
  },
  get: async (id: number | string) => {
    const res = await apiClient.get(`/suppliers/${id}`);
    return res.data;
  },
  create: async (data: CreateSupplierPayload) => {
    const res = await apiClient.post('/suppliers', data);
    return res.data;
  },
  update: async (id: number | string, data: Partial<CreateSupplierPayload & { status: string }>) => {
    const res = await apiClient.put(`/suppliers/${id}`, data);
    return res.data;
  },
};
