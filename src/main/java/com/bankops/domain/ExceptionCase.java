package com.bankops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kuyruktaki istisna kaydı. İlişkili kayıtlara (işlem, kullanıcılar) kimlikleri üzerinden başvurulur. */
@Entity
@Table(name = "exception_cases")
@Getter
@Setter
@NoArgsConstructor
public class ExceptionCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", nullable = false, unique = true)
    private String caseNumber;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", nullable = false)
    private ErrorCode errorCode;

    @Column(name = "error_description", nullable = false)
    private String errorDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status;

    @Column(name = "assigned_to_user_id")
    private Long assignedToUserId;

    @Column(name = "proposed_by_user_id")
    private Long proposedByUserId;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    @Column(name = "sla_deadline", nullable = false)
    private Instant slaDeadline;

    @Column(name = "is_escalated", nullable = false)
    private boolean escalated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
