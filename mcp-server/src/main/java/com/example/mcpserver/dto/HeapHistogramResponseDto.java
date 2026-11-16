package com.example.mcpserver.dto;

import java.util.List;

public record HeapHistogramResponseDto(
        String fileName,
        int totalClasses,
        List<ClassHistogramEntryDto> classes
) {
}
