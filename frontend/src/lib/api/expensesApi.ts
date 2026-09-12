import { delay, expenses as mockExpenses, type Expense } from './mockData';
const store: Expense[] = [...mockExpenses];
export const expensesApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(e => e.id === id); },
  create: async (data: Omit<Expense, 'id'>) => {
    await delay(400);
    const e: Expense = { ...data, id: 'exp' + Date.now() };
    store.push(e); return e;
  },
  update: async (id: string, data: Partial<Expense>) => {
    await delay(400);
    const idx = store.findIndex(e => e.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(e => e.id === id); if (idx !== -1) store.splice(idx, 1); },
};
