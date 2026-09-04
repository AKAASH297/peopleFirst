import { FormatUtils } from '../utils/formatUtils.js';
import { DateUtils } from '../utils/dateUtils.js';

export const LeaveCard = {
  renderBalanceCard(balance) {
    return `
      <div class="balance-card">
        <div class="balance-card-type">${balance.leaveTypeDisplayName}</div>
        <div class="balance-card-days">${FormatUtils.formatDays(balance.remainingDays)}</div>
        <div class="balance-card-meta">
          <span>Used: <strong>${FormatUtils.formatDays(balance.usedDays)}</strong></span>
          <span>Pending: <strong>${FormatUtils.formatDays(balance.pendingDays)}</strong></span>
          <span>Alloc: <strong>${FormatUtils.formatDays(balance.allocatedDays)}</strong></span>
        </div>
      </div>
    `;
  },

  renderLeaveRow(leave, actionsHtml = null) {
    return `
      <tr>
        <td>
          <div style="font-weight: 600;">${leave.leaveTypeDisplayName}</div>
          ${leave.combinedWithType ? `<div style="font-size:0.75rem; color:var(--primary);">+ Combined with ${leave.combinedWithType}</div>` : ''}
        </td>
        <td>
          <div>${DateUtils.formatDate(leave.startDate)} &rarr; ${DateUtils.formatDate(leave.endDate)}</div>
          <div style="font-size:0.75rem; color:var(--text-muted);">${leave.totalDays} day${leave.totalDays > 1 ? 's' : ''} ${leave.halfDay ? '(Half-Day)' : ''}</div>
        </td>
        <td>${FormatUtils.getStatusBadge(leave.status)}</td>
        <td style="max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">
          ${leave.reason || '—'}
        </td>
        ${actionsHtml !== null ? `<td style="text-align: right;">${actionsHtml}</td>` : ''}
      </tr>
    `;
  }
};
