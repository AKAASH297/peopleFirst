import { leaveApi } from '../../api/leaveApi.js';
import { DateUtils } from '../../utils/dateUtils.js';
import { FormatUtils } from '../../utils/formatUtils.js';
import { Modal } from '../../components/modal.js';
import { Auth } from '../../core/auth.js';

export const ApprovalDashboard = {
  pendingLeaves: [],

  async render() {
    const user = Auth.getCurrentUser();
    const isManager = Auth.isManager();

    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Team Leave Approvals</h2>
          <p class="view-subtitle">Review pending leave applications from your team reportees. Approve, reject, or send back for updates.</p>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <span>Pending Leave Requests</span>
          <button id="refreshApprovalsBtn" class="btn btn-outline btn-sm">↻ Refresh</button>
        </div>
        <div class="table-container" style="border:none;">
          <table class="table">
            <thead>
              <tr>
                <th>Employee</th>
                <th>Leave Type</th>
                <th>Dates & Duration</th>
                <th>Reason</th>
                <th style="text-align: right;">Decision</th>
              </tr>
            </thead>
            <tbody id="approvalsTbody">
              <tr><td colspan="5" style="text-align:center; color:var(--text-muted);">Loading pending requests...</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    document.getElementById('refreshApprovalsBtn')?.addEventListener('click', () => this.loadData());
    await this.loadData();
  },

  async loadData() {
    const tbody = document.getElementById('approvalsTbody');
    try {
      this.pendingLeaves = await leaveApi.getPendingApprovals();
      const currentUser = Auth.getCurrentUser();

      if (!this.pendingLeaves.length) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center; color:var(--text-muted); padding:2rem;">🎉 No pending approval requests at this time. All caught up!</td></tr>';
        return;
      }

      tbody.innerHTML = this.pendingLeaves.map(l => {
        const isOwn = currentUser.id === l.userId;

        const actions = isOwn ? `
          <span style="font-size:0.75rem; color:var(--text-muted);">Self-Approval Blocked</span>
        ` : `
          <div class="flex items-center gap-2" style="justify-content: flex-end;">
            <button class="btn btn-success btn-sm approve-btn" data-id="${l.id}">Approve</button>
            <button class="btn btn-secondary btn-sm sendback-btn" data-id="${l.id}">Send Back</button>
            <button class="btn btn-danger btn-sm reject-btn" data-id="${l.id}">Reject</button>
          </div>
        `;

        return `
          <tr>
            <td>
              <div style="font-weight: 600;">${l.employeeName || 'Team Member'}</div>
              <div style="font-size: 0.75rem; color: var(--text-muted);">${l.employeeRole || ''} • ${l.department || ''}</div>
            </td>
            <td>
              <div style="font-weight: 600;">${l.leaveTypeDisplayName}</div>
              ${l.combinedWithType ? `<div style="font-size:0.75rem; color:var(--primary);">+ Combined with ${l.combinedWithType}</div>` : ''}
            </td>
            <td>
              <div>${DateUtils.formatDate(l.startDate)} &rarr; ${DateUtils.formatDate(l.endDate)}</div>
              <div style="font-size: 0.75rem; color: var(--text-muted);">${l.totalDays} day${l.totalDays > 1 ? 's' : ''}</div>
            </td>
            <td style="max-width: 200px;">
              ${l.reason || '—'}
              ${l.documentAttached ? `<div style="font-size:0.75rem; margin-top:2px;"><a href="${l.documentUrl}" target="_blank">📄 View Medical Doc</a></div>` : ''}
            </td>
            <td style="text-align: right;">${actions}</td>
          </tr>
        `;
      }).join('');

      // Attach action listeners
      document.querySelectorAll('.approve-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const l = this.pendingLeaves.find(x => x.id === btn.getAttribute('data-id'));
          if (l) this.showApproveModal(l);
        });
      });

      document.querySelectorAll('.reject-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const l = this.pendingLeaves.find(x => x.id === btn.getAttribute('data-id'));
          if (l) this.showRejectModal(l);
        });
      });

      document.querySelectorAll('.sendback-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          const l = this.pendingLeaves.find(x => x.id === btn.getAttribute('data-id'));
          if (l) this.showSendBackModal(l);
        });
      });

    } catch (err) {
      console.error(err);
      tbody.innerHTML = `<tr><td colspan="5" style="color:var(--danger); text-align:center;">Failed to load approvals: ${err.message}</td></tr>`;
    }
  },

  showApproveModal(leave) {
    Modal.show({
      title: `Approve Leave: ${leave.employeeName}`,
      content: `
        <p style="font-size:0.875rem; margin-bottom:1rem;">
          Approve <strong>${leave.leaveTypeDisplayName}</strong> (${leave.totalDays} days) for <strong>${leave.employeeName}</strong>?
        </p>
        <div class="form-group">
          <label class="form-label" for="approveCommentInput">Approval Note / Comment (Optional)</label>
          <input id="approveCommentInput" type="text" class="form-input" placeholder="e.g. Approved, coverage confirmed." />
        </div>
      `,
      buttons: [
        {
          id: 'cancelApproveModal',
          text: 'Cancel',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'confirmApproveBtn',
          text: 'Confirm Approval',
          className: 'btn-success',
          onClick: async (_, modal) => {
            const comment = document.getElementById('approveCommentInput')?.value;
            try {
              await leaveApi.approveLeave(leave.id, comment);
              modal.close();
              ApprovalDashboard.loadData();
            } catch (err) {
              alert('Error approving leave: ' + err.message);
            }
          }
        }
      ]
    });
  },

  showRejectModal(leave) {
    Modal.show({
      title: `Reject Leave: ${leave.employeeName}`,
      content: `
        <p style="font-size:0.875rem; margin-bottom:1rem;">
          Are you sure you want to reject <strong>${leave.leaveTypeDisplayName}</strong> for <strong>${leave.employeeName}</strong>?
        </p>
        <div class="form-group">
          <label class="form-label" for="rejectCommentInput">Reason for Rejection *</label>
          <input id="rejectCommentInput" type="text" class="form-input" placeholder="e.g. Critical release sprint deadline" required />
        </div>
      `,
      buttons: [
        {
          id: 'cancelRejectModal',
          text: 'Cancel',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'confirmRejectBtn',
          text: 'Confirm Rejection',
          className: 'btn-danger',
          onClick: async (_, modal) => {
            const comment = document.getElementById('rejectCommentInput')?.value;
            if (!comment) {
              alert('Please enter a rejection reason.');
              return;
            }
            try {
              await leaveApi.rejectLeave(leave.id, comment);
              modal.close();
              ApprovalDashboard.loadData();
            } catch (err) {
              alert('Error rejecting leave: ' + err.message);
            }
          }
        }
      ]
    });
  },

  showSendBackModal(leave) {
    Modal.show({
      title: `Send Back Leave: ${leave.employeeName}`,
      content: `
        <p style="font-size:0.875rem; margin-bottom:1rem;">
          Return this application to <strong>${leave.employeeName}</strong> for updates or date adjustments. Status will transition to <strong>RETURNED</strong>.
        </p>
        <div class="form-group">
          <label class="form-label" for="sendBackCommentInput">Feedback / Requested Changes *</label>
          <input id="sendBackCommentInput" type="text" class="form-input" placeholder="e.g. Please shift by one day due to team handover" required />
        </div>
      `,
      buttons: [
        {
          id: 'cancelSendBackModal',
          text: 'Cancel',
          className: 'btn-secondary',
          onClick: (_, modal) => modal.close()
        },
        {
          id: 'confirmSendBackBtn',
          text: 'Send Back to Employee',
          className: 'btn-primary',
          onClick: async (_, modal) => {
            const comment = document.getElementById('sendBackCommentInput')?.value;
            if (!comment) {
              alert('Please enter feedback.');
              return;
            }
            try {
              await leaveApi.sendBackLeave(leave.id, comment);
              modal.close();
              ApprovalDashboard.loadData();
            } catch (err) {
              alert('Error sending back leave: ' + err.message);
            }
          }
        }
      ]
    });
  }
};
