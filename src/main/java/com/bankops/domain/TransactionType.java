package com.bankops.domain;

public enum TransactionType {
    /** Havale */
    Transfer,
    Fast,
    /** Toplu Ödeme */
    BulkPayment,
    /** Kurumsal Talimat */
    CorporateOrder
}
