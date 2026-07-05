package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.dto.KnowledgeDtos.SearchResponse;
import com.aishop.service.KnowledgeService;

@RestController
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/api/knowledge/import")
    public Object importDoc(@RequestBody ImportRequest request) {
        return knowledgeService.importDocument(request);
    }

    @GetMapping("/api/knowledge/search")
    public List<SearchResponse> search(@RequestParam String keyword) {
        return knowledgeService.search(keyword);
    }
}
