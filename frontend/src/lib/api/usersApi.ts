import { delay, users as mockUsers, type User } from './mockData';
const store: User[] = [...mockUsers];
export const usersApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(u => u.id === id); },
  create: async (data: Omit<User, 'id' | 'createdAt' | 'lastLogin'>) => {
    await delay(400);
    const u: User = { ...data, id: 'u' + Date.now(), createdAt: new Date().toISOString().slice(0, 10), lastLogin: '-' };
    store.push(u); return u;
  },
  update: async (id: string, data: Partial<User>) => {
    await delay(400);
    const idx = store.findIndex(u => u.id === id);
    if (idx === -1) throw new Error('Not found');
    store[idx] = { ...store[idx], ...data }; return store[idx];
  },
  delete: async (id: string) => { await delay(300); const idx = store.findIndex(u => u.id === id); if (idx !== -1) store.splice(idx, 1); },
};
