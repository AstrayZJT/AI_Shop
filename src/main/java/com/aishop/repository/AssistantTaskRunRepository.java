package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AssistantPlanRun;
import com.aishop.domain.AssistantTaskRun;

public interface AssistantTaskRunRepository extends JpaRepository<AssistantTaskRun, Long> {
    List<AssistantTaskRun> findByPlanRunOrderByTaskOrderAsc(AssistantPlanRun planRun);
    Optional<AssistantTaskRun> findByPlanRunAndTaskId(AssistantPlanRun planRun, String taskId);
}
