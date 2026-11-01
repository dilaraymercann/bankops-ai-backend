package com.bankops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Çözüm kaydı ve bilgi tabanı girdisi. Yalnızca onaylanan ({@code verified}) çözümler AI kaynağı olabilir.
 * Embedding sütunu Faz 4'te eklenecek.
 */
@Entity
@Table(name = "resolutions")
@Getter
@Setter
@NoArgsConstructor
public class Resolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @Column(name = "proposed_by_user_id", nullable = false)
    private Long proposedByUserId;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "include_in_knowledge_base", nullable = false)
    private boolean includeInKnowledgeBase;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
