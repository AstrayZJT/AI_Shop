package com.aishop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantSession;

public interface AssistantSessionRepository extends JpaRepository<AssistantSession, Long> {
    List<AssistantSession> findByUserOrderByCreatedAtDesc(AppUser user);
    Optional<AssistantSession> findByIdAndUser(Long id, AppUser user);
}
