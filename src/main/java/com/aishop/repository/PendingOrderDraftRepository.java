package com.aishop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.PendingOrderDraft;

public interface PendingOrderDraftRepository extends JpaRepository<PendingOrderDraft, Long> {
    Optional<PendingOrderDraft> findTop1ByThreadIdOrderByCreatedAtDesc(String threadId);
    Optional<PendingOrderDraft> findTop1ByThreadIdAndUserIdOrderByCreatedAtDesc(String threadId, Long userId);
}
