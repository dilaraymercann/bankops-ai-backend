package com.bankops.api;

import com.bankops.application.cases.CaseDetail;
import com.bankops.application.cases.CaseListItem;
import com.bankops.application.cases.CaseQueryService;
import com.bankops.application.cases.CaseSearchCriteria;
import com.bankops.application.cases.CaseSummary;
import com.bankops.application.cases.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** İnce controller: kimliği çıkarır, işi servise devreder. */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
class CaseController {

    private final CaseQueryService caseQueryService;

    @GetMapping
    PageResponse<CaseListItem> list(CaseSearchCriteria criteria, @AuthenticationPrincipal Jwt jwt) {
        return caseQueryService.search(criteria, CurrentUsers.from(jwt));
    }

    @GetMapping("/summary")
    CaseSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return caseQueryService.summary(CurrentUsers.from(jwt));
    }

    @GetMapping("/{id}")
    CaseDetail get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return caseQueryService.get(id, CurrentUsers.from(jwt));
    }
}
