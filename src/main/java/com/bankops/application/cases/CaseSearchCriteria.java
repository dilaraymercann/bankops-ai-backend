package com.bankops.application.cases;

import com.bankops.domain.CaseStatus;
import com.bankops.domain.ErrorCode;
import com.bankops.domain.Priority;
import com.bankops.domain.SlaState;
import com.bankops.domain.TransactionType;

/** Kuyruk filtreleri (GET /api/cases sorgu parametreleri). Boş alanlar filtre uygulanmaz demektir. */
public record CaseSearchCriteria(
        CaseStatus status,
        Priority priority,
        ErrorCode errorCode,
        TransactionType transactionType,
        SlaState slaState,
        Long assignedTo,
        String search,
        Integer page,
        Integer pageSize,
        String sort) {

    public static CaseSearchCriteria none() {
        return new CaseSearchCriteria(null, null, null, null, null, null, null, null, null, null);
    }
}
