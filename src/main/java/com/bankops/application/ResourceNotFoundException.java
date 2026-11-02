package com.bankops.application;

/** İstenen kayıt bulunamadı (API'de 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
