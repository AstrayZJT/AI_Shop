package com.aishop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aishop.assistant.state.PendingActionStatus;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantPlanRun;
import com.aishop.domain.AssistantTaskRun;
import com.aishop.domain.PendingAssistantAction;

import jakarta.persistence.LockModeType;

public interface PendingAssistantActionRepository extends JpaRepository<PendingAssistantAction, Long> {
    Optional<PendingAssistantAction> findByTaskRun(AssistantTaskRun taskRun);

    Optional<PendingAssistantAction> findTopByPlanRunAndStatusOrderByCreatedAtDesc(
            AssistantPlanRun planRun,
            PendingActionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select action from PendingAssistantAction action where action.id = :id and action.user = :user")
    Optional<PendingAssistantAction> findOwnedByIdForUpdate(
            @Param("id") Long id,
            @Param("user") AppUser user);
}
