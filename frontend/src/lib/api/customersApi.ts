import { delay, customers as mockCustomers, type Customer } from './mockData';
const store: Customer[] = [...mockCustomers];
export const customersApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(c => c.id === id); },
  create: async (data: Omit<Customer, 'id' | 'createdAt'>) => {
    await delay(400);
    const c: Customer = { ...data, id: 'c' + Date.now(), createdAt: new Date().toISOString().slice(0, 10) };
    store.push(c); return c;
  },
  update: async (id: string, data: Partial<Customer>) => {
    await delay(400);
    const idx = store.findIndex(c => c.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(c => c.id === id); if (idx !== -1) store.splice(idx, 1); },
};
