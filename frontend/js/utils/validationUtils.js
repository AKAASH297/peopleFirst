import { DateUtils } from './dateUtils.js';

export const ValidationUtils = {
  validateLeaveForm({ leaveType, combinedWithType, startDate, endDate, totalDays, isHalfDay, documentAttached, isContractor }) {
    const errors = [];

    if (!leaveType) {
      errors.push('Please select a leave type.');
    }

    if (!startDate || !endDate) {
      errors.push('Start date and end date are required.');
    }

    if (startDate && endDate) {
      const dStart = DateUtils.parseLocalDate(startDate);
      const dEnd = DateUtils.parseLocalDate(endDate);
      if (dStart && dEnd && dEnd < dStart) {
        errors.push('End date cannot be earlier than start date.');
      }
    }

    if (startDate) {
      const dStart = DateUtils.parseLocalDate(startDate);
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      if (dStart && dStart <= today) {
        errors.push("Leaves must be applied before the actual leave date. You can't apply leave for today or backdate.");
      }
    }

    // Contractor checks
    if (isContractor) {
      if (['CASUAL', 'WFH', 'MATERNITY', 'VOLUNTEERING'].includes(leaveType)) {
        errors.push(`Contractors are not eligible for ${leaveType}. Eligible types: Sick, Paid, LOP.`);
      }
      if (combinedWithType) {
        errors.push('Contractors do not have permission to combine leave types.');
      }
    }

    // Casual combination rule
    if (leaveType === 'CASUAL' && combinedWithType && combinedWithType !== 'WFH') {
      errors.push('Casual Leave may only be combined with WFH. Other combinations are strictly prohibited.');
    }

    // Sick leave > 2 days documentation requirement
    if (leaveType === 'SICK' && totalDays > 2 && !documentAttached) {
      errors.push('Medical documentation is mandatory for Sick Leave exceeding 2 days.');
    }

    // Paid leave notice (> 2 days)
    if (leaveType === 'PAID' && startDate) {
      const today = new Date();
      today.setHours(0, 0, 0, 0);
      const minNoticeDate = new Date(today);
      minNoticeDate.setDate(minNoticeDate.getDate() + 2);

      const chosenStart = DateUtils.parseLocalDate(startDate);
      if (chosenStart && chosenStart <= minNoticeDate) {
        errors.push('Paid Leave requires more than 2 days advance notice (start date must be at least 3 days from today).');
      }
    }

    // Weekend check
    if (DateUtils.isWeekend(startDate) || DateUtils.isWeekend(endDate) || totalDays === 0) {
      errors.push('Leaves cannot be applied on weekends (Saturday or Sunday). Please select working days (Monday to Friday).');
    }

    return {
      isValid: errors.length === 0,
      errors
    };
  }
};
