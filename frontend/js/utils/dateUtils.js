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

  getTomorrowStr() {
    return this.formatDateISO(this.addDays(new Date(), 1));
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

  isWeekend(dateStr) {
    if (!dateStr) return false;
    const d = this.parseLocalDate(dateStr);
    if (!d || isNaN(d.getTime())) return false;
    const day = d.getDay();
    return day === 0 || day === 6; // 0 = Sunday, 6 = Saturday
  },

  calculateDays(startDateStr, endDateStr, isHalfDay = false) {
    if (!startDateStr || !endDateStr) return 0;
    if (isHalfDay) return 0.5;
    const d1 = this.parseLocalDate(startDateStr);
    const d2 = this.parseLocalDate(endDateStr);
    if (!d1 || !d2 || isNaN(d1.getTime()) || isNaN(d2.getTime())) return 0;
    if (d2 < d1) return 0;

    let workingDays = 0;
    let curr = new Date(d1.getTime());
    while (curr <= d2) {
      const day = curr.getDay();
      if (day !== 0 && day !== 6) {
        workingDays++;
      }
      curr.setDate(curr.getDate() + 1);
    }
    return workingDays;
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
