package com.aishop.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.aishop.domain.AppUser;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.service.AuthService;
import com.aishop.service.KnowledgeService;

import jakarta.servlet.http.HttpSession;

class KnowledgeControllerTest {

    @Test
    void requiresAdminBeforeImportingKnowledge() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        AuthService authService = mock(AuthService.class);
        HttpSession session = mock(HttpSession.class);
        when(authService.requireAdmin(session)).thenReturn(new AppUser());
        KnowledgeController controller = new KnowledgeController(knowledgeService, authService);
        ImportRequest request = new ImportRequest("规则", "policy", "规则正文");

        controller.importDoc(session, request);

        verify(authService).requireAdmin(session);
        verify(knowledgeService).importDocument(request);
    }

    @Test
    void doesNotImportWhenAdminAuthenticationFails() {
        KnowledgeService knowledgeService = mock(KnowledgeService.class);
        AuthService authService = mock(AuthService.class);
        HttpSession session = mock(HttpSession.class);
        when(authService.requireAdmin(session)).thenThrow(new IllegalStateException("请先登录管理员账号"));
        KnowledgeController controller = new KnowledgeController(knowledgeService, authService);

        assertThatThrownBy(() -> controller.importDoc(
                session, new ImportRequest("规则", "policy", "规则正文")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员");
    }
}
