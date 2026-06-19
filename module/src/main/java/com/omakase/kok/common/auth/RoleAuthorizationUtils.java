package com.omakase.kok.common.auth;

import com.omakase.kok.common.exception.BaseException;
import com.omakase.kok.common.exception.CommonErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class RoleAuthorizationUtils {
    private static final String ROLE_PREFIX = "ROLE_";

    private RoleAuthorizationUtils() {
    }

    public static void requireRole(String roleHeader, String requiredRole) {
        requireAnyRole(roleHeader, requiredRole);
    }

    // 권한 없으면 오류 발생
    public static void requireAnyRole(String roleHeader, String... allowedRoles) {
        if (!hasAnyRole(roleHeader, allowedRoles)) {
            throw new BaseException(CommonErrorCode.ACCESS_DENIED);
        }
    }

    // 권한 확인
    public static boolean hasAnyRole(String roleHeader, String... allowedRoles) {
        String normalizedRole = normalize(roleHeader);
        if (normalizedRole == null) {
            return false;
        }

        return allowedRoleSet(allowedRoles).contains(normalizedRole);
    }

    private static Set<String> allowedRoleSet(String... allowedRoles) {
        if (allowedRoles == null || allowedRoles.length == 0) {
            throw new IllegalArgumentException("allowedRoles must not be empty");
        }

        return Arrays.stream(allowedRoles)
                .map(RoleAuthorizationUtils::normalize)
                .filter(role -> role != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }

        String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
        if (normalizedRole.startsWith(ROLE_PREFIX)) {
            return normalizedRole.substring(ROLE_PREFIX.length());
        }

        return normalizedRole;
    }
}
