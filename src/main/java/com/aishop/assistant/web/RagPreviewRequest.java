package com.aishop.assistant.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagPreviewRequest(
        @NotBlank(message = "question 不能为空")
        @Size(max = 512, message = "question 不能超过 512 字符")
        String question
) {
}
