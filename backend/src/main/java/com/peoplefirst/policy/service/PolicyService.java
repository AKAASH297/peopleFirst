package com.peoplefirst.policy.service;

import com.peoplefirst.policy.dto.LeaveEligibilityDto;
import com.peoplefirst.policy.dto.PolicyResponseDto;
import com.peoplefirst.policy.entity.LeaveType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PolicyService {

    public PolicyResponseDto getCompanyPolicies() {
        List<String> generalRules = List.of(
                "Employees enjoy full access to web portal and Kura AI Concierge.",
                "Contractors interact exclusively through the Kura AI Agent interface; web portal login is restricted.",
                "Leave must be applied, edited, or cancelled strictly before the leave start date.",
                "Retroactive corrections and late submissions must be submitted via a Support Ticket.",
                "Managers can approve or reject leave requests only for their direct reportees.",
                "Admins can review leaves organization-wide, perform audited direct-DB edits, but cannot approve their own leave."
        );

        List<String> deadlineRules = List.of(
                "Sick Leave exceeding 2 consecutive days strictly requires medical documentation attached at the time of application.",
                "Paid Leave requires advance notice: the leave start date must be more than 2 days after the application date (appliedDate + 2 < startDate).",
                "Casual Leave and WFH requests must be submitted on or before the end of the current week (Sunday 23:59:59). Late requests require a support ticket.",
                "Sick, Paid, and LOP requests for the current month must be submitted on or before the 25th of the month. Subsequent requests require a support ticket."
        );

        List<String> combinationRules = List.of(
                "Casual Leave may only be combined with WFH. Any other combination involving Casual Leave is strictly rejected.",
                "Contractors have no combination rights and cannot combine different leave types in a single request."
        );

        List<LeaveEligibilityDto> leaveTypes = new ArrayList<>();
        for (LeaveType type : LeaveType.values()) {
            leaveTypes.add(new LeaveEligibilityDto(
                    type,
                    type.getDisplayName(),
                    type.getEmployeeAnnualQuota(),
                    type.getContractorAnnualQuota(),
                    type.isEmployeeEligible(),
                    type.isContractorEligible()
            ));
        }

        return new PolicyResponseDto(generalRules, deadlineRules, combinationRules, leaveTypes);
    }
}
