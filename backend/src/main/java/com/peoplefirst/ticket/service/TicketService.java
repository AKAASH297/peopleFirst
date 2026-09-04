package com.peoplefirst.ticket.service;

import com.peoplefirst.ticket.dto.CreateTicketRequestDto;
import com.peoplefirst.ticket.dto.TicketResponseDto;
import com.peoplefirst.ticket.entity.SupportTicket;
import com.peoplefirst.ticket.mapper.TicketMapper;
import com.peoplefirst.ticket.repository.SupportTicketRepository;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final UserService userService;
    private final TicketMapper ticketMapper;

    public TicketService(SupportTicketRepository supportTicketRepository,
                         UserService userService,
                         TicketMapper ticketMapper) {
        this.supportTicketRepository = supportTicketRepository;
        this.userService = userService;
        this.ticketMapper = ticketMapper;
    }

    @Transactional
    public TicketResponseDto createTicket(CreateTicketRequestDto dto, User user) {
        SupportTicket ticket = new SupportTicket(
                user.getId(),
                dto.getTicketType(),
                dto.getSubject(),
                dto.getDescription(),
                dto.getRelatedLeaveId()
        );
        SupportTicket saved = supportTicketRepository.save(ticket);
        return ticketMapper.toDto(saved, user);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDto> getTicketsForUser(UUID userId) {
        User user = userService.getUserEntityById(userId);
        return supportTicketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(t -> ticketMapper.toDto(t, user))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDto> getAllTickets(User adminUser) {
        if (adminUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can view all support tickets.");
        }
        return supportTicketRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(t -> {
                    User user = userService.getUserEntityById(t.getUserId());
                    return ticketMapper.toDto(t, user);
                })
                .collect(Collectors.toList());
    }
}
