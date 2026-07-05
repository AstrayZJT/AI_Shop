package com.aishop.dto;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(String username, String password, String displayName) {}
    public record LoginRequest(String username, String password) {}
    public record UserResponse(Long id, String username, String displayName, String phone, String shippingAddress) {}
}
