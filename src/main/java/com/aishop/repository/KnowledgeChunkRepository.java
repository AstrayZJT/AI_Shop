package com.aishop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aishop.domain.KnowledgeChunk;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {
    List<KnowledgeChunk> findTop10ByChunkTextContainingIgnoreCaseOrderByIdDesc(String chunkText);
}
