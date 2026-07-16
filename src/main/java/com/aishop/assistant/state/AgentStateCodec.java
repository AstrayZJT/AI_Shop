package com.aishop.assistant.state;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.tool.TaskToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AgentStateCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public AgentStateCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Agent 状态序列化失败", ex);
        }
    }

    public AssistantPlan readPlan(String json) {
        return read(json, AssistantPlan.class);
    }

    public AssistantTask readTask(String json) {
        return read(json, AssistantTask.class);
    }

    public TaskToolResult readResult(String json) {
        return read(json, TaskToolResult.class);
    }

    public Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Agent Map 状态反序列化失败", ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Agent 状态反序列化失败: " + type.getSimpleName(), ex);
        }
    }
}
