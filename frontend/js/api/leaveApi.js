import { apiRequest } from './apiClient.js';

export const leaveApi = {
  async getMyLeaves() {
    return apiRequest('/api/leaves');
  },

  async getLeaveById(id) {
    return apiRequest(`/api/leaves/${id}`);
  },

  async applyLeave(leaveData) {
    return apiRequest('/api/leaves', {
      method: 'POST',
      body: JSON.stringify(leaveData)
    });
  },

  async editLeave(id, leaveData) {
    return apiRequest(`/api/leaves/${id}`, {
      method: 'PUT',
      body: JSON.stringify(leaveData)
    });
  },

  async cancelLeave(id, comment) {
    const query = comment ? `?comment=${encodeURIComponent(comment)}` : '';
    return apiRequest(`/api/leaves/${id}${query}`, {
      method: 'DELETE'
    });
  },

  async getBalances(scope) {
    const query = scope ? `?scope=${scope}` : '';
    return apiRequest(`/api/leaves/balances${query}`);
  },

  // Approvals
  async getPendingApprovals() {
    return apiRequest('/api/leaves/approvals/pending');
  },

  async approveLeave(id, comment) {
    return apiRequest(`/api/leaves/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comment })
    });
  },

  async rejectLeave(id, comment) {
    return apiRequest(`/api/leaves/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ comment })
    });
  },

  async sendBackLeave(id, comment) {
    return apiRequest(`/api/leaves/${id}/sendBack`, {
      method: 'POST',
      body: JSON.stringify({ comment })
    });
  },

  // Admin privileged operations
  async getAllLeavesOrgWide() {
    return apiRequest('/api/admin/leaves');
  },

  async adminDirectEdit(id, editData) {
    return apiRequest(`/api/admin/leaves/${id}/direct-edit`, {
      method: 'PUT',
      body: JSON.stringify(editData)
    });
  },

  async getAuditLogsForLeave(id) {
    return apiRequest(`/api/admin/leaves/${id}/audit-logs`);
  },

  async getAllAuditLogs() {
    return apiRequest('/api/admin/audit-logs');
  }
};
