package com.example.mcpserver.controller;

import com.example.mcpserver.dto.ClassHistogramEntryDto;
import com.example.mcpserver.service.HeapDumpAnalyzerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HeapDumpController.class, ApiExceptionHandler.class})
class HeapDumpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HeapDumpAnalyzerService analyzerService;

    @Test
    void topClassesShouldReturnHistogramResponse() throws Exception {
        when(analyzerService.topClasses(any(), eq(20)))
                .thenReturn(List.of(new ClassHistogramEntryDto("java.lang.String", 42, 0)));

        var file = new MockMultipartFile("file", "heap.hprof", MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());

        mockMvc.perform(multipart("/api/v1/heapdump/top-classes").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("heap.hprof"))
                .andExpect(jsonPath("$.totalClasses").value(1))
                .andExpect(jsonPath("$.classes[0].className").value("java.lang.String"))
                .andExpect(jsonPath("$.classes[0].objects").value(42));
    }

    @Test
    void topClassesShouldValidateLimit() throws Exception {
        var file = new MockMultipartFile("file", "heap.hprof", MediaType.APPLICATION_OCTET_STREAM_VALUE, "x".getBytes());

        mockMvc.perform(multipart("/api/v1/heapdump/top-classes").file(file).param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
