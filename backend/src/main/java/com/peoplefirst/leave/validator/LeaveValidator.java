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

        if (isHalfDay) {
            if (!startDate.equals(endDate)) {
                throw new PolicyViolationException("Half-day leave can only be applied for a single day.");
            }
            return 0.5;
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return (double) daysBetween;
    }
}
