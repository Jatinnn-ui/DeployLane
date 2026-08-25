package com.deployforge.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Small helper for the handful of places where structured data is persisted into a {@code TEXT}
 * column (AI evidence, suggested fixes, activity metadata).
 *
 * <p>Deliberately lenient on read: a malformed legacy row must not break an API response.
 */
@Component
public class JsonCodec {

    private static final Logger log = LoggerFactory.getLogger(JsonCodec.class);

    private final ObjectMapper objectMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Value could not be serialised to JSON", e);
        }
    }

    public <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("json_decode_failed type={} reason={}", type.getType(), e.getOriginalMessage());
            return fallback;
        }
    }

    public List<String> readStringList(String json) {
        return read(json, new TypeReference<List<String>>() {}, List.of());
    }

    public Map<String, Object> readMap(String json) {
        return read(json, new TypeReference<Map<String, Object>>() {}, Map.of());
    }

    public ObjectMapper mapper() {
        return objectMapper;
    }
}
