package com.peoplefirst.wellbeing.dto;

import java.time.LocalDateTime;
import java.util.List;

public class VacationEmailDto {

    private String recipientEmail;
    private String employeeName;
    private String subject;
    private String content;
    private LocalDateTime sentAt;
    private List<ResortPartnerDto> suggestedResorts;

    public VacationEmailDto() {
    }

    public VacationEmailDto(String recipientEmail, String employeeName, String subject, String content, LocalDateTime sentAt, List<ResortPartnerDto> suggestedResorts) {
        this.recipientEmail = recipientEmail;
        this.employeeName = employeeName;
        this.subject = subject;
        this.content = content;
        this.sentAt = sentAt;
        this.suggestedResorts = suggestedResorts;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public List<ResortPartnerDto> getSuggestedResorts() {
        return suggestedResorts;
    }

    public void setSuggestedResorts(List<ResortPartnerDto> suggestedResorts) {
        this.suggestedResorts = suggestedResorts;
    }
}
