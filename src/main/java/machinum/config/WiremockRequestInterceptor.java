package machinum.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import machinum.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.async.AsyncHelper;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class WiremockRequestInterceptor implements ClientHttpRequestInterceptor {

    private final Boolean enabled;
    private final ObjectMapper objectMapper;
    private final Path logDir;
    private final AsyncHelper asyncHelper;


    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (!enabled) {
            return execution.execute(request, body);
        }

        ObjectNode mapping = objectMapper.createObjectNode();

        // Request
        ObjectNode requestNode = mapping.putObject("request");
        requestNode.put("method", request.getMethod().name());
        requestNode.put("url", request.getURI().toString());

        // Request headers
        ObjectNode requestHeaders = requestNode.putObject("headers");
        for (Map.Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
            requestHeaders.putPOJO(entry.getKey(), entry.getValue());
        }

        // Request body
        if (body.length > 0) {
            String bodyStr = new String(body);
            requestNode.put("body", bodyStr);
//            requestNode.put("bodyFileName", Base64.getEncoder().encodeToString(body));
        }

        // Execute and capture response
        ClientHttpResponse response = execution.execute(request, body);

        // Response
        ObjectNode responseNode = mapping.putObject("response");
        responseNode.put("status", response.getStatusCode().value());

        // Response headers
        ObjectNode responseHeaders = responseNode.putObject("headers");
        for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
            responseHeaders.putPOJO(entry.getKey(), entry.getValue());
        }

        // Response body
        byte[] responseBody = response.getBody().readAllBytes();
        if (responseBody.length > 0) {
            String responseBodyStr = new String(responseBody);
            responseNode.put("body", responseBodyStr);
//            responseNode.put("bodyFileName", Base64.getEncoder().encodeToString(responseBody));
        }

        // Save to file
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        var rayId = TraceUtil.currentRayId()
                .map("_%s"::formatted)
                .orElse("");
        String filename = String.format("request%s_%s.json", rayId, timestamp);

        saveToFile(timestamp, filename, mapping);

        return response;
    }

    private void saveToFile(String folder, String filename, ObjectNode mapping) throws IOException {
        asyncHelper.runAsync(() -> {
            try {
                var folderFile = new File(logDir.toString(), folder);
                folderFile.mkdirs();
                var resultFile = new File(folderFile, filename);
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(resultFile, mapping);
                log.trace("Saved http exchange to {}", resultFile.getAbsolutePath());
            } catch (IOException e) {
                log.error("An error occurred when saving http logs: %s".formatted(e.getMessage()), e);
                throw new RuntimeException(e);
            }
        });
    }

}