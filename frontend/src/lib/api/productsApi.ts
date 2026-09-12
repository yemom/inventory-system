import { delay, products as mockProducts, type Product } from './mockData';
export const productStore: Product[] = [...mockProducts];
export const productsApi = {
  list: async () => { await delay(300); return [...productStore]; },
  get: async (id: string) => { await delay(200); return productStore.find(p => p.id === id); },
  create: async (data: Omit<Product, 'id' | 'createdAt'>) => {
    await delay(400);
    const p: Product = { ...data, id: 'p' + Date.now(), createdAt: new Date().toISOString().slice(0, 10) };
    productStore.push(p); return p;
  },
  update: async (id: string, data: Partial<Product>) => {
    await delay(400);
    const idx = productStore.findIndex(p => p.id === id);
    if (idx === -1) throw new Error('Not found');
    productStore[idx] = { ...productStore[idx], ...data }; return productStore[idx];
  },
  delete: async (id: string) => {
    await delay(300);
    const idx = productStore.findIndex(p => p.id === id);
    if (idx !== -1) productStore.splice(idx, 1);
  },
};
