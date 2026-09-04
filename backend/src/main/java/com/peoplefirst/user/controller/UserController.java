package com.peoplefirst.user.controller;

import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    public UserController(UserService userService, CurrentUserProvider currentUserProvider) {
        this.userService = userService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/reportees")
    public ResponseEntity<List<UserResponseDto>> getDirectReportees() {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() != Role.MANAGER && currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only managers and admins can view reportees");
        }
        List<UserResponseDto> reportees = userService.getDirectReportees(currentUser.getId());
        return ResponseEntity.ok(reportees);
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only admins can view all users");
        }
        List<UserResponseDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable UUID id) {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN && !currentUser.getId().equals(id)) {
            // Check if user is a direct reportee of current manager
            User target = userService.getUserEntityById(id);
            if (!currentUser.getId().equals(target.getManagerId())) {
                throw new AccessDeniedException("You do not have permission to view this user");
            }
        }
        UserResponseDto user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
}
