import { delay, purchaseOrders as mockPurchases, type PurchaseOrder } from './mockData';
const store: PurchaseOrder[] = [...mockPurchases];
export const purchasesApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(p => p.id === id); },
  create: async (data: Omit<PurchaseOrder, 'id'>) => {
    await delay(400);
    const p: PurchaseOrder = { ...data, id: 'po' + Date.now() };
    store.push(p); return p;
  },
  update: async (id: string, data: Partial<PurchaseOrder>) => {
    await delay(400);
    const idx = store.findIndex(p => p.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(p => p.id === id); if (idx !== -1) store.splice(idx, 1); },
};
