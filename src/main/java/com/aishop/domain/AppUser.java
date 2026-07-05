package com.aishop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Column(length = 32)
    private String phone;

    @Column(length = 512)
    private String shippingAddress;

    @Column(length = 512)
    private String preferencesSummary;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private UserRole role;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getPreferencesSummary() {
        return preferencesSummary;
    }

    public void setPreferencesSummary(String preferencesSummary) {
        this.preferencesSummary = preferencesSummary;
    }

    public UserRole getRole() {
        return role == null ? UserRole.CUSTOMER : role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
