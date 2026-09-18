package com.example.mcpserver.service;

import com.example.mcpserver.dto.ClassHistogramEntryDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class HeapDumpMcpTools {

    private final HeapDumpAnalyzerService analyzerService;

    public HeapDumpMcpTools(HeapDumpAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @Tool(description = "Analyze a .hprof heap dump file and return top classes by instance count")
    public List<ClassHistogramEntryDto> topClasses(
            @ToolParam(description = "Absolute path to .hprof file on server filesystem") String heapDumpPath,
            @ToolParam(description = "How many classes to return, from 1 to 500") int limit
    ) {
        int normalizedLimit = Math.max(1, Math.min(500, limit));
        return analyzerService.topClassesFromPath(Path.of(heapDumpPath), normalizedLimit);
    }
}
