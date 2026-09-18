/** @jest-environment node */
import { customersApi } from '../lib/api/customersApi';
import { suppliersApi } from '../lib/api/suppliersApi';
import apiClient from '../lib/api/apiClient';

jest.mock('../lib/api/apiClient');

describe('customersApi & suppliersApi', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('customersApi.create should post payload to /customers', async () => {
    const payload = { name: 'Customer Test', phone: '+123456789' };
    (apiClient.post as jest.Mock).mockResolvedValueOnce({ data: { success: true } });

    const res = await customersApi.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/customers', payload);
    expect(res).toEqual({ success: true });
  });

  it('suppliersApi.create should post payload to /suppliers', async () => {
    const payload = { companyName: 'Supplier Inc', contactPerson: 'Bob' };
    (apiClient.post as jest.Mock).mockResolvedValueOnce({ data: { success: true } });

    const res = await suppliersApi.create(payload);
    expect(apiClient.post).toHaveBeenCalledWith('/suppliers', payload);
    expect(res).toEqual({ success: true });
  });
});
