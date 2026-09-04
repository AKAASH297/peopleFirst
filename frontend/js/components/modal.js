export const Modal = {
  show({ title, content, body, buttons = [] }) {
    this.close();

    const backdrop = document.createElement('div');
    backdrop.id = 'activeModalBackdrop';
    backdrop.className = 'modal-backdrop';

    const modalBody = content !== undefined ? content : (body !== undefined ? body : '');

    const btnHtml = buttons.map(b => `
      <button id="${b.id}" class="btn ${b.className || 'btn-secondary'}">${b.text}</button>
    `).join('');

    backdrop.innerHTML = `
      <div class="modal-content" style="max-width: 650px; width: 90%;">
        <div class="modal-header">
          <h3 style="font-size: 1.125rem; font-weight: 600;">${title}</h3>
          <button id="modalCloseBtn" style="background:none; border:none; font-size:1.25rem; cursor:pointer; color:var(--text-muted);">&times;</button>
        </div>
        <div class="modal-body">${modalBody}</div>
        ${buttons.length ? `<div class="modal-footer">${btnHtml}</div>` : ''}
      </div>
    `;

    document.body.appendChild(backdrop);

    document.getElementById('modalCloseBtn')?.addEventListener('click', () => this.close());
    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) this.close();
    });

    buttons.forEach(b => {
      const el = document.getElementById(b.id);
      if (el && b.onClick) {
        el.addEventListener('click', (ev) => b.onClick(ev, this));
      }
    });
  },

  close() {
    const existing = document.getElementById('activeModalBackdrop');
    if (existing) {
      existing.remove();
    }
  }
};
