package com.bankops.api;

import com.bankops.application.cases.AddNoteRequest;
import com.bankops.application.cases.AssignRequest;
import com.bankops.application.cases.CaseDetail;
import com.bankops.application.cases.CaseListItem;
import com.bankops.application.cases.CaseQueryService;
import com.bankops.application.cases.CaseSearchCriteria;
import com.bankops.application.cases.CaseSummary;
import com.bankops.application.cases.CaseWorkflowService;
import com.bankops.application.cases.PageResponse;
import com.bankops.application.cases.ProposeResolutionRequest;
import com.bankops.application.cases.TransitionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** İnce controller: kimliği çıkarır, işi servise devreder. Yazma işlemleri güncel kayıt detayını döner. */
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
class CaseController {

    private final CaseQueryService caseQueryService;
    private final CaseWorkflowService workflowService;

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

    @PostMapping("/{id}/notes")
    CaseDetail addNote(@PathVariable Long id, @Valid @RequestBody AddNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
        return workflowService.addNote(id, request, CurrentUsers.from(jwt));
    }

    @PostMapping("/{id}/transitions")
    CaseDetail transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request, @AuthenticationPrincipal Jwt jwt) {
        return workflowService.transition(id, request, CurrentUsers.from(jwt));
    }

    @PostMapping("/{id}/resolution")
    CaseDetail proposeResolution(@PathVariable Long id, @Valid @RequestBody ProposeResolutionRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return workflowService.proposeResolution(id, request, CurrentUsers.from(jwt));
    }

    @PostMapping("/{id}/assign")
    CaseDetail assign(@PathVariable Long id, @Valid @RequestBody AssignRequest request, @AuthenticationPrincipal Jwt jwt) {
        return workflowService.assign(id, request, CurrentUsers.from(jwt));
    }
}
