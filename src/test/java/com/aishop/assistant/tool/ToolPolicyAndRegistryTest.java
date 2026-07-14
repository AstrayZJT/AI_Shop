package com.aishop.assistant.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

class ToolPolicyAndRegistryTest {

    @Test
    void rejectsAutoExecutionForPrepareOnlyTool() {
        assertThatThrownBy(() -> new ToolPolicy(
                "danger", "danger tool", AssistantAction.CANCEL_ORDER, ToolRiskLevel.PREPARE_ONLY, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只有 READ_ONLY 工具");
    }

    @Test
    void registersToolByActionAndName() {
        AssistantTool tool = fakeTool("query_order", AssistantAction.QUERY_ORDER);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(tool));

        assertThat(registry.find(AssistantAction.QUERY_ORDER)).contains(tool);
        assertThat(registry.find("query_order")).contains(tool);
        assertThat(registry.specifications()).hasSize(1);
        assertThat(registry.policies()).extracting(ToolPolicy::name).containsExactly("query_order");
    }

    @Test
    void rejectsDuplicateAction() {
        AssistantTool first = fakeTool("query_order", AssistantAction.QUERY_ORDER);
        AssistantTool second = fakeTool("query_order_v2", AssistantAction.QUERY_ORDER);

        assertThatThrownBy(() -> new AssistantToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复的工具 action");
    }

    @Test
    void rejectsDuplicateName() {
        AssistantTool first = fakeTool("same", AssistantAction.QUERY_ORDER);
        AssistantTool second = fakeTool("same", AssistantAction.QUERY_LOGISTICS);

        assertThatThrownBy(() -> new AssistantToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复的工具 name");
    }

    private AssistantTool fakeTool(String name, AssistantAction action) {
        ToolPolicy policy = new ToolPolicy(name, name + " description", action, ToolRiskLevel.READ_ONLY, true);
        ToolSpecification specification = ToolSpecification.builder()
                .name(name)
                .description(policy.description())
                .parameters(JsonObjectSchema.builder().additionalProperties(false).build())
                .build();
        return new AssistantTool() {
            @Override
            public ToolPolicy policy() {
                return policy;
            }

            @Override
            public ToolSpecification specification() {
                return specification;
            }

            @Override
            public PreparedToolCall prepare(ToolContext context, Map<String, Object> arguments) {
                return new PreparedToolCall(action, name, ToolRiskLevel.READ_ONLY, arguments, null, Map.of());
            }

            @Override
            public ToolExecutionOutcome execute(ToolContext context, PreparedToolCall call) {
                return new ToolExecutionOutcome(Map.of(), "ok");
            }
        };
    }
}
