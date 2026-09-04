package com.peoplefirst.ticket.controller;

import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.ticket.dto.CreateTicketRequestDto;
import com.peoplefirst.ticket.dto.TicketResponseDto;
import com.peoplefirst.ticket.service.TicketService;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final CurrentUserProvider currentUserProvider;

    public TicketController(TicketService ticketService, CurrentUserProvider currentUserProvider) {
        this.ticketService = ticketService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<TicketResponseDto> createTicket(@Valid @RequestBody CreateTicketRequestDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        TicketResponseDto response = ticketService.createTicket(dto, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TicketResponseDto>> getTickets(@RequestParam(required = false) String scope) {
        User currentUser = currentUserProvider.getCurrentUser();
        if ("all".equalsIgnoreCase(scope) && currentUser.getRole() == Role.ADMIN) {
            return ResponseEntity.ok(ticketService.getAllTickets(currentUser));
        }
        return ResponseEntity.ok(ticketService.getTicketsForUser(currentUser.getId()));
    }
}
