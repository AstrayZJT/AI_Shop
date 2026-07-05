package com.aishop.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AuthDtos.LoginRequest;
import com.aishop.dto.AuthDtos.RegisterRequest;
import com.aishop.dto.AuthDtos.UserResponse;
import com.aishop.service.AuthService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/register")
    public UserResponse register(@RequestBody RegisterRequest request) {
        var user = authService.register(request);
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone(), user.getShippingAddress());
    }

    @PostMapping("/api/auth/login")
    public UserResponse login(@RequestBody LoginRequest request, HttpSession session) {
        var user = authService.login(request, session);
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone(), user.getShippingAddress());
    }

    @PostMapping("/api/auth/logout")
    public void logout(HttpSession session) {
        authService.logout(session);
    }

    @GetMapping("/api/auth/me")
    public UserResponse me(HttpSession session) {
        var user = authService.currentUser(session);
        if (user == null) {
            return null;
        }
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhone(), user.getShippingAddress());
    }
}
