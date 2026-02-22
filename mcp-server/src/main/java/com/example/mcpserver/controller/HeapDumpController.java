package com.example.mcpserver.controller;

import com.example.mcpserver.dto.HeapHistogramResponseDto;
import com.example.mcpserver.service.HeapDumpAnalyzerService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            @RequestParam(name = "limit", defaultValue = "20") @Min(1) @Max(500) int limit
    ) {
        var classes = analyzerService.topClasses(file, limit);
        return new HeapHistogramResponseDto(file.getOriginalFilename(), classes.size(), classes);
    }
}
