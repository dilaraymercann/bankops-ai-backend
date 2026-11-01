package com.bankops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** İstisnaya düşen ödeme talimatı (sentetik). Tutarlar her yerde BigDecimal / numeric(18,2). */
@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "TRY";

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "sender_iban", nullable = false)
    private String senderIban;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "receiver_iban", nullable = false)
    private String receiverIban;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
