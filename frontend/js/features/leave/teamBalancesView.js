import { leaveApi } from '../../api/leaveApi.js';
import { Auth } from '../../core/auth.js';
import { FormatUtils } from '../../utils/formatUtils.js';

export const TeamBalancesView = {
  async render() {
    const isAdmin = Auth.isAdmin();
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">${isAdmin ? 'Organization-Wide Leave Balances' : 'Direct Reportee Leave Balances'}</h2>
          <p class="view-subtitle">${isAdmin ? 'Aggregated view of all employee leave quotas and consumption org-wide.' : 'View remaining leave quotas for your direct reporting team.'}</p>
        </div>
      </div>

      <div class="card">
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Team Member</th>
                <th>Leave Type</th>
                <th>Allocated</th>
                <th>Used</th>
                <th>Pending</th>
                <th>Remaining</th>
              </tr>
            </thead>
            <tbody id="teamBalancesTbody">
              <tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading balances...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    const tbody = document.getElementById('teamBalancesTbody');
    const isAdmin = Auth.isAdmin();
    const scope = isAdmin ? 'all' : 'reportees';

    try {
      const fmt = (v) => (typeof FormatUtils !== 'undefined' ? FormatUtils.formatDays(v) : (v == null || isNaN(v) ? '0.0' : Number(v).toFixed(1)));
      const balances = await leaveApi.getBalances(scope);
      if (!balances.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted); padding:2rem;">No balance records found for team.</td></tr>';
        return;
      }

      tbody.innerHTML = balances.map(b => `
        <tr>
          <td style="font-weight: 600;">${b.employeeName || 'Employee'}</td>
          <td>${b.leaveTypeDisplayName}</td>
          <td>${fmt(b.allocatedDays)}</td>
          <td><strong style="color:var(--danger);">${fmt(b.usedDays)}</strong></td>
          <td><span style="color:#d97706;">${fmt(b.pendingDays)}</span></td>
          <td><strong style="color:var(--primary); font-size:1rem;">${fmt(b.remainingDays)}</strong></td>
        </tr>
      `).join('');
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6" style="color:var(--danger); text-align:center;">Failed to load balances: ${err.message}</td></tr>`;
    }
  }
};
