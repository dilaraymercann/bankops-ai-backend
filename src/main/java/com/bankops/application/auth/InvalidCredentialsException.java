package com.bankops.application.auth;

/** Kullanıcı adı veya parola hatalı (API'de 401). Hangisinin hatalı olduğu bilerek söylenmez. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Kullanıcı adı veya parola hatalı");
    }
}
