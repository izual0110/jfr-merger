package com.example.mcpserver.dto;

import java.time.OffsetDateTime;

public record ErrorResponseDto(
        OffsetDateTime timestamp,
        String message
) {
}
