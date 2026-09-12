import { delay, payments as mockPayments, type Payment } from './mockData';
const store: Payment[] = [...mockPayments];
export const paymentsApi = {
  list: async () => { await delay(300); return [...store]; },
  get: async (id: string) => { await delay(200); return store.find(p => p.id === id); },
  create: async (data: Omit<Payment, 'id'>) => {
    await delay(400);
    const p: Payment = { ...data, id: 'pay' + Date.now() };
    store.push(p); return p;
  },
};
