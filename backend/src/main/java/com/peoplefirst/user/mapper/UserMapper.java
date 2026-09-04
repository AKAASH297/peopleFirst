package com.peoplefirst.user.mapper;

import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponseDto toDto(User user) {
        if (user == null) {
            return null;
        }
        return new UserResponseDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isContractor(),
                user.getDepartment(),
                user.getBaseLocation(),
                user.getManagerId(),
                user.getCreatedAt()
        );
    }
}
