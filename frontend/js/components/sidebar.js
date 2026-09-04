import { Auth } from '../core/auth.js';
import { Router } from '../core/router.js';
import { AppState } from '../core/state.js';

export const Sidebar = {
  render() {
    const user = Auth.getCurrentUser();
    if (!user) return '';

    const currentView = AppState.currentView;
    const isManager = Auth.isManager();
    const isAdmin = Auth.isAdmin();

    return `
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span style="font-size: 1.5rem;">🌿</span>
          <span>peopleFirst</span>
        </div>
        <nav class="sidebar-nav">
          <div style="font-size: 0.6875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); padding: 0.5rem 0.875rem;">
            My Workplace
          </div>
          <a class="nav-item ${currentView === 'dashboard' ? 'active' : ''}" data-view="dashboard">
            <span>📊</span> Dashboard
          </a>
          <a class="nav-item ${currentView === 'applyLeave' ? 'active' : ''}" data-view="applyLeave">
            <span>📝</span> Apply for Leave
          </a>
          <a class="nav-item ${currentView === 'myLeaves' ? 'active' : ''}" data-view="myLeaves">
            <span>🗓️</span> My Leave History
          </a>
          <a class="nav-item ${currentView === 'policies' ? 'active' : ''}" data-view="policies">
            <span>📋</span> Company Policies
          </a>
          <a class="nav-item ${currentView === 'tickets' ? 'active' : ''}" data-view="tickets">
            <span>🎫</span> Support Tickets
          </a>
          <a class="nav-item ${currentView === 'wellness' ? 'active' : ''}" data-view="wellness">
            <span>✨</span> Wellness Concierge
          </a>

          ${isManager ? `
            <div style="margin-top: 1rem; font-size: 0.6875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); padding: 0.5rem 0.875rem;">
              Management Hub
            </div>
            <a class="nav-item ${currentView === 'approvals' ? 'active' : ''}" data-view="approvals">
              <span>✅</span> Team Approvals
            </a>
            <a class="nav-item ${currentView === 'teamBalances' ? 'active' : ''}" data-view="teamBalances">
              <span>👥</span> Team Balances
            </a>
          ` : ''}

          ${isAdmin ? `
            <div style="margin-top: 1rem; font-size: 0.6875rem; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); padding: 0.5rem 0.875rem;">
              Administration
            </div>
            <a class="nav-item ${currentView === 'adminLeaves' ? 'active' : ''}" data-view="adminLeaves">
              <span>⚡</span> Org Leaves & Direct Edit
            </a>
            <a class="nav-item ${currentView === 'adminAudit' ? 'active' : ''}" data-view="adminAudit">
              <span>📜</span> System Audit Logs
            </a>
            <a class="nav-item ${currentView === 'allBalances' ? 'active' : ''}" data-view="allBalances">
              <span>🌐</span> Org-Wide Balances
            </a>
          ` : ''}
        </nav>
        <div class="sidebar-footer">
          <div style="font-size: 0.75rem; color: var(--text-muted);">
            AI Concierge: <strong>Kura</strong>
          </div>
          <span style="font-size: 0.75rem; background: #e0e7ff; color: #3730a3; padding: 2px 6px; border-radius: 4px;">v2026.1</span>
        </div>
      </aside>
    `;
  },

  attachEvents() {
    document.querySelectorAll('.sidebar .nav-item').forEach(item => {
      item.addEventListener('click', (e) => {
        e.preventDefault();
        const view = item.getAttribute('data-view');
        if (view) {
          Router.navigate(view);
        }
      });
    });
  }
};
