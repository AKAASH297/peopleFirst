package com.peoplefirst.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Universal Generative AI Client supporting both Google Gemini (gemini-1.5-pro / gemini-2.0-flash)
 * and OpenAI-compatible (gpt-3.5-turbo / gpt-4o-mini) medium-range models for superior natural language dialogue.
 * Gracefully falls back to the deterministic policy engine when offline or if no API key is provided.
 */
@Component
public class GenAiClient {

    private static final Logger log = LoggerFactory.getLogger(GenAiClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.genai.enabled:true}")
    private boolean enabled;

    @Value("${app.genai.api-key:${GEMINI_API_KEY:${OPENAI_API_KEY:${GENAI_API_KEY:}}}}")
    private String apiKey;

    @Value("${app.genai.model:${GENAI_MODEL:${GEMINI_MODEL:gemini-1.5-pro}}}")
    private String model;

    @Value("${app.genai.endpoint:https://generativelanguage.googleapis.com/v1beta/models}")
    private String geminiEndpoint;

    public GenAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.trim().isEmpty();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setApiKey(String key) {
        this.apiKey = key;
    }

    /**
     * Generates a conversational response using the configured medium-range model (Gemini 1.5 Pro or GPT-3.5/4o).
     */
    public Optional<String> generateContent(String systemInstruction, String userMessage) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        String key = apiKey.trim();
        String currentModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-1.5-pro";

        // Route to OpenAI if key starts with sk- or model references gpt / 3.5
        if (key.startsWith("sk-") || currentModel.toLowerCase().contains("gpt") || currentModel.contains("3.5")) {
            String openAiModel = currentModel.contains("gemini") ? "gpt-3.5-turbo" : currentModel;
            return callOpenAiApi(key, openAiModel, systemInstruction, userMessage);
        } else {
            return callGeminiApi(key, currentModel, systemInstruction, userMessage);
        }
    }

    private Optional<String> callGeminiApi(String key, String targetModel, String systemInstruction, String userMessage) {
        try {
            String url = String.format("%s/%s:generateContent?key=%s", geminiEndpoint, targetModel, key);

            Map<String, Object> requestBody = new HashMap<>();

            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                requestBody.put("systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ));
            }

            requestBody.put("contents", List.of(
                    Map.of(
                            "role", "user",
                            "parts", List.of(Map.of("text", userMessage))
                    )
            ));

            requestBody.put("generationConfig", Map.of(
                    "temperature", 0.4,
                    "maxOutputTokens", 1000
            ));

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini API ({}) returned HTTP {}: {}", targetModel, response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    String text = parts.get(0).path("text").asText();
                    if (text != null && !text.trim().isEmpty()) {
                        return Optional.of(text.trim());
                    }
                }
            }

            log.warn("Unexpected Gemini API response structure: {}", response.body());
            return Optional.empty();

        } catch (Exception e) {
            log.warn("Gemini API call error (fallback to policy engine): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> callOpenAiApi(String key, String targetModel, String systemInstruction, String userMessage) {
        try {
            String url = "https://api.openai.com/v1/chat/completions";

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemInstruction));
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = Map.of(
                    "model", targetModel,
                    "messages", messages,
                    "temperature", 0.4,
                    "max_tokens", 1000
            );

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenAI API ({}) returned HTTP {}: {}", targetModel, response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String text = choices.get(0).path("message").path("content").asText();
                if (text != null && !text.trim().isEmpty()) {
                    return Optional.of(text.trim());
                }
            }

            log.warn("Unexpected OpenAI API response: {}", response.body());
            return Optional.empty();

        } catch (Exception e) {
            log.warn("OpenAI API call error (fallback to policy engine): {}", e.getMessage());
            return Optional.empty();
        }
    }
}
