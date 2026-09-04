import { Auth } from '../core/auth.js';
import { FormatUtils } from '../utils/formatUtils.js';

export const Navbar = {
  render() {
    const user = Auth.getCurrentUser();
    if (!user) return '';

    return `
      <header class="topbar">
        <div class="flex items-center gap-3">
          <span style="font-weight: 600; color: var(--text-main);">Welcome, ${user.fullName}</span>
          ${FormatUtils.getRoleBadge(user.role, user.contractor)}
          <span style="font-size: 0.8125rem; color: var(--text-muted);">(${user.department} • ${user.baseLocation})</span>
        </div>
        <div class="flex items-center gap-4">
          <button id="logoutBtn" class="btn btn-outline btn-sm">
            Sign Out
          </button>
        </div>
      </header>
    `;
  },

  attachEvents() {
    const btn = document.getElementById('logoutBtn');
    if (btn) {
      btn.addEventListener('click', () => Auth.logout());
    }
  }
};
