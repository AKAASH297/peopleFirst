package com.peoplefirst.policy.dto;

import com.peoplefirst.policy.entity.LeaveType;

public class LeaveEligibilityDto {

    private LeaveType leaveType;
    private String displayName;
    private double employeeAnnualQuota;
    private double contractorAnnualQuota;
    private boolean employeeEligible;
    private boolean contractorEligible;

    public LeaveEligibilityDto() {
    }

    public LeaveEligibilityDto(LeaveType leaveType, String displayName, double employeeAnnualQuota,
                               double contractorAnnualQuota, boolean employeeEligible, boolean contractorEligible) {
        this.leaveType = leaveType;
        this.displayName = displayName;
        this.employeeAnnualQuota = employeeAnnualQuota;
        this.contractorAnnualQuota = contractorAnnualQuota;
        this.employeeEligible = employeeEligible;
        this.contractorEligible = contractorEligible;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(LeaveType leaveType) {
        this.leaveType = leaveType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getEmployeeAnnualQuota() {
        return employeeAnnualQuota;
    }

    public void setEmployeeAnnualQuota(double employeeAnnualQuota) {
        this.employeeAnnualQuota = employeeAnnualQuota;
    }

    public double getContractorAnnualQuota() {
        return contractorAnnualQuota;
    }

    public void setContractorAnnualQuota(double contractorAnnualQuota) {
        this.contractorAnnualQuota = contractorAnnualQuota;
    }

    public boolean isEmployeeEligible() {
        return employeeEligible;
    }

    public void setEmployeeEligible(boolean employeeEligible) {
        this.employeeEligible = employeeEligible;
    }

    public boolean isContractorEligible() {
        return contractorEligible;
    }

    public void setContractorEligible(boolean contractorEligible) {
        this.contractorEligible = contractorEligible;
    }
}
