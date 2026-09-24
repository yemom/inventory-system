import apiClient from './apiClient';

export interface Customer {
  id: number;
  customerNumber?: string;
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  customerType?: string;
  creditLimit?: number;
  outstandingBalance?: number;
  status?: string;
  createdAt?: string;
  [key: string]: any;
}

export interface CreateCustomerPayload {
  name: string;
  phone?: string;
  email?: string;
  address?: string;
  customerType?: string;
  creditLimit?: number;
}

export const customersApi = {
  list: async (page = 0, size = 100, search = ''): Promise<Customer[]> => {
    const res = await apiClient.get('/customers', { params: { page, size, search } });
    // Backend: ApiResponse<Page<CustomerDTO>>  →  res.data.data.content
    return res.data?.data?.content || [];
  },
  get: async (id: number | string): Promise<Customer> => {
    const res = await apiClient.get(`/customers/${id}`);
    return res.data?.data;
  },
  create: async (data: CreateCustomerPayload): Promise<Customer> => {
    const res = await apiClient.post('/customers', data);
    return res.data?.data;
  },
  update: async (id: number | string, data: Partial<CreateCustomerPayload & { status: string }>): Promise<Customer> => {
    const res = await apiClient.put(`/customers/${id}`, data);
    return res.data?.data;
  },
};
