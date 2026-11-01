package com.bankops.domain;

/** Durum makinesinde tanımlı olmayan bir geçiş denendiğinde fırlatılır (API'de 409 Conflict). */
public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(CaseStatus from, CaseStatus to) {
        super("Geçersiz durum geçişi: " + from + " -> " + to);
    }
}
