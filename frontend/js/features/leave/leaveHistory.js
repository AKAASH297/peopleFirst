import { leaveApi } from '../../api/leaveApi.js';
import { LeaveCard } from '../../components/leaveCard.js';
import { DateUtils } from '../../utils/dateUtils.js';
import { FormatUtils } from '../../utils/formatUtils.js';
import { Modal } from '../../components/modal.js';
import { Router } from '../../core/router.js';
import { AppState } from '../../core/state.js';

export const LeaveHistory = {
  allLeaves: [],

  async render() {
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">My Leave History</h2>
          <p class="view-subtitle">Review, edit returned leaves, cancel upcoming leaves, or view transition audit logs.</p>
        </div>
        <div class="flex items-center gap-2">
          <select id="leaveStatusFilter" class="form-select" style="width: auto; padding: 0.375rem 0.75rem;">
            <option value="ALL">All Statuses</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="RETURNED">Returned / Send Back</option>
            <option value="REJECTED">Rejected</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
      </div>

      <div class="card">
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Leave Type</th>
                <th>Dates & Duration</th>
                <th>Status</th>
                <th>Reason</th>
                <th style="text-align: right;">Actions</th>
              </tr>
            </thead>
            <tbody id="historyTbody">
              <tr><td colspan="5" style="text-align:center; color:var(--text-muted);">Loading leave history...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    try {
      this.allLeaves = await leaveApi.getMyLeaves();
      this.renderTableRows(this.allLeaves);

      document.getElementById('leaveStatusFilter')?.addEventListener('change', (e) => {
        const filter = e.target.value;
        if (filter === 'ALL') {
          this.renderTableRows(this.allLeaves);
        } else {
          const filtered = this.allLeaves.filter(l => l.status === filter);
          this.renderTableRows(filtered);
        }
      });

      // Auto-open selected leave details if coming from dashboard
      if (AppState.viewParams && AppState.viewParams.selectedId) {
        const target = this.allLeaves.find(l => l.id === AppState.viewParams.selectedId);
        if (target) this.showDetailsModal(target);
        AppState.viewParams = null;
      }

    } catch (err) {
      console.error(err);
      document.getElementById('historyTbody').innerHTML = `<tr><td colspan="5" style="color:var(--danger); text-align:center;">Failed to load leaves: ${err.message}</td></tr>`;
    }
  },

  renderTableRows(leaves) {
    const tbody = document.getElementById('historyTbody');
    if (!leaves.length) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:2rem;">No leave records found matching criteria.</td></tr>';
      return;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    tbody.innerHTML = leaves.map(l => {
      const isBeforeStart = new Date(l.startDate) > today;
      const canCancel = (l.status === 'PENDING' || l.status === 'APPROVED') && isBeforeStart;
      const canEdit = (l.status === 'PENDING' || l.status === 'RETURNED') && isBeforeStart;

      const actions = `
        <div class="flex items-center gap-2" style="justify-content: flex-end;">
          <button class="btn btn-outline btn-sm history-details-btn" data-id="${l.id}">Audit Trail</button>
          ${canEdit ? `<button class="btn btn-secondary btn-sm history-edit-btn" data-id="${l.id}">Edit</button>` : ''}
          ${canCancel ? `<button class="btn btn-danger btn-sm history-cancel-btn" data-id="${l.id}">Cancel</button>` : ''}
        </div>
      `;

      return LeaveCard.renderLeaveRow(l, actions);
    }).join('');

    // Attach row events
    document.querySelectorAll('.history-details-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const leave = this.allLeaves.find(l => l.id === btn.getAttribute('data-id'));
        if (leave) this.showDetailsModal(leave);
      });
    });

    document.querySelectorAll('.history-cancel-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const leave = this.allLeaves.find(l => l.id === btn.getAttribute('data-id'));
        if (leave) this.showCancelModal(leave);
      });
    });

    document.querySelectorAll('.history-edit-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const leave = this.allLeaves.find(l => l.id === btn.getAttribute('data-id'));
        if (leave) this.showEditModal(leave);
      });
    });
  },

  async showDetailsModal(leave) {
    let auditLogs = [];
    try {
      auditLogs = await leaveApi.getAuditLogsForLeave(leave.id);
    } catch (e) {
      console.warn(e);
    }

    const logsHtml = auditLogs.length ? `
      <div style="margin-top: 1rem;">
        <h4 style="font-size: 0.875rem; font-weight: 600; margin-bottom: 0.5rem;">Audit & Transition Trail</h4>
        <div style="display: flex; flex-direction: column; gap: 0.5rem; max-height: 200px; overflow-y: auto;">
          ${auditLogs.map(log => `
            <div style="background: #f8fafc; border: 1px solid var(--border); border-radius: 6px; padding: 8px; font-size: 0.75rem;">
              <div class="flex justify-between" style="font-weight: 600;">
                <span>${log.action} by ${log.actorName} (${log.actorRole})</span>
                <span style="color: var(--text-muted);">${DateUtils.formatDateTime(log.timestamp)}</span>
              </div>
              <div style="color: var(--text-sub); margin-top: 2px;">
                Status: <strong>${log.previousStatus || '—'}</strong> &rarr; <strong>${log.newStatus}</strong>
                ${log.adminDirectEdit ? '<span class="badge badge-rejected" style="font-size:0.6875rem; margin-left:4px;">ADMIN_DIRECT_EDIT</span>' : ''}
              </div>
              ${log.comment ? `<div style="font-style: italic; color: #475569; margin-top: 2px;">"${log.comment}"</div>` : ''}
            </div>
          `).join('')}
        </div>
      </div>
    ` : '<div style="font-size:0.8125rem; color:var(--text-muted); margin-top:1rem;">No audit logs recorded.</div>';

    Modal.show({
      title: `Leave Details: ${leave.leaveTypeDisplayName}`,
      content: `
        <div style="line-height: 1.8; font-size: 0.875rem;">
          <div><strong>Status:</strong> ${FormatUtils.getStatusBadge(leave.status)}</div>
          <div><strong>Dates:</strong> ${DateUtils.formatDate(leave.startDate)} &rarr; ${DateUtils.formatDate(leave.endDate)} (${leave.totalDays} day${leave.totalDays > 1 ? 's' : ''})</div>
          ${leave.combinedWithType ? `<div><strong>Combined with:</strong> ${leave.combinedWithType}</div>` : ''}
          <div><strong>Applied On:</strong> ${DateUtils.formatDate(leave.appliedDate)}</div>
          <div><strong>Reason:</strong> ${leave.reason || '—'}</div>
          ${leave.documentAttached ? `<div><strong>Medical Document:</strong> <a href="${leave.documentUrl}" target="_blank">View Certificate</a></div>` : ''}
        </div>
        ${logsHtml}
      `,
      buttons: [
        {
          id: 'closeDetailsBtn',
          text: 'Close',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        }
      ]
    });
  },

  showCancelModal(leave) {
    Modal.show({
      title: 'Confirm Leave Cancellation',
      content: `
        <p style="font-size:0.875rem; margin-bottom:1rem;">
          Are you sure you want to cancel your <strong>${leave.leaveTypeDisplayName}</strong> from
          <strong>${DateUtils.formatDate(leave.startDate)}</strong> to <strong>${DateUtils.formatDate(leave.endDate)}</strong>?
        </p>
        <p style="font-size:0.8125rem; color:var(--text-muted); margin-bottom:1rem;">
          Cancelling will instantly restore <strong>${leave.totalDays} days</strong> back to your available leave balance.
        </p>
        <div class="form-group">
          <label class="form-label" for="cancelCommentInput">Cancellation Reason (Optional)</label>
          <input id="cancelCommentInput" type="text" class="form-input" placeholder="e.g. Schedule conflict" />
        </div>
      `,
      buttons: [
        {
          id: 'abortCancelBtn',
          text: 'Never mind',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'confirmCancelBtn',
          text: 'Yes, Cancel Leave',
          className: 'btn-danger',
          onClick: async (_, modal) => {
            const comment = document.getElementById('cancelCommentInput')?.value;
            try {
              await leaveApi.cancelLeave(leave.id, comment);
              modal.close();
              LeaveHistory.attachEvents();
            } catch (err) {
              alert('Failed to cancel leave: ' + err.message);
            }
          }
        }
      ]
    });
  },

  showEditModal(leave) {
    Modal.show({
      title: `Edit Leave Request (${leave.leaveTypeDisplayName})`,
      content: `
        <div id="editModalAlert"></div>
        <form id="editLeaveModalForm">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
            <div class="form-group">
              <label class="form-label">Start Date *</label>
              <input id="editStartDate" type="date" class="form-input" value="${leave.startDate}" required onclick="try{this.showPicker()}catch(e){}" />
            </div>
            <div class="form-group">
              <label class="form-label">End Date *</label>
              <input id="editEndDate" type="date" class="form-input" value="${leave.endDate}" required onclick="try{this.showPicker()}catch(e){}" />
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">Reason for Modification</label>
            <textarea id="editReason" class="form-textarea" rows="2">${leave.reason || ''}</textarea>
          </div>
          <p style="font-size:0.75rem; color:var(--text-muted);">
            Editing will resubmit the request into <strong>PENDING</strong> status for managerial re-approval.
          </p>
        </form>
      `,
      buttons: [
        {
          id: 'abortEditBtn',
          text: 'Cancel',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'saveEditBtn',
          text: 'Save & Resubmit',
          className: 'btn-primary',
          onClick: async (_, modal) => {
            const start = document.getElementById('editStartDate').value;
            const end = document.getElementById('editEndDate').value;
            const reason = document.getElementById('editReason').value;
            const alertEl = document.getElementById('editModalAlert');

            try {
              await leaveApi.editLeave(leave.id, {
                leaveType: leave.leaveType,
                combinedWithType: leave.combinedWithType,
                startDate: start,
                endDate: end,
                halfDay: leave.halfDay,
                halfDaySession: leave.halfDaySession,
                reason: reason,
                documentAttached: leave.documentAttached,
                documentUrl: leave.documentUrl
              });
              modal.close();
              LeaveHistory.attachEvents();
            } catch (err) {
              alertEl.innerHTML = `<div class="alert alert-danger" style="margin-bottom:1rem;">${err.message}</div>`;
            }
          }
        }
      ]
    });
  }
};
