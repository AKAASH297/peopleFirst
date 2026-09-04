import { authApi } from '../../api/authApi.js';
import { AppState } from '../../core/state.js';
import { Router } from '../../core/router.js';

export const LoginFeature = {
  init() {
    const form = document.getElementById('loginForm');
    const usernameInput = document.getElementById('loginUsername');
    const passwordInput = document.getElementById('loginPassword');
    const alertContainer = document.getElementById('loginAlert');
    const rolePreview = document.getElementById('loginRolePreview');

    const updatePreview = (u) => {
      if (!rolePreview) return;
      const lower = (u || '').toLowerCase().trim();
      if (lower.includes('contractor')) {
        rolePreview.textContent = '🚀 Destination: Kura AI Concierge (Contractor Portal)';
        rolePreview.style.background = '#fef3c7';
        rolePreview.style.color = '#92400e';
        rolePreview.classList.remove('hidden');
      } else if (lower.includes('admin')) {
        rolePreview.textContent = '🛡️ Destination: Admin Governance Portal';
        rolePreview.style.background = '#f3e8ff';
        rolePreview.style.color = '#6b21a8';
        rolePreview.classList.remove('hidden');
      } else if (lower.includes('manager')) {
        rolePreview.textContent = '👔 Destination: Manager Approvals Portal';
        rolePreview.style.background = '#e0e7ff';
        rolePreview.style.color = '#3730a3';
        rolePreview.classList.remove('hidden');
      } else if (lower.includes('employee')) {
        rolePreview.textContent = '👤 Destination: Employee Leave Dashboard';
        rolePreview.style.background = '#f0fdf4';
        rolePreview.style.color = '#166534';
        rolePreview.classList.remove('hidden');
      } else {
        rolePreview.classList.add('hidden');
      }
    };

    usernameInput.addEventListener('input', (e) => updatePreview(e.target.value));

    document.querySelectorAll('.demo-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const u = btn.getAttribute('data-user');
        const portal = btn.getAttribute('data-portal');
        const color = btn.getAttribute('data-color');
        const text = btn.getAttribute('data-text');

        usernameInput.value = u;
        passwordInput.value = 'password123';

        if (rolePreview) {
          rolePreview.textContent = `🚀 Destination: ${portal}`;
          rolePreview.style.background = color || '#e0e7ff';
          rolePreview.style.color = text || '#3730a3';
          rolePreview.classList.remove('hidden');
        }
      });
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertContainer.innerHTML = '';

      const submitBtn = document.getElementById('loginSubmitBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Authenticating & verifying status...';

      try {
        const username = usernameInput.value.trim();
        const password = passwordInput.value;

        let result;
        try {
          // Attempt standard web login
          result = await authApi.login(username, password, 'WEB');
        } catch (webErr) {
          // If user is a contractor (HTTP 403), authenticate via AGENT channel seamlessly
          if (webErr.status === 403 && webErr.message && webErr.message.toLowerCase().includes('contractor')) {
            result = await authApi.login(username, password, 'AGENT');
          } else {
            throw webErr;
          }
        }

        // Store active user and JWT credentials
        AppState.setUser(result.user, result.accessToken, result.refreshToken);

        // Check user status/role and redirect to their dedicated portal immediately
        const user = result.user;
        const isContractor = user.contractor === true || user.role === 'CONTRACTOR';

        if (isContractor) {
          submitBtn.textContent = 'Redirecting to Contractor Portal...';
          window.location.href = 'contractor.html';
          return;
        }

        if (user.role === 'ADMIN') {
          submitBtn.textContent = 'Redirecting to Admin Portal...';
          AppState.setCurrentView('adminLeaves');
          Router.navigate('adminLeaves');
          return;
        }

        if (user.role === 'MANAGER') {
          submitBtn.textContent = 'Redirecting to Manager Approvals...';
          AppState.setCurrentView('approvals');
          Router.navigate('approvals');
          return;
        }

        // Default: Standard Employee
        submitBtn.textContent = 'Redirecting to Leave Dashboard...';
        AppState.setCurrentView('dashboard');
        Router.navigate('dashboard');

      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Sign In';
        alertContainer.innerHTML = `
          <div class="alert alert-danger">
            ${err.message || 'Invalid username or password.'}
          </div>
        `;
      }
    });
  }
};
