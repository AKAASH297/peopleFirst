import { agentApi } from '../../api/agentApi.js';

export const PolicyView = {
  async render() {
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Company Leave Policies & Guidelines</h2>
          <p class="view-subtitle">Official policy governing eligibility, combination rules, cutoffs, and documentation requirements.</p>
        </div>
      </div>

      <div id="policyContentContainer">
        <div style="color:var(--text-muted);">Loading company policies...</div>
      </div>
    `;
  },

  async attachEvents() {
    const container = document.getElementById('policyContentContainer');
    try {
      const data = await agentApi.getPolicies();
      if (!data) return;

      container.innerHTML = `
        <div style="display: flex; flex-direction: column; gap: 1.5rem;">
          <div class="card">
            <div class="card-header">1. General Policy & Channel Governance</div>
            <div class="card-body">
              <ul style="margin-left: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.875rem;">
                ${data.generalRules.map(r => `<li>${r}</li>`).join('')}
              </ul>
            </div>
          </div>

          <div class="card">
            <div class="card-header">2. Application Deadlines & Documentation Constraints</div>
            <div class="card-body">
              <ul style="margin-left: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.875rem;">
                ${data.deadlineRules.map(r => `<li>${r}</li>`).join('')}
              </ul>
            </div>
          </div>

          <div class="card">
            <div class="card-header">3. Leave Combination Rules</div>
            <div class="card-body">
              <ul style="margin-left: 1.5rem; display: flex; flex-direction: column; gap: 0.5rem; font-size: 0.875rem;">
                ${data.combinationRules.map(r => `<li>${r}</li>`).join('')}
              </ul>
            </div>
          </div>

          <div class="card">
            <div class="card-header">4. Annual Quotas & Role Eligibility Matrix</div>
            <div class="table-container" style="border:none;">
              <table class="table">
                <thead>
                  <tr>
                    <th>Leave Type</th>
                    <th>Employee Eligibility</th>
                    <th>Employee Quota</th>
                    <th>Contractor Eligibility</th>
                    <th>Contractor Quota</th>
                  </tr>
                </thead>
                <tbody>
                  ${data.leaveTypes.map(t => `
                    <tr>
                      <td style="font-weight: 600;">${t.displayName}</td>
                      <td>${t.employeeEligible ? '✅ Eligible' : '❌ Not Eligible'}</td>
                      <td><strong>${t.employeeAnnualQuota}</strong> days/year</td>
                      <td>${t.contractorEligible ? '✅ Eligible' : '<span style="color:var(--danger); font-weight:600;">❌ Restricted (0)</span>'}</td>
                      <td><strong>${t.contractorAnnualQuota}</strong> days/year</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      `;
    } catch (err) {
      console.error(err);
      container.innerHTML = `<div class="alert alert-danger">Failed to load policies: ${err.message}</div>`;
    }
  }
};
