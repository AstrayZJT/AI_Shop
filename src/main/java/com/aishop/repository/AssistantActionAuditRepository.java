package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AssistantActionAudit;
import com.aishop.domain.PendingAssistantAction;

public interface AssistantActionAuditRepository extends JpaRepository<AssistantActionAudit, Long> {
    List<AssistantActionAudit> findByPendingActionOrderByCreatedAtAsc(PendingAssistantAction pendingAction);
}
