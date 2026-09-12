import { delay, suppliers as mockSuppliers, type Supplier } from './mockData';
const store: Supplier[] = [...mockSuppliers];
export const suppliersApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(s => s.id === id); },
  create: async (data: Omit<Supplier, 'id' | 'createdAt'>) => {
    await delay(400);
    const s: Supplier = { ...data, id: 's' + Date.now(), createdAt: new Date().toISOString().slice(0, 10) };
    store.push(s); return s;
  },
  update: async (id: string, data: Partial<Supplier>) => {
    await delay(400);
    const idx = store.findIndex(s => s.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(s => s.id === id); if (idx !== -1) store.splice(idx, 1); },
};
