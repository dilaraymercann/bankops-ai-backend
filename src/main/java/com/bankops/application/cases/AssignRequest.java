package com.bankops.application.cases;

import jakarta.validation.constraints.NotNull;

public record AssignRequest(@NotNull Long userId) {
}
