package com.bankops.application.approvals;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevisionRequest(@NotBlank @Size(max = 500) String note) {
}
