package com.aishop.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aishop.domain.AppUser;
import com.aishop.domain.UserRole;
import com.aishop.dto.AuthDtos.LoginRequest;
import com.aishop.dto.AuthDtos.RegisterRequest;
import com.aishop.dto.AuthDtos.UpdateProfileRequest;
import com.aishop.repository.AppUserRepository;

import jakarta.servlet.http.HttpSession;

@Service
public class AuthService {

    public static final String LEGACY_SESSION_USER_ID = "SESSION_USER_ID";
    public static final String SESSION_CUSTOMER_USER_ID = "SESSION_CUSTOMER_USER_ID";
    public static final String SESSION_ADMIN_USER_ID = "SESSION_ADMIN_USER_ID";
    public static final String AUTH_SCOPE_CUSTOMER = "customer";
    public static final String AUTH_SCOPE_ADMIN = "admin";

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
        user.setRole(UserRole.CUSTOMER);
        return userRepository.save(user);
    }

    public AppUser login(LoginRequest request, HttpSession session, String scope) {
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        String normalizedScope = normalizeScope(scope);
        if (AUTH_SCOPE_ADMIN.equals(normalizedScope) && user.getRole() != UserRole.ADMIN) {
            throw new SecurityException("当前账号没有管理端登录权限");
        }
        session.removeAttribute(LEGACY_SESSION_USER_ID);
        session.setAttribute(sessionKey(normalizedScope), user.getId());
        return user;
    }

    @Transactional
    public AppUser updateProfile(AppUser user, UpdateProfileRequest request) {
        AppUser managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            managed.setDisplayName(request.displayName().trim());
        }
        managed.setPhone(blankToNull(request.phone()));
        managed.setShippingAddress(blankToNull(request.shippingAddress()));
        if (request.preferencesSummary() != null) {
            managed.setPreferencesSummary(blankToNull(request.preferencesSummary()));
        }
        return userRepository.save(managed);
    }

    public void logout(HttpSession session, String scope) {
        session.removeAttribute(sessionKey(normalizeScope(scope)));
        if (session.getAttribute(SESSION_CUSTOMER_USER_ID) == null
                && session.getAttribute(SESSION_ADMIN_USER_ID) == null) {
            session.removeAttribute(LEGACY_SESSION_USER_ID);
        }
    }

    public AppUser currentUser(HttpSession session) {
        return currentUser(session, AUTH_SCOPE_CUSTOMER);
    }

    public AppUser currentUser(HttpSession session, String scope) {
        return loadScopedUser(session, normalizeScope(scope));
    }

    public AppUser requireUser(HttpSession session) {
        AppUser user = currentUser(session, AUTH_SCOPE_CUSTOMER);
        if (user == null) {
            throw new IllegalStateException("请先登录");
        }
        return user;
    }

    public AppUser requireAdmin(HttpSession session) {
        AppUser user = currentUser(session, AUTH_SCOPE_ADMIN);
        if (user == null) {
            throw new IllegalStateException("请先登录管理员账号");
        }
        if (user.getRole() != UserRole.ADMIN) {
            throw new SecurityException("需要管理员权限");
        }
        return user;
    }

    private AppUser loadScopedUser(HttpSession session, String scope) {
        AppUser scopedUser = findUserBySessionAttribute(session.getAttribute(sessionKey(scope)));
        if (scopedUser != null) {
            return AUTH_SCOPE_ADMIN.equals(scope) && scopedUser.getRole() != UserRole.ADMIN ? null : scopedUser;
        }

        AppUser legacyUser = findUserBySessionAttribute(session.getAttribute(LEGACY_SESSION_USER_ID));
        if (legacyUser == null) {
            return null;
        }
        return AUTH_SCOPE_ADMIN.equals(scope) && legacyUser.getRole() != UserRole.ADMIN ? null : legacyUser;
    }

    private AppUser findUserBySessionAttribute(Object id) {
        if (id instanceof Long userId) {
            return userRepository.findById(userId).orElse(null);
        }
        if (id instanceof Integer intId) {
            return userRepository.findById(intId.longValue()).orElse(null);
        }
        return null;
    }

    private String normalizeScope(String scope) {
        if (scope != null && AUTH_SCOPE_ADMIN.equalsIgnoreCase(scope.trim())) {
            return AUTH_SCOPE_ADMIN;
        }
        return AUTH_SCOPE_CUSTOMER;
    }

    private String sessionKey(String scope) {
        return AUTH_SCOPE_ADMIN.equals(scope) ? SESSION_ADMIN_USER_ID : SESSION_CUSTOMER_USER_ID;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
