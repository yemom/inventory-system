import apiClient from './apiClient';

export interface StockMovement {
  id: number;
  productId: number;
  productName?: string;
  productSku?: string;
  warehouseId?: number;
  warehouseName?: string;
  type: 'IN' | 'OUT' | 'TRANSFER' | 'ADJUSTMENT';
  quantity: number;   // positive = in, negative = out
  direction?: string; // "in" | "out" – derived alias from backend
  reference?: string;
  notes?: string;
  createdBy?: string;
  createdAt?: string;
  date?: string;
}

export const inventoryApi = {
  listProducts: async (): Promise<any[]> => {
    const res = await apiClient.get('/products');
    return res.data?.data?.content || [];
  },
  listMovements: async (): Promise<StockMovement[]> => {
    const res = await apiClient.get('/inventory/movements');
    return res.data?.data?.content || [];
  },
  getLowStock: async (): Promise<any[]> => {
    const res = await apiClient.get('/products');
    const products = res.data?.data?.content || [];
    // Products where quantity is at or below reorderLevel
    return products.filter((p: any) =>
      p.active && p.quantity !== undefined && p.reorderLevel && p.quantity <= p.reorderLevel
    );
  },
  listTransfers: async (): Promise<StockMovement[]> => {
    const res = await apiClient.get('/inventory/movements');
    const movements: StockMovement[] = res.data?.data?.content || [];
    return movements.filter(m => m.type === 'TRANSFER');
  },
  /**
   * Record a stock adjustment.
   * @param productId - product Long id
   * @param quantity  - positive = add stock, negative = remove
   * @param notes     - reason/note
   */
  adjust: async (productId: number, quantity: number, notes: string): Promise<any> => {
    const res = await apiClient.post('/inventory/movements', {
      productId: Number(productId),
      type: 'ADJUSTMENT',
      quantity,   // signed int – positive=add, negative=remove
      notes,
    });
    return res.data?.data;
  },
  recordMovement: async (data: Partial<StockMovement>): Promise<any> => {
    const res = await apiClient.post('/inventory/movements', data);
    return res.data?.data;
  },
  createTransfer: async (data: any): Promise<any> => {
    const res = await apiClient.post('/inventory/movements', { ...data, type: 'TRANSFER' });
    return res.data?.data || res.data;
  }
};
