import { agentApi } from '../../api/agentApi.js';
import { authApi } from '../../api/authApi.js';
import { AppState } from '../../core/state.js';

export const AgentChat = {
  containerId: null,

  init(containerId) {
    this.containerId = containerId;
    this.render();
  },

  render() {
    const container = document.getElementById(this.containerId);
    if (!container) return;

    if (!AppState.token) {
      this.renderLoginForm(container);
    } else {
      this.renderChatInterface(container);
    }
  },

  renderLoginForm(container) {
    container.innerHTML = `
      <div style="padding: 2.5rem; text-align: center; max-width: 450px; margin: auto;">
        <div style="font-size: 3rem; margin-bottom: 0.5rem;">🤖</div>
        <h2 style="font-size: 1.5rem; font-weight: 700; color: var(--primary);">Kura AI Agent Portal</h2>
        <p style="font-size: 0.875rem; color: var(--text-muted); margin-bottom: 1.5rem;">
          Contractor Leave Management & Wellbeing Concierge
        </p>

        <div id="contractorLoginAlert"></div>

        <form id="contractorLoginForm" style="text-align: left;">
          <div class="form-group">
            <label class="form-label" for="cUsername">Username</label>
            <input id="cUsername" type="text" class="form-input" value="contractor1" required />
          </div>
          <div class="form-group">
            <label class="form-label" for="cPassword">Password</label>
            <input id="cPassword" type="password" class="form-input" value="password123" required />
          </div>
          <button type="submit" id="cLoginSubmit" class="btn btn-primary" style="width: 100%; padding: 0.75rem; margin-top: 0.5rem;">
            Authenticate with Kura Agent
          </button>
        </form>

        <div style="margin-top: 1.5rem; font-size: 0.75rem; color: var(--text-muted);">
          Note: Contractors access peopleFirst services exclusively through the AI Agent interface.
        </div>
      </div>
    `;

    document.getElementById('contractorLoginForm')?.addEventListener('submit', async (e) => {
      e.preventDefault();
      const u = document.getElementById('cUsername').value.trim();
      const p = document.getElementById('cPassword').value;
      const alertEl = document.getElementById('contractorLoginAlert');
      alertEl.innerHTML = '';

      try {
        // Log in via AGENT channel
        const result = await authApi.login(u, p, 'AGENT');
        AppState.setUser(result.user, result.accessToken, result.refreshToken);
        if (!result.user.contractor) {
          window.location.href = 'index.html';
          return;
        }
        this.render();
      } catch (err) {
        alertEl.innerHTML = `<div class="alert alert-danger">${err.message || 'Login failed'}</div>`;
      }
    });
  },

  renderChatInterface(container) {
    const user = AppState.currentUser;
    container.innerHTML = `
      <div style="display: flex; flex-direction: column; height: 100%;">
        <!-- Header -->
        <div style="padding: 1rem 1.5rem; border-bottom: 1px solid var(--border); display: flex; justify-content: space-between; align-items: center; background: #fff;">
          <div class="flex items-center gap-3">
            <div style="width: 42px; height: 42px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), var(--purple)); display: flex; align-items: center; justify-content: center; color: #fff; font-size: 1.25rem;">
              ✨
            </div>
            <div>
              <div style="font-weight: 700; font-size: 1.0625rem; color: var(--text-main);">Kura · Leave & Wellbeing Concierge</div>
              <div style="font-size: 0.75rem; color: var(--text-muted);">
                Logged in as <strong>${user.fullName}</strong> (${user.contractor ? 'Contractor Partner' : user.role})
              </div>
            </div>
          </div>
          <div class="flex items-center gap-2">
            ${!user.contractor ? `<a href="index.html" class="btn btn-secondary btn-sm">Return to Web Portal &rarr;</a>` : ''}
            <button id="contractorViewPoliciesBtn" class="btn btn-outline btn-sm">📋 View Policies (Read Only)</button>
            <button id="agentLogoutBtn" class="btn btn-outline btn-sm">Sign Out</button>
          </div>
        </div>

        <!-- Main Body: Dual-Pane Layout -->
        <div class="contractor-layout">
          <!-- Left: Conversational Agent -->
          <div style="display: flex; flex-direction: column; height: 100%; overflow: hidden;">
            <!-- Chat messages -->
            <div id="fullChatMessages" class="chat-messages" style="flex: 1; padding: 1.5rem; overflow-y: auto;">
              <div class="chat-bubble chat-bubble-agent">
                Hello ${user.fullName}! I am <strong>Kura</strong>, your autonomous leave and wellbeing assistant at peopleFirst. As a contractor, you can manage your Sick, Paid, and LOP leaves, check quotas, read leave policies, or explore campus health amenities directly through our conversation.
              </div>
            </div>

            <!-- Quick reply chips -->
            <div id="fullChatQuickReplies" class="chat-quick-replies" style="padding: 0.75rem 1.5rem; background: #fff; border-top: 1px solid var(--border);">
              <button class="quick-reply-chip" data-msg="What are my leave balances?">📊 My Balances</button>
              <button class="quick-reply-chip" data-msg="Check my weekly wellbeing status">💙 Weekly Wellbeing</button>
              <button class="quick-reply-chip" data-msg="Apply for 1 day sick leave tomorrow">📝 Apply Sick Leave</button>
              <button class="quick-reply-chip" data-msg="What are contractor leave rules?">📋 Contractor Policies</button>
              <button class="quick-reply-chip" data-msg="Campus amenities and healthcare">🌿 Wellness Amenities</button>
            </div>

            <!-- Input row -->
            <form id="fullChatInputForm" class="chat-input-row" style="padding: 1rem 1.5rem; background: #fff; border-top: 1px solid var(--border);">
              <input id="fullChatTextInput" type="text" class="chat-input" placeholder="Type a message or instruction for Kura..." autocomplete="off" style="padding: 0.75rem 1rem; border-radius: var(--radius-md);" />
              <button type="submit" class="btn btn-primary" style="padding: 0 1.5rem; border-radius: var(--radius-md);">
                Send &rarr;
              </button>
            </form>
          </div>

          <!-- Right: Leave Types & Access & Policy Sidebar -->
          <div class="contractor-sidebar">
            <div class="card" style="margin: 0; box-shadow: none; border: 1px solid var(--border);">
              <div class="card-header" style="font-size: 0.875rem; padding: 0.75rem 1rem; background: #fff;">
                <span>🎯 Leave Types & Access</span>
                <span class="badge badge-pending" style="font-size: 0.6875rem;">Contractor</span>
              </div>
              <div class="card-body" style="padding: 0.875rem; font-size: 0.8125rem; display: flex; flex-direction: column; gap: 0.625rem;">
                <div style="padding: 0.5rem 0.625rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
                  <div style="font-weight: 600; color: #166534;">✅ Sick Leave</div>
                  <div style="color: #15803d; font-size: 0.75rem;">16.0 days/yr • Medical doc for &gt;2 days</div>
                </div>
                <div style="padding: 0.5rem 0.625rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
                  <div style="font-weight: 600; color: #166534;">✅ Paid Leave</div>
                  <div style="color: #15803d; font-size: 0.75rem;">24.0 days/yr • &gt;2 days notice required</div>
                </div>
                <div style="padding: 0.5rem 0.625rem; background: #f0fdf4; border-radius: 6px; border: 1px solid #bbf7d0;">
                  <div style="font-weight: 600; color: #166534;">✅ Loss of Pay (LOP)</div>
                  <div style="color: #15803d; font-size: 0.75rem;">30.0 days/yr • Unpaid leave quota</div>
                </div>
                <div style="padding: 0.5rem 0.625rem; background: #fef2f2; border-radius: 6px; border: 1px solid #fecaca;">
                  <div style="font-weight: 600; color: #991b1b;">❌ Restricted Leave Types</div>
                  <div style="color: #b91c1c; font-size: 0.75rem;">Casual, WFH, Maternity, Volunteering (0.0 days)</div>
                </div>
              </div>
            </div>

            <div class="card" style="margin: 0; box-shadow: none; border: 1px solid var(--border);">
              <div class="card-header" style="font-size: 0.875rem; padding: 0.75rem 1rem; background: #fff;">
                <span>⚠️ Contractor Constraints</span>
              </div>
              <div class="card-body" style="padding: 0.875rem; font-size: 0.75rem; color: var(--text-sub); line-height: 1.5; display: flex; flex-direction: column; gap: 0.375rem;">
                <div>• <strong>No Combinations:</strong> Contractors cannot combine different leave types.</div>
                <div>• <strong>Notice:</strong> Apply, cancel, or update <em>before</em> the actual leave date.</div>
                <div>• <strong>Agent Access:</strong> Full leave concierge services via Kura chat.</div>
              </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 0.5rem;">
              <button id="sidebarCheckBalanceBtn" class="btn btn-outline btn-sm" style="width: 100%; justify-content: center;">
                📊 Check My Leave Balances
              </button>
              <button id="sidebarViewPoliciesBtn" class="btn btn-secondary btn-sm" style="width: 100%; justify-content: center;">
                📋 Read Company Policies
              </button>
            </div>
          </div>
        </div>
      </div>
    `;

    document.getElementById('agentLogoutBtn')?.addEventListener('click', () => {
      AppState.setUser(null, null, null);
      window.location.href = 'index.html';
    });

    const openPoliciesModal = async () => {
      try {
        const data = await agentApi.getPolicies();
        const generalRules = data?.generalRules || [];
        const deadlineRules = data?.deadlineRules || [];
        const combinationRules = data?.combinationRules || [];
        const leaveTypes = data?.leaveTypes || [];

        const bodyHtml = `
          <div style="max-height: 65vh; overflow-y: auto; display: flex; flex-direction: column; gap: 1rem; font-size: 0.8125rem;">
            <div class="card" style="margin:0;">
              <div class="card-header" style="font-size: 0.8125rem; font-weight: 600;">1. Contractor Specific Rules</div>
              <div class="card-body" style="padding: 0.75rem 1rem;">
                <ul style="margin-left: 1rem; display: flex; flex-direction: column; gap: 0.25rem;">
                  <li>Contractors access leave management exclusively through Kura AI Agent.</li>
                  <li>Eligible for Sick (16 days), Paid (24 days), and LOP (30 days).</li>
                  <li>Ineligible for Casual, WFH, Maternity, and Volunteering.</li>
                  <li>Zero combination rights permitted across different leave types.</li>
                  <li>All leave submissions, updates, and cancellations must occur before the actual leave start date.</li>
                </ul>
              </div>
            </div>

            ${generalRules.length ? `
            <div class="card" style="margin:0;">
              <div class="card-header" style="font-size: 0.8125rem; font-weight: 600;">2. General Company Rules</div>
              <div class="card-body" style="padding: 0.75rem 1rem;">
                <ul style="margin-left: 1rem; display: flex; flex-direction: column; gap: 0.25rem;">
                  ${generalRules.map(r => `<li>${r}</li>`).join('')}
                </ul>
              </div>
            </div>` : ''}

            ${deadlineRules.length ? `
            <div class="card" style="margin:0;">
              <div class="card-header" style="font-size: 0.8125rem; font-weight: 600;">3. Application Deadlines & Constraints</div>
              <div class="card-body" style="padding: 0.75rem 1rem;">
                <ul style="margin-left: 1rem; display: flex; flex-direction: column; gap: 0.25rem;">
                  ${deadlineRules.map(r => `<li>${r}</li>`).join('')}
                </ul>
              </div>
            </div>` : ''}

            ${combinationRules.length ? `
            <div class="card" style="margin:0;">
              <div class="card-header" style="font-size: 0.8125rem; font-weight: 600;">4. Leave Combination Rules</div>
              <div class="card-body" style="padding: 0.75rem 1rem;">
                <ul style="margin-left: 1rem; display: flex; flex-direction: column; gap: 0.25rem;">
                  ${combinationRules.map(r => `<li>${r}</li>`).join('')}
                </ul>
              </div>
            </div>` : ''}

            ${leaveTypes.length ? `
            <div class="card" style="margin:0;">
              <div class="card-header" style="font-size: 0.8125rem; font-weight: 600;">5. Quotas & Role Matrix</div>
              <div class="table-container" style="border:none; padding: 0.5rem;">
                <table class="table" style="font-size: 0.75rem;">
                  <thead>
                    <tr>
                      <th>Leave Type</th>
                      <th>Contractor Status</th>
                      <th>Contractor Quota</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${leaveTypes.map(t => `
                      <tr>
                        <td style="font-weight: 600;">${t.displayName}</td>
                        <td>${t.contractorEligible ? '✅ Eligible' : '<span style="color:var(--danger); font-weight:600;">❌ Restricted</span>'}</td>
                        <td><strong>${t.contractorAnnualQuota}</strong> days/yr</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>` : ''}
          </div>
        `;
        const { Modal } = await import('../../components/modal.js');
        Modal.show({
          title: '📋 Company Leave Policies (Read Only)',
          content: bodyHtml,
          buttons: [
            { id: 'policyModalCloseBtn', text: 'Close', className: 'btn-secondary', onClick: (e, modal) => modal.close() }
          ]
        });
      } catch (err) {
        console.error(err);
      }
    };

    document.getElementById('contractorViewPoliciesBtn')?.addEventListener('click', openPoliciesModal);
    document.getElementById('sidebarViewPoliciesBtn')?.addEventListener('click', openPoliciesModal);
    document.getElementById('sidebarCheckBalanceBtn')?.addEventListener('click', () => {
      this.sendChatMessage('What are my leave balances?');
    });

    const form = document.getElementById('fullChatInputForm');
    const input = document.getElementById('fullChatTextInput');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      await this.sendChatMessage(text);
    });

    this.attachQuickReplies();
  },

  attachQuickReplies() {
    document.querySelectorAll('#fullChatQuickReplies .quick-reply-chip').forEach(btn => {
      btn.addEventListener('click', async () => {
        const msg = btn.getAttribute('data-msg');
        if (msg) await this.sendChatMessage(msg);
      });
    });
  },

  async sendChatMessage(text) {
    const messagesContainer = document.getElementById('fullChatMessages');
    const quickRepliesContainer = document.getElementById('fullChatQuickReplies');

    // Add user bubble
    const userBubble = document.createElement('div');
    userBubble.className = 'chat-bubble chat-bubble-user';
    userBubble.textContent = text;
    messagesContainer.appendChild(userBubble);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    // Add typing
    const typing = document.createElement('div');
    typing.className = 'chat-bubble chat-bubble-agent';
    typing.innerHTML = '<em>Kura is processing...</em>';
    messagesContainer.appendChild(typing);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    try {
      const response = await agentApi.chat(text, 'contractor-agent-session');
      typing.remove();

      const agentBubble = document.createElement('div');
      agentBubble.className = 'chat-bubble chat-bubble-agent';
      agentBubble.innerHTML = this.formatReply(response.reply);
      messagesContainer.appendChild(agentBubble);

      // Append wellbeing cards if present
      if (response.wellbeingSuggestions && response.wellbeingSuggestions.length) {
        response.wellbeingSuggestions.forEach(sug => {
          const sugCard = document.createElement('div');
          sugCard.style.cssText = 'background:#f0fdf4; border:1px solid #bbf7d0; border-radius:8px; padding:10px 14px; margin-top:8px; font-size:0.875rem;';
          
          let extraHtml = '';
          if (sug.partnerHospitals && sug.partnerHospitals.length) {
            extraHtml += `
              <div style="margin-top:8px; display:flex; flex-direction:column; gap:4px;">
                <div style="font-weight:600; font-size:0.8125rem; color:#166534;">🏥 Partner Hospitals with OPD Discounts:</div>
                ${sug.partnerHospitals.map(h => `
                  <div style="background:#fff; border:1px solid #dcfce7; padding:6px 8px; border-radius:4px; font-size:0.75rem;">
                    <strong>${h.name}</strong> (${h.city})<br/>
                    🩺 ${h.opdDiscount} • 🔬 ${h.labTestDiscount} • 📞 ${h.contactNumber}
                  </div>
                `).join('')}
              </div>
            `;
          }

          if (sug.groupSuggestions && sug.groupSuggestions.length) {
            extraHtml += `
              <div style="margin-top:8px; display:flex; flex-direction:column; gap:4px;">
                <div style="font-weight:600; font-size:0.8125rem; color:#166534;">🤝 Active Corporate Volunteering Groups:</div>
                ${sug.groupSuggestions.map(g => `
                  <div style="background:#fff; border:1px solid #dcfce7; padding:4px 8px; border-radius:4px; font-size:0.75rem; color:#166534; font-weight:500;">
                    ${g}
                  </div>
                `).join('')}
              </div>
            `;
          }

          if (sug.partnerResorts && sug.partnerResorts.length) {
            extraHtml += `
              <div style="margin-top:8px; display:flex; flex-direction:column; gap:4px;">
                <div style="font-weight:600; font-size:0.8125rem; color:#166534;">🌴 Partner Resorts & Discounts:</div>
                ${sug.partnerResorts.map(r => `
                  <div style="background:#fff; border:1px solid #dcfce7; padding:6px 8px; border-radius:4px; font-size:0.75rem;">
                    <strong>${r.name}</strong> (${r.destination}) — <span style="color:#059669; font-weight:600;">${r.discount}</span> (Code: <code>${r.couponCode}</code>)
                  </div>
                `).join('')}
              </div>
            `;
          }

          sugCard.innerHTML = `
            <div style="font-weight:600; color:#166534;">🌿 ${sug.title}</div>
            <div style="color:#1e293b; margin-top:4px; line-height: 1.4;">${sug.message}</div>
            ${extraHtml}
            ${sug.actionUrl ? `<div style="margin-top:8px;"><a href="${sug.actionUrl}" target="_blank" rel="noopener noreferrer" style="color:#0284c7; text-decoration:underline; font-weight:500; font-size:0.8125rem;">📄 Open Action Portal &rarr;</a></div>` : ''}
          `;
          agentBubble.appendChild(sugCard);
        });
      }

      // Update quick replies
      if (response.quickReplies && response.quickReplies.length) {
        quickRepliesContainer.innerHTML = response.quickReplies.map(q => `
          <button class="quick-reply-chip" data-msg="${q}">${q}</button>
        `).join('');
        this.attachQuickReplies();
      }

      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    } catch (err) {
      typing.remove();
      const errBubble = document.createElement('div');
      errBubble.className = 'chat-bubble chat-bubble-agent';
      errBubble.style.borderColor = '#fca5a5';
      errBubble.style.color = '#991b1b';
      errBubble.textContent = err.message || 'Sorry, I encountered an error.';
      messagesContainer.appendChild(errBubble);
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
  },

  formatReply(text) {
    if (!text) return '';
    const lines = text.split('\n');
    let html = '';
    let i = 0;
    while (i < lines.length) {
      if (this.isTableStart(lines, i)) {
        const rows = [];
        let j = i;
        while (j < lines.length && lines[j].startsWith('|')) {
          rows.push(lines[j]);
          j++;
        }
        html += this.renderTable(rows);
        i = j;
      } else {
        html += this.formatInline(lines[i]);
        if (i < lines.length - 1) html += '<br/>';
        i++;
      }
    }
    return html;
  },

  escapeHtml(s) {
    return s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  },

  formatInline(line) {
    let s = this.escapeHtml(line);
    return s
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      .replace(/`([^`]+)`/g, '<code style="background:rgba(0,0,0,0.06); padding:2px 5px; border-radius:4px; font-size:0.85em;">$1</code>')
      .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer" style="color:#0284c7; text-decoration:underline;">$1</a>');
  },

  isTableStart(lines, i) {
    return i + 1 < lines.length
      && lines[i].startsWith('|')
      && lines[i + 1].startsWith('|')
      && /^[\s|:\-]+$/.test(lines[i + 1]);
  },

  renderTable(rows) {
    const cells = (row) => {
      let t = row.trim();
      if (t.startsWith('|')) t = t.slice(1);
      if (t.endsWith('|')) t = t.slice(0, -1);
      return t.split('|').map((c) => this.escapeHtml(c.trim()));
    };
    const header = cells(rows[0]).map((c) => `<th>${c}</th>`).join('');
    const body = rows.slice(2)
      .map((row) => `<tr>${cells(row).map((c) => `<td>${c}</td>`).join('')}</tr>`)
      .join('');
    return `<table class="kura-table"><thead><tr>${header}</tr></thead><tbody>${body}</tbody></table>`;
  }
};
