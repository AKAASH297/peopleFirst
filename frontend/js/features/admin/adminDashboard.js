import { leaveApi } from '../../api/leaveApi.js';
import { DateUtils } from '../../utils/dateUtils.js';
import { FormatUtils } from '../../utils/formatUtils.js';
import { Modal } from '../../components/modal.js';
import { Auth } from '../../core/auth.js';

export const AdminDashboard = {
  leaves: [],

  async render() {
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Org Leaves & Direct-DB-Edit Utility</h2>
          <p class="view-subtitle">Privileged administrative screen to view all leaves across the organization and perform audited direct-DB corrections.</p>
        </div>
        <div class="flex items-center gap-2">
          <button id="refreshAdminLeavesBtn" class="btn btn-outline btn-sm">↻ Refresh</button>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <span>All Organization Leave Records</span>
          <span style="font-size:0.75rem; color:var(--text-muted); font-weight:normal;">Direct edit actions are logged with ADMIN_DIRECT_EDIT</span>
        </div>
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Employee / Dept</th>
                <th>Leave Type</th>
                <th>Dates & Duration</th>
                <th>Status</th>
                <th>Reason</th>
                <th style="text-align: right;">Privileged Action</th>
              </tr>
            </thead>
            <tbody id="adminLeavesTbody">
              <tr><td colspan="6" style="text-align:center; color:var(--text-muted);">Loading org leaves...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    document.getElementById('refreshAdminLeavesBtn')?.addEventListener('click', () => this.loadData());
    await this.loadData();
  },

  async loadData() {
    const tbody = document.getElementById('adminLeavesTbody');
    try {
      this.leaves = await leaveApi.getAllLeavesOrgWide();
      if (!this.leaves.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--text-muted); padding:2rem;">No leave records in the system.</td></tr>';
        return;
      }

      tbody.innerHTML = this.leaves.map(l => `
        <tr>
          <td>
            <div style="font-weight: 600;">${l.employeeName}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">${l.department} • ${l.employeeRole}</div>
          </td>
          <td>
            <div style="font-weight: 600;">${l.leaveTypeDisplayName}</div>
            ${l.combinedWithType ? `<div style="font-size:0.75rem; color:var(--primary);">+ ${l.combinedWithType}</div>` : ''}
          </td>
          <td>
            <div>${DateUtils.formatDate(l.startDate)} &rarr; ${DateUtils.formatDate(l.endDate)}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">${l.totalDays} days</div>
          </td>
          <td>${FormatUtils.getStatusBadge(l.status)}</td>
          <td style="max-width: 180px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
            ${l.reason || '—'}
          </td>
          <td style="text-align: right;">
            <div class="flex items-center gap-2" style="justify-content: flex-end;">
              <button class="btn btn-outline btn-sm admin-history-btn" data-id="${l.id}">Audit Logs</button>
              <button class="btn btn-primary btn-sm admin-direct-edit-btn" data-id="${l.id}">⚡ Direct Edit</button>
            </div>
          </td>
        </tr>
      `).join('');

      document.querySelectorAll('.admin-direct-edit-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const l = this.leaves.find(x => x.id === btn.getAttribute('data-id'));
          if (l) this.showDirectEditModal(l);
        });
      });

      document.querySelectorAll('.admin-history-btn').forEach(btn => {
        btn.addEventListener('click', async () => {
          const id = btn.getAttribute('data-id');
          await this.showAuditLogsModal(id);
        });
      });

    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="6" style="color:var(--danger); text-align:center;">Failed to load leaves: ${err.message}</td></tr>`;
    }
  },

  showDirectEditModal(leave) {
    Modal.show({
      title: `Privileged Direct DB Edit: ${leave.employeeName}`,
      content: `
        <div class="alert alert-warning" style="font-size: 0.8125rem; margin-bottom: 1rem;">
          <strong>Audited Administration:</strong> Modifying records through Direct Edit immediately updates the database and balances. Every direct edit is recorded with the <code>ADMIN_DIRECT_EDIT</code> audit tag.
        </div>

        <form id="directEditForm">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
            <div class="form-group">
              <label class="form-label">Status Override *</label>
              <select id="directEditStatus" class="form-select">
                <option value="PENDING" ${leave.status === 'PENDING' ? 'selected' : ''}>PENDING</option>
                <option value="APPROVED" ${leave.status === 'APPROVED' ? 'selected' : ''}>APPROVED</option>
                <option value="REJECTED" ${leave.status === 'REJECTED' ? 'selected' : ''}>REJECTED</option>
                <option value="RETURNED" ${leave.status === 'RETURNED' ? 'selected' : ''}>RETURNED</option>
                <option value="CANCELLED" ${leave.status === 'CANCELLED' ? 'selected' : ''}>CANCELLED</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">Total Days</label>
              <input id="directEditTotalDays" type="number" step="0.5" class="form-input" value="${leave.totalDays}" />
            </div>
          </div>

          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
            <div class="form-group">
              <label class="form-label">Start Date</label>
              <input id="directEditStartDate" type="date" class="form-input" value="${leave.startDate}" onclick="try{this.showPicker()}catch(e){}" />
            </div>
            <div class="form-group">
              <label class="form-label">End Date</label>
              <input id="directEditEndDate" type="date" class="form-input" value="${leave.endDate}" onclick="try{this.showPicker()}catch(e){}" />
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">Updated Reason</label>
            <input id="directEditReason" type="text" class="form-input" value="${leave.reason || ''}" />
          </div>

          <div class="form-group">
            <label class="form-label" style="color:#991b1b; font-weight:600;">Mandatory Audit Justification / Comment *</label>
            <textarea id="directEditAuditComment" class="form-textarea" rows="2" placeholder="State reason for privileged administrative modification (e.g. Executive override approved by VP)" required></textarea>
          </div>
        </form>
      `,
      buttons: [
        {
          id: 'abortDirectEditBtn',
          text: 'Cancel',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'confirmDirectEditBtn',
          text: 'Execute Direct Edit',
          className: 'btn-danger',
          onClick: async (_, modal) => {
            const status = document.getElementById('directEditStatus').value;
            const totalDays = parseFloat(document.getElementById('directEditTotalDays').value);
            const startDate = document.getElementById('directEditStartDate').value;
            const endDate = document.getElementById('directEditEndDate').value;
            const reason = document.getElementById('directEditReason').value;
            const auditComment = document.getElementById('directEditAuditComment').value.trim();

            if (!auditComment) {
              alert('Mandatory audit comment is required for direct DB edits.');
              return;
            }

            try {
              await leaveApi.adminDirectEdit(leave.id, {
                status,
                totalDays,
                startDate,
                endDate,
                reason,
                auditComment
              });
              modal.close();
              AdminDashboard.loadData();
            } catch (err) {
              alert('Direct edit failed: ' + err.message);
            }
          }
        }
      ]
    });
  },

  async showAuditLogsModal(leaveId) {
    try {
      const logs = await leaveApi.getAuditLogsForLeave(leaveId);
      Modal.show({
        title: 'Leave Audit & Transition Trail',
        content: `
          <div style="display: flex; flex-direction: column; gap: 0.75rem; max-height: 400px; overflow-y: auto;">
            ${logs.map(log => `
              <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 8px; padding: 10px; font-size: 0.8125rem;">
                <div class="flex justify-between" style="font-weight: 600;">
                  <span>Action: <strong>${log.action}</strong></span>
                  <span style="color: var(--text-muted); font-size: 0.75rem;">${DateUtils.formatDateTime(log.timestamp)}</span>
                </div>
                <div style="margin-top: 4px; color: var(--text-sub);">
                  Actor: <strong>${log.actorName}</strong> (${log.actorRole})
                </div>
                <div style="margin-top: 4px;">
                  Transition: <strong>${log.previousStatus || 'INIT'}</strong> &rarr; <strong>${log.newStatus}</strong>
                  ${log.adminDirectEdit ? '<span class="badge badge-rejected" style="font-size:0.6875rem; margin-left:6px;">ADMIN_DIRECT_EDIT</span>' : ''}
                  ${log.adminOverride ? '<span class="badge badge-approved" style="font-size:0.6875rem; margin-left:6px;">ADMIN_OVERRIDE</span>' : ''}
                </div>
                ${log.comment ? `<div style="margin-top: 6px; font-style: italic; color: #334155; background: #fff; padding: 6px 8px; border-radius: 4px; border: 1px solid #e2e8f0;">"${log.comment}"</div>` : ''}
              </div>
            `).join('')}
          </div>
        `,
        buttons: [
          {
            id: 'closeAuditModalBtn',
            text: 'Close',
            className: 'btn-secondary',
            onClick: (_, modal) => modal.close()
          }
        ]
      });
    } catch (err) {
      alert('Failed to load audit logs: ' + err.message);
    }
  }
};
