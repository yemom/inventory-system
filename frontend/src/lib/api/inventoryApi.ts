import apiClient from './apiClient';

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
    const response =
      await apiClient.get('/products');

    return (
      response.data?.data?.content ??
      response.data?.data ??
      []
    );
  },

  listMovements:
    async (): Promise<StockMovement[]> => {
      const response =
        await apiClient.get(
          '/inventory/movements',
        );

      return (
        response.data?.data?.content ??
        []
      );
    },

  getLowStock: async (): Promise<any[]> => {
    const response =
      await apiClient.get('/products');

    const products =
      response.data?.data?.content ??
      response.data?.data ??
      [];

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
      const response =
        await apiClient.get(
          '/inventory/movements',
        );

      const movements =
        response.data?.data?.content ??
        [];

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