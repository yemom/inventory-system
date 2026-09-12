import { delay, stockMovements, transfers, type StockMovement, type Product, type Transfer } from './mockData';
import { productStore } from './productsApi';
const movementsStore: StockMovement[] = [...stockMovements];
const transfersStore: Transfer[] = [...transfers];

export const inventoryApi = {
  listProducts: async (): Promise<Product[]> => { await delay(300); return [...productStore]; },
  listMovements: async (): Promise<StockMovement[]> => { await delay(300); return [...movementsStore].sort((a, b) => b.date.localeCompare(a.date)); },
  getLowStock: async (): Promise<Product[]> => { await delay(300); return productStore.filter(p => p.quantity <= p.reorderLevel); },
  getOutOfStock: async (): Promise<Product[]> => { await delay(300); return productStore.filter(p => p.quantity === 0); },
  adjust: async (productId: string, qty: number, note: string): Promise<void> => {
    await delay(400);
    const p = productStore.find(p => p.id === productId);
    if (p) { p.quantity += qty; }
    movementsStore.push({ id: 'sm' + Date.now(), date: new Date().toISOString().slice(0, 10), type: 'adjustment', productId, productName: p?.name ?? '', quantity: Math.abs(qty), direction: qty >= 0 ? 'in' : 'out', reference: 'ADJ-' + Date.now(), note });
  },
  listTransfers: async (): Promise<Transfer[]> => { await delay(300); return [...transfersStore]; },
  createTransfer: async (data: Omit<Transfer, 'id'>): Promise<Transfer> => {
    await delay(400);
    const t: Transfer = { ...data, id: 'tr' + Date.now() };
    transfersStore.push(t); return t;
  },
};
