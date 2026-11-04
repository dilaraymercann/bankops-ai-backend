package com.bankops.application.cases;

import com.bankops.domain.CaseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransitionRequest(@NotNull CaseStatus toStatus, @Size(max = 500) String reason) {
}
