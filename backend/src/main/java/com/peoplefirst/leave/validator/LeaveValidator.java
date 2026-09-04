package com.peoplefirst.leave.validator;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.leave.repository.LeaveRequestRepository;
import com.peoplefirst.policy.validator.PolicyViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class LeaveValidator {

    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveValidator(LeaveRequestRepository leaveRequestRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
    }

    public double calculateTotalDays(LocalDate startDate, LocalDate endDate, boolean isHalfDay) {
        if (startDate == null || endDate == null) {
            throw new PolicyViolationException("Start date and end date must not be null.");
        }

        if (endDate.isBefore(startDate)) {
            throw new PolicyViolationException("End date cannot be before start date.");
        }

        if (isHalfDay) {
            if (!startDate.equals(endDate)) {
                throw new PolicyViolationException("Half-day leave can only be applied for a single day.");
            }
            return 0.5;
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return (double) daysBetween;
    }

    public void validateNoOverlap(UUID userId, LocalDate start, LocalDate end,
            boolean isHalfDay, String halfDaySession, UUID excludeLeaveId) {
        List<LeaveRequest> clashes = leaveRequestRepository
            .findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), end, start);
        for (LeaveRequest c : clashes) {
            if (excludeLeaveId != null && excludeLeaveId.equals(c.getId())) continue;
            if (isHalfDay && c.isHalfDay()
                    && halfDaySession != null && !halfDaySession.equals(c.getHalfDaySession())
                    && start.equals(c.getStartDate())) continue; // complementary halves share the day
            throw new PolicyViolationException(
                "This overlaps your " + c.getLeaveType().getDisplayName() + " (" +
                c.getStartDate() + " to " + c.getEndDate() + ", " + c.getStatus() + ").");
        }
    }
}
