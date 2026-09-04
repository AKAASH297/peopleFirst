import { apiRequest } from './apiClient.js';

export const agentApi = {
  async chat(message, conversationId = 'web-chat-session') {
    return apiRequest('/api/agent/chat', {
      method: 'POST',
      body: JSON.stringify({ message, conversationId })
    });
  },

  async getAgentInfo() {
    return apiRequest('/api/agent/info');
  },

  async getPolicies() {
    return apiRequest('/api/policies');
  },

  async getAmenities() {
    return apiRequest('/api/wellbeing/amenities');
  },

  async getHospitals(city) {
    const query = city ? `?city=${encodeURIComponent(city)}` : '';
    return apiRequest(`/api/wellbeing/hospitals${query}`);
  },

  async getResorts() {
    return apiRequest('/api/wellbeing/resorts');
  },

  async getVacationNudge() {
    return apiRequest('/api/wellbeing/vacation-nudge');
  },

  async getWeeklyWellbeingStatus() {
    return apiRequest('/api/wellbeing/weekly-status');
  },

  async sendVacationEmail() {
    return apiRequest('/api/wellbeing/send-vacation-email', {
      method: 'POST'
    });
  },

  async getAgentStatus() {
    return apiRequest('/api/agent/status');
  },

  async updateAgentConfig(apiKey) {
    return apiRequest('/api/agent/config', {
      method: 'POST',
      body: JSON.stringify({ apiKey })
    });
  }
};
