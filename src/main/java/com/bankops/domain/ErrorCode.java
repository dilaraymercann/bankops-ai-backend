package com.bankops.domain;

import static com.bankops.domain.Priority.Critical;
import static com.bankops.domain.Priority.High;
import static com.bankops.domain.Priority.Low;
import static com.bankops.domain.Priority.Medium;
import static com.bankops.domain.TransactionType.BulkPayment;
import static com.bankops.domain.TransactionType.CorporateOrder;
import static com.bankops.domain.TransactionType.Fast;
import static com.bankops.domain.TransactionType.Transfer;

import java.util.EnumSet;
import java.util.Set;

/** Hata kataloğu (CLAUDE.md §8): her hata kodunun açıklaması, geçerli işlem tipleri ve varsayılan önceliği. */
public enum ErrorCode {
    IBAN_CHECKSUM("IBAN kontrol hanesi hatalı", EnumSet.of(Transfer, Fast), Medium),
    NAME_MISMATCH("Alıcı adı ile IBAN sahibi uyumsuz", EnumSet.of(Transfer, Fast), High),
    DUPLICATE_SUSPECT("Mükerrer talimat şüphesi", EnumSet.allOf(TransactionType.class), High),
    FAST_LIMIT("FAST tutar limiti aşımı", EnumSet.of(Fast), Medium),
    OUT_OF_HOURS("EFT saatleri dışında gelen talimat", EnumSet.of(CorporateOrder), Low),
    HOLIDAY_VALUE_DATE("Valör tarihi resmi tatile denk geliyor", EnumSet.of(CorporateOrder, BulkPayment), Medium),
    ACCOUNT_BLOCKED("Alıcı hesap bloke/kapalı", EnumSet.allOf(TransactionType.class), Medium),
    SANCTION_HIT("Yaptırım listesi isim benzerliği", EnumSet.allOf(TransactionType.class), Critical),
    BATCH_FORMAT("Toplu ödeme dosyasında satır format hatası", EnumSet.of(BulkPayment), Medium),
    TIMEOUT("Karşı bankadan yanıt alınamadı, durum belirsiz", EnumSet.of(Fast), Critical);

    private final String description;
    private final Set<TransactionType> applicableTypes;
    private final Priority defaultPriority;

    ErrorCode(String description, Set<TransactionType> applicableTypes, Priority defaultPriority) {
        this.description = description;
        this.applicableTypes = applicableTypes;
        this.defaultPriority = defaultPriority;
    }

    public String description() {
        return description;
    }

    public Priority defaultPriority() {
        return defaultPriority;
    }

    public Set<TransactionType> applicableTypes() {
        return applicableTypes;
    }

    public boolean appliesTo(TransactionType type) {
        return applicableTypes.contains(type);
    }
}
