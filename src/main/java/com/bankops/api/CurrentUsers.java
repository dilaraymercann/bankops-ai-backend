package com.bankops.api;

import com.bankops.application.CurrentUser;
import com.bankops.domain.Role;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT'den isteği yapan kullanıcıyı çıkarır. */
final class CurrentUsers {

    private CurrentUsers() {
    }

    static CurrentUser from(Jwt jwt) {
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString("role")));
    }
}
