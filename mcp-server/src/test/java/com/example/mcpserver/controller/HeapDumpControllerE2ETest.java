package com.example.mcpserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HeapDumpControllerE2ETest {

    @LocalServerPort
    private int port;

    @Test
    void topClassesShouldReturnBadRequestForInvalidLimit() throws IOException, InterruptedException {
        HttpResponse<String> response = sendMultipartRequest(0, "heap.hprof", "dummy".getBytes(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("limit must be between 1 and 500");
    }

    @Test
    void topClassesShouldReturnBadRequestForCorruptedHeapDump() throws IOException, InterruptedException {
        HttpResponse<String> response = sendMultipartRequest(5, "broken.hprof", "not-a-real-hprof".getBytes(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Failed to analyze heap dump");
    }

    private HttpResponse<String> sendMultipartRequest(int limit, String filename, byte[] fileContent)
            throws IOException, InterruptedException {
        String boundary = "----mcp-boundary-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, filename, fileContent);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/heapdump/top-classes?limit=" + limit))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private byte[] multipartBody(String boundary, String filename, byte[] fileContent) {
        String preamble = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String epilogue = "\r\n--" + boundary + "--\r\n";

        byte[] head = preamble.getBytes(StandardCharsets.UTF_8);
        byte[] tail = epilogue.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[head.length + fileContent.length + tail.length];

        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(fileContent, 0, result, head.length, fileContent.length);
        System.arraycopy(tail, 0, result, head.length + fileContent.length, tail.length);
        return result;
    }
}
