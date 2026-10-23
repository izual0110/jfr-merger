package com.example.mcpserver.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HeapDumpControllerE2ETest {

    @LocalServerPort
    private int port;

    @Test
    void mcpShouldProvideSessionIdAndVersionViaInitialize() throws Exception {
        SseSession sseSession = openSseSession();
        try {
            assertThat(sseSession.sessionId).isNotBlank();

            String initializeRequest = """
                    {
                      \"jsonrpc\": \"2.0\",
                      \"id\": 1,
                      \"method\": \"initialize\",
                      \"params\": {
                        \"protocolVersion\": \"2024-11-05\",
                        \"capabilities\": {},
                        \"clientInfo\": {
                          \"name\": \"e2e-test\",
                          \"version\": \"1.0.0\"
                        }
                      }
                    }
                    """;

            HttpRequest initRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/mcp/message?sessionId=" + sseSession.sessionId))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(initializeRequest))
                    .build();

            HttpResponse<String> initResponse = HttpClient.newHttpClient()
                    .send(initRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            assertThat(initResponse.statusCode()).isEqualTo(200);

            String versionPayload = initResponse.body();
            if (!versionPayload.contains("\"serverInfo\"") || !versionPayload.contains("\"version\"")) {
                versionPayload = readUntilContains(sseSession.inputStream, "serverInfo", 10_000);
            }

            assertThat(versionPayload).contains("\"serverInfo\"");
            assertThat(versionPayload).contains("\"version\"");
        } finally {
            sseSession.inputStream.close();
            sseSession.connection.disconnect();
        }
    }

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

    private SseSession openSseSession() throws IOException {
        URL url = URI.create("http://localhost:" + port + "/sse").toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(1000);

        InputStream inputStream = connection.getInputStream();
        String initialSse = readUntilContains(inputStream, "sessionId=", 10_000);
        Matcher matcher = Pattern.compile("sessionId=([^&\\s]+)").matcher(initialSse);
        if (!matcher.find()) {
            connection.disconnect();
            throw new IllegalStateException("sessionId was not found in SSE payload: " + initialSse);
        }

        return new SseSession(connection, inputStream, matcher.group(1));
    }

    private String readUntilContains(InputStream inputStream, String needle, long timeoutMs) throws IOException {
        StringBuilder payload = new StringBuilder();
        byte[] buffer = new byte[512];
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                int read = inputStream.read(buffer);
                if (read > 0) {
                    payload.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    if (payload.toString().contains(needle)) {
                        return payload.toString();
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // keep polling SSE stream
            }
        }

        throw new IllegalStateException("Did not receive expected token '" + needle + "' in SSE payload: " + payload);
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

    private record SseSession(HttpURLConnection connection, InputStream inputStream, String sessionId) {
    }
}
