package com.aishop.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aishop.dto.AdminDtos.AdminOrderResponse;
import com.aishop.dto.AdminDtos.AdminAssistantDraftResponse;
import com.aishop.dto.AdminDtos.AdminAssistantMessageResponse;
import com.aishop.dto.AdminDtos.AdminAssistantSessionResponse;
import com.aishop.dto.AdminDtos.AdminUserResponse;
import com.aishop.dto.AdminDtos.DashboardMetricResponse;
import com.aishop.dto.AdminDtos.KnowledgeDocumentResponse;
import com.aishop.dto.AdminDtos.KnowledgeSearchResponse;
import com.aishop.dto.AdminDtos.ProductUpsertRequest;
import com.aishop.dto.AdminDtos.RefundReviewRequest;
import com.aishop.dto.AdminDtos.UpdateOrderStatusRequest;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.service.AdminService;
import com.aishop.service.AuthService;
import com.aishop.service.KnowledgeService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;
    private final KnowledgeService knowledgeService;

    public AdminController(AuthService authService,
                           AdminService adminService,
                           KnowledgeService knowledgeService) {
        this.authService = authService;
        this.adminService = adminService;
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/api/admin/dashboard")
    public DashboardMetricResponse dashboard(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.dashboard();
    }

    @GetMapping("/api/admin/products")
    public List<ProductResponse> products(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listProducts();
    }

    @PostMapping("/api/admin/products")
    public ProductResponse createProduct(HttpSession session, @RequestBody ProductUpsertRequest request) {
        authService.requireAdmin(session);
        return adminService.createProduct(request);
    }

    @PutMapping("/api/admin/products/{id}")
    public ProductResponse updateProduct(HttpSession session,
                                         @PathVariable Long id,
                                         @RequestBody ProductUpsertRequest request) {
        authService.requireAdmin(session);
        return adminService.updateProduct(id, request);
    }

    @GetMapping("/api/admin/orders")
    public List<AdminOrderResponse> orders(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listOrders();
    }

    @PatchMapping("/api/admin/orders/{id}/status")
    public AdminOrderResponse updateOrderStatus(HttpSession session,
                                                @PathVariable Long id,
                                                @RequestBody UpdateOrderStatusRequest request) {
        authService.requireAdmin(session);
        return adminService.updateOrderStatus(id, request.status(), request.note(), request.shippingCarrier(), request.trackingNo());
    }

    @PatchMapping("/api/admin/orders/{id}/refund-review")
    public AdminOrderResponse reviewRefund(HttpSession session,
                                           @PathVariable Long id,
                                           @RequestBody RefundReviewRequest request) {
        authService.requireAdmin(session);
        return adminService.reviewRefund(id, request.approved(), request.note());
    }

    @GetMapping("/api/admin/knowledge/documents")
    public List<KnowledgeDocumentResponse> knowledgeDocuments(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listKnowledgeDocuments();
    }

    @GetMapping("/api/admin/knowledge/search")
    public KnowledgeSearchResponse searchKnowledge(HttpSession session, @RequestParam String keyword) {
        authService.requireAdmin(session);
        return adminService.searchKnowledge(keyword);
    }

    @PostMapping("/api/admin/knowledge/import")
    public Object importKnowledge(HttpSession session, @RequestBody ImportRequest request) {
        authService.requireAdmin(session);
        return knowledgeService.importDocument(request);
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserResponse> users(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listUsers();
    }

    @GetMapping("/api/admin/assistant/sessions")
    public List<AdminAssistantSessionResponse> assistantSessions(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listAssistantSessions();
    }

    @GetMapping("/api/admin/assistant/sessions/{id}/messages")
    public List<AdminAssistantMessageResponse> assistantMessages(HttpSession session, @PathVariable Long id) {
        authService.requireAdmin(session);
        return adminService.assistantMessages(id);
    }

    @GetMapping("/api/admin/assistant/drafts")
    public List<AdminAssistantDraftResponse> assistantDrafts(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listPendingAssistantDrafts();
    }
}
