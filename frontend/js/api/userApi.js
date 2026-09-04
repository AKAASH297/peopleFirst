import { apiRequest } from './apiClient.js';

export const userApi = {
  async getDirectReportees() {
    return apiRequest('/api/users/reportees');
  },

  async getAllUsers() {
    return apiRequest('/api/users');
  },

  async getUserById(id) {
    return apiRequest(`/api/users/${id}`);
  }
};
