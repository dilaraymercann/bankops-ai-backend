package com.bankops.application.ai;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * LLM'e ve embedding servisine giden her metnin kişisel verilerini maskeler (CLAUDE.md §10.4).
 * Sıra önemlidir: IBAN içindeki rakam dizileri TCKN/telefon gibi okunmasın diye önce IBAN, en son isimler değiştirilir.
 */
@Component
public final class PiiMasker {

    /** TR + 2 kontrol hanesi + 20 hane + 2 hane; boşluklu ya da boşluksuz, büyük/küçük harf fark etmez. */
    private static final Pattern IBAN = Pattern.compile("\\bTR\\s?\\d{2}(?:\\s?\\d{4}){5}\\s?\\d{2}\\b", Pattern.CASE_INSENSITIVE);

    /** TCKN: 11 hane ve 0 ile başlamaz (0 ile başlayan 11 hane telefon numarasıdır). */
    private static final Pattern TCKN = Pattern.compile("(?<!\\d)[1-9]\\d{10}(?!\\d)");

    /** Cep telefonu: +90 / 0 öneki isteğe bağlı, 5XX XXX XX XX; boşluk, tire ve parantez ayracı kabul edilir. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:(?:\\+90|0090|0)[\\s-]?)?\\(?5\\d{2}\\)?[\\s-]?\\d{3}[\\s-]?\\d{2}[\\s-]?\\d{2}(?!\\d)");

    /** Maskelenmiş metin ve kaç alanın maskelendiği ({@code AiAnalysis.maskedFieldCount}). */
    public record Result(String text, int maskedCount) {
    }

    public Result mask(String text, String senderName, String receiverName) {
        if (text == null || text.isBlank()) {
            return new Result(text == null ? "" : text, 0);
        }
        AtomicInteger count = new AtomicInteger();
        String masked = replace(text, IBAN, "[IBAN]", count);
        masked = replace(masked, TCKN, "[TCKN]", count);
        masked = replace(masked, PHONE, "[TELEFON]", count);
        masked = replaceName(masked, senderName, "[GÖNDEREN]", count);
        masked = replaceName(masked, receiverName, "[ALICI]", count);
        return new Result(masked, count.get());
    }

    public Result mask(String text) {
        return mask(text, null, null);
    }

    private static String replaceName(String text, String name, String placeholder, AtomicInteger count) {
        if (name == null || name.isBlank()) {
            return text;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(name.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return replace(text, pattern, placeholder, count);
    }

    private static String replace(String text, Pattern pattern, String placeholder, AtomicInteger count) {
        return pattern.matcher(text).replaceAll(match -> {
            count.incrementAndGet();
            return placeholder;
        });
    }
}
