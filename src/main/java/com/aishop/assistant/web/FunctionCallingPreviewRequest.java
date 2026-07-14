package com.aishop.assistant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FunctionCallingPreviewRequest(
        @NotBlank @Size(max = 4000) String message
) {
}
