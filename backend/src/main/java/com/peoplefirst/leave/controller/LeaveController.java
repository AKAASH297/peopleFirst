package com.peoplefirst.leave.controller;

import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.leave.dto.*;
import com.peoplefirst.leave.entity.LeaveBalance;
import com.peoplefirst.leave.mapper.LeaveMapper;
import com.peoplefirst.leave.service.LeaveBalanceService;
import com.peoplefirst.leave.service.LeaveService;
import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;
    private final LeaveBalanceService leaveBalanceService;
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;
    private final LeaveMapper leaveMapper;

    public LeaveController(LeaveService leaveService,
                           LeaveBalanceService leaveBalanceService,
                           CurrentUserProvider currentUserProvider,
                           UserService userService,
                           LeaveMapper leaveMapper) {
        this.leaveService = leaveService;
        this.leaveBalanceService = leaveBalanceService;
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
        this.leaveMapper = leaveMapper;
    }

    @PostMapping
    public ResponseEntity<LeaveResponseDto> applyLeave(@Valid @RequestBody CreateLeaveRequestDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = leaveService.applyLeave(dto, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<LeaveResponseDto>> getCurrentUserLeaves() {
        User currentUser = currentUserProvider.getCurrentUser();
        List<LeaveResponseDto> leaves = leaveService.getLeavesForUser(currentUser.getId());
        return ResponseEntity.ok(leaves);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LeaveResponseDto> getLeaveById(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto leave = leaveService.getLeaveById(id);

        // Access check
        if (currentUser.getRole() != Role.ADMIN && !currentUser.getId().equals(leave.getUserId())) {
            User leaveOwner = userService.getUserEntityById(leave.getUserId());
            if (!currentUser.getId().equals(leaveOwner.getManagerId())) {
                throw new AccessDeniedException("You are not authorized to view this leave request.");
            }
        }
        return ResponseEntity.ok(leave);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LeaveResponseDto> editLeave(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateLeaveRequestDto dto) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = leaveService.editLeave(id, dto, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<LeaveResponseDto> cancelLeave(@PathVariable UUID id,
                                                        @RequestParam(required = false) String comment) {
        User currentUser = currentUserProvider.getCurrentUser();
        LeaveResponseDto response = leaveService.cancelLeave(id, currentUser, comment);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balances")
    public ResponseEntity<List<LeaveBalanceDto>> getLeaveBalances(@RequestParam(required = false) String scope) {
        User currentUser = currentUserProvider.getCurrentUser();
        int currentYear = LocalDate.now().getYear();

        if ("reportees".equalsIgnoreCase(scope)) {
            if (currentUser.getRole() != Role.MANAGER && currentUser.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Only managers and admins can view reportee balances.");
            }
            List<UserResponseDto> reportees = userService.getDirectReportees(currentUser.getId());
            List<UUID> reporteeIds = reportees.stream().map(UserResponseDto::getId).collect(Collectors.toList());
            List<LeaveBalanceDto> dtos = new ArrayList<>();
            for (UUID uid : reporteeIds) {
                User u = userService.getUserEntityById(uid);
                leaveBalanceService.initializeUserBalancesIfAbsent(u, currentYear);
                List<LeaveBalance> balances = leaveBalanceService.getUserBalances(uid, currentYear);
                for (LeaveBalance b : balances) {
                    dtos.add(leaveMapper.toBalanceDto(b, u));
                }
            }
            return ResponseEntity.ok(dtos);
        } else if ("all".equalsIgnoreCase(scope)) {
            if (currentUser.getRole() != Role.ADMIN) {
                throw new AccessDeniedException("Only admins can view organization-wide balances.");
            }
            List<UserResponseDto> allUsers = userService.getAllUsers();
            List<LeaveBalanceDto> dtos = new ArrayList<>();
            for (UserResponseDto uDto : allUsers) {
                User u = userService.getUserEntityById(uDto.getId());
                leaveBalanceService.initializeUserBalancesIfAbsent(u, currentYear);
                List<LeaveBalance> balances = leaveBalanceService.getUserBalances(u.getId(), currentYear);
                for (LeaveBalance b : balances) {
                    dtos.add(leaveMapper.toBalanceDto(b, u));
                }
            }
            return ResponseEntity.ok(dtos);
        } else {
            leaveBalanceService.initializeUserBalancesIfAbsent(currentUser, currentYear);
            List<LeaveBalance> balances = leaveBalanceService.getUserBalances(currentUser.getId(), currentYear);
            List<LeaveBalanceDto> dtos = balances.stream()
                    .map(b -> leaveMapper.toBalanceDto(b, currentUser))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(dtos);
        }
    }
}
