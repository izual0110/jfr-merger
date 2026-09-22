package com.example.mcpserver.controller;

import com.example.mcpserver.dto.HeapHistogramResponseDto;
import com.example.mcpserver.service.HeapDumpAnalyzerService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/heapdump")
@Validated
public class HeapDumpController {

    private final HeapDumpAnalyzerService analyzerService;

    public HeapDumpController(HeapDumpAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping("/top-classes")
    public HeapHistogramResponseDto topClasses(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }
        var classes = analyzerService.topClasses(file, limit);
        return new HeapHistogramResponseDto(file.getOriginalFilename(), classes.size(), classes);
    }
}
