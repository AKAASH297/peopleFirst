package com.peoplefirst.auth.mapper;

import com.peoplefirst.auth.dto.LoginResponseDto;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    private final UserMapper userMapper;

    public AuthMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public LoginResponseDto toLoginResponseDto(String accessToken, String refreshToken, User user) {
        return new LoginResponseDto(
                accessToken,
                refreshToken,
                userMapper.toDto(user)
        );
    }
}
