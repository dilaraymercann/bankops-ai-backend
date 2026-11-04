package com.bankops.application.approvals;

import jakarta.validation.constraints.Size;

public record ApproveRequest(boolean includeInKnowledgeBase, @Size(max = 500) String note) {
}
