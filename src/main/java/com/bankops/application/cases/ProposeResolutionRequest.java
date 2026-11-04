package com.bankops.application.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposeResolutionRequest(@NotBlank @Size(max = 2000) String text) {
}
