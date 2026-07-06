package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {
    List<AssistantMessage> findBySessionOrderByCreatedAtAsc(AssistantSession session);
    long countBySession(AssistantSession session);
}
