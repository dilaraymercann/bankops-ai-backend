package com.bankops.domain;

/** Çözümü öneren kullanıcı aynı çözümü onaylamaya çalıştığında fırlatılır (API'de 403 Forbidden). */
public class FourEyesViolationException extends RuntimeException {

    public FourEyesViolationException() {
        super("Dört göz ilkesi: çözümü öneren kullanıcı aynı çözümü onaylayamaz");
    }
}
