package com.peoplefirst.auth.service;

import com.peoplefirst.auth.dto.LoginRequestDto;
import com.peoplefirst.auth.dto.LoginResponseDto;
import com.peoplefirst.auth.dto.RefreshTokenRequestDto;
import com.peoplefirst.auth.mapper.AuthMapper;
import com.peoplefirst.auth.security.JwtTokenProvider;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       AuthMapper authMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authMapper = authMapper;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        // SPEC.md §1 & Acceptance Criterion 1: Contractor cannot reach the webpage under any login path — only the agent.
        String channel = request.getChannel() != null ? request.getChannel().trim().toUpperCase() : "WEB";
        if (user.isContractor() && !"AGENT".equals(channel)) {
            throw new AccessDeniedException("Contractor accounts are restricted from the web portal. Please use the Contractor Agent Interface.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isContractor(),
                channel
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return authMapper.toLoginResponseDto(accessToken, refreshToken, user);
    }

    @Transactional(readOnly = true)
    public LoginResponseDto refreshToken(RefreshTokenRequestDto request) {
        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(request.getRefreshToken());
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User associated with token not found"));

        String channel = user.isContractor() ? "AGENT" : "WEB";
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.isContractor(),
                channel
        );

        return authMapper.toLoginResponseDto(newAccessToken, request.getRefreshToken(), user);
    }
}
