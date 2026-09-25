import apiClient from './apiClient';
import {
  extractItem,
  extractList,
} from './response';

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

  notes?: string;

  createdAt?: string;

  updatedAt?: string;
}

export interface CreateCustomerRequest {
  name: string;

  phone?: string;

  email?: string;

  address?: string;

  customerType?: string;

  creditLimit?: number;

  notes?: string;
}

export const customersApi = {

  async list(
    page = 0,
    size = 100,
    search = ''
  ): Promise<Customer[]> {

    const response =
      await apiClient.get(
        '/customers',
        {
          params: {
            page,
            size,
            search:
              search.trim() || undefined,
          },
        }
      );

    return extractList<Customer>(
      response.data
    );
  },

  async get(
    id: number
  ): Promise<Customer> {

    const response =
      await apiClient.get(
        `/customers/${id}`
      );

    const customer =
      extractItem<Customer>(
        response.data
      );

    if (!customer) {
      throw new Error(
        'Customer not found'
      );
    }

    return customer;
  },

  async create(
    data: CreateCustomerRequest
  ): Promise<Customer> {

    const response =
      await apiClient.post(
        '/customers',
        data
      );

    const customer =
      extractItem<Customer>(
        response.data
      );

    if (!customer) {
      throw new Error(
        'Customer was created but no customer was returned'
      );
    }

    return customer;
  },

  async update(
    id: number,
    data: Partial<CreateCustomerRequest>
  ): Promise<Customer> {

    const response =
      await apiClient.put(
        `/customers/${id}`,
        data
      );

    const customer =
      extractItem<Customer>(
        response.data
      );

    if (!customer) {
      throw new Error(
        'Customer was updated but no customer was returned'
      );
    }

    return customer;
  },
};