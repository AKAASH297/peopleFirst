package com.peoplefirst.policy.validator;

import com.peoplefirst.policy.entity.LeaveType;
import com.peoplefirst.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Component
public class PolicyValidator {

    /**
     * Validates all leave application constraints per SPEC.md §2, §3, §4.
     */
    public void validateLeaveApplication(User user, LeaveType leaveType, LeaveType combinedWithType,
                                         LocalDate startDate, LocalDate endDate, double totalDays,
                                         boolean documentAttached, String documentUrl, LocalDate appliedDate) {

        if (appliedDate == null) {
            appliedDate = LocalDate.now();
        }

        if (startDate == null || endDate == null) {
            throw new PolicyViolationException("Start date and end date are required.");
        }

        if (endDate.isBefore(startDate)) {
            throw new PolicyViolationException("End date cannot be earlier than start date.");
        }

        // SPEC.md §5: Apply / cancel / edit leave before the actual leave date.
        // Leave cannot be applied normally after the leave date has already passed.
        if (startDate.isBefore(appliedDate)) {
            throw new PolicyViolationException(
                    "Leave cannot be applied retroactively after the leave date has passed. Please raise a support ticket instead.");
        }

        // SPEC.md §2: Role eligibility
        if (!leaveType.isEligibleForUser(user.isContractor())) {
            throw new PolicyViolationException(
                    (user.isContractor() ? "Contractors" : "Employees") +
                            " are not eligible for " + leaveType.getDisplayName() +
                            ". Eligible types: Sick Leave, Paid Leave, Loss of Pay (LOP).");
        }

        // SPEC.md §3: Combination rules
        if (combinedWithType != null) {
            // Contractors have NO combination rights at all
            if (user.isContractor()) {
                throw new PolicyViolationException("Contractors do not have access to apply combinations of different leave types.");
            }

            // Casual Leave may only be combined with WFH. No other combination involving Casual Leave is allowed.
            if (leaveType == LeaveType.CASUAL) {
                if (combinedWithType != LeaveType.WFH) {
                    throw new PolicyViolationException("Casual Leave may only be combined with WFH. Other combinations are rejected.");
                }
            } else if (combinedWithType == LeaveType.CASUAL) {
                if (leaveType != LeaveType.WFH) {
                    throw new PolicyViolationException("Casual Leave may only be combined with WFH. Other combinations are rejected.");
                }
            }
        }

        // SPEC.md §4.2: Sick Leave for more than 2 days at a time -> medical documents mandatory
        if (leaveType == LeaveType.SICK && totalDays > 2.0) {
            if (!documentAttached || documentUrl == null || documentUrl.trim().isEmpty()) {
                throw new PolicyViolationException("Medical documents are mandatory for Sick Leave exceeding 2 days.");
            }
        }

        // SPEC.md §4.3: Paid Leave is only valid if actual leave start date is more than 2 days after application date (applied on + 2 < startDate)
        if (leaveType == LeaveType.PAID) {
            LocalDate minimumAllowedStartDate = appliedDate.plusDays(2);
            if (!startDate.isAfter(minimumAllowedStartDate)) {
                throw new PolicyViolationException(
                        "Paid Leave requires advance notice of more than 2 days. For application on " +
                                appliedDate + ", start date must be on or after " + minimumAllowedStartDate.plusDays(1) + ".");
            }
        }

        // SPEC.md §4.4: Casual / WFH requests must be submitted by end of the current week; once that window passes, raise support ticket
        if (leaveType == LeaveType.CASUAL || leaveType == LeaveType.WFH || combinedWithType == LeaveType.WFH || combinedWithType == LeaveType.CASUAL) {
            LocalDate currentWeekSunday = appliedDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            LocalDate currentWeekMonday = appliedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            // If the leave falls in the current week, it must be submitted before the end of the current week (Sunday)
            if (startDate.isBefore(currentWeekMonday)) {
                throw new PolicyViolationException(
                        "Casual / WFH requests must be submitted by end of the current week. That window has passed; please raise a support ticket instead.");
            }
        }

        // SPEC.md §4.5: Sick / Paid / LOP requests must be submitted on or before the 25th of the current month; after the 25th, raise support ticket
        if (leaveType == LeaveType.SICK || leaveType == LeaveType.PAID || leaveType == LeaveType.LOP) {
            if (startDate.getYear() == appliedDate.getYear() && startDate.getMonth() == appliedDate.getMonth()) {
                if (appliedDate.getDayOfMonth() > 25) {
                    throw new PolicyViolationException(
                            "Sick, Paid, and LOP requests for the current month must be submitted on or before the 25th. Please raise a support ticket instead.");
                }
            } else if (startDate.isBefore(appliedDate.withDayOfMonth(1))) {
                throw new PolicyViolationException(
                        "Leave requests for previous months cannot be applied normally. Please raise a support ticket instead.");
            }
        }
    }

    /**
     * Validates that an edit or cancellation occurs strictly BEFORE the leave start date.
     */
    public void validateActionBeforeStartDate(LocalDate startDate, String actionName) {
        if (!LocalDate.now().isBefore(startDate)) {
            throw new PolicyViolationException(
                    "Cannot " + actionName + " leave after or on the leave start date (" + startDate +
                            "). Please raise a support ticket for post-date corrections.");
        }
    }
}
