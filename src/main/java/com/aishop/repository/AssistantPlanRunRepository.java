package com.aishop.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.assistant.state.AgentRunStatus;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantPlanRun;
import com.aishop.domain.AssistantSession;

public interface AssistantPlanRunRepository extends JpaRepository<AssistantPlanRun, Long> {
    Optional<AssistantPlanRun> findTopBySessionAndUserAndStatusInOrderByCreatedAtDesc(
            AssistantSession session,
            AppUser user,
            Collection<AgentRunStatus> statuses);
}
