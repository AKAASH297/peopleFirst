import { AppState } from './state.js';
import { Auth } from './auth.js';
import { Router } from './router.js';
import { Navbar } from '../components/navbar.js';
import { Sidebar } from '../components/sidebar.js';
import { ChatWidget } from '../components/chatWidget.js';
import { FormatUtils } from '../utils/formatUtils.js';

if (typeof window !== 'undefined') {
  window.FormatUtils = FormatUtils;
}

import { LoginView } from '../features/auth/loginView.js';
import { LoginFeature } from '../features/auth/login.js';

import { LeaveDashboard } from '../features/leave/leaveDashboard.js';
import { ApplyLeave } from '../features/leave/applyLeave.js';
import { LeaveHistory } from '../features/leave/leaveHistory.js';
import { ApprovalDashboard } from '../features/approval/approvalDashboard.js';
import { TeamBalancesView } from '../features/leave/teamBalancesView.js';
import { AdminDashboard } from '../features/admin/adminDashboard.js';
import { AdminAuditView } from '../features/admin/adminAuditView.js';
import { PolicyView } from '../features/policy/policyView.js';
import { TicketView } from '../features/ticket/ticketView.js';
import { WellnessView } from '../features/leave/wellnessView.js';

class App {
  init() {
    Router.init();
    AppState.subscribe(() => this.render());
    this.render();
  }

  async render() {
    const root = document.getElementById('app');
    if (!root) return;

    // 1. If not authenticated, render Login
    if (!Auth.isAuthenticated()) {
      root.innerHTML = LoginView.render();
      LoginFeature.init();
      return;
    }

    // 2. Acceptance criterion 1: Contractor cannot reach webpage under any login path — auto-redirect to Kura Agent Portal
    if (Auth.isContractor()) {
      window.location.href = 'contractor.html';
      return;
    }

    // 3. Render Main Portal layout
    root.innerHTML = `
      <div class="app-layout">
        ${Sidebar.render()}
        <main class="main-content">
          ${Navbar.render()}
          <div id="viewContainer" class="content-container">
            <!-- Dynamic view injected here -->
          </div>
        </main>
      </div>
    `;

    Sidebar.attachEvents();
    Navbar.attachEvents();
    ChatWidget.init();

    await this.renderActiveView();
  }

  async renderActiveView() {
    const container = document.getElementById('viewContainer');
    if (!container) return;

    const view = AppState.currentView;

    switch (view) {
      case 'dashboard':
        container.innerHTML = await LeaveDashboard.render();
        await LeaveDashboard.attachEvents();
        break;

      case 'applyLeave':
        container.innerHTML = ApplyLeave.render();
        ApplyLeave.attachEvents();
        break;

      case 'myLeaves':
        container.innerHTML = await LeaveHistory.render();
        await LeaveHistory.attachEvents();
        break;

      case 'approvals':
        if (!Auth.isManager()) {
          Router.navigate('dashboard');
          return;
        }
        container.innerHTML = await ApprovalDashboard.render();
        await ApprovalDashboard.attachEvents();
        break;

      case 'teamBalances':
        if (!Auth.isManager()) {
          Router.navigate('dashboard');
          return;
        }
        container.innerHTML = await TeamBalancesView.render();
        await TeamBalancesView.attachEvents();
        break;

      case 'adminLeaves':
        if (!Auth.isAdmin()) {
          Router.navigate('dashboard');
          return;
        }
        container.innerHTML = await AdminDashboard.render();
        await AdminDashboard.attachEvents();
        break;

      case 'adminAudit':
        if (!Auth.isAdmin()) {
          Router.navigate('dashboard');
          return;
        }
        container.innerHTML = await AdminAuditView.render();
        await AdminAuditView.attachEvents();
        break;

      case 'allBalances':
        if (!Auth.isAdmin()) {
          Router.navigate('dashboard');
          return;
        }
        container.innerHTML = await TeamBalancesView.render();
        await TeamBalancesView.attachEvents();
        break;

      case 'policies':
        container.innerHTML = await PolicyView.render();
        await PolicyView.attachEvents();
        break;

      case 'tickets':
        container.innerHTML = await TicketView.render();
        await TicketView.attachEvents();
        break;

      case 'wellness':
        container.innerHTML = await WellnessView.render();
        await WellnessView.attachEvents();
        break;

      default:
        container.innerHTML = await LeaveDashboard.render();
        await LeaveDashboard.attachEvents();
        break;
    }
  }
}

document.addEventListener('DOMContentLoaded', () => {
  const app = new App();
  app.init();
});
