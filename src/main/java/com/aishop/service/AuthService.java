package com.aishop.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.dto.AuthDtos.LoginRequest;
import com.aishop.dto.AuthDtos.RegisterRequest;
import com.aishop.repository.AppUserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

    public static final String SESSION_USER_ID = "SESSION_USER_ID";

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        userRepository.findByUsername(request.username()).ifPresent(user -> {
            throw new IllegalArgumentException("用户名已存在");
        });
        var user = new AppUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPreferencesSummary("偏好未初始化");
        return userRepository.save(user);
    }

    public AppUser login(LoginRequest request, HttpSession session) {
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        session.setAttribute(SESSION_USER_ID, user.getId());
        return user;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public AppUser currentUser(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER_ID);
        if (id instanceof Long userId) {
            return userRepository.findById(userId).orElse(null);
        }
        if (id instanceof Integer intId) {
            return userRepository.findById(intId.longValue()).orElse(null);
        }
        return null;
    }
}
