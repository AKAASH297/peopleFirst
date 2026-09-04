import { leaveApi } from '../../api/leaveApi.js';
import { agentApi } from '../../api/agentApi.js';
import { LeaveCard } from '../../components/leaveCard.js';
import { Router } from '../../core/router.js';
import { Auth } from '../../core/auth.js';

export const LeaveDashboard = {
  async render() {
    const user = Auth.getCurrentUser();

    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Dashboard</h2>
          <p class="view-subtitle">Overview of your leave quotas, active requests, and wellbeing.</p>
        </div>
        <div>
          <button id="quickApplyBtn" class="btn btn-primary">
            <span>➕</span> Apply for Leave
          </button>
        </div>
      </div>

      <div id="vacationNudgeContainer"></div>

      <div style="margin-bottom: 2rem;">
        <h3 style="font-size: 1.125rem; font-weight: 600; margin-bottom: 1rem;">My Leave Balances (${new Date().getFullYear()})</h3>
        <div id="balancesGrid" class="balance-grid">
          <div style="color: var(--text-muted);">Loading balances...</div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <span>Recent Leave Requests</span>
          <button id="viewAllHistoryBtn" class="btn btn-outline btn-sm">View All History &rarr;</button>
        </div>
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Leave Type</th>
                <th>Dates & Duration</th>
                <th>Status</th>
                <th>Reason</th>
                <th style="text-align: right;">Action</th>
              </tr>
            </thead>
            <tbody id="recentLeavesTbody">
              <tr><td colspan="5" style="text-align: center; color: var(--text-muted);">Loading requests...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    document.getElementById('quickApplyBtn')?.addEventListener('click', () => Router.navigate('applyLeave'));
    document.getElementById('viewAllHistoryBtn')?.addEventListener('click', () => Router.navigate('myLeaves'));

    // 1. Fetch balances
    try {
      const balances = await leaveApi.getBalances();
      const grid = document.getElementById('balancesGrid');
      if (balances && balances.length) {
        grid.innerHTML = balances.map(b => LeaveCard.renderBalanceCard(b)).join('');
      } else {
        grid.innerHTML = '<div style="color:var(--text-muted);">No leave balance records available.</div>';
      }
    } catch (err) {
      console.error(err);
    }

    // 2. Fetch recent leaves
    try {
      const leaves = await leaveApi.getMyLeaves();
      const tbody = document.getElementById('recentLeavesTbody');
      if (leaves && leaves.length) {
        const recent = leaves.slice(0, 5);
        tbody.innerHTML = recent.map(l => {
          const actionBtn = `<button class="btn btn-outline btn-sm view-leave-btn" data-id="${l.id}">Details</button>`;
          return LeaveCard.renderLeaveRow(l, actionBtn);
        }).join('');

        document.querySelectorAll('.view-leave-btn').forEach(btn => {
          btn.addEventListener('click', () => {
            const id = btn.getAttribute('data-id');
            Router.navigate('myLeaves', { selectedId: id });
          });
        });
      } else {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 2rem;">No leave requests yet. Click "Apply for Leave" above to get started.</td></tr>';
      }
    } catch (err) {
      console.error(err);
    }

    // 3. Check Trigger 4: Vacation Nudge (§6)
    try {
      const nudge = await agentApi.getVacationNudge();
      if (nudge && nudge.trigger === 'NO_LEAVE_LAST_QUARTER') {
        const container = document.getElementById('vacationNudgeContainer');
        if (container) {
          container.innerHTML = `
            <div class="alert alert-info" style="border-left: 4px solid var(--primary); margin-bottom: 1.5rem;">
              <span style="font-size: 1.5rem;">🌴</span>
              <div style="flex: 1;">
                <div style="font-weight: 600; font-size: 0.9375rem;">${nudge.title}</div>
                <div style="margin-top: 2px;">${nudge.message}</div>
                <div style="margin-top: 0.5rem; display: flex; gap: 0.5rem;">
                  <button id="exploreResortsBtn" class="btn btn-primary btn-sm">Explore Partner Resorts & Discounts</button>
                  <button id="dismissNudgeBtn" class="btn btn-secondary btn-sm">Dismiss</button>
                </div>
              </div>
            </div>
          `;

          document.getElementById('exploreResortsBtn')?.addEventListener('click', () => Router.navigate('wellness'));
          document.getElementById('dismissNudgeBtn')?.addEventListener('click', () => {
            container.innerHTML = '';
          });
        }
      }
    } catch (err) {
      console.warn('Vacation nudge check failed:', err);
    }
  }
};
