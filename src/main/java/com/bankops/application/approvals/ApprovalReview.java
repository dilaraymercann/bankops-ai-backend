package com.bankops.application.approvals;

import com.bankops.domain.AiFeedback;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.Priority;
import java.time.Instant;
import java.util.List;

/** Onay ekranının sağ tarafı: seçili çözümün incelemesi. */
public record ApprovalReview(
        Long caseId,
        String caseNumber,
        ErrorCode errorCode,
        Priority priority,
        String resolutionText,
        String proposedByName,
        Instant proposedAt,
        FourEyesCheck fourEyes,
        boolean aiAnalysisUsed,
        AiFeedback aiFeedback,
        long resolutionMinutes,
        List<AuditLine> recentAudit) {
}
