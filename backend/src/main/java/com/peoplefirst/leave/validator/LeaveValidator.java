package com.peoplefirst.leave.validator;

import com.peoplefirst.policy.validator.PolicyViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class LeaveValidator {

    public double calculateTotalDays(LocalDate startDate, LocalDate endDate, boolean isHalfDay) {
        if (startDate == null || endDate == null) {
            throw new PolicyViolationException("Start date and end date must not be null.");
        }

        if (endDate.isBefore(startDate)) {
            throw new PolicyViolationException("End date cannot be before start date.");
        }

        if (startDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || startDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY ||
                endDate.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || endDate.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            throw new PolicyViolationException("Leaves cannot be applied on weekends (Saturday or Sunday). Please select working days (Monday to Friday).");
        }

        if (isHalfDay) {
            if (!startDate.equals(endDate)) {
                throw new PolicyViolationException("Half-day leave can only be applied for a single day.");
            }
            return 0.5;
        }

        long workingDays = 0;
        LocalDate curr = startDate;
        while (!curr.isAfter(endDate)) {
            if (curr.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && curr.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                workingDays++;
            }
            curr = curr.plusDays(1);
        }

        if (workingDays == 0) {
            throw new PolicyViolationException("Leaves cannot be applied on weekends (Saturday or Sunday). Please select working days (Monday to Friday).");
        }

        return (double) workingDays;
    }

    public void validateNoDateOverlap(java.util.List<com.peoplefirst.leave.entity.LeaveRequest> existingLeaves,
                                      java.util.UUID excludeLeaveId,
                                      LocalDate newStart,
                                      LocalDate newEnd,
                                      boolean newIsHalfDay,
                                      String newSession) {
        if (existingLeaves == null || existingLeaves.isEmpty()) {
            return;
        }

        for (com.peoplefirst.leave.entity.LeaveRequest existing : existingLeaves) {
            if (excludeLeaveId != null && existing.getId().equals(excludeLeaveId)) {
                continue;
            }

            // An overlap occurs if neither range is completely before or after the other
            boolean rangesOverlap = !(newEnd.isBefore(existing.getStartDate()) || newStart.isAfter(existing.getEndDate()));
            if (!rangesOverlap) {
                continue;
            }

            // Special case: both are half-days on the exact same date with different sessions
            if (newIsHalfDay && existing.isHalfDay() &&
                    newStart.equals(newEnd) && existing.getStartDate().equals(existing.getEndDate()) &&
                    newStart.equals(existing.getStartDate())) {
                String existingSession = existing.getHalfDaySession() != null ? existing.getHalfDaySession().toUpperCase() : "";
                String candidateSession = newSession != null ? newSession.toUpperCase() : "";
                if (!existingSession.isEmpty() && !candidateSession.isEmpty() && !existingSession.equals(candidateSession)) {
                    continue;
                }
            }

            throw new PolicyViolationException(
                    "You already have an active " + existing.getLeaveType().getDisplayName() +
                            " (" + existing.getStatus() + ") scheduled from " + existing.getStartDate() +
                            " to " + existing.getEndDate() + ". Overlapping leave requests on the same date are not permitted."
            );
        }
    }
}
