import apiClient from './apiClient';

export interface Supplier {
  id: number;
  supplierNumber?: string;
  companyName: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  address?: string;
  taxNumber?: string;
  paymentTerms?: string;
  outstandingBalance?: number;
  status?: string;
  createdAt?: string;
  [key: string]: any;
}

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
  list: async (page = 0, size = 100, search = ''): Promise<Supplier[]> => {
    const res = await apiClient.get('/suppliers', { params: { page, size, search } });
    // Backend: ApiResponse<Page<SupplierDTO>>  →  res.data.data.content
    return res.data?.data?.content || [];
  },
  get: async (id: number | string): Promise<Supplier> => {
    const res = await apiClient.get(`/suppliers/${id}`);
    return res.data?.data;
  },
  create: async (data: CreateSupplierPayload): Promise<Supplier> => {
    const res = await apiClient.post('/suppliers', data);
    return res.data?.data;
  },
  update: async (id: number | string, data: Partial<CreateSupplierPayload & { status: string }>): Promise<Supplier> => {
    const res = await apiClient.put(`/suppliers/${id}`, data);
    return res.data?.data;
  },
};
