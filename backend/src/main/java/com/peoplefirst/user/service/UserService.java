package com.peoplefirst.user.service;

import com.peoplefirst.user.dto.UserResponseDto;
import com.peoplefirst.user.entity.User;
import com.peoplefirst.user.mapper.UserMapper;
import com.peoplefirst.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserEntityByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(UUID id) {
        return userMapper.toDto(getUserEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getDirectReportees(UUID managerId) {
        return userRepository.findByManagerId(managerId).stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<User> getDirectReportEntities(UUID managerId) {
        return userRepository.findByManagerId(managerId);
    }

    @Transactional(readOnly = true)
    public List<User> getAllUserEntities() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }
}
