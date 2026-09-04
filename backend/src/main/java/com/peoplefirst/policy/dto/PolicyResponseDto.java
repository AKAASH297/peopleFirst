package com.peoplefirst.policy.dto;

import com.peoplefirst.policy.entity.LeaveType;

import java.util.List;

public class PolicyResponseDto {

    private String companyName = "peopleFirst";
    private String version = "2026.1";
    private List<String> generalRules;
    private List<String> deadlineRules;
    private List<String> combinationRules;
    private List<LeaveEligibilityDto> leaveTypes;

    public PolicyResponseDto() {
    }

    public PolicyResponseDto(List<String> generalRules, List<String> deadlineRules,
                             List<String> combinationRules, List<LeaveEligibilityDto> leaveTypes) {
        this.generalRules = generalRules;
        this.deadlineRules = deadlineRules;
        this.combinationRules = combinationRules;
        this.leaveTypes = leaveTypes;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getGeneralRules() {
        return generalRules;
    }

    public void setGeneralRules(List<String> generalRules) {
        this.generalRules = generalRules;
    }

    public List<String> getDeadlineRules() {
        return deadlineRules;
    }

    public void setDeadlineRules(List<String> deadlineRules) {
        this.deadlineRules = deadlineRules;
    }

    public List<String> getCombinationRules() {
        return combinationRules;
    }

    public void setCombinationRules(List<String> combinationRules) {
        this.combinationRules = combinationRules;
    }

    public List<LeaveEligibilityDto> getLeaveTypes() {
        return leaveTypes;
    }

    public void setLeaveTypes(List<LeaveEligibilityDto> leaveTypes) {
        this.leaveTypes = leaveTypes;
    }
}
