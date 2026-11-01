package com.bankops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sla_policies")
@Getter
@Setter
@NoArgsConstructor
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    /** Doluysa kural yalnızca bu hata koduna uygulanır ve öncelik bazlı kuraldan önce gelir. */
    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private ErrorCode errorCode;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
}
