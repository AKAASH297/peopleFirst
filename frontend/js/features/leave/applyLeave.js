import { leaveApi } from '../../api/leaveApi.js';
import { agentApi } from '../../api/agentApi.js';
import { DateUtils } from '../../utils/dateUtils.js';
import { ValidationUtils } from '../../utils/validationUtils.js';
import { Modal } from '../../components/modal.js';
import { Router } from '../../core/router.js';
import { Auth } from '../../core/auth.js';

export const ApplyLeave = {
  render() {
    const user = Auth.getCurrentUser();
    const isContractor = Auth.isContractor();

    return `
      <div class="view-header">
        <div>
          <h2 class="view-title">Apply for Leave</h2>
          <p class="view-subtitle">Submit a leave request. Backend policies will validate combinations, deadlines, and documentation.</p>
        </div>
      </div>

      <div style="display: grid; grid-template-columns: 2fr 1fr; gap: 1.5rem;">
        <div class="card">
          <div class="card-header">Leave Application Form</div>
          <div class="card-body">
            <div id="applyAlert"></div>

            <form id="applyLeaveForm">
              <div class="form-group">
                <label class="form-label" for="applyLeaveType">Leave Type *</label>
                <select id="applyLeaveType" class="form-select" required>
                  <option value="">-- Select Leave Type --</option>
                  ${!isContractor ? '<option value="CASUAL">Casual Leave (12 days/yr)</option>' : ''}
                  <option value="SICK">Sick Leave (16 days/yr)</option>
                  <option value="PAID">Paid Leave (${isContractor ? '24' : '20'} days/yr)</option>
                  <option value="LOP">Loss of Pay (LOP) (${isContractor ? '30' : '180'} days/yr)</option>
                  ${!isContractor ? '<option value="WFH">Work From Home (WFH) (24 days/yr)</option>' : ''}
                  ${!isContractor ? '<option value="MATERNITY">Maternity Leave (182 days/yr)</option>' : ''}
                  ${!isContractor ? '<option value="VOLUNTEERING">Volunteering Leave (2 days/yr)</option>' : ''}
                </select>
                <div id="leaveTypeNotice" class="form-helper"></div>
              </div>

              <div id="combinationGroup" class="form-group hidden">
                <label class="form-label" for="applyCombinedWith">Combine with Another Leave Type</label>
                <select id="applyCombinedWith" class="form-select">
                  <option value="">-- None (Single Leave Type) --</option>
                  <option value="WFH">Work From Home (WFH)</option>
                </select>
                <div class="form-helper">Policy Rule: Casual Leave may only be combined with WFH.</div>
              </div>

              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem;">
                <div class="form-group">
                  <label class="form-label" for="applyStartDate">Start Date *</label>
                  <input id="applyStartDate" type="date" class="form-input" required onclick="try{this.showPicker()}catch(e){}" />
                  <div class="form-helper">Click field or 📅 icon to open picker</div>
                </div>
                <div class="form-group">
                  <label class="form-label" for="applyEndDate">End Date *</label>
                  <input id="applyEndDate" type="date" class="form-input" required onclick="try{this.showPicker()}catch(e){}" />
                  <div class="form-helper">Click field or 📅 icon to open picker</div>
                </div>
              </div>

              <div style="margin-bottom: 1rem; display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; background: #f8fafc; padding: 0.5rem 0.75rem; border-radius: var(--radius-sm); border: 1px dashed var(--border);">
                <span style="font-size: 0.75rem; color: var(--text-muted); font-weight: 600;">⚡ Quick Pick:</span>
                <button type="button" class="btn btn-secondary btn-sm date-preset-btn" data-preset="tomorrow">Tomorrow</button>
                <button type="button" class="btn btn-secondary btn-sm date-preset-btn" data-preset="next-mon">Next Monday</button>
                <button type="button" class="btn btn-secondary btn-sm date-preset-btn" data-preset="3days">Next 3 Days</button>
                <button type="button" class="btn btn-secondary btn-sm date-preset-btn" data-preset="next-week">Next Week (5 Days)</button>
              </div>

              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; padding: 0.75rem; background: #f8fafc; border-radius: var(--radius-sm);">
                <div class="flex items-center gap-2">
                  <input id="applyHalfDay" type="checkbox" style="width:16px; height:16px; cursor:pointer;" />
                  <label for="applyHalfDay" style="font-size:0.875rem; font-weight:500; cursor:pointer;">Half-Day Leave</label>
                </div>
                <div id="halfDaySessionGroup" class="hidden flex items-center gap-2">
                  <select id="applySession" class="form-select" style="width: auto; padding: 0.25rem 0.5rem; font-size: 0.8125rem;">
                    <option value="FIRST_HALF">First Half</option>
                    <option value="SECOND_HALF">Second Half</option>
                  </select>
                </div>
                <div style="font-size: 0.875rem; font-weight: 600; color: var(--primary);">
                  Total Days: <span id="applyDaysDisplay">0</span>
                </div>
              </div>

              <div id="documentUploadGroup" class="form-group hidden" style="background:#fffbeb; border:1px solid #fde68a; border-radius:var(--radius-sm); padding:1rem;">
                <label class="form-label" style="color:#92400e; font-weight:600;">Medical Document Required *</label>
                <p style="font-size:0.8125rem; color:#78350f; margin-bottom:0.5rem;">
                  Sick leave exceeding 2 days requires a valid medical certificate or prescription.
                </p>
                <div class="flex items-center gap-2">
                  <input id="applyDocCheckbox" type="checkbox" style="width:16px; height:16px; cursor:pointer;" />
                  <label for="applyDocCheckbox" style="font-size:0.8125rem; cursor:pointer;">I have attached a verified medical certificate</label>
                </div>
                <input id="applyDocUrl" type="text" class="form-input" placeholder="Medical Certificate File URL / Ref" value="https://documents.peoplefirst.internal/medical-cert.pdf" style="margin-top:0.5rem; font-size:0.8125rem;" />
              </div>

              <div class="form-group">
                <label class="form-label" for="applyReason">Reason for Leave</label>
                <textarea id="applyReason" class="form-textarea" rows="3" placeholder="Briefly state the reason for your leave..."></textarea>
              </div>

              <div style="display: flex; gap: 0.75rem; justify-content: flex-end; margin-top: 1.5rem;">
                <button type="button" id="applyCancelBtn" class="btn btn-secondary">Cancel</button>
                <button type="submit" id="applySubmitBtn" class="btn btn-primary">Submit Application</button>
              </div>
            </form>
          </div>
        </div>

        <div>
          <div class="card" style="margin-bottom: 1.5rem;">
            <div class="card-header">Leave Policy Checklist</div>
            <div class="card-body" style="font-size: 0.8125rem; line-height: 1.6; color: var(--text-sub);">
              <p>• <strong>Notice:</strong> Apply before the leave start date.</p>
              <p>• <strong>Paid Leave:</strong> Requires &gt; 2 days notice (start date must be 3+ days out).</p>
              <p>• <strong>Sick Leave &gt; 2 days:</strong> Medical documentation is mandatory.</p>
              <p>• <strong>Casual Leave:</strong> Can only be combined with WFH.</p>
              <p>• <strong>Weekly Cutoff:</strong> Casual/WFH requests must be submitted by end of the current week.</p>
              <p>• <strong>Monthly Cutoff:</strong> Sick/Paid/LOP submitted on or before the 25th.</p>
              <p style="margin-top: 0.5rem; color: var(--primary);">
                Late requests or retroactive corrections must be raised via a <strong>Support Ticket</strong>.
              </p>
            </div>
          </div>
        </div>
      </div>
    `;
  },

  attachEvents() {
    const leaveTypeEl = document.getElementById('applyLeaveType');
    const combinedWithGroup = document.getElementById('combinationGroup');
    const combinedWithEl = document.getElementById('applyCombinedWith');
    const startDateEl = document.getElementById('applyStartDate');
    const endDateEl = document.getElementById('applyEndDate');
    const halfDayEl = document.getElementById('applyHalfDay');
    const sessionGroup = document.getElementById('halfDaySessionGroup');
    const daysDisplay = document.getElementById('applyDaysDisplay');
    const docGroup = document.getElementById('documentUploadGroup');
    const docCheckbox = document.getElementById('applyDocCheckbox');
    const noticeEl = document.getElementById('leaveTypeNotice');
    const form = document.getElementById('applyLeaveForm');
    const alertEl = document.getElementById('applyAlert');

    const tomorrowStr = DateUtils.getTomorrowStr();
    startDateEl.min = tomorrowStr;
    endDateEl.min = tomorrowStr;

    const openPickerSafe = (inputEl) => {
      try {
        if (typeof inputEl.showPicker === 'function') {
          inputEl.showPicker();
        }
      } catch (e) {
        // Fallback for browsers without showPicker support
      }
    };

    startDateEl.addEventListener('click', () => openPickerSafe(startDateEl));
    endDateEl.addEventListener('click', () => openPickerSafe(endDateEl));

    // Wire up quick pick presets
    document.querySelectorAll('.date-preset-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        const preset = btn.getAttribute('data-preset');
        const today = new Date();
        let start = new Date();
        let end = new Date();

        if (preset === 'tomorrow') {
          start = DateUtils.addDays(today, 1);
          end = start;
        } else if (preset === 'next-mon') {
          const day = today.getDay(); // 0 Sun, 1 Mon...
          const diff = (8 - day) % 7 || 7;
          start = DateUtils.addDays(today, diff);
          end = start;
        } else if (preset === '3days') {
          const isPaid = leaveTypeEl.value === 'PAID';
          start = DateUtils.addDays(today, isPaid ? 3 : 1);
          end = DateUtils.addDays(start, 2);
        } else if (preset === 'next-week') {
          const day = today.getDay();
          const diff = (8 - day) % 7 || 7;
          start = DateUtils.addDays(today, diff);
          end = DateUtils.addDays(start, 4);
        }

        startDateEl.value = DateUtils.formatDateISO(start);
        endDateEl.value = DateUtils.formatDateISO(end);
        endDateEl.min = startDateEl.value;
        updateCalculations();
      });
    });

    const updateCalculations = () => {
      const type = leaveTypeEl.value;
      const start = startDateEl.value;
      const end = endDateEl.value;
      const isHalf = halfDayEl.checked;

      // Adjust min date for Paid Leave (> 2 days notice requirement)
      if (type === 'PAID') {
        const minPaid = DateUtils.formatDateISO(DateUtils.addDays(new Date(), 3));
        startDateEl.min = minPaid;
        noticeEl.innerHTML = `<span style="color:#d97706;">⚠️ Paid Leave requires advance notice of more than 2 days (earliest start: ${DateUtils.formatDate(minPaid)}).</span>`;
      } else {
        startDateEl.min = tomorrowStr;
        if (type === 'VOLUNTEERING') {
          noticeEl.innerHTML = '<span style="color:#059669;">🌿 Volunteering triggers corporate CSR chapter enrollment recommendations!</span>';
        } else {
          noticeEl.innerHTML = '';
        }
      }

      if (start) {
        endDateEl.min = start;
      } else {
        endDateEl.min = tomorrowStr;
      }

      // Show/hide combination option if Casual or WFH
      if (type === 'CASUAL' && !Auth.isContractor()) {
        combinedWithGroup.classList.remove('hidden');
      } else {
        combinedWithGroup.classList.add('hidden');
        combinedWithEl.value = '';
      }

      // Show/hide half-day session selector
      if (isHalf) {
        sessionGroup.classList.remove('hidden');
        if (start && !end) endDateEl.value = start;
      } else {
        sessionGroup.classList.add('hidden');
      }

      const totalDays = DateUtils.calculateDays(start, end, isHalf);
      daysDisplay.textContent = totalDays;

      // Show/hide medical document requirement if Sick > 2 days
      if (type === 'SICK' && totalDays > 2) {
        docGroup.classList.remove('hidden');
      } else {
        docGroup.classList.add('hidden');
      }
    };

    const handleStartDateChange = () => {
      if (startDateEl.value) {
        endDateEl.min = startDateEl.value;
        if (!endDateEl.value || DateUtils.parseLocalDate(endDateEl.value) < DateUtils.parseLocalDate(startDateEl.value)) {
          endDateEl.value = startDateEl.value;
        }
      }
      updateCalculations();
    };

    leaveTypeEl.addEventListener('change', updateCalculations);
    startDateEl.addEventListener('change', handleStartDateChange);
    startDateEl.addEventListener('input', handleStartDateChange);
    endDateEl.addEventListener('change', updateCalculations);
    endDateEl.addEventListener('input', updateCalculations);
    halfDayEl.addEventListener('change', updateCalculations);

    updateCalculations();

    document.getElementById('applyCancelBtn')?.addEventListener('click', () => Router.navigate('dashboard'));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      alertEl.innerHTML = '';

      const type = leaveTypeEl.value;
      const combined = combinedWithEl.value || null;
      const start = startDateEl.value;
      const end = endDateEl.value;
      const isHalf = halfDayEl.checked;
      const session = isHalf ? document.getElementById('applySession').value : null;
      const reason = document.getElementById('applyReason').value.trim();
      const docAttached = docCheckbox.checked;
      const docUrl = docAttached ? document.getElementById('applyDocUrl').value : null;
      const totalDays = DateUtils.calculateDays(start, end, isHalf);

      // Client-side validation
      const validation = ValidationUtils.validateLeaveForm({
        leaveType: type,
        combinedWithType: combined,
        startDate: start,
        endDate: end,
        totalDays,
        isHalfDay: isHalf,
        documentAttached: docAttached,
        isContractor: Auth.isContractor()
      });

      if (!validation.isValid) {
        alertEl.innerHTML = `
          <div class="alert alert-danger">
            <strong>Application Notice:</strong>
            <ul style="margin-left: 1.25rem; margin-top: 0.25rem;">
              ${validation.errors.map(err => `<li>${err}</li>`).join('')}
            </ul>
          </div>
        `;
        return;
      }

      const submitBtn = document.getElementById('applySubmitBtn');
      submitBtn.disabled = true;
      submitBtn.textContent = 'Submitting...';

      try {
        const payload = {
          leaveType: type,
          combinedWithType: combined,
          startDate: start,
          endDate: end,
          isHalfDay: isHalf,
          halfDaySession: session,
          reason,
          documentAttached: docAttached,
          documentUrl: docUrl
        };

        const result = await leaveApi.applyLeave(payload);

        // Fetch wellbeing suggestions for this application (§6)
        let wellbeingSuggestions = [];
        try {
          const chatCheck = await agentApi.chat(`Applied for ${type} leave from ${start} to ${end}`);
          if (chatCheck && chatCheck.wellbeingSuggestions) {
            wellbeingSuggestions = chatCheck.wellbeingSuggestions;
          }
        } catch (e) {
          // non-blocking
        }

        let wellbeingHtml = '';
        if (wellbeingSuggestions.length) {
          wellbeingHtml = `
            <div style="margin-top: 1rem; border-top: 1px solid var(--border); padding-top: 1rem;">
              <div style="font-weight:600; color:var(--primary); margin-bottom:0.5rem;">✨ Kura Wellbeing Recommendations</div>
              ${wellbeingSuggestions.map(s => `
                <div style="background:#f0fdf4; border:1px solid #bbf7d0; border-radius:8px; padding:10px; margin-bottom:8px; font-size:0.8125rem;">
                  <div style="font-weight:600; color:#166534;">${s.title}</div>
                  <div style="margin-top:2px;">${s.message}</div>
                </div>
              `).join('')}
            </div>
          `;
        }

        Modal.show({
          title: 'Leave Request Submitted',
          content: `
            <div style="text-align: center; margin-bottom: 1rem;">
              <span style="font-size: 2.5rem;">✅</span>
              <p style="font-weight: 600; margin-top: 0.5rem;">Your leave application is now ${result.status}.</p>
            </div>
            <div style="font-size: 0.875rem; background: #f8fafc; padding: 1rem; border-radius: 8px; line-height: 1.6;">
              <div>• <strong>Type:</strong> ${result.leaveTypeDisplayName} ${result.combinedWithType ? `(+ ${result.combinedWithType})` : ''}</div>
              <div>• <strong>Dates:</strong> ${DateUtils.formatDate(result.startDate)} to ${DateUtils.formatDate(result.endDate)} (${result.totalDays} days)</div>
              <div>• <strong>Reason:</strong> ${result.reason || '—'}</div>
            </div>
            ${wellbeingHtml}
          `,
          buttons: [
            {
              id: 'doneApplyBtn',
              text: 'View My Leaves',
              className: 'btn-primary',
              onClick: (_, modal) => {
                modal.close();
                Router.navigate('myLeaves');
              }
            }
          ]
        });

      } catch (err) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit Application';

        alertEl.innerHTML = `
          <div class="alert alert-danger">
            <strong>Submission Rejected by Policy Engine:</strong>
            <div style="margin-top: 0.25rem;">${err.message}</div>
            ${err.message && err.message.includes('ticket') ? `
              <button id="goToTicketFromApply" class="btn btn-secondary btn-sm" style="margin-top:0.5rem;">
                Raise a Support Ticket &rarr;
              </button>
            ` : ''}
          </div>
        `;

        document.getElementById('goToTicketFromApply')?.addEventListener('click', () => Router.navigate('tickets'));
      }
    });
  }
};
