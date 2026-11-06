package com.bankops.application.ai;

import com.bankops.domain.AiFeedback;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(@NotNull AiFeedback feedback) {
}
