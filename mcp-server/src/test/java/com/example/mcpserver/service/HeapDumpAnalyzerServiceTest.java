package com.example.mcpserver.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeapDumpAnalyzerServiceTest {

    private final HeapDumpAnalyzerService service = new HeapDumpAnalyzerService();

    @Test
    void topClassesFromPathShouldThrowBadRequestForMissingFile() {
        assertThatThrownBy(() -> service.topClassesFromPath(Path.of("/tmp/does-not-exist.hprof"), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to analyze heap dump");
    }
}
