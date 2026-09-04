package com.peoplefirst.auth.controller;

import com.peoplefirst.auth.dto.LoginRequestDto;
import com.peoplefirst.auth.dto.LoginResponseDto;
import com.peoplefirst.auth.dto.RefreshTokenRequestDto;
import com.peoplefirst.auth.security.CurrentUserProvider;
import com.peoplefirst.auth.service.AuthService;
import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;
    private final UserMapper userMapper;

    public AuthController(AuthService authService,
                          CurrentUserProvider currentUserProvider,
                          UserMapper userMapper) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        LoginResponseDto response = authService.login(loginRequestDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDto> refresh(@Valid @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        LoginResponseDto response = authService.refreshToken(refreshTokenRequestDto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getCurrentUserProfile() {
        User currentUser = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(userMapper.toDto(currentUser));
    }
}
