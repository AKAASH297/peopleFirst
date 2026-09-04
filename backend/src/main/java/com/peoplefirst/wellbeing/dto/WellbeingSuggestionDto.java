package com.peoplefirst.wellbeing.dto;

import java.util.List;

public class WellbeingSuggestionDto {

    private String trigger;
    private String title;
    private String message;
    private String actionType; // PROMPT, LINK, SUGGESTION, NUDGE
    private String actionUrl;
    private List<HospitalPartnerDto> partnerHospitals;
    private List<ResortPartnerDto> partnerResorts;
    private List<String> groupSuggestions;

    public WellbeingSuggestionDto() {
    }

    public WellbeingSuggestionDto(String trigger, String title, String message, String actionType) {
        this.trigger = trigger;
        this.title = title;
        this.message = message;
        this.actionType = actionType;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public List<HospitalPartnerDto> getPartnerHospitals() {
        return partnerHospitals;
    }

    public void setPartnerHospitals(List<HospitalPartnerDto> partnerHospitals) {
        this.partnerHospitals = partnerHospitals;
    }

    public List<ResortPartnerDto> getPartnerResorts() {
        return partnerResorts;
    }

    public void setPartnerResorts(List<ResortPartnerDto> partnerResorts) {
        this.partnerResorts = partnerResorts;
    }

    public List<String> getGroupSuggestions() {
        return groupSuggestions;
    }

    public void setGroupSuggestions(List<String> groupSuggestions) {
        this.groupSuggestions = groupSuggestions;
    }
}
