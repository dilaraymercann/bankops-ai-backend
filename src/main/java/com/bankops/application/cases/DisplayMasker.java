package com.bankops.application.cases;

import com.bankops.domain.IbanValidator;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Arayüzde gösterilen kişisel verilerin maskelenmesi (CLAUDE.md §16). Maskeleme sunucuda yapılır;
 * ham IBAN ve kişi adları tarayıcıya hiç gönderilmez. (LLM'e giden metinlerin maskelenmesi ayrıdır: Faz 4, PiiMasker.)
 */
public final class DisplayMasker {

    private DisplayMasker() {
    }

    /** "TR330006100519786457841326" -> "TR33 **** **** **** **** 4578 41". */
    public static String iban(String iban) {
        String normalized = IbanValidator.normalize(iban);
        if (normalized.length() != 26) {
            return "****";
        }
        String tail = normalized.substring(20);
        return normalized.substring(0, 4) + " **** **** **** **** " + tail.substring(0, 4) + " " + tail.substring(4);
    }

    /** Kişi adlarında her kelimenin yalnızca ilk harfi kalır: "Mehmet Yılmaz" -> "M*** Y***". Şirket unvanları açık kalır. */
    public static String name(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (looksLikeCompany(name)) {
            return name;
        }
        return Arrays.stream(name.trim().split("\\s+"))
                .map(word -> word.substring(0, 1) + "***")
                .collect(Collectors.joining(" "));
    }

    private static boolean looksLikeCompany(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("a.ş.") || lower.contains("ltd.") || lower.contains("şti.");
    }
}
