import { apiRequest } from './apiClient.js';

export const ticketApi = {
  async createTicket(ticketData) {
    return apiRequest('/api/tickets', {
      method: 'POST',
      body: JSON.stringify(ticketData)
    });
  },

  async getTickets(scope) {
    const query = scope ? `?scope=${scope}` : '';
    return apiRequest(`/api/tickets${query}`);
  }
};
