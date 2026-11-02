package com.bankops.application.auth;

import com.bankops.domain.User;
import java.time.Instant;

/** Token üretme işi altyapıdadır (JWT); application katmanı yalnızca bu arayüzü bilir. */
public interface TokenIssuer {

    IssuedToken issue(User user);

    record IssuedToken(String value, Instant expiresAt) {
    }
}
