package com.peoplefirst.policy.entity;

public enum LeaveType {
    CASUAL("Casual Leave", 12.0, 0.0, true, false),
    SICK("Sick Leave", 16.0, 16.0, true, true),
    PAID("Paid Leave", 20.0, 24.0, true, true),
    LOP("Loss of Pay", 180.0, 30.0, true, true),
    WFH("Work From Home", 24.0, 0.0, true, false),
    MATERNITY("Maternity Leave", 182.0, 0.0, true, false),
    VOLUNTEERING("Volunteering Leave", 2.0, 0.0, true, false);

    private final String displayName;
    private final double employeeAnnualQuota;
    private final double contractorAnnualQuota;
    private final boolean employeeEligible;
    private final boolean contractorEligible;

    LeaveType(String displayName, double employeeAnnualQuota, double contractorAnnualQuota,
              boolean employeeEligible, boolean contractorEligible) {
        this.displayName = displayName;
        this.employeeAnnualQuota = employeeAnnualQuota;
        this.contractorAnnualQuota = contractorAnnualQuota;
        this.employeeEligible = employeeEligible;
        this.contractorEligible = contractorEligible;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getEmployeeAnnualQuota() {
        return employeeAnnualQuota;
    }

    public double getContractorAnnualQuota() {
        return contractorAnnualQuota;
    }

    public boolean isEmployeeEligible() {
        return employeeEligible;
    }

    public boolean isContractorEligible() {
        return contractorEligible;
    }

    public double getDefaultQuotaForUser(boolean isContractor) {
        return isContractor ? contractorAnnualQuota : employeeAnnualQuota;
    }

    public boolean isEligibleForUser(boolean isContractor) {
        return isContractor ? contractorEligible : employeeEligible;
    }
}
