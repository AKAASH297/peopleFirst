export const LoginView = {
  render() {
    return `
      <div style="min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%); padding: 1.5rem;">
        <div class="card" style="max-width: 440px; width: 100%; box-shadow: var(--shadow-lg); border-radius: var(--radius-lg); overflow: hidden;">
          <div style="background: linear-gradient(135deg, var(--primary), var(--purple)); padding: 2rem; color: #fff; text-align: center;">
            <div style="font-size: 2.5rem; margin-bottom: 0.5rem;">🌿</div>
            <h1 style="font-size: 1.5rem; font-weight: 700; letter-spacing: -0.025em;">peopleFirst</h1>
            <p style="font-size: 0.875rem; opacity: 0.9; margin-top: 0.25rem;">Unified Gateway • Leave & Wellbeing Concierge</p>
          </div>

          <div class="card-body" style="padding: 2rem;">
            <div id="loginAlert"></div>

            <form id="loginForm">
              <div class="form-group">
                <label class="form-label" for="loginUsername">Username</label>
                <input id="loginUsername" type="text" class="form-input" placeholder="Enter username (e.g. employee1, contractor1)" required />
              </div>

              <div class="form-group">
                <label class="form-label" for="loginPassword">Password</label>
                <input id="loginPassword" type="password" class="form-input" placeholder="Enter password" required />
              </div>

              <div id="loginRolePreview" class="hidden" style="margin-bottom: 0.75rem; font-size: 0.75rem; text-align: center; padding: 0.4rem 0.6rem; border-radius: 4px; font-weight: 600;"></div>

              <button id="loginSubmitBtn" type="submit" class="btn btn-primary" style="width: 100%; padding: 0.75rem;">
                Sign In
              </button>
            </form>

            <div style="margin-top: 1.5rem; padding-top: 1.25rem; border-top: 1px solid var(--border);">
              <div style="font-size: 0.75rem; font-weight: 600; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.75rem; text-align: center;">
                ⚡ 1-Click Role Accounts (password: password123)
              </div>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem;">
                <button type="button" class="btn btn-secondary btn-sm demo-btn" data-user="employee1" data-portal="Employee Dashboard" data-color="#e0e7ff" data-text="#3730a3">👤 Employee</button>
                <button type="button" class="btn btn-secondary btn-sm demo-btn" data-user="manager1" data-portal="Manager Approvals" data-color="#e0e7ff" data-text="#3730a3">👔 Manager</button>
                <button type="button" class="btn btn-secondary btn-sm demo-btn" data-user="admin1" data-portal="Admin Governance" data-color="#e0e7ff" data-text="#3730a3">🛡️ Admin</button>
                <button type="button" class="btn btn-secondary btn-sm demo-btn" data-user="contractor1" data-portal="Contractor Kura Agent" data-color="#fef3c7" data-text="#92400e" style="color:#92400e; background:#fef3c7; border-color:#fde68a;">🛠️ Contractor</button>
              </div>
              <p style="font-size: 0.75rem; color: var(--text-muted); text-align: center; margin-top: 0.75rem; line-height: 1.4;">
                Single unified sign-in: Automatically detects your role and redirects to your dedicated portal.
              </p>
            </div>
          </div>
        </div>
      </div>
    `;
  }
};
