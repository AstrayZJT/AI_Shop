package com.aishop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.KnowledgeDocument;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
}
