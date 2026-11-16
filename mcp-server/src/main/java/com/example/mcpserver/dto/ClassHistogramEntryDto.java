package com.example.mcpserver.dto;

public record ClassHistogramEntryDto(
        String className,
        long objects,
        long shallowHeapBytes
) {
}
