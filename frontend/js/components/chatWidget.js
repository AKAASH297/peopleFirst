import { agentApi } from '../api/agentApi.js';
import { Auth } from '../core/auth.js';

export const ChatWidget = {
  isOpen: false,
  messages: [],

  init() {
    if (document.getElementById('kuraChatContainer')) return;

    const container = document.createElement('div');
    container.id = 'kuraChatContainer';
    container.innerHTML = `
      <button id="kuraChatLauncher" class="chat-launcher" title="Ask Kura AI Concierge">
        <span>✨</span>
        <span>Chat with Kura</span>
      </button>

      <div id="kuraChatDrawer" class="chat-drawer hidden">
        <div class="chat-header">
          <div class="flex items-center gap-2">
            <span style="font-size: 1.25rem;">✨</span>
            <div>
              <div style="font-weight: 700; font-size: 0.9375rem;">Kura · Leave & Wellbeing Concierge</div>
            </div>
          </div>
          <button id="closeKuraChat" style="background:none; border:none; color:#fff; font-size:1.25rem; cursor:pointer;">&times;</button>
        </div>
        <div id="kuraChatMessages" class="chat-messages">
          <div class="chat-bubble chat-bubble-agent">
            Hello! I am <strong>Kura</strong>, your leave management and wellbeing concierge. Ask me about your leave balances, policies, or campus health amenities!
          </div>
        </div>
        <div id="kuraQuickReplies" class="chat-quick-replies">
          <button class="quick-reply-chip" data-msg="What are my leave balances?">My Balances</button>
          <button class="quick-reply-chip" data-msg="Check my weekly wellbeing status">📊 Weekly Wellbeing</button>
          <button class="quick-reply-chip" data-msg="Company leave policies">Policies</button>
          <button class="quick-reply-chip" data-msg="Campus amenities">Amenities</button>
        </div>
        <form id="kuraChatForm" class="chat-input-row">
          <input id="kuraChatInput" type="text" class="chat-input" placeholder="Message Kura..." autocomplete="off" maxlength="2000" />
          <button type="submit" class="btn btn-primary btn-sm">Send</button>
        </form>
      </div>
    `;

    document.body.appendChild(container);
    this.attachEvents();
  },

  attachEvents() {
    const launcher = document.getElementById('kuraChatLauncher');
    const drawer = document.getElementById('kuraChatDrawer');
    const closeBtn = document.getElementById('closeKuraChat');
    const form = document.getElementById('kuraChatForm');
    const input = document.getElementById('kuraChatInput');

    launcher.addEventListener('click', () => {
      this.isOpen = !this.isOpen;
      drawer.classList.toggle('hidden', !this.isOpen);
      if (this.isOpen) input.focus();
    });

    closeBtn.addEventListener('click', () => {
      this.isOpen = false;
      drawer.classList.add('hidden');
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      input.value = '';
      await this.sendMessage(text);
    });

    this.attachQuickReplyListeners();
  },

  attachQuickReplyListeners() {
    document.querySelectorAll('.quick-reply-chip').forEach(chip => {
      chip.addEventListener('click', async () => {
        const msg = chip.getAttribute('data-msg');
        if (msg) await this.sendMessage(msg);
      });
    });
  },

  async sendMessage(text) {
    const messagesEl = document.getElementById('kuraChatMessages');
    const quickRepliesEl = document.getElementById('kuraQuickReplies');

    // Add user bubble
    const userBubble = document.createElement('div');
    userBubble.className = 'chat-bubble chat-bubble-user';
    userBubble.textContent = text;
    messagesEl.appendChild(userBubble);
    messagesEl.scrollTop = messagesEl.scrollHeight;

    // Add typing indicator
    const typingBubble = document.createElement('div');
    typingBubble.className = 'chat-bubble chat-bubble-agent';
    typingBubble.innerHTML = '<em>Kura is thinking...</em>';
    messagesEl.appendChild(typingBubble);
    messagesEl.scrollTop = messagesEl.scrollHeight;

    try {
      const response = await agentApi.chat(text);
      typingBubble.remove();

      const agentBubble = document.createElement('div');
      agentBubble.className = 'chat-bubble chat-bubble-agent';
      agentBubble.innerHTML = this.formatReply(response.reply);
      messagesEl.appendChild(agentBubble);

      // Append wellbeing suggestions if any
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
        quickRepliesEl.innerHTML = response.quickReplies.map(q => `
          <button class="quick-reply-chip" data-msg="${q}">${q}</button>
        `).join('');
        this.attachQuickReplyListeners();
      }

      messagesEl.scrollTop = messagesEl.scrollHeight;
    } catch (err) {
      typingBubble.remove();
      const errBubble = document.createElement('div');
      errBubble.className = 'chat-bubble chat-bubble-agent';
      errBubble.style.borderColor = '#fca5a5';
      errBubble.style.color = '#991b1b';
      errBubble.textContent = err.message || 'Sorry, I encountered an error.';
      messagesEl.appendChild(errBubble);
      messagesEl.scrollTop = messagesEl.scrollHeight;
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
