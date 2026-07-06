package com.aishop.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AuthDtos.LoginRequest;
import com.aishop.dto.AuthDtos.RegisterRequest;
import com.aishop.dto.AuthDtos.UpdateProfileRequest;
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
        return toUserResponse(user);
    }

    @PostMapping("/api/auth/login")
    public UserResponse login(@RequestBody LoginRequest request,
                              HttpSession session,
                              @RequestParam(required = false) String scope) {
        var user = authService.login(request, session, scope);
        return toUserResponse(user);
    }

    @PostMapping("/api/auth/logout")
    public void logout(HttpSession session, @RequestParam(required = false) String scope) {
        authService.logout(session, scope);
    }

    @GetMapping("/api/auth/me")
    public UserResponse me(HttpSession session, @RequestParam(required = false) String scope) {
        var user = authService.currentUser(session, scope);
        if (user == null) {
            return null;
        }
        return toUserResponse(user);
    }

    @PutMapping("/api/auth/profile")
    public UserResponse updateProfile(HttpSession session, @RequestBody UpdateProfileRequest request) {
        var updated = authService.updateProfile(authService.requireUser(session), request);
        return toUserResponse(updated);
    }

    private UserResponse toUserResponse(com.aishop.domain.AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPhone(),
                user.getShippingAddress(),
                user.getPreferencesSummary(),
                user.getRole().name());
    }
}
