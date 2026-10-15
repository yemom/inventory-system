import apiClient, { fetchAllPages } from './apiClient';

/**
 * Stock movement types.
 *
 * The backend records the reason as well as the direction — a purchase receipt
 * and a sales return are both stock coming in, but they mean different things to
 * a reader — so this is the full set from {@code StockMovement.TYPE_*}, not the
 * four directions. Typing it as the four directions meant every one of the
 * others was a type error the moment a screen tried to render it.
 */
export type StockMovementType =
  | 'OPENING'
  | 'IN'
  | 'PURCHASE_RECEIPT'
  | 'SALE'
  | 'OUT'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'
  | 'TRANSFER'
  | 'SALES_RETURN'
  | 'PURCHASE_RETURN'
  | 'DAMAGE'
  | 'EXPIRY'
  | 'LOSS'
  | 'ADJUSTMENT'
  | 'STOCK_COUNT';

export interface StockMovement {
  id: number;

  productId: number;

  productName?: string;

  productSku?: string;

  warehouseId?: number;

  warehouseName?: string;

  type: StockMovementType | string;

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