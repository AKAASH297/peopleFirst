package com.peoplefirst.auth;

import com.peoplefirst.auth.dto.LoginRequestDto;
import com.peoplefirst.auth.dto.LoginResponseDto;
import com.peoplefirst.auth.mapper.AuthMapper;
import com.peoplefirst.auth.security.JwtTokenProvider;
import com.peoplefirst.auth.service.AuthService;
import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.Role;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.mapper.UserMapper;
import com.peoplefirst.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthMapper authMapper;
    private AuthService authService;

    private User employee;
    private User contractor;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
        UserMapper userMapper = new UserMapper();
        authMapper = new AuthMapper(userMapper);

        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, authMapper);

        employee = new User("emp1", "emp1@test.com", "encodedPass", "Test Employee",
                Role.EMPLOYEE, false, "Eng", "Bangalore", UUID.randomUUID());
        employee.setId(UUID.randomUUID());

        contractor = new User("cont1", "cont1@test.com", "encodedPass", "Test Contractor",
                Role.CONTRACTOR, true, "Eng", "Bangalore", UUID.randomUUID());
        contractor.setId(UUID.randomUUID());

        when(passwordEncoder.matches("password123", "encodedPass")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), anyBoolean(), any())).thenReturn("mock-access-token");
        when(jwtTokenProvider.generateRefreshToken(any(), any())).thenReturn("mock-refresh-token");
    }

    @Test
    @DisplayName("Criterion 1: Contractor cannot reach the webpage under any login path — only the agent")
    void testContractorWebLoginDenied() {
        when(userRepository.findByUsername("cont1")).thenReturn(Optional.of(contractor));

        // Attempt login via WEB channel
        LoginRequestDto webRequest = new LoginRequestDto("cont1", "password123", "WEB");
        assertThrows(AccessDeniedException.class, () -> authService.login(webRequest));

        // Attempt login with no channel specified (defaults to WEB)
        LoginRequestDto defaultRequest = new LoginRequestDto("cont1", "password123", null);
        assertThrows(AccessDeniedException.class, () -> authService.login(defaultRequest));

        // Contractor CAN login via AGENT channel
        LoginRequestDto agentRequest = new LoginRequestDto("cont1", "password123", "AGENT");
        LoginResponseDto response = authService.login(agentRequest);
        assertNotNull(response);
        assertEquals("mock-access-token", response.getAccessToken());
    }

    @Test
    @DisplayName("Employee can login via WEB and AGENT")
    void testEmployeeWebAndAgentLogin() {
        when(userRepository.findByUsername("emp1")).thenReturn(Optional.of(employee));

        // Employee via WEB
        LoginRequestDto webRequest = new LoginRequestDto("emp1", "password123", "WEB");
        LoginResponseDto webResp = authService.login(webRequest);
        assertNotNull(webResp);

        // Employee via AGENT
        LoginRequestDto agentRequest = new LoginRequestDto("emp1", "password123", "AGENT");
        LoginResponseDto agentResp = authService.login(agentRequest);
        assertNotNull(agentResp);
    }
}
