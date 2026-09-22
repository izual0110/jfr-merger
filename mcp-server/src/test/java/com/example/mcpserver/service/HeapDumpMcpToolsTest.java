package com.example.mcpserver.service;

import com.example.mcpserver.dto.ClassHistogramEntryDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpMcpToolsTest {

    @Mock
    private HeapDumpAnalyzerService analyzerService;

    @Test
    void topClassesShouldNormalizeLimitAndDelegateToAnalyzer() {
        var tool = new HeapDumpMcpTools(analyzerService);
        var expected = List.of(new ClassHistogramEntryDto("java.lang.String", 10, 0));
        when(analyzerService.topClassesFromPath(any(Path.class), anyInt())).thenReturn(expected);

        var result = tool.topClasses("/tmp/test.hprof", 0);

        assertThat(result).isEqualTo(expected);

        var pathCaptor = ArgumentCaptor.forClass(Path.class);
        var limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(analyzerService).topClassesFromPath(pathCaptor.capture(), limitCaptor.capture());
        assertThat(pathCaptor.getValue()).isEqualTo(Path.of("/tmp/test.hprof"));
        assertThat(limitCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void topClassesShouldClampLimitToUpperBound() {
        var tool = new HeapDumpMcpTools(analyzerService);
        when(analyzerService.topClassesFromPath(any(Path.class), anyInt())).thenReturn(List.of());

        tool.topClasses("/tmp/test.hprof", 900);

        var limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(analyzerService).topClassesFromPath(any(Path.class), limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(500);
    }
}
