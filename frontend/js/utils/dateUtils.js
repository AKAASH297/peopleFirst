export const DateUtils = {
  parseLocalDate(dateStr) {
    if (!dateStr) return null;
    const parts = dateStr.split('-');
    if (parts.length !== 3) return null;
    const y = parseInt(parts[0], 10);
    const m = parseInt(parts[1], 10) - 1;
    const d = parseInt(parts[2], 10);
    return new Date(y, m, d);
  },

  formatDateISO(date) {
    if (!date || isNaN(date.getTime())) return '';
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  },

  addDays(date, days) {
    const res = new Date(date.getTime());
    res.setDate(res.getDate() + days);
    return res;
  },

  getTodayStr() {
    return this.formatDateISO(new Date());
  },

  formatDate(dateStr) {
    if (!dateStr) return '—';
    const d = this.parseLocalDate(dateStr);
    if (!d || isNaN(d.getTime())) return dateStr;
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  },

  formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '—';
    const d = new Date(dateTimeStr);
    if (isNaN(d.getTime())) return dateTimeStr;
    return d.toLocaleDateString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  },

  calculateDays(startDateStr, endDateStr, isHalfDay = false) {
    if (!startDateStr || !endDateStr) return 0;
    if (isHalfDay) return 0.5;
    const p1 = startDateStr.split('-').map(Number);
    const p2 = endDateStr.split('-').map(Number);
    if (p1.length !== 3 || p2.length !== 3 || isNaN(p1[0]) || isNaN(p2[0])) return 0;
    const utc1 = Date.UTC(p1[0], p1[1] - 1, p1[2]);
    const utc2 = Date.UTC(p2[0], p2[1] - 1, p2[2]);
    if (utc2 < utc1) return 0;
    return Math.round((utc2 - utc1) / (1000 * 60 * 60 * 24)) + 1;
  },

  isBeforeToday(dateStr) {
    if (!dateStr) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = this.parseLocalDate(dateStr);
    if (!target) return false;
    return target < today;
  }
};
