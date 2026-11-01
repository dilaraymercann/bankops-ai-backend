package com.bankops.domain;

/** Kullanıcının rolü bu işlem için yetersiz olduğunda fırlatılır (API'de 403 Forbidden). */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
