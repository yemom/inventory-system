import apiClient from './apiClient';

export interface CreateUserPayload {
  username?: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  gender?: string;
  dateOfBirth?: string;
  address?: string;
  profilePhotoUrl?: string;
  employeeId?: string;
  jobTitle?: string;
  department?: string;
  branch?: string;
  warehouse?: string;
  dateJoined?: string;
  password: string;
  roleName: string;
  status?: string;
}

export interface UpdateUserPayload {
  firstName?: string;
  lastName?: string;
  email?: string;
  phone?: string;
  gender?: string;
  dateOfBirth?: string;
  address?: string;
  profilePhotoUrl?: string;
  employeeId?: string;
  jobTitle?: string;
  department?: string;
  branch?: string;
  warehouse?: string;
  dateJoined?: string;
  roleName?: string;
  status?: string;
}

export interface StaffListParams {
  page?: number;
  size?: number;
  search?: string;
  role?: string;
  department?: string;
  branch?: string;
  warehouse?: string;
  status?: string;
  sort?: string;
}

export const usersApi = {
  list: async (page = 0, size = 20, search = '') => {
    const res = await apiClient.get('/users', { params: { page, size, search } });
    return res.data;
  },
  listStaff: async (params: StaffListParams = {}) => {
    const res = await apiClient.get('/staff', { params });
    return res.data;
  },
  staffStats: async () => {
    const res = await apiClient.get('/staff/stats');
    return res.data;
  },
  get: async (id: number | string) => {
    const res = await apiClient.get(`/staff/${id}`);
    return res.data;
  },
  create: async (data: CreateUserPayload) => {
    const res = await apiClient.post('/staff', data);
    return res.data;
  },
  update: async (id: number | string, data: UpdateUserPayload) => {
    const res = await apiClient.put(`/staff/${id}`, data);
    return res.data;
  },
  activate: async (id: number | string) => {
    const res = await apiClient.patch(`/staff/${id}/status`, { status: 'ACTIVE' });
    return res.data;
  },
  deactivate: async (id: number | string) => {
    const res = await apiClient.patch(`/staff/${id}/status`, { status: 'INACTIVE' });
    return res.data;
  },
  changeRole: async (id: number | string, roleName: string) => {
    const res = await apiClient.patch(`/users/${id}/role`, { roleName });
    return res.data;
  },
  resetPassword: async (id: number | string, temporaryPassword: string) => {
    const res = await apiClient.patch(`/staff/${id}/reset-password`, { temporaryPassword });
    return res.data;
  },
  remove: async (id: number | string) => {
    const res = await apiClient.delete(`/staff/${id}`);
    return res.data;
  },
};
