import apiClient from './apiClient';

export interface StockMovement {
  id: number;
  productId: number;
  productName?: string;
  productSku?: string;
  warehouseId?: number;
  warehouseName?: string;
  type: 'IN' | 'OUT' | 'TRANSFER' | 'ADJUSTMENT';
  quantity: number;
  reference?: string;
  notes?: string;
  createdBy?: string;
  createdAt?: string;
}

export const inventoryApi = {
  listProducts: async (): Promise<any[]> => {
    const res = await apiClient.get('/products');
    return res.data.data.content || [];
  },
  listMovements: async (): Promise<any[]> => {
    const res = await apiClient.get('/inventory/movements');
    return res.data.data.content || [];
  },
  getLowStock: async (): Promise<any[]> => {
    const res = await apiClient.get('/products');
    const products = res.data.data.content || [];
    return products.filter((p: any) => p.minStockLevel && p.minStockLevel > 0 && p.minStockLevel <= (p.reorderLevel || 10));
  },
  listTransfers: async (): Promise<any[]> => {
    const res = await apiClient.get('/inventory/movements');
    const movements = res.data.data.content || [];
    return movements.filter((m: any) => m.type === 'TRANSFER');
  },
  adjust: async (productId: number | string, quantity: number, notes: string): Promise<any> => {
    const res = await apiClient.post('/inventory/movements', {
      productId,
      type: 'ADJUSTMENT',
      quantity,
      notes
    });
    return res.data.data;
  },
  recordMovement: async (data: Partial<StockMovement>): Promise<any> => {
    const res = await apiClient.post('/inventory/movements', data);
    return res.data.data;
  },
  createTransfer: async (data: any): Promise<any> => {
    const res = await apiClient.post('/inventory/transfers', data);
    return res.data?.data || res.data;
  }
};
