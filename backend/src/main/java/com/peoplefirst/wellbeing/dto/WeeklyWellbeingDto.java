package com.peoplefirst.wellbeing.dto;

import java.util.List;

public class WeeklyWellbeingDto {

    private String employeeName;
    private String baseLocation;
    private String status; // "HEALTHY", "RECHARGE_RECOMMENDED", "ACTION_REQUIRED"
    private String summary;
    private long leavesTakenThisMonth;
    private long leavesTakenLastQuarter;
    private boolean recentSickLeave;
    private String opdClaimReminder;
    private boolean vacationNudge;
    private List<AmenityDto> recommendedAmenities;
    private List<HospitalPartnerDto> suggestedHospitals;
    private List<ResortPartnerDto> suggestedResorts;
    private String insuranceClaimsPortalUrl;

    public WeeklyWellbeingDto() {
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getBaseLocation() {
        return baseLocation;
    }

    public void setBaseLocation(String baseLocation) {
        this.baseLocation = baseLocation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public long getLeavesTakenThisMonth() {
        return leavesTakenThisMonth;
    }

    public void setLeavesTakenThisMonth(long leavesTakenThisMonth) {
        this.leavesTakenThisMonth = leavesTakenThisMonth;
    }

    public long getLeavesTakenLastQuarter() {
        return leavesTakenLastQuarter;
    }

    public void setLeavesTakenLastQuarter(long leavesTakenLastQuarter) {
        this.leavesTakenLastQuarter = leavesTakenLastQuarter;
    }

    public boolean isRecentSickLeave() {
        return recentSickLeave;
    }

    public void setRecentSickLeave(boolean recentSickLeave) {
        this.recentSickLeave = recentSickLeave;
    }

    public String getOpdClaimReminder() {
        return opdClaimReminder;
    }

    public void setOpdClaimReminder(String opdClaimReminder) {
        this.opdClaimReminder = opdClaimReminder;
    }

    public boolean isVacationNudge() {
        return vacationNudge;
    }

    public void setVacationNudge(boolean vacationNudge) {
        this.vacationNudge = vacationNudge;
    }

    public List<AmenityDto> getRecommendedAmenities() {
        return recommendedAmenities;
    }

    public void setRecommendedAmenities(List<AmenityDto> recommendedAmenities) {
        this.recommendedAmenities = recommendedAmenities;
    }

    public List<HospitalPartnerDto> getSuggestedHospitals() {
        return suggestedHospitals;
    }

    public void setSuggestedHospitals(List<HospitalPartnerDto> suggestedHospitals) {
        this.suggestedHospitals = suggestedHospitals;
    }

    public List<ResortPartnerDto> getSuggestedResorts() {
        return suggestedResorts;
    }

    public void setSuggestedResorts(List<ResortPartnerDto> suggestedResorts) {
        this.suggestedResorts = suggestedResorts;
    }

    public String getInsuranceClaimsPortalUrl() {
        return insuranceClaimsPortalUrl;
    }

    public void setInsuranceClaimsPortalUrl(String insuranceClaimsPortalUrl) {
        this.insuranceClaimsPortalUrl = insuranceClaimsPortalUrl;
    }
}
