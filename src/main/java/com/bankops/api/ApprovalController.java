package com.bankops.api;

import com.bankops.application.approvals.ApprovalItem;
import com.bankops.application.approvals.ApprovalReview;
import com.bankops.application.approvals.ApprovalService;
import com.bankops.application.approvals.ApproveRequest;
import com.bankops.application.approvals.RevisionRequest;
import com.bankops.application.cases.CaseDetail;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Takım lideri onay uç noktaları (yalnızca TeamLead; SecurityConfig'te kısıtlıdır). */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping
    List<ApprovalItem> pending() {
        return approvalService.pending();
    }

    @GetMapping("/{caseId}")
    ApprovalReview review(@PathVariable Long caseId, @AuthenticationPrincipal Jwt jwt) {
        return approvalService.review(caseId, CurrentUsers.from(jwt));
    }

    @PostMapping("/{caseId}/approve")
    CaseDetail approve(@PathVariable Long caseId, @Valid @RequestBody ApproveRequest request, @AuthenticationPrincipal Jwt jwt) {
        return approvalService.approve(caseId, request, CurrentUsers.from(jwt));
    }

    @PostMapping("/{caseId}/request-revision")
    CaseDetail requestRevision(@PathVariable Long caseId, @Valid @RequestBody RevisionRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return approvalService.requestRevision(caseId, request, CurrentUsers.from(jwt));
    }
}
