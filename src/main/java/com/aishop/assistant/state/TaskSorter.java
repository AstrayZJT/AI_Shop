package com.aishop.assistant.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.springframework.stereotype.Component;

import com.aishop.assistant.model.AssistantPlan;
import com.aishop.assistant.model.AssistantTask;

@Component
public class TaskSorter {

    public List<AssistantTask> sort(AssistantPlan plan) {
        Map<String, AssistantTask> tasks = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (AssistantTask task : plan.tasks()) {
            tasks.put(task.taskId(), task);
            indegree.put(task.taskId(), task.dependsOn().size());
            graph.put(task.taskId(), new ArrayList<>());
        }
        for (AssistantTask task : plan.tasks()) {
            for (String dependency : task.dependsOn()) {
                graph.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(task.taskId());
            }
        }
        Queue<String> queue = new ArrayDeque<>();
        for (String taskId : tasks.keySet()) {
            if (indegree.get(taskId) == 0) {
                queue.add(taskId);
            }
        }
        List<AssistantTask> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove();
            sorted.add(tasks.get(current));
            for (String next : graph.getOrDefault(current, List.of())) {
                int nextDegree = indegree.computeIfPresent(next, (ignored, degree) -> degree - 1);
                if (nextDegree == 0) {
                    queue.add(next);
                }
            }
        }
        if (sorted.size() != tasks.size()) {
            throw new IllegalArgumentException("任务依赖存在循环，无法执行工具");
        }
        return List.copyOf(sorted);
    }
}
