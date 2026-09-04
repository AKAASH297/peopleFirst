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
              <div style="font-weight: 700; font-size: 0.9375rem;">Kura AI Concierge</div>
              <div style="font-size: 0.6875rem; opacity: 0.9;">Google Gemini GenAI + Grounded Policies</div>
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
          <button class="quick-reply-chip" data-msg="Company leave policies">Policies</button>
          <button class="quick-reply-chip" data-msg="Campus amenities">Amenities</button>
        </div>
        <form id="kuraChatForm" class="chat-input-row">
          <input id="kuraChatInput" type="text" class="chat-input" placeholder="Message Kura..." autocomplete="off" />
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
          sugCard.style.cssText = 'background:#f0fdf4; border:1px solid #bbf7d0; border-radius:8px; padding:8px 12px; margin-top:6px; font-size:0.8125rem;';
          sugCard.innerHTML = `
            <div style="font-weight:600; color:#166534;">🌿 ${sug.title}</div>
            <div style="color:#1e293b; margin-top:3px;">${sug.message}</div>
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
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br/>');
  }
};
