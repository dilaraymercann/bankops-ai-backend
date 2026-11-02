package com.bankops.application.cases;

import com.bankops.domain.Channel;
import com.bankops.domain.TransactionType;
import java.time.Instant;
import java.time.LocalDate;

/** İşlem bilgileri; IBAN ve kişi adları maskelidir. */
public record TransactionView(
        String reference,
        TransactionType type,
        String amount,
        String currency,
        String senderName,
        String senderIban,
        String receiverName,
        String receiverIban,
        String description,
        Channel channel,
        LocalDate valueDate,
        Instant createdAt) {
}
