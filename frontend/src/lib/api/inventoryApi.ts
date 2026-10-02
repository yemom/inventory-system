import apiClient, { fetchAllPages } from './apiClient';

export interface StockMovement {
  id: number;

  productId: number;

  productName?: string;

  productSku?: string;

  warehouseId?: number;

  warehouseName?: string;

  type:
  | 'IN'
  | 'OUT'
  | 'TRANSFER'
  | 'ADJUSTMENT';

  quantity: number;

  direction?: string;

  reference?: string;

  notes?: string;

  createdBy?: string;

  createdAt?: string;

  date?: string;
}

export interface CreateStockMovementRequest {
  productId: number;
  warehouseId?: number;
  type:
  | 'IN'
  | 'OUT'
  | 'TRANSFER'
  | 'ADJUSTMENT';
  quantity: number;
  reference?: string;
  notes?: string;
}

export const inventoryApi = {
  listProducts: async (): Promise<any[]> => {
    return fetchAllPages('/products');
  },

  listMovements:
    async (): Promise<StockMovement[]> => {
      return fetchAllPages<StockMovement>('/inventory/movements');
    },

  getLowStock: async (): Promise<any[]> => {
    const products = await fetchAllPages('/products');

    return products.filter(
      (product: any) =>
        product.active &&
        product.quantity !== undefined &&
        product.reorderLevel !== undefined &&
        product.quantity <=
        product.reorderLevel,
    );
  },

  listTransfers:
    async (): Promise<StockMovement[]> => {
      const movements =
        await fetchAllPages<StockMovement>('/inventory/movements');

      return movements.filter(
        (movement: StockMovement) =>
          movement.type === 'TRANSFER',
      );
    },

  adjust: async (
    productId: number,
    quantity: number,
    notes: string,
  ): Promise<any> => {
    const response =
      await apiClient.post(
        '/inventory/movements',
        {
          productId,
          type: 'ADJUSTMENT',
          quantity,
          notes,
        },
      );

    return response.data?.data;
  },

  recordMovement: async (
    data: CreateStockMovementRequest,
  ): Promise<StockMovement> => {
    const response =
      await apiClient.post(
        '/inventory/movements',
        data,
      );

    return response.data?.data;
  },
};