import { z } from 'zod';
import apiClient, { fetchAllPages } from './apiClient';

/**
 * ============================================================
 * SALE ORDERS
 * ============================================================
 *
 * The shape is declared once, as a zod schema, and the TypeScript type is
 * inferred from it. That ordering matters: previously this file hand-wrote an
 * interface that ended in `[key: string]: any`, which made every field optional
 * in practice and unvalidated at runtime. A typo in a field name, or a backend
 * that stopped sending one, produced `undefined` at some render site and a blank
 * cell or a crash — with the type checker silent and no test failing.
 *
 * Validating at the boundary means a contract change is reported once, with the
 * offending payload, instead of as a scattering of blank cells.
 */

/** A named reference: `{ id, name }`, either of which may be absent. */
const refSchema = z
  .object({
    id: z.number().nullable().optional(),
    name: z.string().nullable().optional(),
  })
  .nullable()
  .optional();

export const saleOrderItemSchema = z.object({
  id: z.number().nullable().optional(),
  productId: z.number().nullable().optional(),
  productName: z.string().nullable().optional(),
  productSku: z.string().nullable().optional(),
  quantity: z.number().nullable().optional(),
  unitPrice: z.number().nullable().optional(),
  discount: z.number().nullable().optional(),
  total: z.number().nullable().optional(),
});

export const saleOrderSchema = z.object({
  id: z.union([z.string(), z.number()]),

  // Only one of these is guaranteed. The backend sends `orderNumber` and mirrors
  // it as `reference`; older rows predate the mirror.
  orderNumber: z.string().nullable().optional(),
  reference: z.string().nullable().optional(),

  customerName: z.string().nullable().optional(),
  customerId: z.number().nullable().optional(),

  totalAmount: z.number().nullable().optional(),
  finalAmount: z.number().nullable().optional(),
  // `total` mirrors finalAmount for backward compatibility.
  total: z.number().nullable().optional(),
  discount: z.number().nullable().optional(),
  tax: z.number().nullable().optional(),
  paid: z.number().nullable().optional(),

  status: z.string().nullable().optional(),
  paymentStatus: z.string().nullable().optional(),
  paymentMethod: z.string().nullable().optional(),

  date: z.string().nullable().optional(),
  createdAt: z.string().nullable().optional(),

  /** Who rang it up. Absent on imported or seeded orders. */
  cashier: refSchema,
  /** Where it was rung. Absent when the cashier has no branch recorded. */
  branch: refSchema,

  voidStatus: z.string().nullable().optional(),
  refundStatus: z.string().nullable().optional(),

  /**
   * Absent for an order with no lines. Guarded rather than assumed: a sale with
   * no items is a legitimate record, and `.map` over undefined threw.
   */
  items: z.array(saleOrderItemSchema).nullable().optional(),
});

export type SaleOrderItem = z.infer<typeof saleOrderItemSchema>;
export type SaleOrder = z.infer<typeof saleOrderSchema>;

/**
 * Thrown when a response does not match the schema.
 *
 * <p>Its own type so callers can distinguish "the server answered, but not with
 * what we expect" from "the server did not answer" — the two need different
 * copy in the UI.
 */
export class ApiContractError extends Error {
  readonly issues: string[];

  constructor(message: string, issues: string[]) {
    super(message);
    this.name = 'ApiContractError';
    this.issues = issues;
  }
}

/**
 * Validates a response, throwing {@link ApiContractError} on mismatch.
 *
 * <p>Deliberately strict about `id` and lenient about the rest: an id is the one
 * field with no sensible fallback — without it the row cannot be keyed or
 * fetched. Everything else degrades to an em dash in the UI, which is a better
 * outcome than refusing to render a whole list because one optional field moved.
 */
function parseOrThrow<T>(schema: z.ZodType<T>, data: unknown, what: string): T {
  const result = schema.safeParse(data);
  if (!result.success) {
    const issues = result.error.issues.map(
      (i) => `${i.path.join('.') || '(root)'}: ${i.message}`,
    );
    throw new ApiContractError(
      `The ${what} response did not match the expected shape`,
      issues,
    );
  }
  return result.data;
}

export const salesApi = {
  list: async (): Promise<SaleOrder[]> => {
    const orders = await fetchAllPages<unknown>('/sales');
    return orders.map((order) => parseOrThrow(saleOrderSchema, order, 'sale order list'));
  },

  get: async (id: string | number): Promise<SaleOrder> => {
    const res = await apiClient.get(`/sales/${id}`);
    return parseOrThrow(saleOrderSchema, res.data?.data ?? res.data, 'sale order');
  },

  create: async (data: unknown): Promise<SaleOrder> => {
    const res = await apiClient.post('/sales', data);
    return parseOrThrow(saleOrderSchema, res.data?.data ?? res.data, 'created sale order');
  },

  report: async (params?: {
    from?: string; to?: string; customerId?: number; productId?: number;
    sellerId?: number; paymentMethod?: string; status?: string;
  }): Promise<{ summary: any; sales: any[] }> => {
    const res = await apiClient.get('/reports/sales', { params });
    const data = res.data?.data ?? res.data;
    return { summary: data?.summary ?? {}, sales: data?.sales ?? [] };
  },

  update: async (id: string | number, data: unknown): Promise<SaleOrder> => {
    const res = await apiClient.put(`/sales/${id}`, data);
    return parseOrThrow(saleOrderSchema, res.data?.data ?? res.data, 'updated sale order');
  },

  delete: async (id: string | number): Promise<void> => {
    await apiClient.delete(`/sales/${id}`);
  },
};
