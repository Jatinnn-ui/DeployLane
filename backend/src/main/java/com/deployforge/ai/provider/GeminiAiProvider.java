package com.deployforge.ai.provider;

import com.deployforge.ai.AiModels.AiAnalysisContext;
import com.deployforge.ai.AiModels.AiAnalysisResult;
import com.deployforge.ai.AiModels.AiChatContext;
import com.deployforge.ai.AiModels.AiChatResult;
import com.deployforge.ai.AiProvider;
import com.deployforge.ai.AiProviderException;
import com.deployforge.ai.AiPromptFactory;
import com.deployforge.ai.AiResponseParser;
import com.deployforge.common.util.JsonCodec;
import com.deployforge.config.DeployForgeProperties;
import com.deployforge.config.HttpClientConfig;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Google Gemini implementation of {@link AiProvider}.
 *
 * <p>Requests {@code responseMimeType=application/json} and a low temperature: this is a diagnosis task,
 * not a creative one, and structured output is what the parser expects. The API key travels as a query
 * parameter because that is what the API requires - it is never logged.
 */
@Component
public class GeminiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);

    private final RestClient restClient;
    private final DeployForgeProperties properties;
    private final AiPromptFactory promptFactory;
    private final AiResponseParser responseParser;
    private final JsonCodec json;

    public GeminiAiProvider(
            @Qualifier(HttpClientConfig.AI_CLIENT) RestClient restClient,
            DeployForgeProperties properties,
            AiPromptFactory promptFactory,
            AiResponseParser responseParser,
            JsonCodec json) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptFactory = promptFactory;
        this.responseParser = responseParser;
        this.json = json;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.ai().gemini().apiKey());
    }

    @Override
    public AiAnalysisResult analyzeDeploymentFailure(AiAnalysisContext context) {
        String response =
                generate(
                        promptFactory.analysisSystemPrompt(),
                        promptFactory.analysisUserPrompt(context),
                        true);
        return responseParser
                .parse(response, name(), model())
                .orElseThrow(
                        () ->
                                new AiProviderException(
                                        "Gemini returned output that did not match the required schema", true));
    }

    @Override
    public AiChatResult chat(AiChatContext context) {
        String response =
                generate(promptFactory.chatSystemPrompt(), promptFactory.chatUserPrompt(context), false);
        if (!StringUtils.hasText(response)) {
            throw new AiProviderException("Gemini returned an empty answer", true);
        }
        return new AiChatResult(response.trim(), name(), model(), List.of());
    }

    private String model() {
        return properties.ai().gemini().model();
    }

    private String generate(String systemPrompt, String userPrompt, boolean jsonOutput) {
        Map<String, Object> generationConfig =
                jsonOutput
                        ? Map.of(
                                "temperature", 0.2,
                                "maxOutputTokens", 2048,
                                "responseMimeType", "application/json")
                        : Map.of("temperature", 0.3, "maxOutputTokens", 1024);

        Map<String, Object> body =
                Map.of(
                        "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                        "contents",
                                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                        "generationConfig", generationConfig);

        String url =
                properties.ai().gemini().baseUrl()
                        + "/v1beta/models/"
                        + model()
                        + ":generateContent?key="
                        + properties.ai().gemini().apiKey();

        try {
            String raw =
                    restClient
                            .post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
            return extractText(raw);
        } catch (HttpClientErrorException e) {
            // 4xx: bad key, quota, or a request the model refused. Not worth retrying as-is.
            log.warn("gemini_request_rejected status={}", e.getStatusCode().value());
            throw new AiProviderException(
                    "Gemini rejected the request (HTTP " + e.getStatusCode().value() + ")", false, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AiProviderException("Gemini is unavailable right now", true, e);
        }
    }

    /** Pulls the text out of {@code candidates[0].content.parts[*].text}. */
    private String extractText(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new AiProviderException("Gemini returned an empty response", true);
        }
        try {
            JsonNode root = json.mapper().readTree(rawResponse);
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                String blockReason = root.path("promptFeedback").path("blockReason").asText("");
                throw new AiProviderException(
                        blockReason.isEmpty()
                                ? "Gemini returned no candidates"
                                : "Gemini blocked the request: " + blockReason,
                        false);
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidates.get(0).path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
            return text.toString();
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Gemini response could not be parsed", true, e);
        }
    }
}
