import { ticketApi } from '../../api/ticketApi.js';
import { DateUtils } from '../../utils/dateUtils.js';
import { Auth } from '../../core/auth.js';

export const TicketView = {
  async render() {
    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Support Tickets Desk</h2>
          <p class="view-subtitle">File tickets for late submissions after cutoffs, post-date corrections, technical errors, or policy exceptions.</p>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem;">
        <div class="card">
          <div class="card-header">Create Support Ticket</div>
          <div class="card-body">
            <div id="ticketAlert"></div>

            <form id="createTicketForm">
              <div class="form-group">
                <label class="form-label" for="ticketTypeSelect">Ticket Reason / Category *</label>
                <select id="ticketTypeSelect" class="form-select" required>
                  <option value="">-- Select Category --</option>
                  <option value="LATE_SUBMISSION">Late Submission (Missed cutoff: End-of-week or 25th of month)</option>
                  <option value="POST_DATE_CORRECTION">Post-Date Leave Correction (Leave date has already passed)</option>
                  <option value="TECHNICAL_ERROR">Technical Error Encountered while applying</option>
                  <option value="POLICY_EXCEPTION">Policy Exception / Discretionary Request</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label" for="ticketSubjectInput">Subject / Summary *</label>
                <input id="ticketSubjectInput" type="text" class="form-input" placeholder="Brief summary of your issue" required />
              </div>

              <div class="form-group">
                <label class="form-label" for="ticketDescriptionInput">Detailed Explanation & Leave Dates *</label>
                <textarea id="ticketDescriptionInput" class="form-textarea" rows="4" placeholder="Explain why the request is late, or details of the adjustment required..." required></textarea>
              </div>

              <button type="submit" id="submitTicketBtn" class="btn btn-primary" style="width: 100%;">
                Submit Support Ticket
              </button>
            </form>
          </div>
        </div>

        <div class="card">
          <div class="card-header">My Submitted Tickets</div>
          <div class="card-body" style="padding: 0;">
            <div id="ticketsListContainer" style="padding: 1rem; display: flex; flex-direction: column; gap: 0.75rem; max-height: 500px; overflow-y: auto;">
              <div style="color:var(--text-muted);">Loading tickets...</div>
            </div>
          </div>
        </div>
      </div>
    `;
  },

  async attachEvents() {
    const form = document.getElementById('createTicketForm');
    const alertEl = document.getElementById('ticketAlert');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertEl.innerHTML = '';

      const type = document.getElementById('ticketTypeSelect').value;
      const subject = document.getElementById('ticketSubjectInput').value.trim();
      const desc = document.getElementById('ticketDescriptionInput').value.trim();

      const btn = document.getElementById('submitTicketBtn');
      btn.disabled = true;
      btn.textContent = 'Submitting ticket...';

      try {
        await ticketApi.createTicket({
          ticketType: type,
          subject,
          description: desc
        });

        alertEl.innerHTML = '<div class="alert alert-success">Ticket submitted successfully! An HR administrator will review it.</div>';
        form.reset();
        btn.disabled = false;
        btn.textContent = 'Submit Support Ticket';
        this.loadTickets();
      } catch (err) {
        btn.disabled = false;
        btn.textContent = 'Submit Support Ticket';
        alertEl.innerHTML = `<div class="alert alert-danger">${err.message}</div>`;
      }
    });

    await this.loadTickets();
  },

  async loadTickets() {
    const container = document.getElementById('ticketsListContainer');
    try {
      const tickets = await ticketApi.getTickets();
      if (!tickets.length) {
        container.innerHTML = '<div style="color:var(--text-muted); font-size:0.875rem; text-align:center; padding:1.5rem;">No support tickets filed yet.</div>';
        return;
      }

      container.innerHTML = tickets.map(t => `
        <div style="border: 1px solid var(--border); border-radius: 8px; padding: 12px; background: #f8fafc;">
          <div class="flex justify-between items-center" style="margin-bottom: 4px;">
            <span style="font-weight: 600; font-size: 0.875rem;">${t.subject}</span>
            <span class="badge ${t.status === 'RESOLVED' ? 'badge-approved' : 'badge-pending'}">${t.status}</span>
          </div>
          <div style="font-size: 0.75rem; color: var(--text-muted); margin-bottom: 6px;">
            ${t.ticketType} • Filed ${DateUtils.formatDateTime(t.createdAt)}
          </div>
          <div style="font-size: 0.8125rem; color: var(--text-sub); line-height: 1.4;">
            ${t.description}
          </div>
          ${t.resolutionComment ? `
            <div style="margin-top: 6px; padding: 6px 10px; background: #e0e7ff; border-radius: 6px; font-size: 0.75rem; color: #3730a3;">
              <strong>Admin Response:</strong> ${t.resolutionComment}
            </div>
          ` : ''}
        </div>
      `).join('');
    } catch (err) {
      container.innerHTML = `<div style="color:var(--danger); font-size:0.875rem;">Failed to load tickets: ${err.message}</div>`;
    }
  }
};
