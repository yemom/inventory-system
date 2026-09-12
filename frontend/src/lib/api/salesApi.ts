import { delay, saleOrders as mockSales, type SaleOrder } from './mockData';
const store: SaleOrder[] = [...mockSales];
export const salesApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(s => s.id === id); },
  create: async (data: Omit<SaleOrder, 'id'>) => {
    await delay(400);
    const s: SaleOrder = { ...data, id: 'so' + Date.now() };
    store.push(s); return s;
  },
  update: async (id: string, data: Partial<SaleOrder>) => {
    await delay(400);
    const idx = store.findIndex(s => s.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(s => s.id === id); if (idx !== -1) store.splice(idx, 1); },
};
