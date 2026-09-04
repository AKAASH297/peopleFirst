package com.peoplefirst.auth.dto;

import com.peoplefirst.user.dto.UserResponseDto;

public class LoginResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private UserResponseDto user;

    public LoginResponseDto() {
    }

    public LoginResponseDto(String accessToken, String refreshToken, UserResponseDto user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public UserResponseDto getUser() {
        return user;
    }

    public void setUser(UserResponseDto user) {
        this.user = user;
    }
}
