import { apiRequest } from './apiClient.js';

export const authApi = {
  async login(username, password, channel = 'WEB') {
    return apiRequest('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username, password, channel })
    });
  },

  async refresh(refreshToken) {
    return apiRequest('/api/auth/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken })
    });
  },

  async getCurrentUserProfile() {
    return apiRequest('/api/auth/me', {
      method: 'GET'
    });
  }
};
