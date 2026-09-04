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

    @Value("${app.genai.base-url:${OPENAI_BASE_URL:https://api.openai.com/v1}}")
    private String openAiBaseUrl;

    @Value("${app.genai.provider:${GENAI_PROVIDER:auto}}")
    private String provider;

    public record IntentSlots(String intent, Map<String, String> slots) {
    }

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

    public void setBaseUrl(String baseUrl) {
        this.openAiBaseUrl = baseUrl;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        String providerName = (provider != null) ? provider.trim() : "auto";

        // Explicit provider routing; auto keeps the historical key/model sniffing.
        if ("openai_compatible".equalsIgnoreCase(providerName)) {
            String openAiModel = currentModel.contains("gemini") ? "gpt-3.5-turbo" : currentModel;
            return callOpenAiApi(key, openAiModel, systemInstruction, userMessage);
        } else if ("gemini".equalsIgnoreCase(providerName)) {
            return callGeminiApi(key, currentModel, systemInstruction, userMessage);
        }

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
            String url = openAiBaseUrl().replaceAll("/+$", "") + "/chat/completions";

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

    /**
     * Chat via the OpenAI-compatible endpoint with tool definitions, returning the raw
     * assistant message JSON ({@code {"content": "...", "tool_calls": [...]}}) as a String.
     */
    public Optional<String> chatWithTools(String systemInstruction, List<Map<String, String>> history, List<Map<String, Object>> tools) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            String currentModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gpt-3.5-turbo";
            String openAiModel = currentModel.contains("gemini") ? "gpt-3.5-turbo" : currentModel;

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                messages.add(Map.of("role", "system", "content", systemInstruction));
            }
            if (history != null) {
                messages.addAll(history);
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openAiModel);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.4);
            requestBody.put("max_tokens", 1000);
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");

            Optional<JsonNode> root = postOpenAiChat(requestBody, openAiModel);
            if (root.isEmpty()) {
                return Optional.empty();
            }

            JsonNode choices = root.get().path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode message = choices.get(0).path("message");
                if (!message.isMissingNode()) {
                    return Optional.of(objectMapper.writeValueAsString(message));
                }
            }

            log.warn("Unexpected OpenAI API response: {}", root.get());
            return Optional.empty();

        } catch (Exception e) {
            log.warn("OpenAI API call error (fallback to policy engine): {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * JSON-degrade path: asks the OpenAI-compatible endpoint for a strict JSON object
     * and parses it into {@link IntentSlots}. Empty on any failure.
     */
    public Optional<IntentSlots> parseIntentJson(String systemInstruction, String userMessage) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            String currentModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gpt-3.5-turbo";
            String openAiModel = currentModel.contains("gemini") ? "gpt-3.5-turbo" : currentModel;

            String shape = "Respond with exactly this JSON object and nothing else: "
                    + "{\"intent\": \"<ONE_OF_10>\", \"slots\": "
                    + "{\"leaveType\": \"\", \"startDate\": \"\", \"endDate\": \"\", \"confirmed\": \"\"}}";
            String effectiveSystem = (systemInstruction != null && !systemInstruction.trim().isEmpty())
                    ? systemInstruction.trim() + "\n" + shape
                    : shape;

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", effectiveSystem));
            messages.add(Map.of("role", "user", "content", userMessage));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openAiModel);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.4);
            requestBody.put("max_tokens", 1000);
            requestBody.put("response_format", Map.of("type", "json_object"));

            Optional<JsonNode> root = postOpenAiChat(requestBody, openAiModel);
            if (root.isEmpty()) {
                return Optional.empty();
            }

            JsonNode choices = root.get().path("choices");
            if (choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).path("message").path("content").asText();
                if (content != null && !content.trim().isEmpty()) {
                    JsonNode node = objectMapper.readTree(content.trim());
                    String intent = node.path("intent").asText(null);
                    if (intent != null && !intent.trim().isEmpty()) {
                        Map<String, String> slots = new HashMap<>();
                        JsonNode slotsNode = node.path("slots");
                        if (slotsNode.isObject()) {
                            slotsNode.fields().forEachRemaining(entry ->
                                    slots.put(entry.getKey(),
                                            entry.getValue().isNull() ? "" : entry.getValue().asText()));
                        }
                        return Optional.of(new IntentSlots(intent.trim(), slots));
                    }
                }
            }

            log.warn("Unexpected OpenAI API response: {}", root.get());
            return Optional.empty();

        } catch (Exception e) {
            log.warn("OpenAI API call error (fallback to policy engine): {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String openAiBaseUrl() {
        String base = (openAiBaseUrl != null && !openAiBaseUrl.trim().isEmpty())
                ? openAiBaseUrl.trim()
                : "https://api.openai.com/v1";
        return base;
    }

    private Optional<JsonNode> postOpenAiChat(Map<String, Object> requestBody, String targetModel) {
        try {
            String url = openAiBaseUrl().replaceAll("/+$", "") + "/chat/completions";
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenAI API ({}) returned HTTP {}: {}", targetModel, response.statusCode(), response.body());
                return Optional.empty();
            }

            return Optional.of(objectMapper.readTree(response.body()));

        } catch (Exception e) {
            log.warn("OpenAI API call error (fallback to policy engine): {}", e.getMessage());
            return Optional.empty();
        }
    }
}
