package com.aishop.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.aishop.domain.AfterSalesCase;
import com.aishop.domain.AppUser;
import com.aishop.domain.AssistantMessage;
import com.aishop.domain.AssistantSession;
import com.aishop.domain.CustomerProductEvent;
import com.aishop.domain.OrderItem;
import com.aishop.domain.OrderStatus;
import com.aishop.domain.PendingOrderDraft;
import com.aishop.domain.Product;
import com.aishop.domain.ProductCategory;
import com.aishop.domain.ProductFavorite;
import com.aishop.domain.ProductReview;
import com.aishop.domain.ShopOrder;
import com.aishop.domain.ShippingAddress;
import com.aishop.domain.UserRole;
import com.aishop.dto.KnowledgeDtos.ImportRequest;
import com.aishop.repository.AfterSalesCaseRepository;
import com.aishop.repository.AppUserRepository;
import com.aishop.repository.AssistantMessageRepository;
import com.aishop.repository.AssistantSessionRepository;
import com.aishop.repository.CustomerProductEventRepository;
import com.aishop.repository.KnowledgeDocumentRepository;
import com.aishop.repository.OrderItemRepository;
import com.aishop.repository.PendingOrderDraftRepository;
import com.aishop.repository.ProductCategoryRepository;
import com.aishop.repository.ProductFavoriteRepository;
import com.aishop.repository.ProductRepository;
import com.aishop.repository.ProductReviewRepository;
import com.aishop.repository.ShopOrderRepository;
import com.aishop.repository.ShippingAddressRepository;
import com.aishop.service.CustomerBehaviorService;
import com.aishop.service.KnowledgeService;
import com.aishop.service.OrderInvoiceService;
import com.aishop.service.PromotionService;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(AppUserRepository userRepository,
                               ProductCategoryRepository categoryRepository,
                               ProductRepository productRepository,
                               KnowledgeDocumentRepository knowledgeDocumentRepository,
                               KnowledgeService knowledgeService,
                               ShopOrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               ProductReviewRepository productReviewRepository,
                               AfterSalesCaseRepository afterSalesCaseRepository,
                               AssistantSessionRepository assistantSessionRepository,
                               AssistantMessageRepository assistantMessageRepository,
                               PendingOrderDraftRepository pendingOrderDraftRepository,
                               ProductFavoriteRepository productFavoriteRepository,
                               ShippingAddressRepository shippingAddressRepository,
                               CustomerProductEventRepository customerProductEventRepository,
                               OrderInvoiceService orderInvoiceService,
                               PromotionService promotionService) {
        return args -> {
            var encoder = new BCryptPasswordEncoder();

            AppUser demoUser = userRepository.findByUsername("demo").orElseGet(() -> {
                var user = new AppUser();
                user.setUsername("demo");
                user.setDisplayName("演示用户");
                user.setPasswordHash(encoder.encode("demo123"));
                user.setPhone("13800000001");
                user.setShippingAddress("上海市浦东新区演示路 88 号");
                user.setPreferencesSummary("更关注通勤、降噪、轻办公和性价比。");
                user.setRole(UserRole.CUSTOMER);
                return userRepository.save(user);
            });
            if (demoUser.getRole() != UserRole.CUSTOMER) {
                demoUser.setRole(UserRole.CUSTOMER);
            }
            demoUser.setDisplayName("演示用户");
            demoUser.setPhone("13800000001");
            demoUser.setShippingAddress("上海市浦东新区演示路 88 号");
            demoUser.setPreferencesSummary("更关注通勤、降噪、轻办公和性价比。");
            userRepository.save(demoUser);

            AppUser adminUser = userRepository.findByUsername("admin").orElseGet(() -> {
                var admin = new AppUser();
                admin.setUsername("admin");
                admin.setDisplayName("平台管理员");
                admin.setPasswordHash(encoder.encode("admin123"));
                admin.setPhone("13900000001");
                admin.setShippingAddress("上海市徐汇区管理中心 1 号");
                admin.setPreferencesSummary("负责商品、订单、售后和知识库运营。");
                admin.setRole(UserRole.ADMIN);
                return userRepository.save(admin);
            });
            adminUser.setDisplayName("平台管理员");
            adminUser.setPhone("13900000001");
            adminUser.setShippingAddress("上海市徐汇区管理中心 1 号");
            adminUser.setPreferencesSummary("负责商品、订单、售后和知识库运营。");
            adminUser.setRole(UserRole.ADMIN);
            userRepository.save(adminUser);

            AppUser supportUser = userRepository.findByUsername("support1").orElseGet(() -> {
                var support = new AppUser();
                support.setUsername("support1");
                support.setDisplayName("客服专员一号");
                support.setPasswordHash(encoder.encode("support123"));
                support.setPhone("13700000001");
                support.setShippingAddress("上海市静安区客服中心 9 号");
                support.setPreferencesSummary("负责 AI 客服会话接管、物流跟进和售后协调。");
                support.setRole(UserRole.ADMIN);
                return userRepository.save(support);
            });
            supportUser.setDisplayName("客服专员一号");
            supportUser.setPhone("13700000001");
            supportUser.setShippingAddress("上海市静安区客服中心 9 号");
            supportUser.setPreferencesSummary("负责 AI 客服会话接管、物流跟进和售后协调。");
            supportUser.setRole(UserRole.ADMIN);
            userRepository.save(supportUser);

            var electronics = categoryRepository.findByName("数码").orElseGet(() -> {
                var category = new ProductCategory();
                category.setName("数码");
                category.setDescription("手机、耳机、平板与移动办公设备。");
                return categoryRepository.save(category);
            });

            var home = categoryRepository.findByName("家居").orElseGet(() -> {
                var category = new ProductCategory();
                category.setName("家居");
                category.setDescription("品质生活、智能家电与家居清洁。");
                return categoryRepository.save(category);
            });

            var sports = categoryRepository.findByName("运动").orElseGet(() -> {
                var category = new ProductCategory();
                category.setName("运动");
                category.setDescription("通勤穿搭、日常健身与户外轻运动。");
                return categoryRepository.save(category);
            });

            seedProduct(productRepository, electronics, "PHONE-001", "曜石 AI 手机", "1 英寸主摄、全天续航，适合拍照、通勤与重度办公。", "5299.00", 18,
                    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, electronics, "AUDIO-002", "降噪头戴耳机 Pro", "42dB 主动降噪，支持空间音频和多设备切换。", "1899.00", 32,
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, electronics, "PAD-003", "轻办公平板 12", "适合会议记录、追剧和轻量绘图，附带磁吸键盘。", "3299.00", 14,
                    "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, home, "HOME-004", "空气净化器 Max", "适合卧室与客厅，支持睡眠模式和空气质量联动。", "2399.00", 10,
                    "https://images.unsplash.com/photo-1585771724684-38269d6639fd?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, home, "COFFEE-005", "意式胶囊咖啡机", "3 档浓度调节，适合晨间快速出杯。", "1299.00", 22,
                    "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=80");
            seedProduct(productRepository, sports, "SPORT-006", "城市通勤跑鞋", "缓震中底和轻量鞋面，适合日常跑步与步行通勤。", "699.00", 40,
                    "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80");

            seedKnowledgeDocument(knowledgeDocumentRepository, knowledgeService,
                    "退款与售后政策",
                    "policy",
                    """
                    1. 已发货订单和已完成订单支持用户在线发起退款申请，需填写退款原因，平台客服会在 24 小时内审核。
                    2. 已确认但尚未发货的订单，用户可直接在线取消；如已进入发货流程，则需要联系客服协助处理。
                    3. 数码商品如出现质量问题，支持在签收后 7 天内申请售后，平台会结合订单状态、问题描述和商品情况给出处理方案。
                    """);
            seedKnowledgeDocument(knowledgeDocumentRepository, knowledgeService,
                    "发货与物流说明",
                    "faq",
                    """
                    1. 订单支付或确认后通常会在 24 小时内进入处理阶段，处理完成后更新为已发货。
                    2. 已发货订单支持用户在客户端查看状态，并在收到商品后确认收货。
                    3. 如遇节假日、大促或偏远地区配送，物流时效可能延长，建议结合订单状态和物流节点统一说明。
                    """);
            seedKnowledgeDocument(knowledgeDocumentRepository, knowledgeService,
                    "商品咨询与推荐指引",
                    "faq",
                    """
                    1. AI 客服在推荐商品时应优先参考商品分类、库存、价格、描述与最新评价，不推荐无库存商品。
                    2. 当用户询问通勤、降噪、轻办公、居家生活等场景时，应优先结合对应商品特点进行推荐。
                    3. 当知识库命中售后或物流规则时，应先引用规则，再结合用户订单状态给出处理建议。
                    """);
            seedKnowledgeDocument(knowledgeDocumentRepository, knowledgeService,
                    "七天无理由退货规则",
                    "policy",
                    """
                    1. 符合条件的商品支持自签收次日起 7 个自然日内申请七天无理由退货。
                    2. 商品应保持完好，配件、赠品、包装和防伪标识齐全，不影响二次销售；退回前需要在订单售后入口提交申请。
                    3. 定制商品、已激活或影响二次销售的数码商品不适用七天无理由退货。质量问题按照售后政策另行处理。
                    """);
            seedKnowledgeDocument(knowledgeDocumentRepository, knowledgeService,
                    "发票与保修规则",
                    "policy",
                    """
                    1. 用户可以在订单详情提交电子发票申请，填写发票抬头、税号和接收邮箱；平台审核后发送电子发票。
                    2. 普通商品保修期限和范围以商品详情及厂商政策为准。申请保修时需要提供订单信息和故障描述。
                    3. 人为损坏、非授权拆修等情况不属于免费保修范围，具体结论以售后检测结果为准。
                    """);

            promotionService.seedPromotion(
                    "COMMUTE50",
                    "通勤装备满 999 减 50",
                    "适合耳机、跑鞋与轻便数码，满 999 自动可用。",
                    PromotionService.DISCOUNT_TYPE_FIXED,
                    "50.00",
                    "999.00",
                    true,
                    Instant.now().plus(Duration.ofDays(45)));
            promotionService.seedPromotion(
                    "SUMMER10",
                    "夏季精选 9 折",
                    "部分大额订单可享 9 折优惠，适合轻办公与数码大件。",
                    PromotionService.DISCOUNT_TYPE_PERCENT,
                    "10.00",
                    "2000.00",
                    true,
                    Instant.now().plus(Duration.ofDays(30)));

            seedDemoBusinessData(
                    demoUser,
                    supportUser,
                    productRepository,
                    orderRepository,
                    orderItemRepository,
                    productReviewRepository,
                    afterSalesCaseRepository,
                    assistantSessionRepository,
                    assistantMessageRepository,
                    pendingOrderDraftRepository,
                    productFavoriteRepository,
                    shippingAddressRepository,
                    customerProductEventRepository,
                    orderInvoiceService);
        };
    }

    private void seedProduct(ProductRepository productRepository,
                             ProductCategory category,
                             String sku,
                             String name,
                             String description,
                             String price,
                             int stock,
                             String imageUrl) {
        Product product = productRepository.findBySku(sku).orElseGet(Product::new);
        product.setSku(sku);
        product.setName(name);
        product.setDescription(description);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setCategory(category);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
    }

    private void seedKnowledgeDocument(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                       KnowledgeService knowledgeService,
                                       String title,
                                       String docType,
                                       String content) {
        if (knowledgeDocumentRepository.existsByTitle(title)) {
            return;
        }
        knowledgeService.importDocument(new ImportRequest(title, docType, content));
    }

    private void seedDemoBusinessData(AppUser demoUser,
                                      AppUser supportUser,
                                      ProductRepository productRepository,
                                      ShopOrderRepository orderRepository,
                                      OrderItemRepository orderItemRepository,
                                      ProductReviewRepository productReviewRepository,
                                      AfterSalesCaseRepository afterSalesCaseRepository,
                                      AssistantSessionRepository assistantSessionRepository,
                                      AssistantMessageRepository assistantMessageRepository,
                                      PendingOrderDraftRepository pendingOrderDraftRepository,
                                      ProductFavoriteRepository productFavoriteRepository,
                                      ShippingAddressRepository shippingAddressRepository,
                                      CustomerProductEventRepository customerProductEventRepository,
                                      OrderInvoiceService orderInvoiceService) {
        Product audio = requireProduct(productRepository, "AUDIO-002");
        Product phone = requireProduct(productRepository, "PHONE-001");
        Product tablet = requireProduct(productRepository, "PAD-003");
        Product purifier = requireProduct(productRepository, "HOME-004");
        Product runningShoes = requireProduct(productRepository, "SPORT-006");
        Instant now = Instant.now();

        seedFavorite(productFavoriteRepository, demoUser, audio);
        seedFavorite(productFavoriteRepository, demoUser, tablet);
        seedAddress(shippingAddressRepository, demoUser, "家", "演示用户", "13800000001", "上海市浦东新区演示路 88 号", true);
        seedAddress(shippingAddressRepository, demoUser, "公司", "演示用户", "13800000001", "上海市徐汇区漕溪北路 399 号 12 层", false);
        seedProductEvent(customerProductEventRepository, demoUser, audio, CustomerBehaviorService.EVENT_VIEW, "demo-seed", "首页推荐区查看", 1);
        seedProductEvent(customerProductEventRepository, demoUser, tablet, CustomerBehaviorService.EVENT_AI_CONSULT, "demo-seed", "咨询轻办公平板对比", 1);
        seedProductEvent(customerProductEventRepository, demoUser, phone, CustomerBehaviorService.EVENT_ADD_TO_CART, "demo-seed", "加入购物车后继续比较", 1);
        seedProductEvent(customerProductEventRepository, demoUser, audio, CustomerBehaviorService.EVENT_CHECKOUT, "demo-seed", "DEMO-ORDER-1001", 1);

        SeededOrder completedAudioOrder = seedOrder(
                orderRepository,
                orderItemRepository,
                demoUser,
                audio,
                "DEMO-ORDER-1001",
                OrderStatus.COMPLETED,
                1,
                "上海市浦东新区星图路 88 号 18 楼",
                "支付宝",
                "PAY-DEMO-1001",
                now.minus(Duration.ofDays(6)),
                now.minus(Duration.ofDays(5)),
                "顺丰速运",
                "SF1001001",
                "演示完成订单，可用于查看评价、时间线和 AI 订单问答。");
        orderInvoiceService.seedInvoice(
                completedAudioOrder.order(),
                OrderInvoiceService.STATUS_ISSUED,
                OrderInvoiceService.HEADER_COMPANY,
                "上海星图科技有限公司",
                "91310000MA1DEMO88X",
                "finance@demo-shop.cn",
                "用于企业报销留档",
                "电子发票已发送至企业邮箱",
                "INV-DEMO-1001",
                now.minus(Duration.ofDays(5)),
                now.minus(Duration.ofDays(4)),
                now.minus(Duration.ofDays(4)));
        seedReview(
                productReviewRepository,
                demoUser,
                audio,
                completedAudioOrder.order(),
                completedAudioOrder.item(),
                5,
                "降噪很稳，地铁和办公室都安静很多，佩戴半天也不会压耳。");

        SeededOrder completedPhoneOrder = seedOrder(
                orderRepository,
                orderItemRepository,
                demoUser,
                phone,
                "DEMO-ORDER-1002",
                OrderStatus.COMPLETED,
                1,
                "上海市浦东新区星图路 88 号 18 楼",
                "微信支付",
                "PAY-DEMO-1002",
                now.minus(Duration.ofDays(12)),
                now.minus(Duration.ofDays(11)),
                "京东物流",
                "JD1002002",
                "演示已完成手机订单，方便客户端展示不同商品的口碑数据。");
        seedReview(
                productReviewRepository,
                demoUser,
                phone,
                completedPhoneOrder.order(),
                completedPhoneOrder.item(),
                4,
                "拍照和续航都不错，适合日常通勤和临时办公，机身发热控制也还可以。");

        SeededOrder shippedTabletOrder = seedOrder(
                orderRepository,
                orderItemRepository,
                demoUser,
                tablet,
                "DEMO-ORDER-1003",
                OrderStatus.SHIPPED,
                1,
                "上海市徐汇区漕溪北路 399 号",
                "模拟支付",
                "PAY-DEMO-1003",
                now.minus(Duration.ofDays(2)),
                now.minus(Duration.ofHours(28)),
                "中通快递",
                "ZT1003003",
                "演示物流在途订单，可用于客户端和 AI 查询配送节点。");
        orderInvoiceService.seedInvoice(
                shippedTabletOrder.order(),
                OrderInvoiceService.STATUS_REQUESTED,
                OrderInvoiceService.HEADER_PERSONAL,
                "演示用户",
                null,
                "demo@aishop.local",
                "请发送电子发票到常用邮箱",
                null,
                null,
                now.minus(Duration.ofDays(2)),
                null,
                null);

        SeededOrder refundOrder = seedOrder(
                orderRepository,
                orderItemRepository,
                demoUser,
                purifier,
                "DEMO-ORDER-1004",
                OrderStatus.REFUND_REQUESTED,
                1,
                "上海市静安区南京西路 188 号",
                "银行卡",
                "PAY-DEMO-1004",
                now.minus(Duration.ofDays(8)),
                now.minus(Duration.ofDays(7)),
                "圆通速递",
                "YT1004004",
                "演示售后退款申请，后台可直接查看审核与回寄信息。");
        seedAfterSalesCase(
                afterSalesCaseRepository,
                refundOrder.order(),
                "AWAITING_CUSTOMER_RETURN",
                "机器运行时有持续噪音，希望退货退款。",
                "已通过审核，请按回寄地址寄回，仓库验收后会继续完成退款。",
                true,
                "上海市嘉定区仓储路 66 号 AI Shop 售后仓",
                null,
                null,
                "请保留原包装与滤芯配件。",
                now.minus(Duration.ofDays(1)),
                now.minus(Duration.ofHours(20)),
                null,
                null);

        SeededOrder pendingPaymentOrder = seedOrder(
                orderRepository,
                orderItemRepository,
                demoUser,
                runningShoes,
                "DEMO-ORDER-1005",
                OrderStatus.PENDING_PAYMENT,
                1,
                "上海市浦东新区星图路 88 号 18 楼",
                null,
                null,
                null,
                null,
                null,
                null,
                "演示待支付订单，可从客户端、管理端或 AI 客服触发支付流程。");

        pendingPaymentOrder.order().setPromotionCode("COMMUTE50");
        pendingPaymentOrder.order().setPromotionTitle("通勤装备满 999 减 50");
        pendingPaymentOrder.order().setDiscountAmount(new BigDecimal("50.00"));
        pendingPaymentOrder.order().setTotalAmount(pendingPaymentOrder.order().getOriginalAmount().subtract(new BigDecimal("50.00")));
        orderRepository.save(pendingPaymentOrder.order());

        AssistantSession activeDraftSession = seedAssistantSession(
                assistantSessionRepository,
                demoUser,
                null,
                "通勤耳机推荐和下单",
                "用户咨询降噪耳机并让 AI 生成下单草稿。",
                "PRODUCT_RECOMMENDATION",
                "ACTIVE",
                0L,
                0L);
        seedAssistantMessages(
                assistantMessageRepository,
                activeDraftSession,
                List.of(
                        new DemoMessage("user", "我想买一款适合通勤、最好降噪强一点的耳机。"),
                        new DemoMessage("assistant", "如果你更看重通勤和降噪，降噪头戴耳机 Pro 会是更稳妥的选择。我已经结合库存和价格帮你准备了一份下单草稿。")));
        seedPendingDraft(
                pendingOrderDraftRepository,
                demoUser,
                "assistant-" + activeDraftSession.getId(),
                """
                {"productId":%d,"productName":"%s","quantity":1,"unitPrice":%s,"totalAmount":%s,"note":"AI 已根据通勤降噪需求生成演示下单草稿"}
                """.formatted(audio.getId(), audio.getName(), audio.getPrice().toPlainString(), audio.getPrice().toPlainString()),
                "PENDING_CONFIRMATION");

        AssistantSession escalatedSession = seedAssistantSession(
                assistantSessionRepository,
                demoUser,
                supportUser,
                "物流异常人工跟进",
                "用户查询在途订单并申请人工继续跟踪物流异常。",
                "ORDER_TRACKING",
                "ESCALATED",
                0L,
                1L);
        escalatedSession.setAssignedAt(now.minus(Duration.ofHours(6)));
        escalatedSession.setFirstSupportReplyAt(now.minus(Duration.ofHours(5)));
        escalatedSession.setLastCustomerMessageAt(now.minus(Duration.ofHours(6)));
        escalatedSession.setLastSupportMessageAt(now.minus(Duration.ofHours(5)));
        assistantSessionRepository.save(escalatedSession);
        seedAssistantMessages(
                assistantMessageRepository,
                escalatedSession,
                List.of(
                        new DemoMessage("user", "帮我查一下 DEMO-ORDER-1003 的物流，页面看起来一直没更新。"),
                        new DemoMessage("assistant", "我查到订单已经发货并在运输中。如果你希望人工继续跟进，我可以帮你转接客服。"),
                        new DemoMessage("support", "已经为你登记物流跟进工单，若今晚还未更新节点，我们会继续联系承运商确认。")));
    }

    private Product requireProduct(ProductRepository productRepository, String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalStateException("Missing seeded product: " + sku));
    }

    private void seedFavorite(ProductFavoriteRepository productFavoriteRepository, AppUser user, Product product) {
        if (productFavoriteRepository.existsByUserAndProduct(user, product)) {
            return;
        }
        ProductFavorite favorite = new ProductFavorite();
        favorite.setUser(user);
        favorite.setProduct(product);
        productFavoriteRepository.save(favorite);
    }

    private void seedAddress(ShippingAddressRepository shippingAddressRepository,
                             AppUser user,
                             String label,
                             String recipientName,
                             String phone,
                             String addressLine,
                             boolean defaultAddress) {
        boolean exists = shippingAddressRepository.findByUserOrderByDefaultAddressDescCreatedAtDesc(user).stream()
                .anyMatch(address -> addressLine.equals(address.getAddressLine()));
        if (exists) {
            return;
        }
        ShippingAddress address = new ShippingAddress();
        address.setUser(user);
        address.setLabel(label);
        address.setRecipientName(recipientName);
        address.setPhone(phone);
        address.setAddressLine(addressLine);
        address.setDefaultAddress(defaultAddress);
        shippingAddressRepository.save(address);
    }

    private void seedProductEvent(CustomerProductEventRepository eventRepository,
                                  AppUser user,
                                  Product product,
                                  String eventType,
                                  String source,
                                  String detail,
                                  int quantity) {
        if (eventRepository.existsByUserAndProductAndEventTypeAndSource(user, product, eventType, source)) {
            return;
        }
        CustomerProductEvent event = new CustomerProductEvent();
        event.setUser(user);
        event.setProduct(product);
        event.setEventType(eventType);
        event.setSource(source);
        event.setDetail(detail);
        event.setQuantity(quantity);
        eventRepository.save(event);
    }

    private SeededOrder seedOrder(ShopOrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  AppUser user,
                                  Product product,
                                  String orderNo,
                                  OrderStatus status,
                                  int quantity,
                                  String shippingAddress,
                                  String paymentMethod,
                                  String paymentReference,
                                  Instant paidAt,
                                  Instant shippedAt,
                                  String shippingCarrier,
                                  String trackingNo,
                                  String riskNote) {
        ShopOrder order = orderRepository.findByOrderNo(orderNo).orElseGet(ShopOrder::new);
        order.setOrderNo(orderNo);
        order.setUser(user);
        order.setStatus(status);
        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        order.setOriginalAmount(totalAmount);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(totalAmount);
        order.setShippingAddress(shippingAddress);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentReference(paymentReference);
        order.setPaidAt(paidAt);
        order.setShippedAt(shippedAt);
        order.setShippingCarrier(shippingCarrier);
        order.setTrackingNo(trackingNo);
        order.setRiskNote(riskNote);
        order = orderRepository.save(order);

        OrderItem item = orderItemRepository.findByOrder(order).stream().findFirst().orElseGet(OrderItem::new);
        item.setOrder(order);
        item.setProductName(product.getName());
        item.setProductSku(product.getSku());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        item = orderItemRepository.save(item);
        return new SeededOrder(order, item);
    }

    private void seedReview(ProductReviewRepository productReviewRepository,
                            AppUser user,
                            Product product,
                            ShopOrder order,
                            OrderItem orderItem,
                            int rating,
                            String content) {
        ProductReview review = productReviewRepository.findByOrderItemAndUser(orderItem, user).orElseGet(ProductReview::new);
        review.setProduct(product);
        review.setUser(user);
        review.setOrder(order);
        review.setOrderItem(orderItem);
        review.setRating(rating);
        review.setContent(content);
        productReviewRepository.save(review);
    }

    private void seedAfterSalesCase(AfterSalesCaseRepository afterSalesCaseRepository,
                                    ShopOrder order,
                                    String status,
                                    String customerReason,
                                    String adminReply,
                                    boolean returnRequired,
                                    String returnAddress,
                                    String returnCarrier,
                                    String returnTrackingNo,
                                    String returnNote,
                                    Instant requestedAt,
                                    Instant adminRespondedAt,
                                    Instant customerShippedAt,
                                    Instant resolvedAt) {
        AfterSalesCase afterSalesCase = afterSalesCaseRepository.findByOrder(order).orElseGet(AfterSalesCase::new);
        afterSalesCase.setOrder(order);
        afterSalesCase.setStatus(status);
        afterSalesCase.setCustomerReason(customerReason);
        afterSalesCase.setAdminReply(adminReply);
        afterSalesCase.setReturnRequired(returnRequired);
        afterSalesCase.setReturnAddress(returnAddress);
        afterSalesCase.setReturnCarrier(returnCarrier);
        afterSalesCase.setReturnTrackingNo(returnTrackingNo);
        afterSalesCase.setReturnNote(returnNote);
        afterSalesCase.setRequestedAt(requestedAt);
        afterSalesCase.setAdminRespondedAt(adminRespondedAt);
        afterSalesCase.setCustomerShippedAt(customerShippedAt);
        afterSalesCase.setResolvedAt(resolvedAt);
        afterSalesCaseRepository.save(afterSalesCase);
    }

    private AssistantSession seedAssistantSession(AssistantSessionRepository assistantSessionRepository,
                                                  AppUser user,
                                                  AppUser assignedAdmin,
                                                  String title,
                                                  String summary,
                                                  String lastIntent,
                                                  String serviceStatus,
                                                  long supportUnreadCount,
                                                  long customerUnreadCount) {
        AssistantSession session = assistantSessionRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(item -> title.equals(item.getTitle()))
                .findFirst()
                .orElseGet(AssistantSession::new);
        session.setUser(user);
        session.setTitle(title);
        session.setSummary(summary);
        session.setLastIntent(lastIntent);
        session.setServiceStatus(serviceStatus);
        session.setAssignedAdmin(assignedAdmin);
        session.setSupportUnreadCount(supportUnreadCount);
        session.setCustomerUnreadCount(customerUnreadCount);
        return assistantSessionRepository.save(session);
    }

    private void seedAssistantMessages(AssistantMessageRepository assistantMessageRepository,
                                       AssistantSession session,
                                       List<DemoMessage> messages) {
        if (assistantMessageRepository.countBySession(session) > 0) {
            return;
        }
        for (DemoMessage message : messages) {
            AssistantMessage entity = new AssistantMessage();
            entity.setSession(session);
            entity.setRole(message.role());
            entity.setContent(message.content());
            assistantMessageRepository.save(entity);
        }
    }

    private void seedPendingDraft(PendingOrderDraftRepository pendingOrderDraftRepository,
                                  AppUser user,
                                  String threadId,
                                  String draftJson,
                                  String status) {
        PendingOrderDraft draft = pendingOrderDraftRepository.findTop1ByThreadIdOrderByCreatedAtDesc(threadId)
                .orElseGet(PendingOrderDraft::new);
        draft.setUser(user);
        draft.setThreadId(threadId);
        draft.setDraftJson(draftJson);
        draft.setStatus(status);
        pendingOrderDraftRepository.save(draft);
    }

    private record SeededOrder(ShopOrder order, OrderItem item) {}

    private record DemoMessage(String role, String content) {}
}
