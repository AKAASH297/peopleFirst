package com.peoplefirst.ticket.mapper;

import com.peoplefirst.ticket.dto.TicketResponseDto;
import com.peoplefirst.ticket.entity.SupportTicket;
import com.peoplefirst.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper {

    public TicketResponseDto toDto(SupportTicket ticket, User user) {
        if (ticket == null) {
            return null;
        }

        TicketResponseDto dto = new TicketResponseDto();
        dto.setId(ticket.getId());
        dto.setUserId(ticket.getUserId());
        if (user != null) {
            dto.setUserName(user.getFullName());
            dto.setUserEmail(user.getEmail());
        }
        dto.setTicketType(ticket.getTicketType());
        dto.setSubject(ticket.getSubject());
        dto.setDescription(ticket.getDescription());
        dto.setRelatedLeaveId(ticket.getRelatedLeaveId());
        dto.setStatus(ticket.getStatus());
        dto.setResolutionComment(ticket.getResolutionComment());
        dto.setCreatedAt(ticket.getCreatedAt());

        return dto;
    }
}
