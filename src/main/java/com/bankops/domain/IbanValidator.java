package com.bankops.domain;

import java.util.regex.Pattern;

/** Türk IBAN'ı (TR + 24 hane) için mod-97 doğrulaması ve üretimi. */
public final class IbanValidator {

    private static final Pattern TURKISH_IBAN = Pattern.compile("TR\\d{24}");

    private IbanValidator() {
    }

    /** Boşlukları siler ve büyük harfe çevirir. */
    public static String normalize(String iban) {
        return iban == null ? "" : iban.replace(" ", "").toUpperCase();
    }

    public static boolean isValid(String iban) {
        String normalized = normalize(iban);
        return TURKISH_IBAN.matcher(normalized).matches() && mod97(normalized) == 1;
    }

    /**
     * Banka kodu (5 hane) ve hesap numarasından (17 hane: 1 rezerv + 16 hesap) geçerli bir IBAN üretir.
     * Yalnızca sentetik veri üretimi içindir.
     */
    public static String generate(String bankCode, String accountNumber) {
        String bban = bankCode + accountNumber;
        if (!bban.matches("\\d{22}")) {
            throw new IllegalArgumentException("BBAN 22 haneli olmalıdır: " + bban);
        }
        int checkDigits = 98 - mod97("TR00" + bban);
        return "TR%02d%s".formatted(checkDigits, bban);
    }

    /** IBAN'ın ilk 4 karakteri sona taşınır, harfler sayıya çevrilir (A=10 ... Z=35) ve 97'ye bölümünden kalan alınır. */
    private static int mod97(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            String digits = Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10);
            for (char digit : digits.toCharArray()) {
                remainder = (remainder * 10 + (digit - '0')) % 97;
            }
        }
        return remainder;
    }
}
