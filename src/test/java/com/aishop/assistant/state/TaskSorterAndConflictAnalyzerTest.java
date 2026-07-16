package com.aishop.assistant.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aishop.assistant.model.AssistantAction;
import com.aishop.assistant.model.AssistantIntent;
import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;
import com.aishop.assistant.model.ExecutionMode;
import com.aishop.assistant.model.PlanType;

class TaskSorterAndConflictAnalyzerTest {

    @Test
    void sortsTasksByDependsOnInsteadOfModelArrayOrder() {
        AssistantTask first = task("t1", AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ, List.of());
        AssistantTask second = task("t2", AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM, List.of("t1"));

        List<AssistantTask> sorted = new TaskSorter().sort(
                new AssistantPlan(PlanType.MULTI_TASK, List.of(second, first), "test"));

        assertThat(sorted).extracting(AssistantTask::taskId).containsExactly("t1", "t2");
    }

    @Test
    void rejectsCycleEvenIfPlanBypassesEarlierValidation() {
        AssistantTask first = task("t1", AssistantAction.QUERY_ORDER, ExecutionMode.TOOL_READ, List.of("t2"));
        AssistantTask second = task("t2", AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM, List.of("t1"));

        assertThatThrownBy(() -> new TaskSorter().sort(
                new AssistantPlan(PlanType.MULTI_TASK, List.of(first, second), "cycle")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("循环");
    }

    @Test
    void findsCancelAndPayConflictForSameOrder() {
        AssistantTask cancel = task("t1", AssistantAction.CANCEL_ORDER, ExecutionMode.ASK_CONFIRM, List.of());
        AssistantTask pay = task("t2", AssistantAction.PAY_ORDER, ExecutionMode.ASK_CONFIRM, List.of());

        List<TaskConflict> conflicts = new TaskConflictAnalyzer().analyze(
                new AssistantPlan(PlanType.MULTI_TASK, List.of(cancel, pay), "conflict"));

        assertThat(conflicts).singleElement().satisfies(conflict -> {
            assertThat(conflict.leftTaskId()).isEqualTo("t1");
            assertThat(conflict.rightTaskId()).isEqualTo("t2");
            assertThat(conflict.targetRef()).isEqualTo("ORD-12345678");
        });
    }

    private AssistantTask task(String id,
                               AssistantAction action,
                               ExecutionMode mode,
                               List<String> dependsOn) {
        return new AssistantTask(
                id,
                AssistantIntent.ORDER,
                action,
                mode,
                Map.of("orderNo", "ORD-12345678"),
                List.of(),
                dependsOn,
                List.of(),
                0.9,
                "test");
    }
}
