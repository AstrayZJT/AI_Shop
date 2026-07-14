package com.aishop.assistant.tool;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantAction;

import dev.langchain4j.agent.tool.ToolSpecification;

@Component
public class AssistantToolRegistry {

    private final Map<AssistantAction, AssistantTool> byAction;
    private final Map<String, AssistantTool> byName;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        Map<AssistantAction, AssistantTool> actions = new EnumMap<>(AssistantAction.class);
        Map<String, AssistantTool> names = new LinkedHashMap<>();
        for (AssistantTool tool : tools) {
            ToolPolicy policy = tool.policy();
            if (actions.putIfAbsent(policy.action(), tool) != null) {
                throw new IllegalStateException("重复的工具 action: " + policy.action());
            }
            if (names.putIfAbsent(policy.name(), tool) != null) {
                throw new IllegalStateException("重复的工具 name: " + policy.name());
            }
        }
        this.byAction = Collections.unmodifiableMap(actions);
        this.byName = Collections.unmodifiableMap(names);
    }

    public Optional<AssistantTool> find(AssistantAction action) {
        return Optional.ofNullable(byAction.get(action));
    }

    public Optional<AssistantTool> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<ToolSpecification> specifications() {
        return byName.values().stream().map(AssistantTool::specification).toList();
    }

    public List<ToolPolicy> policies() {
        return byName.values().stream().map(AssistantTool::policy).toList();
    }
}
