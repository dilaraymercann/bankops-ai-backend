package com.bankops.application.approvals;

/** Dört göz kontrol sonucu: geçerse kullanıcı onaylayabilir. */
public record FourEyesCheck(boolean passed, String message) {
}
