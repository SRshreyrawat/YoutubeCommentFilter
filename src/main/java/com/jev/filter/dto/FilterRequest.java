package com.jev.filter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FilterRequest(
        @NotBlank(message = "author is required")
        @Size(max = 100, message = "author must be at most 100 characters")
        String author,
        @NotBlank(message = "text is required")
        @Size(max = 1000, message = "text must be at most 1000 characters")
        String text) {
}
