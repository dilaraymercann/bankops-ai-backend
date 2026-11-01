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
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** AI analiz sonucu: hem önbellek hem de uzman geri bildirimi (dashboard'daki kabul oranı) için saklanır. */
@Entity
@Table(name = "ai_analyses")
@Getter
@Setter
@NoArgsConstructor
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "referenced_case_ids", nullable = false)
    private List<String> referencedCaseIds;

    @Column(name = "masked_field_count", nullable = false)
    private int maskedFieldCount;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;

    @Enumerated(EnumType.STRING)
    private AiFeedback feedback;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
