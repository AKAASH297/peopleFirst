package com.peoplefirst.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public class CreateTicketRequestDto {

    @NotBlank(message = "Ticket type is required")
    private String ticketType;

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotBlank(message = "Description is required")
    private String description;

    private UUID relatedLeaveId;

    public CreateTicketRequestDto() {
    }

    public CreateTicketRequestDto(String ticketType, String subject, String description, UUID relatedLeaveId) {
        this.ticketType = ticketType;
        this.subject = subject;
        this.description = description;
        this.relatedLeaveId = relatedLeaveId;
    }

    public String getTicketType() {
        return ticketType;
    }

    public void setTicketType(String ticketType) {
        this.ticketType = ticketType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getRelatedLeaveId() {
        return relatedLeaveId;
    }

    public void setRelatedLeaveId(UUID relatedLeaveId) {
        this.relatedLeaveId = relatedLeaveId;
    }
}
