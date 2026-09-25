import apiClient from "./apiClient";
import {
  extractItem,
  extractList,
} from "./response";

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

  notes?: string;

  createdAt?: string;

  updatedAt?: string;
}

export interface CreateSupplierRequest {
  companyName: string;

  contactPerson?: string;

  phone?: string;

  email?: string;

  address?: string;

  taxNumber?: string;

  paymentTerms?: string;

  notes?: string;
}

export const suppliersApi = {
  /**
   * Get suppliers.
   */
  async list(
    page = 0,
    size = 100,
    search = "",
  ): Promise<Supplier[]> {
    const response =
      await apiClient.get("/suppliers", {
        params: {
          page,
          size,
          search:
            search.trim() || undefined,
        },
      });

    return extractList<Supplier>(
      response.data,
    );
  },

  /**
   * Get supplier by ID.
   */
  async get(id: number): Promise<Supplier> {
    const response =
      await apiClient.get(
        `/suppliers/${id}`,
      );

    const supplier =
      extractItem<Supplier>(
        response.data,
      );

    if (!supplier) {
      throw new Error(
        "Supplier not found",
      );
    }

    return supplier;
  },

  /**
   * Create supplier.
   */
  async create(
    data: CreateSupplierRequest,
  ): Promise<Supplier> {
    const response =
      await apiClient.post(
        "/suppliers",
        data,
      );

    const supplier =
      extractItem<Supplier>(
        response.data,
      );

    if (!supplier) {
      throw new Error(
        "Supplier was created but no supplier was returned",
      );
    }

    return supplier;
  },

  /**
   * Update supplier.
   */
  async update(
    id: number,
    data: Partial<CreateSupplierRequest>,
  ): Promise<Supplier> {
    const response =
      await apiClient.put(
        `/suppliers/${id}`,
        data,
      );

    const supplier =
      extractItem<Supplier>(
        response.data,
      );

    if (!supplier) {
      throw new Error(
        "Supplier was updated but no supplier was returned",
      );
    }

    return supplier;
  },
};