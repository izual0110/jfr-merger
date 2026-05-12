package com.example.mcpserver.service;

import com.example.mcpserver.dto.ClassHistogramEntryDto;
import org.openjdk.jol.heap.HeapDumpException;
import org.openjdk.jol.heap.HeapDumpReader;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.util.Multiset;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class HeapDumpAnalyzerService {

    public List<ClassHistogramEntryDto> topClasses(MultipartFile multipartFile, int limit) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("heapdump-", ".hprof");
            multipartFile.transferTo(tempFile);
            return topClassesFromPath(tempFile, limit);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to analyze heap dump: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // ignore cleanup failure
                }
            }
        }
    }

    public List<ClassHistogramEntryDto> topClassesFromPath(Path heapDumpPath, int limit) {
        try {
            Multiset<ClassData> classCounts = new HeapDumpReader(heapDumpPath.toFile()).parse();
            return classCounts.keys().stream()
                    .map(classData -> new ClassHistogramEntryDto(classData.name(), classCounts.count(classData), 0))
                    .sorted(Comparator.comparingLong(ClassHistogramEntryDto::objects).reversed())
                    .limit(limit)
                    .toList();
        } catch (IOException | HeapDumpException e) {
            throw new IllegalArgumentException("Failed to analyze heap dump: " + e.getMessage(), e);
        }
    }
}
