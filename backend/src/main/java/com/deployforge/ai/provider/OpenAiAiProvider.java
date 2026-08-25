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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * OpenAI implementation of {@link AiProvider}, using the chat completions API.
 *
 * <p>Exists to prove the abstraction holds: adding it required no change to the analysis service, the
 * prompt factory or the parser - only a new bean and a configuration value.
 */
@Component
public class OpenAiAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAiProvider.class);

    private final RestClient restClient;
    private final DeployForgeProperties properties;
    private final AiPromptFactory promptFactory;
    private final AiResponseParser responseParser;
    private final JsonCodec json;

    public OpenAiAiProvider(
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
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.ai().openai().apiKey());
    }

    @Override
    public AiAnalysisResult analyzeDeploymentFailure(AiAnalysisContext context) {
        String response =
                complete(
                        promptFactory.analysisSystemPrompt(),
                        promptFactory.analysisUserPrompt(context),
                        true);
        return responseParser
                .parse(response, name(), model())
                .orElseThrow(
                        () ->
                                new AiProviderException(
                                        "OpenAI returned output that did not match the required schema", true));
    }

    @Override
    public AiChatResult chat(AiChatContext context) {
        String response =
                complete(promptFactory.chatSystemPrompt(), promptFactory.chatUserPrompt(context), false);
        if (!StringUtils.hasText(response)) {
            throw new AiProviderException("OpenAI returned an empty answer", true);
        }
        return new AiChatResult(response.trim(), name(), model(), List.of());
    }

    private String model() {
        return properties.ai().openai().model();
    }

    private String complete(String systemPrompt, String userPrompt, boolean jsonOutput) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("temperature", jsonOutput ? 0.2 : 0.3);
        body.put("max_tokens", jsonOutput ? 2048 : 1024);
        if (jsonOutput) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        try {
            String raw =
                    restClient
                            .post()
                            .uri(properties.ai().openai().baseUrl() + "/v1/chat/completions")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.ai().openai().apiKey())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(String.class);
            return extractText(raw);
        } catch (HttpClientErrorException e) {
            log.warn("openai_request_rejected status={}", e.getStatusCode().value());
            throw new AiProviderException(
                    "OpenAI rejected the request (HTTP " + e.getStatusCode().value() + ")", false, e);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new AiProviderException("OpenAI is unavailable right now", true, e);
        }
    }

    private String extractText(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new AiProviderException("OpenAI returned an empty response", true);
        }
        try {
            JsonNode root = json.mapper().readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiProviderException("OpenAI returned no choices", true);
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("OpenAI response could not be parsed", true, e);
        }
    }
}
