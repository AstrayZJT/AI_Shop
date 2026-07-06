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
import com.aishop.dto.AdminDtos.AdminAssistantAssignRequest;
import com.aishop.dto.AdminDtos.AdminAssistantReplyRequest;
import com.aishop.dto.AdminDtos.AdminAssistantSessionResponse;
import com.aishop.dto.AdminDtos.AdminUserResponse;
import com.aishop.dto.AdminDtos.DashboardMetricResponse;
import com.aishop.dto.AdminDtos.KnowledgeDocumentResponse;
import com.aishop.dto.AdminDtos.KnowledgeSearchResponse;
import com.aishop.dto.AdminDtos.OrderLogisticsUpdateRequest;
import com.aishop.dto.AdminDtos.ProductUpsertRequest;
import com.aishop.dto.AdminDtos.RefundReviewRequest;
import com.aishop.dto.AdminDtos.ReturnInstructionRequest;
import com.aishop.dto.AdminDtos.UpdateOrderStatusRequest;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.dto.PromotionDtos.PromotionResponse;
import com.aishop.dto.PromotionDtos.PromotionUpsertRequest;
import com.aishop.dto.ProductDtos.ProductResponse;
import com.aishop.dto.ProductDtos.ProductReviewResponse;
import com.aishop.service.AdminService;
import com.aishop.service.AuthService;
import com.aishop.service.KnowledgeService;
import com.aishop.service.PromotionService;
import com.aishop.service.ProductReviewService;

import jakarta.servlet.http.HttpSession;

@RestController
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;
    private final KnowledgeService knowledgeService;
    private final ProductReviewService productReviewService;
    private final PromotionService promotionService;

    public AdminController(AuthService authService,
                           AdminService adminService,
                           KnowledgeService knowledgeService,
                           ProductReviewService productReviewService,
                           PromotionService promotionService) {
        this.authService = authService;
        this.adminService = adminService;
        this.knowledgeService = knowledgeService;
        this.productReviewService = productReviewService;
        this.promotionService = promotionService;
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

    @GetMapping("/api/admin/promotions")
    public List<PromotionResponse> promotions(HttpSession session) {
        authService.requireAdmin(session);
        return promotionService.listAll();
    }

    @PostMapping("/api/admin/promotions")
    public PromotionResponse createPromotion(HttpSession session, @RequestBody PromotionUpsertRequest request) {
        authService.requireAdmin(session);
        return promotionService.create(request);
    }

    @PutMapping("/api/admin/promotions/{id}")
    public PromotionResponse updatePromotion(HttpSession session,
                                             @PathVariable Long id,
                                             @RequestBody PromotionUpsertRequest request) {
        authService.requireAdmin(session);
        return promotionService.update(id, request);
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
        var admin = authService.requireAdmin(session);
        return adminService.updateOrderStatus(id, admin, request.status(), request.note(), request.shippingCarrier(), request.trackingNo());
    }

    @PatchMapping("/api/admin/orders/{id}/refund-review")
    public AdminOrderResponse reviewRefund(HttpSession session,
                                           @PathVariable Long id,
                                           @RequestBody RefundReviewRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.reviewRefund(id, admin, request.approved(), request.note());
    }

    @PostMapping("/api/admin/orders/{id}/logistics-updates")
    public AdminOrderResponse appendLogisticsUpdate(HttpSession session,
                                                    @PathVariable Long id,
                                                    @RequestBody OrderLogisticsUpdateRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.appendLogisticsUpdate(id, admin, request.detail());
    }

    @PostMapping("/api/admin/orders/{id}/after-sales/return-instructions")
    public AdminOrderResponse provideReturnInstructions(HttpSession session,
                                                        @PathVariable Long id,
                                                        @RequestBody ReturnInstructionRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.provideReturnInstructions(id, admin, request.returnAddress(), request.reply());
    }

    @PostMapping("/api/admin/orders/{id}/after-sales/confirm-return-refund")
    public AdminOrderResponse confirmReturnAndRefund(HttpSession session,
                                                     @PathVariable Long id,
                                                     @RequestBody RefundReviewRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.confirmReturnAndRefund(id, admin, request.note());
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

    @GetMapping("/api/admin/reviews")
    public List<ProductReviewResponse> reviews(HttpSession session) {
        authService.requireAdmin(session);
        return productReviewService.listRecentReviews();
    }

    @GetMapping("/api/admin/assistant/sessions")
    public List<AdminAssistantSessionResponse> assistantSessions(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listAssistantSessions();
    }

    @GetMapping("/api/admin/assistant/escalations")
    public List<AdminAssistantSessionResponse> assistantEscalations(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listEscalatedAssistantSessions();
    }

    @GetMapping("/api/admin/assistant/sessions/{id}/messages")
    public List<AdminAssistantMessageResponse> assistantMessages(HttpSession session, @PathVariable Long id) {
        authService.requireAdmin(session);
        return adminService.assistantMessages(id);
    }

    @PostMapping("/api/admin/assistant/sessions/{id}/claim")
    public AdminAssistantSessionResponse claimAssistantSession(HttpSession session, @PathVariable Long id) {
        var admin = authService.requireAdmin(session);
        return adminService.claimAssistantSession(id, admin);
    }

    @PostMapping("/api/admin/assistant/sessions/{id}/assign")
    public AdminAssistantSessionResponse assignAssistantSession(HttpSession session,
                                                                @PathVariable Long id,
                                                                @RequestBody AdminAssistantAssignRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.assignAssistantSession(id, admin, request.adminUsername());
    }

    @PostMapping("/api/admin/assistant/sessions/{id}/reply")
    public AdminAssistantMessageResponse assistantReply(HttpSession session,
                                                        @PathVariable Long id,
                                                        @RequestBody AdminAssistantReplyRequest request) {
        var admin = authService.requireAdmin(session);
        return adminService.replyAssistantSession(id, admin, request.content(), request.resolve());
    }

    @GetMapping("/api/admin/assistant/drafts")
    public List<AdminAssistantDraftResponse> assistantDrafts(HttpSession session) {
        authService.requireAdmin(session);
        return adminService.listPendingAssistantDrafts();
    }
}
