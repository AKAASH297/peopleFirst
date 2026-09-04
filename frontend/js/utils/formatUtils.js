export const FormatUtils = {
  getStatusBadge(status) {
    switch (status) {
      case 'PENDING':
        return '<span class="badge badge-pending">Pending Approval</span>';
      case 'APPROVED':
        return '<span class="badge badge-approved">Approved</span>';
      case 'REJECTED':
        return '<span class="badge badge-rejected">Rejected</span>';
      case 'RETURNED':
        return '<span class="badge badge-returned">Returned / Send Back</span>';
      case 'CANCELLED':
        return '<span class="badge badge-cancelled">Cancelled</span>';
      default:
        return `<span class="badge">${status}</span>`;
    }
  },

  getRoleBadge(role, isContractor) {
    if (isContractor) {
      return '<span class="badge badge-returned" style="background:#fef3c7; color:#92400e;">Contractor</span>';
    }
    switch (role) {
      case 'ADMIN':
        return '<span class="badge badge-rejected" style="background:#fee2e2; color:#991b1b;">Admin</span>';
      case 'MANAGER':
        return '<span class="badge badge-approved" style="background:#e0e7ff; color:#3730a3;">Manager</span>';
      default:
        return '<span class="badge badge-approved" style="background:#f1f5f9; color:#475569;">Employee</span>';
    }
  }
};
