import apiClient, { fetchAllPages } from './apiClient';

export interface Payment {
  id: string | number;
  reference?: string;
  orderReference?: string;
  /**
   * IN or OUT. The previous field was called `type` and held the strings
   * "received" and "made"; the backend now uses a direction, because "type"
   * also meant the payment channel on the same object and one field cannot be
   * both.
   */
  direction?: string;
  /** The channel: CASH, BANK, MOBILE. */
  paymentMethod?: string;
  /** INVOICE, SUPPLIER or CUSTOMER. */
  partyType?: string;
  status?: string;
  amount?: number;
  transactionDate?: string;
  notes?: string;
  externalReference?: string;
  [key: string]: any;
}

/**
 * Money in and money out.
 *
 * <p>Payments are recorded against a document — a sale order or a purchase
 * order — rather than posted as a free-standing row with an amount and a party
 * typed in by the caller. That is the change that makes "cash received" mean
 * something: the amount paid on a sale is now derived from the payment ledger
 * rather than from a field a client was free to set, so an unpaid invoice can
 * no longer be filed as collected income.
 *
 * <p>The practical consequence for callers: there is no generic "create a
 * payment" any more. Pick the document, and the server decides direction, party,
 * status and the resulting balance.
 */
export const paymentsApi = {
  /**
   * Every payment, following pagination to the end.
   *
   * <p>All pages, deliberately. A payment list truncated at the first page
   * would make a ledger that silently disagrees with the reports behind it.
   */
  list: async (params: Record<string, unknown> = {}): Promise<any[]> => {
    return fetchAllPages('/payments', { params });
  },

  /** Money received from a customer against a sale order. */
  recordForSale: async (
    saleOrderId: string | number,
    data: { amount: number; method?: string; reference?: string; notes?: string },
  ): Promise<any> => {
    const res = await apiClient.post(`/payments/sales/${saleOrderId}`, data);
    return res.data?.data ?? res.data;
  },

  /** Money paid to a supplier against a purchase order. */
  recordForPurchase: async (
    purchaseOrderId: string | number,
    data: { amount: number; method?: string; reference?: string; notes?: string },
  ): Promise<any> => {
    const res = await apiClient.post(`/payments/purchases/${purchaseOrderId}`, data);
    return res.data?.data ?? res.data;
  },

  /**
   * A refund paid back to a customer.
   *
   * <p>Separate from a collection because it is money leaving because a sale
   * was undone, which is a different event from money arriving.
   */
  refund: async (data: {
    saleOrderId?: string | number;
    amount: number;
    method?: string;
    notes?: string;
  }): Promise<any> => {
    const res = await apiClient.post('/payments/refunds', data);
    return res.data?.data ?? res.data;
  },
};

export default paymentsApi;