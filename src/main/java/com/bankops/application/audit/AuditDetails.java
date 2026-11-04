package com.bankops.application.audit;

/** Audit kaydının {@code details} alanı için küçük bir JSON oluşturucu: {@code AuditDetails.of("from", a, "to", b)}. */
public final class AuditDetails {

    private AuditDetails() {
    }

    public static String of(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Anahtar ve değerler çift sayıda olmalıdır");
        }
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(String.valueOf(keysAndValues[i]))).append(':').append(value(keysAndValues[i + 1]));
        }
        return json.append('}').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return quote(value instanceof Enum<?> enumValue ? enumValue.name() : value.toString());
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
