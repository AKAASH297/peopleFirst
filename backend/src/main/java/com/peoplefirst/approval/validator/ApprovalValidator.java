package com.peoplefirst.approval.validator;

import com.peoplefirst.leave.entity.LeaveRequest;
import com.peoplefirst.leave.entity.LeaveStatus;
import com.peoplefirst.policy.validator.PolicyViolationException;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class ApprovalValidator {

    public void validateApprovalAuthority(LeaveRequest leaveRequest, User leaveOwner, User approver) {
        // Criterion 11: Admin (and any user) cannot approve their own leave
        if (leaveOwner.getId().equals(approver.getId())) {
            throw new PolicyViolationException("Self-approval is strictly prohibited. Users and Admins cannot approve their own leave.");
        }

        // Leave must be PENDING
        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new PolicyViolationException("Approval actions can only be performed on PENDING requests. Current status: " + leaveRequest.getStatus());
        }

        if (approver.getRole() == Role.ADMIN) {
            // Admins can approve on behalf of manager, but if leave owner is also an Admin, must be a different Admin
            return;
        }

        if (approver.getRole() == Role.MANAGER) {
            // Manager can ONLY approve direct reportees
            if (leaveOwner.getManagerId() == null || !leaveOwner.getManagerId().equals(approver.getId())) {
                throw new AccessDeniedException("Managers can only approve or reject leave requests for their direct reportees.");
            }
            return;
        }

        throw new AccessDeniedException("You do not have authorization to act on leave requests.");
    }
}
