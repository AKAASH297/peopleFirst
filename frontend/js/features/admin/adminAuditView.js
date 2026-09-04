import { leaveApi } from '../../api/leaveApi.js';
import { DateUtils } from '../../utils/dateUtils.js';

export const AdminAuditView = {
  async render() {
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">System Audit Trail</h2>
          <p class="view-subtitle">Chronological ledger of every leave creation, approval, rejection, send back, and admin direct edit.</p>
        </div>
      </div>

      <div class="card">
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Timestamp</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Transition</th>
                <th>Flags</th>
                <th>Comment / Rationale</th>
              </tr>
            </thead>
            <tbody id="fullAuditTbody">
              <tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading audit logs...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    const tbody = document.getElementById('fullAuditTbody');
    try {
      const logs = await leaveApi.getAllAuditLogs();
      if (!logs.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted); padding:2rem;">No audit logs yet.</td></tr>';
        return;
      }

      tbody.innerHTML = logs.map(l => `
        <tr>
          <td style="font-size: 0.75rem; color: var(--text-muted); white-space: nowrap;">
            ${DateUtils.formatDateTime(l.timestamp)}
          </td>
          <td>
            <div style="font-weight: 600;">${l.actorName}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">${l.actorRole}</div>
          </td>
          <td><strong>${l.action}</strong></td>
          <td>
            <span style="font-size: 0.8125rem;">${l.previousStatus || 'INIT'} &rarr; <strong>${l.newStatus}</strong></span>
          </td>
          <td>
            ${l.adminDirectEdit ? '<span class="badge badge-rejected" style="font-size:0.6875rem;">ADMIN_DIRECT_EDIT</span>' : ''}
            ${l.adminOverride ? '<span class="badge badge-approved" style="font-size:0.6875rem;">OVERRIDE</span>' : ''}
            ${!l.adminDirectEdit && !l.adminOverride ? '—' : ''}
          </td>
          <td style="max-width: 250px; font-size: 0.8125rem; color: var(--text-sub);">
            ${l.comment || '—'}
          </td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6" style="color:var(--danger); text-align:center;">Failed to load audit logs: ${err.message}</td></tr>`;
    }
  }
};
