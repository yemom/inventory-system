/** @jest-environment node */
import { usersApi } from '../lib/api/usersApi';
import apiClient from '../lib/api/apiClient';

jest.mock('../lib/api/apiClient');

describe('usersApi', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should call GET /users with pagination parameters', async () => {
    const mockData = { content: [{ id: 1, username: 'admin' }], totalElements: 1 };
    (apiClient.get as jest.Mock).mockResolvedValueOnce({ data: mockData });

    const result = await usersApi.list(0, 10, 'search');

    expect(apiClient.get).toHaveBeenCalledWith('/users', {
      params: { page: 0, size: 10, search: 'search' },
    });
    expect(result).toEqual(mockData);
  });

  it('should call POST /users when creating a user', async () => {
    const newUser = {
      username: 'cashier1',
      firstName: 'Cashier',
      lastName: 'One',
      email: 'cashier@test.com',
      password: 'Password@123',
      roleName: 'CASHIER',
    };
    (apiClient.post as jest.Mock).mockResolvedValueOnce({ data: { success: true } });

    const result = await usersApi.create(newUser);

    // usersApi.create targets /staff, the endpoint the staff page uses.
    expect(apiClient.post).toHaveBeenCalledWith('/staff', newUser);
    expect(result).toEqual({ success: true });
  });

  it('should activate a member via PATCH /staff/:id/status', async () => {
    (apiClient.patch as jest.Mock).mockResolvedValueOnce({ data: { success: true } });

    await usersApi.activate(5);

    expect(apiClient.patch).toHaveBeenCalledWith('/staff/5/status', { status: 'ACTIVE' });
  });

  it('should deactivate a member via PATCH /staff/:id/status', async () => {
    (apiClient.patch as jest.Mock).mockResolvedValueOnce({ data: { success: true } });

    await usersApi.deactivate(5);

    expect(apiClient.patch).toHaveBeenCalledWith('/staff/5/status', { status: 'INACTIVE' });
  });
});
