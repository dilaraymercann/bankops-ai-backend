package com.bankops.application.auth;

import com.bankops.domain.Role;
import com.bankops.domain.User;

public record UserSummary(Long id, String fullName, String username, Role role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFullName(), user.getUsername(), user.getRole());
    }
}
