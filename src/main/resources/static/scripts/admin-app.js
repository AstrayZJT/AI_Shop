const adminState = {
  user: null,
  dashboard: null,
  products: [],
  orders: [],
  knowledgeDocs: [],
  knowledgeMatches: [],
  knowledgeSearchKeyword: "",
  users: [],
  assistantSessions: [],
  assistantMessages: [],
  assistantEscalations: [],
  assistantDrafts: [],
  selectedAssistantSessionId: null,
  editingProductId: null,
  orderFilterStatus: "ALL",
  orderFilterKeyword: "",
  afterSalesFilter: "OPEN",
  assistantOpsFilter: "ALL",
};

const ADMIN_STATUS_LABELS = {
  DRAFT: "草稿",
  PENDING_CONFIRMATION: "待确认",
  PENDING_PAYMENT: "待支付",
  CONFIRMED: "待发货",
  PROCESSING: "处理中",
  SHIPPED: "已发货",
  COMPLETED: "已完成",
  REFUND_REQUESTED: "退款审核中",
  REFUNDED: "已退款",
  CANCELLED: "已取消",
};

const AFTER_SALES_STATUS_LABELS = {
  REQUESTED: "等待平台审核",
  AWAITING_CUSTOMER_RETURN: "等待用户回寄",
  RETURN_SHIPPED: "用户已回寄",
  REFUNDED: "售后已完成",
  REJECTED: "售后已驳回",
};

const ORDER_FILTERS = [
  { key: "ALL", label: "全部订单" },
  { key: "PENDING_PAYMENT", label: "待支付" },
  { key: "REFUND_REQUESTED", label: "退款审核" },
  { key: "PENDING_SHIPMENT", label: "待发货" },
  { key: "SHIPPED", label: "已发货" },
  { key: "COMPLETED", label: "已完成" },
  { key: "REFUNDED", label: "已退款" },
  { key: "CANCELLED", label: "已取消" },
];

const AFTER_SALES_FILTERS = [
  { key: "OPEN", label: "处理中" },
  { key: "REQUESTED", label: "待审核" },
  { key: "AWAITING_CUSTOMER_RETURN", label: "待用户回寄" },
  { key: "RETURN_SHIPPED", label: "待平台验收" },
  { key: "REFUNDED", label: "已完成" },
  { key: "REJECTED", label: "已驳回" },
];

const ASSISTANT_SERVICE_STATUS_LABELS = {
  ACTIVE: "AI 在线服务中",
  ESCALATED: "人工跟进中",
  RESOLVED: "人工已回复",
};
const ASSISTANT_OPS_FILTERS = [
  { key: "ALL", label: "全部会话" },
  { key: "MY_QUEUE", label: "我的待处理" },
  { key: "UNCLAIMED", label: "待认领" },
  { key: "WAITING", label: "待回复" },
  { key: "SLA_RISK", label: "首响超时" },
  { key: "CUSTOMER_UNREAD", label: "客户未读" },
  { key: "RESOLVED", label: "已结案" },
];
const ADMIN_AUTH_SCOPE = "admin";

function adminById(id) {
  return document.getElementById(id);
}

function adminAuthUrl(url) {
  return `${url}${url.includes("?") ? "&" : "?"}scope=${ADMIN_AUTH_SCOPE}`;
}

function adminApiUrl(url) {
  const baseUrl = String(window.AI_SHOP_API_BASE_URL || "").replace(/\/$/, "");
  if (!baseUrl || !url.startsWith("/")) {
    return url;
  }
  return `${baseUrl}${url}`;
}

function adminCredentialsMode() {
  return window.AI_SHOP_API_BASE_URL ? "include" : "same-origin";
}

async function adminFetchJson(url, options = {}) {
  const response = await fetch(adminApiUrl(url), {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    credentials: adminCredentialsMode(),
    ...options,
  });
  const text = await response.text();
  const data = text ? safeParseJson(text) : null;
  if (!response.ok) {
    throw new Error(data?.error || `HTTP ${response.status}`);
  }
  return data;
}

function safeParseJson(text) {
  try {
    return JSON.parse(text);
  } catch (error) {
    return { error: text };
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function statusLabel(status) {
  return ADMIN_STATUS_LABELS[status] || status;
}

function afterSalesStatusLabel(status) {
  return AFTER_SALES_STATUS_LABELS[status] || "售后处理中";
}

function assistantServiceStatusLabel(status) {
  return ASSISTANT_SERVICE_STATUS_LABELS[status] || ASSISTANT_SERVICE_STATUS_LABELS.ACTIVE;
}

function assistantServiceTagClass(status) {
  if (status === "ESCALATED") {
    return "warning";
  }
  if (status === "RESOLVED") {
    return "success";
  }
  return "";
}

function assistantMessageRoleLabel(role) {
  if (role === "user") {
    return "用户";
  }
  if (role === "support") {
    return "人工客服";
  }
  return "AI 客服";
}

function currentAdminAssistantSession() {
  return adminState.assistantSessions.find((item) => item.id === adminState.selectedAssistantSessionId) || null;
}

function adminOperators() {
  return adminState.users.filter((user) => user.role === "ADMIN");
}

function preferredAssignedAdminUsername(session) {
  const operators = adminOperators();
  if (!operators.length) {
    return "";
  }
  if (session?.assignedAdminUsername && operators.some((user) => user.username === session.assignedAdminUsername)) {
    return session.assignedAdminUsername;
  }
  if (adminState.user?.username && operators.some((user) => user.username === adminState.user.username)) {
    return adminState.user.username;
  }
  return operators[0].username;
}

function assistantOperatorOptionLabel(user) {
  const baseLabel = user.displayName && user.displayName !== user.username
    ? `${user.displayName} (${user.username})`
    : (user.displayName || user.username);
  return adminState.user?.username === user.username ? `${baseLabel} · 当前登录` : baseLabel;
}

function isClaimedByCurrentAdmin(session) {
  return Boolean(
    session
    && adminState.user
    && session.assignedAdminUsername
    && session.assignedAdminUsername === adminState.user.username,
  );
}

function assistantOwnerLabel(session) {
  if (!session) {
    return "未认领";
  }
  return session.assignedAdminDisplayName || session.assignedAdminUsername || "未认领";
}

function formatElapsedLabel(value) {
  if (!value) {
    return "时间未知";
  }
  const diffMs = Math.max(0, Date.now() - new Date(value).getTime());
  const totalMinutes = Math.floor(diffMs / 60000);
  if (totalMinutes < 1) {
    return "刚刚";
  }
  if (totalMinutes < 60) {
    return `${totalMinutes} 分钟`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours < 24) {
    return minutes ? `${hours} 小时 ${minutes} 分钟` : `${hours} 小时`;
  }
  const days = Math.floor(hours / 24);
  const remainHours = hours % 24;
  return remainHours ? `${days} 天 ${remainHours} 小时` : `${days} 天`;
}

function assistantWaitingLabel(session) {
  const baseTime = session?.lastCustomerMessageAt || session?.createdAt;
  return baseTime ? `等待 ${formatElapsedLabel(baseTime)}` : "等待时间未知";
}

function assistantWaitingMinutes(session) {
  const baseTime = session?.lastCustomerMessageAt || session?.createdAt;
  if (!baseTime) {
    return 0;
  }
  return Math.max(0, Math.floor((Date.now() - new Date(baseTime).getTime()) / 60000));
}

function isEscalatedSession(session) {
  return session?.serviceStatus === "ESCALATED";
}

function needsSupportReply(session) {
  return isEscalatedSession(session) && Number(session?.supportUnreadCount || 0) > 0;
}

function isCustomerUnreadSession(session) {
  return Number(session?.customerUnreadCount || 0) > 0;
}

function isUnclaimedEscalation(session) {
  return isEscalatedSession(session) && !session?.assignedAdminUsername;
}

function isMyAssistantQueue(session) {
  return Boolean(session && adminState.user && session.assignedAdminUsername === adminState.user.username && session.serviceStatus !== "RESOLVED");
}

function isAssistantSlaRisk(session) {
  return isEscalatedSession(session) && !session?.firstSupportReplyAt && assistantWaitingMinutes(session) >= 30;
}

function assistantFilterCount(filterKey) {
  return adminState.assistantSessions.filter((session) => matchesAssistantOpsFilter(session, filterKey)).length;
}

function matchesAssistantOpsFilter(session, filterKey = adminState.assistantOpsFilter) {
  switch (filterKey) {
    case "MY_QUEUE":
      return isMyAssistantQueue(session);
    case "UNCLAIMED":
      return isUnclaimedEscalation(session);
    case "WAITING":
      return needsSupportReply(session);
    case "SLA_RISK":
      return isAssistantSlaRisk(session);
    case "CUSTOMER_UNREAD":
      return isCustomerUnreadSession(session);
    case "RESOLVED":
      return session?.serviceStatus === "RESOLVED";
    default:
      return true;
  }
}

function filteredAssistantSessions() {
  return adminState.assistantSessions.filter((session) => matchesAssistantOpsFilter(session));
}

function renderAssistantOpsSummary() {
  const host = adminById("assistantOpsSummary");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = "";
    return;
  }
  const sessions = adminState.assistantSessions;
  const items = [
    { label: "待处理会话", value: sessions.filter(needsSupportReply).length, tone: "warning" },
    { label: "待认领", value: sessions.filter(isUnclaimedEscalation).length, tone: "danger" },
    { label: "我的待处理", value: sessions.filter(isMyAssistantQueue).length, tone: "success" },
    { label: "客户未读", value: sessions.filter(isCustomerUnreadSession).length, tone: "default" },
    { label: "首响超时", value: sessions.filter(isAssistantSlaRisk).length, tone: "danger" },
  ];
  host.innerHTML = items.map((item) => `
    <div class="assistant-summary-item ${item.tone}">
      <span class="assistant-summary-label">${escapeHtml(item.label)}</span>
      <strong>${escapeHtml(item.value)}</strong>
    </div>
  `).join("");
}

function renderAssistantOpsFilters() {
  const host = adminById("assistantOpsFilters");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = "";
    return;
  }
  host.innerHTML = ASSISTANT_OPS_FILTERS.map((filter) => `
    <button type="button" class="${adminState.assistantOpsFilter === filter.key ? "active" : ""}" data-assistant-filter-key="${filter.key}">
      ${escapeHtml(filter.label)} · ${assistantFilterCount(filter.key)}
    </button>
  `).join("");
  host.querySelectorAll("[data-assistant-filter-key]").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => applyAssistantOpsFilter(button.dataset.assistantFilterKey)));
  });
}

function renderAssistantFlagTags(session) {
  const tags = [];
  if (isAssistantSlaRisk(session)) {
    tags.push(`<span class="tag danger">首响超时</span>`);
  }
  if (Number(session?.supportUnreadCount || 0) > 0) {
    tags.push(`<span class="tag warning">待处理 ${session.supportUnreadCount}</span>`);
  }
  if (Number(session?.customerUnreadCount || 0) > 0) {
    tags.push(`<span class="tag">客户未读 ${session.customerUnreadCount}</span>`);
  }
  if (isClaimedByCurrentAdmin(session)) {
    tags.push(`<span class="tag success">我在跟进</span>`);
  }
  if (!session?.assignedAdminUsername && session?.serviceStatus === "ESCALATED") {
    tags.push(`<span class="tag danger">待认领</span>`);
  }
  return tags.length ? `<div class="assistant-session-flags">${tags.join("")}</div>` : "";
}

function formatDateTime(value) {
  if (!value) {
    return "时间未知";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function setAdminStatus(text) {
  const node = adminById("adminStatus");
  if (node) {
    node.textContent = text;
  }
}

function money(value) {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(value || 0);
}

function metricCard(label, value) {
  return `
    <article class="metric-card">
      <div class="inline-meta">${escapeHtml(label)}</div>
      <div class="metric-value">${escapeHtml(value)}</div>
    </article>
  `;
}

function orderTagClass(status) {
  if (status === "REFUND_REQUESTED") {
    return "danger";
  }
  if (status === "REFUNDED" || status === "COMPLETED") {
    return "success";
  }
  if (status === "PENDING_PAYMENT") {
    return "info";
  }
  if (status === "CONFIRMED" || status === "PROCESSING") {
    return "warning";
  }
  return "";
}

function normalizeText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function isPendingShipment(order) {
  return order.status === "CONFIRMED" || order.status === "PROCESSING";
}

function refundQueueOrders() {
  return adminState.orders.filter((order) => order.status === "REFUND_REQUESTED");
}

function shipmentQueueOrders() {
  return adminState.orders.filter(isPendingShipment);
}

function normalizedAfterSalesStatus(order) {
  if (order?.afterSales?.status) {
    return order.afterSales.status;
  }
  if (order?.status === "REFUND_REQUESTED") {
    return "REQUESTED";
  }
  if (order?.status === "REFUNDED") {
    return "REFUNDED";
  }
  return "";
}

function afterSalesOrders() {
  return adminState.orders.filter((order) => Boolean(normalizedAfterSalesStatus(order)));
}

function isOpenAfterSalesStatus(status) {
  return ["REQUESTED", "AWAITING_CUSTOMER_RETURN", "RETURN_SHIPPED"].includes(status);
}

function afterSalesFilterCount(filterKey) {
  const orders = afterSalesOrders();
  if (filterKey === "OPEN") {
    return orders.filter((order) => isOpenAfterSalesStatus(normalizedAfterSalesStatus(order))).length;
  }
  return orders.filter((order) => normalizedAfterSalesStatus(order) === filterKey).length;
}

function matchesAfterSalesFilter(order) {
  const status = normalizedAfterSalesStatus(order);
  if (!status) {
    return false;
  }
  if (adminState.afterSalesFilter === "OPEN") {
    return isOpenAfterSalesStatus(status);
  }
  return status === adminState.afterSalesFilter;
}

function filteredAfterSalesOrders() {
  return afterSalesOrders().filter(matchesAfterSalesFilter);
}

function lowStockProducts() {
  return [...adminState.products]
    .filter((product) => Number(product.stock || 0) <= 5)
    .sort((left, right) => Number(left.stock || 0) - Number(right.stock || 0));
}

function orderFilterCount(filterKey) {
  if (filterKey === "ALL") {
    return adminState.orders.length;
  }
  if (filterKey === "PENDING_SHIPMENT") {
    return shipmentQueueOrders().length;
  }
  return adminState.orders.filter((order) => order.status === filterKey).length;
}

function matchesOrderFilter(order) {
  if (adminState.orderFilterStatus === "PENDING_SHIPMENT" && !isPendingShipment(order)) {
    return false;
  }
  if (adminState.orderFilterStatus !== "ALL"
      && adminState.orderFilterStatus !== "PENDING_SHIPMENT"
      && order.status !== adminState.orderFilterStatus) {
    return false;
  }

  const keyword = normalizeText(adminState.orderFilterKeyword);
  if (!keyword) {
    return true;
  }

  const searchPool = [
    order.orderNo,
    order.username,
    order.displayName,
    order.shippingAddress,
    order.shippingCarrier,
    order.trackingNo,
    order.paymentMethod,
    order.paymentReference,
    order.riskNote,
    statusLabel(order.status),
    order.afterSales?.status,
    order.afterSales?.customerReason,
    order.afterSales?.adminReply,
    order.afterSales?.returnAddress,
    order.afterSales?.returnCarrier,
    order.afterSales?.returnTrackingNo,
    order.afterSales?.returnNote,
    ...(order.timeline || []).flatMap((event) => [
      event.title,
      event.detail,
      event.actorLabel,
    ]),
    ...(order.items || []).flatMap((item) => [
      item.productName,
      item.productSku,
    ]),
  ].join(" ").toLowerCase();

  return searchPool.includes(keyword);
}

function filteredOrders() {
  return adminState.orders.filter(matchesOrderFilter);
}

function afterSalesStageCardClass(status) {
  if (status === "REQUESTED" || status === "RETURN_SHIPPED") {
    return "warning";
  }
  if (status === "REFUNDED") {
    return "success";
  }
  if (status === "REJECTED") {
    return "danger";
  }
  return "";
}

function afterSalesCurrentStageTime(order) {
  const afterSales = order?.afterSales || {};
  const status = normalizedAfterSalesStatus(order);
  if (status === "RETURN_SHIPPED") {
    return afterSales.customerShippedAt || afterSales.adminRespondedAt || afterSales.requestedAt;
  }
  if (status === "AWAITING_CUSTOMER_RETURN") {
    return afterSales.adminRespondedAt || afterSales.requestedAt;
  }
  if (status === "REFUNDED" || status === "REJECTED") {
    return afterSales.resolvedAt || afterSales.requestedAt;
  }
  return afterSales.requestedAt;
}

function afterSalesStageElapsedLabel(order) {
  const baseTime = afterSalesCurrentStageTime(order);
  return baseTime ? `当前阶段已持续 ${formatElapsedLabel(baseTime)}` : "当前阶段时间未知";
}

function afterSalesStageNote(order) {
  const afterSales = order?.afterSales || {};
  const status = normalizedAfterSalesStatus(order);
  switch (status) {
    case "REQUESTED":
      return "等待平台审核，确认是直接退款还是先退货回寄。";
    case "AWAITING_CUSTOMER_RETURN":
      return "平台已发出退货指引，等待用户回寄并提交物流单号。";
    case "RETURN_SHIPPED":
      return "用户已经提交回寄物流，平台确认收货后即可完成退款。";
    case "REFUNDED":
      return `售后已完成，${afterSales.resolvedAt ? `完成时间 ${formatDateTime(afterSales.resolvedAt)}。` : "退款已完成。"}`;
    case "REJECTED":
      return `售后已驳回，${afterSales.adminReply ? `处理说明：${afterSales.adminReply}` : "管理员未补充额外说明。"}`;
    default:
      return "售后处理中。";
  }
}

function afterSalesStageNoteClass(status) {
  if (status === "REQUESTED" || status === "RETURN_SHIPPED") {
    return "warning";
  }
  if (status === "REFUNDED") {
    return "success";
  }
  if (status === "REJECTED") {
    return "danger";
  }
  return "info";
}

function renderDashboard() {
  const host = adminById("metricGrid");
  if (!host) {
    return;
  }
  if (!adminState.user || !adminState.dashboard) {
    host.innerHTML = `<div class="empty-state">管理员登录后会显示平台经营数据。</div>`;
    return;
  }

  host.innerHTML = [
    metricCard("用户数", adminState.dashboard.userCount),
    metricCard("商品数", adminState.dashboard.productCount),
    metricCard("订单数", adminState.dashboard.orderCount),
    metricCard("累计销售额", money(adminState.dashboard.totalRevenue)),
    metricCard("退款审核", refundQueueOrders().length),
    metricCard("待发货订单", shipmentQueueOrders().length),
    metricCard("低库存商品", lowStockProducts().length),
    metricCard("知识文档", adminState.dashboard.knowledgeDocumentCount),
  ].join("");
}

function renderRefundQueue() {
  const host = adminById("refundQueueList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看退款审核队列。</div>`;
    return;
  }

  const queue = refundQueueOrders();
  if (!queue.length) {
    host.innerHTML = `<div class="empty-state">当前没有待审核的退款申请。</div>`;
    return;
  }

  host.innerHTML = queue.map((order) => `
    <article class="queue-card">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(order.orderNo)}</strong>
          <div class="inline-meta">${escapeHtml(order.displayName)} / ${escapeHtml(order.username)}</div>
        </div>
        <div class="tag danger">${escapeHtml(statusLabel(order.status))}</div>
      </div>
      <div class="queue-meta">
        <div class="inline-meta">金额 ${money(order.totalAmount)}</div>
        <div class="inline-meta">创建时间 ${formatDateTime(order.createdAt)}</div>
        ${order.afterSales ? `<div class="inline-meta">售后阶段 ${escapeHtml(afterSalesStatusLabel(order.afterSales.status))}</div>` : ""}
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity}</div>
        `).join("")}
      </div>
      ${order.afterSales?.returnAddress ? `<div class="panel-hint">回寄地址：${escapeHtml(order.afterSales.returnAddress)}</div>` : ""}
      ${order.afterSales?.returnTrackingNo ? `<div class="panel-hint">用户回寄：${escapeHtml(order.afterSales.returnCarrier || "")} ${escapeHtml(order.afterSales.returnTrackingNo)}</div>` : ""}
      <textarea class="ops-refund-note-input" data-order-id="${order.id}" placeholder="审核意见，例如同意退款原因或驳回说明"></textarea>
      <div class="order-actions">
        <button type="button" class="primary ops-refund-btn" data-order-id="${order.id}" data-approved="true">通过退款</button>
        <button type="button" class="ghost ops-refund-btn" data-order-id="${order.id}" data-approved="false">驳回退款</button>
        <button type="button" class="ghost ops-focus-order-btn" data-order-no="${escapeHtml(order.orderNo)}" data-order-filter="REFUND_REQUESTED">定位订单</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll(".ops-refund-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => reviewRefund(
      Number(button.dataset.orderId),
      button.dataset.approved === "true",
      readRefundQueueNote(Number(button.dataset.orderId)),
    )));
  });
  bindFocusOrderButtons(host);
}

function renderShipmentQueue() {
  const host = adminById("shipmentQueueList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看待发货队列。</div>`;
    return;
  }

  const queue = shipmentQueueOrders();
  if (!queue.length) {
    host.innerHTML = `<div class="empty-state">当前没有待发货或处理中订单。</div>`;
    return;
  }

  host.innerHTML = queue.map((order) => `
    <article class="queue-card">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(order.orderNo)}</strong>
          <div class="inline-meta">${escapeHtml(order.displayName)} / ${escapeHtml(order.username)}</div>
        </div>
        <div class="tag warning">${escapeHtml(statusLabel(order.status))}</div>
      </div>
      <div class="queue-meta">
        <div class="inline-meta">金额 ${money(order.totalAmount)}</div>
        <div class="inline-meta">地址 ${escapeHtml(order.shippingAddress || "待补充地址")}</div>
      </div>
      <div class="shipment-grid">
        <input class="ops-shipping-carrier-input" data-order-id="${order.id}" placeholder="物流公司，例如顺丰" value="${escapeHtml(order.shippingCarrier || "")}">
        <input class="ops-tracking-no-input" data-order-id="${order.id}" placeholder="运单号" value="${escapeHtml(order.trackingNo || "")}">
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity}</div>
        `).join("")}
      </div>
      <div class="order-actions">
        ${renderShipmentActions(order)}
        <button type="button" class="ghost ops-focus-order-btn" data-order-no="${escapeHtml(order.orderNo)}" data-order-filter="PENDING_SHIPMENT">定位订单</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll(".ops-ship-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => updateOrderStatus(
      Number(button.dataset.orderId),
      button.dataset.nextStatus,
      button.dataset.note,
      readOpsShippingCarrier(Number(button.dataset.orderId)),
      readOpsTrackingNo(Number(button.dataset.orderId)),
    )));
  });
  bindFocusOrderButtons(host);
}

function renderShipmentActions(order) {
  if (order.status === "CONFIRMED") {
    return `
      <button type="button" class="primary ops-ship-btn" data-order-id="${order.id}" data-next-status="PROCESSING" data-note="工作台快速推进：开始处理">开始处理</button>
      <button type="button" class="ghost ops-ship-btn" data-order-id="${order.id}" data-next-status="SHIPPED" data-note="工作台快速推进：直接标记已发货">直接发货</button>
    `;
  }
  if (order.status === "PROCESSING") {
    return `
      <button type="button" class="primary ops-ship-btn" data-order-id="${order.id}" data-next-status="SHIPPED" data-note="工作台快速推进：处理完成并发货">标记发货</button>
    `;
  }
  return "";
}

function renderLowStockList() {
  const host = adminById("lowStockList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看库存预警。</div>`;
    return;
  }

  const products = lowStockProducts();
  if (!products.length) {
    host.innerHTML = `<div class="empty-state">当前没有低库存商品，库存状态比较健康。</div>`;
    return;
  }

  host.innerHTML = products.map((product) => `
    <article class="queue-card">
      <div class="row-top">
        <div class="thumb-row">
          <img src="${escapeHtml(product.imageUrl || "/favicon.svg")}" alt="${escapeHtml(product.name)}">
          <div>
            <strong>${escapeHtml(product.name)}</strong>
            <div class="inline-meta">${escapeHtml(product.sku)} · ${escapeHtml(product.category || "未分类")}</div>
          </div>
        </div>
        <div class="tag warning">库存 ${escapeHtml(product.stock)}</div>
      </div>
      <div class="queue-meta">
        <div class="inline-meta">价格 ${money(product.price)}</div>
        <div class="inline-meta">${escapeHtml(product.description || "暂无描述")}</div>
      </div>
      <div class="order-actions">
        <button type="button" class="primary ops-edit-product-btn" data-product-id="${product.id}">编辑库存</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll(".ops-edit-product-btn").forEach((button) => {
    button.addEventListener("click", () => fillProductForm(Number(button.dataset.productId), true));
  });
}

function renderOperationsWorkbench() {
  renderRefundQueue();
  renderShipmentQueue();
  renderLowStockList();
}

function renderAfterSalesWorkbench() {
  renderAfterSalesSummary();
  renderAfterSalesFilterTabs();
  renderAfterSalesCases();
}

function renderAfterSalesSummary() {
  const host = adminById("afterSalesSummary");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看售后工作台。</div>`;
    return;
  }

  const openCount = afterSalesFilterCount("OPEN");
  const requestedCount = afterSalesFilterCount("REQUESTED");
  const awaitingCount = afterSalesFilterCount("AWAITING_CUSTOMER_RETURN");
  const shippedCount = afterSalesFilterCount("RETURN_SHIPPED");
  const resolvedCount = afterSalesFilterCount("REFUNDED");
  const rejectedCount = afterSalesFilterCount("REJECTED");

  host.innerHTML = [
    { label: "处理中", value: openCount, tone: "" },
    { label: "待审核", value: requestedCount, tone: "warning" },
    { label: "待用户回寄", value: awaitingCount, tone: "" },
    { label: "待平台验收", value: shippedCount, tone: "warning" },
    { label: "已完成", value: resolvedCount, tone: "success" },
    { label: "已驳回", value: rejectedCount, tone: "danger" },
  ].map((item) => `
    <div class="assistant-summary-item ${item.tone}">
      <span class="assistant-summary-label">${escapeHtml(item.label)}</span>
      <strong>${escapeHtml(item.value)}</strong>
    </div>
  `).join("");
}

function renderAfterSalesFilterTabs() {
  const host = adminById("afterSalesFilterTabs");
  const summary = adminById("afterSalesFilterSummary");
  if (!host || !summary) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = "";
    summary.textContent = "登录后可按售后阶段筛选工单。";
    return;
  }

  host.innerHTML = AFTER_SALES_FILTERS.map((filter) => `
    <button type="button" class="${adminState.afterSalesFilter === filter.key ? "active" : ""}" data-after-sales-filter-key="${filter.key}">
      ${escapeHtml(filter.label)} · ${afterSalesFilterCount(filter.key)}
    </button>
  `).join("");

  host.querySelectorAll("[data-after-sales-filter-key]").forEach((button) => {
    button.addEventListener("click", () => {
      adminState.afterSalesFilter = button.dataset.afterSalesFilterKey || "OPEN";
      renderAfterSalesWorkbench();
    });
  });

  const currentFilter = AFTER_SALES_FILTERS.find((item) => item.key === adminState.afterSalesFilter)?.label || "处理中";
  summary.textContent = `当前展示 ${currentFilter} 工单，共 ${filteredAfterSalesOrders().length} 条。`;
}

function renderAfterSalesCases() {
  const host = adminById("afterSalesCasesList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">管理端登录后可集中处理售后工单。</div>`;
    return;
  }

  const orders = filteredAfterSalesOrders();
  if (!orders.length) {
    host.innerHTML = `<div class="empty-state">当前筛选条件下没有售后工单，可以切换阶段查看其他售后记录。</div>`;
    return;
  }

  host.innerHTML = orders.map((order) => renderAfterSalesCase(order)).join("");
  bindAdminOrderActionButtons(host);
  host.querySelectorAll(".focus-after-sales-order-btn").forEach((button) => {
    button.addEventListener("click", () => {
      focusOrder(button.dataset.orderNo || "", button.dataset.orderFilter || "ALL");
      adminById("orderManagement")?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  });
}

function renderAfterSalesCase(order) {
  const afterSales = order.afterSales || {};
  const status = normalizedAfterSalesStatus(order);
  const userLabel = order.displayName && order.displayName !== order.username
    ? `${order.displayName} / ${order.username}`
    : order.username;
  const requestedAt = afterSales.requestedAt || order.createdAt;
  return `
    <article class="after-sales-case-card">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(order.orderNo)}</strong>
          <div class="inline-meta">${escapeHtml(userLabel || "匿名用户")} · 金额 ${money(order.totalAmount)} · 发起时间 ${escapeHtml(formatDateTime(requestedAt))}</div>
        </div>
        <div class="tag ${afterSalesStageCardClass(status)}">${escapeHtml(afterSalesStatusLabel(status))}</div>
      </div>
      <div class="after-sales-stage-row">
        <div class="tag ${afterSalesStageCardClass(status)}">${escapeHtml(afterSalesStageElapsedLabel(order))}</div>
        <div class="tag">${afterSales.returnRequired ? "需退货回寄" : "可直接退款"}</div>
        <div class="tag">${escapeHtml(statusLabel(order.status))}</div>
      </div>
      <div class="after-sales-stage-note ${afterSalesStageNoteClass(status)}">${escapeHtml(afterSalesStageNote(order))}</div>
      <div class="after-sales-case-meta">
        ${afterSales.customerReason ? `<div class="panel-hint">用户原因：${escapeHtml(afterSales.customerReason)}</div>` : ""}
        ${afterSales.adminReply ? `<div class="panel-hint">平台说明：${escapeHtml(afterSales.adminReply)}</div>` : ""}
        ${afterSales.returnAddress ? `<div class="panel-hint">回寄地址：${escapeHtml(afterSales.returnAddress)}</div>` : ""}
        ${afterSales.returnTrackingNo ? `<div class="panel-hint">回寄物流：${escapeHtml(afterSales.returnCarrier || "未填写")} · ${escapeHtml(afterSales.returnTrackingNo)}</div>` : ""}
        ${afterSales.returnNote ? `<div class="panel-hint">用户补充：${escapeHtml(afterSales.returnNote)}</div>` : ""}
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity} · ${money(item.lineTotal)}</div>
        `).join("")}
      </div>
      ${renderAdminOrderTimeline(order, 4)}
      ${renderAfterSalesCaseActions(order)}
      <div class="order-actions">
        <button type="button" class="ghost focus-after-sales-order-btn" data-order-no="${escapeHtml(order.orderNo)}" data-order-filter="${escapeHtml(order.status === "REFUND_REQUESTED" ? "REFUND_REQUESTED" : order.status)}">查看订单全量视图</button>
      </div>
    </article>
  `;
}

function renderAfterSalesCaseActions(order) {
  const status = normalizedAfterSalesStatus(order);
  if (order.status === "REFUND_REQUESTED") {
    return `
      ${renderAdminAfterSalesControls(order, "售后处理备注，例如审核意见、退货验收说明")}
      ${renderAdminLogisticsUpdateBlock(order)}
    `;
  }
  if (status === "REFUNDED") {
    return `<div class="panel-hint">售后已完成，订单已经进入已退款状态，时间线里保留了完整处理轨迹。</div>`;
  }
  if (status === "REJECTED") {
    return `<div class="panel-hint">售后已驳回，订单继续保留在已完成状态；需要时可以回到订单全量视图查看订单履约上下文。</div>`;
  }
  return `<div class="panel-hint">当前售后工单已进入下一阶段，可结合下方时间线和订单视图继续跟进。</div>`;
}

function renderAssistantOperations() {
  renderAssistantOpsSummary();
  renderAssistantOpsFilters();
  renderAssistantSessions();
  renderAssistantSessionDetail();
  renderAssistantEscalations();
  renderAssistantDrafts();
}

function renderAssistantSessions() {
  const host = adminById("adminAssistantSessionList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看 AI 客服会话。</div>`;
    return;
  }
  const sessions = filteredAssistantSessions();
  if (!adminState.assistantSessions.length) {
    host.innerHTML = `<div class="empty-state">当前还没有 AI 客服会话记录。</div>`;
    return;
  }
  if (!sessions.length) {
    host.innerHTML = `<div class="empty-state">当前筛选条件下没有会话，可以切换到其他客服视图。</div>`;
    return;
  }

  host.innerHTML = sessions.map((session) => `
    <button type="button" class="session-pill admin-session-pill ${adminState.selectedAssistantSessionId === session.id ? "active" : ""} ${Number(session.supportUnreadCount || 0) > 0 ? "has-unread" : ""}" data-assistant-session-id="${session.id}">
      <div class="row-top">
        <strong>${escapeHtml(session.displayName || session.username)}</strong>
        <div class="tag ${assistantServiceTagClass(session.serviceStatus)}">${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
      </div>
      <div class="inline-meta">${escapeHtml(session.lastIntent || "chat")} · ${escapeHtml(assistantOwnerLabel(session))}</div>
      <div class="inline-meta">${escapeHtml(session.summary || "暂无摘要")}</div>
      <div class="inline-meta">${escapeHtml(session.threadId)} · ${session.messageCount} 条消息 · ${escapeHtml(assistantWaitingLabel(session))}</div>
      ${renderAssistantFlagTags(session)}
    </button>
  `).join("");

  host.querySelectorAll("[data-assistant-session-id]").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => selectAssistantSession(Number(button.dataset.assistantSessionId))));
  });
}

function renderAssistantSessionDetail() {
  const meta = adminById("adminAssistantSessionMeta");
  const host = adminById("adminAssistantMessages");
  const replyInput = adminById("adminAssistantReplyInput");
  const replyBtn = adminById("adminAssistantReplyBtn");
  const resolveBtn = adminById("adminAssistantResolveBtn");
  const claimBtn = adminById("adminAssistantClaimBtn");
  const assignSelect = adminById("adminAssistantAssignSelect");
  const assignBtn = adminById("adminAssistantAssignBtn");
  if (!meta || !host || !replyInput || !replyBtn || !resolveBtn || !claimBtn || !assignSelect || !assignBtn) {
    return;
  }
  if (!adminState.user) {
    meta.textContent = "管理员登录后可查看 AI 会话摘要。";
    host.innerHTML = `<div class="empty-state">登录后可查看 AI 会话消息。</div>`;
    setAssistantReplyState(true, "登录后可发送人工回复");
    renderAssistantAssignControls(null);
    claimBtn.disabled = true;
    claimBtn.textContent = "认领会话";
    return;
  }
  const session = currentAdminAssistantSession();
  if (!session) {
    meta.textContent = "选择左侧会话后，这里会展示用户、线程、摘要和意图。";
    host.innerHTML = `<div class="empty-state">当前还没有选中的 AI 会话。</div>`;
    setAssistantReplyState(true, "先选择一条 AI 会话");
    renderAssistantAssignControls(null);
    claimBtn.disabled = true;
    claimBtn.textContent = "认领会话";
    return;
  }

  const ownedByMe = isClaimedByCurrentAdmin(session);
  setAssistantReplyState(false, session.serviceStatus === "ESCALATED"
    ? (ownedByMe
      ? "当前会话由你跟进中，发送回复后客户端会立即看到"
      : (session.assignedAdminUsername
        ? `当前会话由 ${assistantOwnerLabel(session)} 跟进，发送回复会自动转交到当前管理员名下`
        : "当前会话还未认领，发送回复时会自动认领"))
    : "可以主动给用户发送人工回复，也可以发送并结案");
  renderAssistantAssignControls(session);
  claimBtn.disabled = session.serviceStatus !== "ESCALATED" || ownedByMe;
  claimBtn.textContent = session.serviceStatus !== "ESCALATED"
    ? "无需认领"
    : (ownedByMe ? "已认领" : (session.assignedAdminUsername ? "接管到我" : "认领会话"));
  meta.innerHTML = `
    <div><strong>用户：</strong>${escapeHtml(session.displayName || session.username)} / ${escapeHtml(session.username)}</div>
    <div><strong>会话：</strong>${escapeHtml(session.threadId)} · <strong>状态：</strong>${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
    <div><strong>跟进人：</strong>${escapeHtml(assistantOwnerLabel(session))} · <strong>意图：</strong>${escapeHtml(session.lastIntent || "chat")}</div>
    <div><strong>消息数：</strong>${session.messageCount} · <strong>待处理：</strong>${session.supportUnreadCount || 0} · <strong>客户未读：</strong>${session.customerUnreadCount || 0}</div>
    <div><strong>最近客户消息：</strong>${escapeHtml(formatDateTime(session.lastCustomerMessageAt))} · <strong>等待：</strong>${escapeHtml(assistantWaitingLabel(session))}</div>
    <div><strong>认领时间：</strong>${escapeHtml(formatDateTime(session.assignedAt))} · <strong>首响：</strong>${escapeHtml(formatDateTime(session.firstSupportReplyAt))} · <strong>结案：</strong>${escapeHtml(formatDateTime(session.resolvedAt))}</div>
    <div><strong>摘要：</strong>${escapeHtml(session.summary || "暂无摘要")}</div>
  `;
  if (!adminState.assistantMessages.length) {
    host.innerHTML = `<div class="empty-state">当前会话还没有消息。</div>`;
    return;
  }

  host.innerHTML = adminState.assistantMessages.map((message) => `
    <div class="message-bubble ${message.role}">
      <div class="message-role">${escapeHtml(assistantMessageRoleLabel(message.role))}</div>
      <div>${escapeHtml(message.content).replaceAll("\n", "<br>")}</div>
      <div class="inline-meta">${escapeHtml(formatDateTime(message.createdAt))}</div>
    </div>
  `).join("");
  host.scrollTop = host.scrollHeight;
}

function renderAssistantAssignControls(session) {
  const select = adminById("adminAssistantAssignSelect");
  const button = adminById("adminAssistantAssignBtn");
  if (!select || !button) {
    return;
  }

  if (!adminState.user) {
    select.innerHTML = `<option value="">登录后可分派客服</option>`;
    select.disabled = true;
    button.disabled = true;
    button.textContent = "转交给所选客服";
    return;
  }

  const operators = adminOperators();
  if (!operators.length) {
    select.innerHTML = `<option value="">当前没有可分派的客服账号</option>`;
    select.disabled = true;
    button.disabled = true;
    button.textContent = "转交给所选客服";
    return;
  }

  select.innerHTML = operators.map((user) => `
    <option value="${escapeHtml(user.username)}">${escapeHtml(assistantOperatorOptionLabel(user))}</option>
  `).join("");
  select.value = preferredAssignedAdminUsername(session);
  syncAssistantAssignControls(session);
}

function syncAssistantAssignControls(session = currentAdminAssistantSession()) {
  const select = adminById("adminAssistantAssignSelect");
  const button = adminById("adminAssistantAssignBtn");
  if (!select || !button) {
    return;
  }

  if (!adminState.user) {
    select.disabled = true;
    button.disabled = true;
    button.textContent = "转交给所选客服";
    return;
  }

  const operators = adminOperators();
  const selectedUsername = select.value?.trim() || "";
  if (!session) {
    if (!operators.length) {
      select.innerHTML = `<option value="">当前没有可分派的客服账号</option>`;
    } else {
      select.innerHTML = `<option value="">先选择一条 AI 会话</option>`;
    }
    select.disabled = true;
    button.disabled = true;
    button.textContent = "先选择会话";
    return;
  }

  if (session.serviceStatus !== "ESCALATED") {
    select.disabled = true;
    button.disabled = true;
    button.textContent = "当前无需转交";
    return;
  }

  const sameOwner = Boolean(session.assignedAdminUsername && selectedUsername === session.assignedAdminUsername);
  select.disabled = false;
  button.disabled = !selectedUsername || sameOwner;
  if (!selectedUsername) {
    button.textContent = "请选择客服";
    return;
  }
  button.textContent = sameOwner
    ? "当前已分配"
    : (session.assignedAdminUsername ? "转交给所选客服" : "分派给所选客服");
}

function renderAssistantEscalations() {
  const host = adminById("adminAssistantEscalationList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看转人工队列。</div>`;
    return;
  }
  if (!adminState.assistantEscalations.length) {
    host.innerHTML = `<div class="empty-state">当前没有等待人工接管的会话。</div>`;
    return;
  }

  host.innerHTML = adminState.assistantEscalations.map((session) => `
    <article class="queue-card">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(session.displayName || session.username)}</strong>
          <div class="inline-meta">${escapeHtml(session.username)} · ${escapeHtml(formatDateTime(session.createdAt))}</div>
        </div>
        <div class="tag ${assistantServiceTagClass(session.serviceStatus)}">${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
      </div>
      <div class="queue-meta">
        <div class="inline-meta">线程 ${escapeHtml(session.threadId)}</div>
        <div class="inline-meta">意图 ${escapeHtml(session.lastIntent || "chat")} · 消息 ${session.messageCount} 条 · ${escapeHtml(assistantWaitingLabel(session))}</div>
        <div class="inline-meta">跟进人 ${escapeHtml(assistantOwnerLabel(session))} · 首响 ${escapeHtml(formatDateTime(session.firstSupportReplyAt))}</div>
      </div>
      ${renderAssistantFlagTags(session)}
      <div class="panel-hint">${escapeHtml(session.summary || "等待客服查看详情")}</div>
      <div class="order-actions">
        ${session.serviceStatus === "ESCALATED" && !isClaimedByCurrentAdmin(session)
          ? `<button type="button" class="secondary assistant-claim-session-btn" data-assistant-session-id="${session.id}">${escapeHtml(session.assignedAdminUsername ? "接管到我" : "认领会话")}</button>`
          : ""}
        <button type="button" class="ghost assistant-focus-session-btn" data-assistant-session-id="${session.id}">查看会话</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll(".assistant-focus-session-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => selectAssistantSession(Number(button.dataset.assistantSessionId), true)));
  });
  host.querySelectorAll(".assistant-claim-session-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => claimAssistantSession(Number(button.dataset.assistantSessionId))));
  });
}

function renderAssistantDrafts() {
  const host = adminById("adminAssistantDraftList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看 AI 下单草稿。</div>`;
    return;
  }
  if (!adminState.assistantDrafts.length) {
    host.innerHTML = `<div class="empty-state">当前没有待确认的 AI 下单草稿。</div>`;
    return;
  }

  host.innerHTML = adminState.assistantDrafts.map((draft) => `
    <article class="queue-card">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(draft.productName)}</strong>
          <div class="inline-meta">${escapeHtml(draft.displayName || draft.username)} / ${escapeHtml(draft.username)}</div>
        </div>
        <div class="tag success">${escapeHtml(statusLabel(draft.status))}</div>
      </div>
      <div class="queue-meta">
        <div class="inline-meta">线程 ${escapeHtml(draft.threadId)}</div>
        <div class="inline-meta">数量 ${draft.quantity} · 单价 ${money(draft.unitPrice)} · 合计 ${money(draft.totalAmount)}</div>
        <div class="inline-meta">创建时间 ${formatDateTime(draft.createdAt)}</div>
      </div>
      <div class="panel-hint">${escapeHtml(draft.note || "用户确认后会转成正式订单。")}</div>
      ${renderAssistantDraftActions(draft)}
    </article>
  `).join("");

  host.querySelectorAll(".assistant-focus-session-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => selectAssistantSession(Number(button.dataset.assistantSessionId), true)));
  });
}

function renderAssistantDraftActions(draft) {
  const sessionId = extractAssistantSessionId(draft.threadId);
  if (!sessionId) {
    return "";
  }
  return `
    <div class="order-actions">
      <button type="button" class="ghost assistant-focus-session-btn" data-assistant-session-id="${sessionId}">查看会话</button>
    </div>
  `;
}

function extractAssistantSessionId(threadId) {
  const match = String(threadId || "").match(/^assistant-(\\d+)$/);
  return match ? Number(match[1]) : null;
}

function renderProducts() {
  const host = adminById("adminProductList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">请先使用管理员账号登录。</div>`;
    return;
  }
  if (!adminState.products.length) {
    host.innerHTML = `<div class="empty-state">当前还没有商品。</div>`;
    return;
  }

  const products = [...adminState.products].sort((left, right) => {
    const leftStock = Number(left.stock || 0);
    const rightStock = Number(right.stock || 0);
    if ((leftStock <= 5) !== (rightStock <= 5)) {
      return leftStock <= 5 ? -1 : 1;
    }
    return leftStock - rightStock;
  });

  host.innerHTML = products.map((product) => `
    <div class="table-row">
      <div class="row-top">
        <div class="thumb-row">
          <img src="${escapeHtml(product.imageUrl || "/favicon.svg")}" alt="${escapeHtml(product.name)}">
          <div>
            <strong>${escapeHtml(product.name)}</strong>
            <div class="inline-meta">${escapeHtml(product.sku)} · ${escapeHtml(product.category || "未分类")}</div>
          </div>
        </div>
        <button type="button" class="ghost edit-product-btn" data-product-id="${product.id}">编辑</button>
      </div>
      <div class="row-bottom">
        <div class="inline-meta">${escapeHtml(product.description || "暂无描述")}</div>
        <div class="tag ${Number(product.stock || 0) <= 5 ? "warning" : ""}">${money(product.price)} · 库存 ${escapeHtml(product.stock)}</div>
      </div>
    </div>
  `).join("");

  host.querySelectorAll(".edit-product-btn").forEach((button) => {
    button.addEventListener("click", () => fillProductForm(Number(button.dataset.productId), true));
  });
}

function renderOrderFilterTabs() {
  const host = adminById("adminOrderFilterTabs");
  const summary = adminById("orderFilterSummary");
  if (!host || !summary) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = "";
    summary.textContent = "登录后可按履约状态和关键词筛选订单。";
    return;
  }

  host.innerHTML = ORDER_FILTERS.map((filter) => `
    <button type="button" class="${adminState.orderFilterStatus === filter.key ? "active" : ""}" data-order-filter-key="${filter.key}">
      ${escapeHtml(filter.label)} · ${orderFilterCount(filter.key)}
    </button>
  `).join("");

  host.querySelectorAll("[data-order-filter-key]").forEach((button) => {
    button.addEventListener("click", () => {
      adminState.orderFilterStatus = button.dataset.orderFilterKey;
      renderOrderFilterTabs();
      renderOrders();
    });
  });

  const resultCount = filteredOrders().length;
  const filterLabel = ORDER_FILTERS.find((item) => item.key === adminState.orderFilterStatus)?.label || "全部订单";
  const keyword = adminState.orderFilterKeyword ? `，关键词“${adminState.orderFilterKeyword}”` : "";
  summary.textContent = `当前筛选：${filterLabel}${keyword}，共命中 ${resultCount} 条订单。`;
}

function bindAdminOrderActionButtons(host) {
  host.querySelectorAll(".save-order-status-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => updateOrderStatus(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".refund-review-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => reviewRefund(
      Number(button.dataset.orderId),
      button.dataset.approved === "true",
    )));
  });
  host.querySelectorAll(".issue-return-instructions-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => provideReturnInstructions(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".confirm-return-refund-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => confirmReturnAndRefund(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".save-logistics-update-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => appendLogisticsUpdate(Number(button.dataset.orderId))));
  });
}

function renderOrders() {
  const host = adminById("adminOrdersList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">管理员登录后可查看订单。</div>`;
    return;
  }

  const orders = filteredOrders();
  if (!orders.length) {
    host.innerHTML = `<div class="empty-state">当前筛选条件下没有订单，换个状态或关键词再试试。</div>`;
    return;
  }

  host.innerHTML = orders.map((order) => `
    <div class="table-row">
      <div class="row-top">
        <div>
          <strong>${escapeHtml(order.orderNo)}</strong>
          <div class="inline-meta">${escapeHtml(order.displayName || order.username)} / ${escapeHtml(order.username)}</div>
        </div>
        <div class="tag ${orderTagClass(order.status)}">${escapeHtml(statusLabel(order.status))}</div>
      </div>
      <div class="row-bottom">
        <div class="inline-meta">创建时间 ${formatDateTime(order.createdAt)}</div>
        <div class="inline-meta">金额 ${money(order.totalAmount)} · 地址 ${escapeHtml(order.shippingAddress || "待补充地址")}</div>
      </div>
      ${renderAdminOrderPaymentMeta(order)}
      ${renderAdminOrderShippingMeta(order)}
      ${order.afterSales ? `<div class="inline-meta">售后阶段 ${escapeHtml(afterSalesStatusLabel(order.afterSales.status))}</div>` : ""}
      ${order.riskNote ? `<div class="inline-meta">备注 ${escapeHtml(order.riskNote)}</div>` : ""}
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity} · ${money(item.lineTotal)}</div>
        `).join("")}
      </div>
      ${renderAdminOrderTimeline(order)}
      ${renderAdminOrderControls(order)}
    </div>
  `).join("");

  host.querySelectorAll(".save-order-status-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => updateOrderStatus(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".refund-review-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => reviewRefund(
      Number(button.dataset.orderId),
      button.dataset.approved === "true",
    )));
  });
  host.querySelectorAll(".issue-return-instructions-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => provideReturnInstructions(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".confirm-return-refund-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => confirmReturnAndRefund(Number(button.dataset.orderId))));
  });
  host.querySelectorAll(".save-logistics-update-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => appendLogisticsUpdate(Number(button.dataset.orderId))));
  });
}

function renderAdminOrderControls(order) {
  const nextStatuses = availableNextStatuses(order.status);
  const notePlaceholder = order.status === "REFUND_REQUESTED"
    ? "审核意见，例如同意退款原因或驳回说明"
    : "运营备注，例如催发货、用户沟通结果";
  const logisticsBlock = renderAdminLogisticsUpdateBlock(order);

  if (order.status === "REFUND_REQUESTED") {
    return `
      ${renderAdminAfterSalesControls(order, notePlaceholder)}
      ${logisticsBlock}
    `;
  }

  if (!nextStatuses.length) {
    return `
      <div class="panel-hint">当前订单已经进入最终状态，暂无可继续推进的履约动作。</div>
      ${logisticsBlock}
    `;
  }

  return `
    <div class="order-action-block">
      <textarea class="admin-order-note-input" data-order-id="${order.id}" placeholder="${escapeHtml(notePlaceholder)}"></textarea>
      ${shouldShowAdminShippingFields(order, nextStatuses)
        ? `<div class="shipment-grid">
            <input class="admin-shipping-carrier-input" data-order-id="${order.id}" placeholder="物流公司，例如顺丰" value="${escapeHtml(order.shippingCarrier || "")}">
            <input class="admin-tracking-no-input" data-order-id="${order.id}" placeholder="运单号" value="${escapeHtml(order.trackingNo || "")}">
          </div>`
        : ""}
      <div class="order-actions">
        <select class="order-status-select" data-order-id="${order.id}">
          ${nextStatuses.map((status) => `<option value="${status}">${escapeHtml(statusLabel(status))}</option>`).join("")}
        </select>
        <button type="button" class="primary save-order-status-btn" data-order-id="${order.id}">推进订单</button>
      </div>
      <div class="panel-hint">这里只显示符合当前状态的下一步动作，减少误操作导致的流程跳跃。</div>
    </div>
    ${logisticsBlock}
  `;
}

function availableNextStatuses(status) {
  switch (status) {
    case "DRAFT":
      return ["PENDING_CONFIRMATION", "PENDING_PAYMENT", "CONFIRMED", "CANCELLED"];
    case "PENDING_CONFIRMATION":
      return ["PENDING_PAYMENT", "CONFIRMED", "CANCELLED"];
    case "PENDING_PAYMENT":
      return ["CONFIRMED", "CANCELLED"];
    case "CONFIRMED":
      return ["PROCESSING", "SHIPPED", "CANCELLED"];
    case "PROCESSING":
      return ["SHIPPED", "CANCELLED"];
    case "SHIPPED":
      return ["COMPLETED"];
    default:
      return [];
  }
}

function renderAdminAfterSalesControls(order, notePlaceholder) {
  const afterSales = order.afterSales || {};
  if (afterSales.status === "AWAITING_CUSTOMER_RETURN") {
    return `
      <div class="order-action-block">
        <div class="panel-hint">当前售后阶段：${escapeHtml(afterSalesStatusLabel(afterSales.status))}</div>
        ${afterSales.returnAddress ? `<div class="inline-meta">回寄地址 ${escapeHtml(afterSales.returnAddress)}</div>` : ""}
        ${afterSales.adminReply ? `<div class="inline-meta">退货说明 ${escapeHtml(afterSales.adminReply)}</div>` : ""}
        <div class="panel-hint">等待用户回寄商品并提交回寄单号，收到后再继续退款。</div>
      </div>
    `;
  }
  if (afterSales.status === "RETURN_SHIPPED") {
    return `
      <div class="order-action-block">
        <div class="panel-hint">当前售后阶段：${escapeHtml(afterSalesStatusLabel(afterSales.status))}</div>
        ${afterSales.returnAddress ? `<div class="inline-meta">回寄地址 ${escapeHtml(afterSales.returnAddress)}</div>` : ""}
        <div class="inline-meta">用户回寄物流 ${escapeHtml(afterSales.returnCarrier || "未填写")} · ${escapeHtml(afterSales.returnTrackingNo || "未填写")}</div>
        ${afterSales.returnNote ? `<div class="inline-meta">用户说明 ${escapeHtml(afterSales.returnNote)}</div>` : ""}
        <textarea class="admin-order-note-input" data-order-id="${order.id}" placeholder="退款完成备注，例如已验收商品无误"></textarea>
        <div class="order-actions">
          <button type="button" class="primary confirm-return-refund-btn" data-order-id="${order.id}">确认收到退货并退款</button>
          <button type="button" class="ghost refund-review-btn" data-order-id="${order.id}" data-approved="false">驳回退款</button>
        </div>
        <div class="panel-hint">确认后订单会变成“已退款”，并记录退货退款完成时间线。</div>
      </div>
    `;
  }
  return `
    <div class="order-action-block">
      <textarea class="admin-order-note-input" data-order-id="${order.id}" placeholder="${escapeHtml(notePlaceholder)}"></textarea>
      <input class="admin-return-address-input" data-order-id="${order.id}" placeholder="退货回寄地址，例如上海市徐汇区售后仓 3 号门">
      <div class="order-actions">
        <button type="button" class="primary refund-review-btn" data-order-id="${order.id}" data-approved="true">直接退款</button>
        <button type="button" class="secondary issue-return-instructions-btn" data-order-id="${order.id}">发送退货指引</button>
        <button type="button" class="ghost refund-review-btn" data-order-id="${order.id}" data-approved="false">驳回退款</button>
      </div>
      <div class="panel-hint">可直接退款，也可以先给用户发送退货回寄地址和说明，等用户回寄后再确认退款。</div>
    </div>
  `;
}

function shouldShowAdminShippingFields(order, nextStatuses) {
  return nextStatuses.includes("SHIPPED") || Boolean(order.shippingCarrier) || Boolean(order.trackingNo);
}

function renderAdminOrderPaymentMeta(order) {
  const lines = [];
  if (order.paymentMethod) {
    lines.push(`<div class="inline-meta">支付方式 ${escapeHtml(order.paymentMethod)}</div>`);
  }
  if (order.paymentReference) {
    lines.push(`<div class="inline-meta">支付流水 ${escapeHtml(order.paymentReference)}</div>`);
  }
  if (order.paidAt) {
    lines.push(`<div class="inline-meta">支付时间 ${escapeHtml(formatDateTime(order.paidAt))}</div>`);
  } else if (order.status === "PENDING_PAYMENT") {
    lines.push(`<div class="inline-meta">支付状态 待用户完成支付；后台也可推进到待发货并补记收款</div>`);
  }
  return lines.join("");
}

function renderAdminOrderShippingMeta(order) {
  const lines = [];
  if (order.shippingCarrier) {
    lines.push(`<div class="inline-meta">物流公司 ${escapeHtml(order.shippingCarrier)}</div>`);
  }
  if (order.trackingNo) {
    lines.push(`<div class="inline-meta">运单号 ${escapeHtml(order.trackingNo)}</div>`);
  }
  if (order.shippedAt) {
    lines.push(`<div class="inline-meta">发货时间 ${escapeHtml(formatDateTime(order.shippedAt))}</div>`);
  }
  const latest = latestLogisticsEvent(order);
  if (latest?.detail) {
    lines.push(`<div class="inline-meta">最新物流 ${escapeHtml(latest.detail)}</div>`);
  }
  return lines.join("");
}

function renderAdminLogisticsUpdateBlock(order) {
  if (!canAppendLogisticsUpdate(order)) {
    return "";
  }
  return `
    <div class="order-action-block logistics-update-block">
      <textarea class="admin-logistics-update-input" data-order-id="${order.id}" placeholder="追加物流节点，例如包裹已到上海转运中心，预计明日上午派送"></textarea>
      <div class="order-actions">
        <button type="button" class="secondary save-logistics-update-btn" data-order-id="${order.id}">追加物流节点</button>
      </div>
      <div class="panel-hint">物流节点会进入订单时间线，客户端订单页和 AI 查物流都会优先读取最新节点。</div>
    </div>
  `;
}

function canAppendLogisticsUpdate(order) {
  return ["SHIPPED", "REFUND_REQUESTED", "COMPLETED"].includes(order.status)
    && Boolean(order.shippingCarrier)
    && Boolean(order.trackingNo);
}

function latestLogisticsEvent(order) {
  const timeline = order.timeline || [];
  for (let index = timeline.length - 1; index >= 0; index -= 1) {
    const event = timeline[index];
    if (event?.eventType === "LOGISTICS_UPDATED" || event?.eventType === "ORDER_SHIPPED") {
      return event;
    }
  }
  return null;
}

function renderAdminOrderTimeline(order, maxItems = 0) {
  const timeline = order.timeline || [];
  if (!timeline.length) {
    return `<div class="timeline-empty">当前订单还没有可展示的流转轨迹。</div>`;
  }
  const visibleTimeline = maxItems > 0 ? timeline.slice(-maxItems) : timeline;
  return `
    <div class="timeline-list compact">
      ${visibleTimeline.map((event) => `
        <div class="timeline-item">
          <div class="timeline-marker"></div>
          <div class="timeline-content">
            <div class="row-top">
              <strong class="timeline-title">${escapeHtml(event.title || "订单进度更新")}</strong>
              <div class="inline-meta">${escapeHtml(formatDateTime(event.occurredAt))}</div>
            </div>
            <div class="inline-meta">处理方 ${escapeHtml(event.actorLabel || "系统")}</div>
            ${event.detail ? `<div class="timeline-detail">${escapeHtml(event.detail)}</div>` : ""}
          </div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderKnowledgeDocs() {
  const host = adminById("knowledgeDocsList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看知识文档。</div>`;
    return;
  }
  if (!adminState.knowledgeDocs.length) {
    host.innerHTML = `<div class="empty-state">还没有知识文档，先导入一条售后、物流或商品规则。</div>`;
    return;
  }

  host.innerHTML = adminState.knowledgeDocs.map((doc) => `
    <div class="knowledge-item">
      <div class="row-top">
        <strong>${escapeHtml(doc.title)}</strong>
        <div class="tag">${escapeHtml(doc.docType)}</div>
      </div>
      <div class="inline-meta">${escapeHtml(doc.contentPreview)}</div>
    </div>
  `).join("");
}

function renderKnowledgeSearchResults() {
  const host = adminById("knowledgeSearchResults");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">管理员登录后可测试知识库的向量检索效果。</div>`;
    return;
  }
  if (!adminState.knowledgeSearchKeyword) {
    host.innerHTML = `<div class="empty-state">输入关键词，看看 AI 客服会命中哪些知识片段。</div>`;
    return;
  }
  if (!adminState.knowledgeMatches.length) {
    host.innerHTML = `<div class="empty-state">没有命中相关知识片段，换个关键词再试试。</div>`;
    return;
  }

  host.innerHTML = adminState.knowledgeMatches.map((match) => `
    <div class="knowledge-item knowledge-match">
      <div class="row-top">
        <strong>${escapeHtml(match.title)}</strong>
        <div class="tag">命中片段</div>
      </div>
      <div class="inline-meta">${escapeHtml(match.chunkText)}</div>
    </div>
  `).join("");
}

function renderUsers() {
  const host = adminById("adminUsersList");
  if (!host) {
    return;
  }
  if (!adminState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看用户。</div>`;
    return;
  }
  if (!adminState.users.length) {
    host.innerHTML = `<div class="empty-state">当前还没有可显示的用户。</div>`;
    return;
  }

  host.innerHTML = adminState.users.map((user) => `
    <div class="user-row">
      <div class="row-top">
        <strong>${escapeHtml(user.displayName || user.username)}</strong>
        <div class="tag">${escapeHtml(user.role)}</div>
      </div>
      <div class="inline-meta">${escapeHtml(user.username)}</div>
      <div class="inline-meta">${escapeHtml(user.shippingAddress || "未填写地址")}</div>
    </div>
  `).join("");
}

function resetProductForm() {
  adminState.editingProductId = null;
  adminById("productFormTitle").textContent = "新增商品";
  adminById("productSku").value = "";
  adminById("productName").value = "";
  adminById("productCategory").value = "";
  adminById("productPrice").value = "";
  adminById("productStock").value = "";
  adminById("productImageUrl").value = "";
  adminById("productDescription").value = "";
}

function fillProductForm(productId, scrollIntoView = false) {
  const product = adminState.products.find((item) => item.id === productId);
  if (!product) {
    return;
  }
  adminState.editingProductId = product.id;
  adminById("productFormTitle").textContent = `编辑商品 · ${product.name}`;
  adminById("productSku").value = product.sku || "";
  adminById("productName").value = product.name || "";
  adminById("productCategory").value = product.category || "";
  adminById("productPrice").value = product.price ?? "";
  adminById("productStock").value = product.stock ?? "";
  adminById("productImageUrl").value = product.imageUrl || "";
  adminById("productDescription").value = product.description || "";

  if (scrollIntoView) {
    adminById("productForm")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

async function loadAdminMe() {
  adminState.user = await adminFetchJson(adminAuthUrl("/api/auth/me"));
  if (!adminState.user) {
    setAdminStatus("等待管理员登录");
    return;
  }
  if (adminState.user.role !== "ADMIN") {
    setAdminStatus("当前账号不是管理员");
    throw new Error("当前登录账号没有管理权限");
  }
  setAdminStatus(`管理员已登录：${adminState.user.displayName || adminState.user.username}`);
}

function clearAdminDataState() {
  adminState.dashboard = null;
  adminState.products = [];
  adminState.orders = [];
  adminState.knowledgeDocs = [];
  adminState.knowledgeMatches = [];
  adminState.knowledgeSearchKeyword = "";
  adminState.users = [];
  adminState.assistantSessions = [];
  adminState.assistantMessages = [];
  adminState.assistantEscalations = [];
  adminState.assistantDrafts = [];
  adminState.selectedAssistantSessionId = null;
  adminState.editingProductId = null;
  adminState.afterSalesFilter = "OPEN";
  adminState.assistantOpsFilter = "ALL";
}

async function loadAdminData() {
  if (!adminState.user || adminState.user.role !== "ADMIN") {
    clearAdminDataState();
    renderDashboard();
    renderOperationsWorkbench();
    renderAfterSalesWorkbench();
    renderProducts();
    renderOrderFilterTabs();
    renderOrders();
    renderAssistantOperations();
    renderKnowledgeDocs();
    renderKnowledgeSearchResults();
    renderUsers();
    return;
  }

  const [dashboard, products, orders, knowledgeDocs, users, assistantSessions, assistantEscalations, assistantDrafts] = await Promise.all([
    adminFetchJson("/api/admin/dashboard"),
    adminFetchJson("/api/admin/products"),
    adminFetchJson("/api/admin/orders"),
    adminFetchJson("/api/admin/knowledge/documents"),
    adminFetchJson("/api/admin/users"),
    adminFetchJson("/api/admin/assistant/sessions"),
    adminFetchJson("/api/admin/assistant/escalations"),
    adminFetchJson("/api/admin/assistant/drafts"),
  ]);

  adminState.dashboard = dashboard;
  adminState.products = products || [];
  adminState.orders = orders || [];
  adminState.knowledgeDocs = knowledgeDocs || [];
  adminState.users = users || [];
  adminState.assistantSessions = assistantSessions || [];
  adminState.assistantEscalations = assistantEscalations || [];
  adminState.assistantDrafts = assistantDrafts || [];

  const visibleSessions = filteredAssistantSessions();
  const sessionStillVisible = visibleSessions.some((session) => session.id === adminState.selectedAssistantSessionId);
  if (!sessionStillVisible) {
    adminState.selectedAssistantSessionId = visibleSessions[0]?.id || null;
  }
  if (adminState.selectedAssistantSessionId) {
    adminState.assistantMessages = await adminFetchJson(`/api/admin/assistant/sessions/${adminState.selectedAssistantSessionId}/messages`);
  } else {
    adminState.assistantMessages = [];
  }

  renderDashboard();
  renderOperationsWorkbench();
  renderAfterSalesWorkbench();
  renderProducts();
  renderOrderFilterTabs();
  renderOrders();
  renderAssistantOperations();
  renderKnowledgeDocs();
  renderKnowledgeSearchResults();
  renderUsers();
}

async function applyAssistantOpsFilter(filterKey) {
  adminState.assistantOpsFilter = filterKey || "ALL";
  const visibleSessions = filteredAssistantSessions();
  const sessionStillVisible = visibleSessions.some((session) => session.id === adminState.selectedAssistantSessionId);
  if (!sessionStillVisible) {
    adminState.selectedAssistantSessionId = visibleSessions[0]?.id || null;
    if (adminState.selectedAssistantSessionId) {
      clearAssistantReplyInput();
      adminState.assistantMessages = await adminFetchJson(`/api/admin/assistant/sessions/${adminState.selectedAssistantSessionId}/messages`);
    } else {
      adminState.assistantMessages = [];
    }
  }
  renderAssistantOperations();
}

async function selectAssistantSession(sessionId, scrollIntoView = false) {
  adminState.selectedAssistantSessionId = sessionId;
  clearAssistantReplyInput();
  adminState.assistantMessages = await adminFetchJson(`/api/admin/assistant/sessions/${sessionId}/messages`);
  renderAssistantOperations();
  if (scrollIntoView) {
    adminById("adminAssistantMessages")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

async function adminLogin() {
  const username = adminById("adminUsername").value.trim();
  const password = adminById("adminPassword").value;
  if (!username || !password) {
    throw new Error("请输入管理员账号和密码");
  }
  await adminFetchJson(adminAuthUrl("/api/auth/login"), {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  await bootstrapAdmin();
}

async function adminLogout() {
  await adminFetchJson(adminAuthUrl("/api/auth/logout"), { method: "POST" });
  adminState.user = null;
  clearAdminDataState();
  await loadAdminData();
  setAdminStatus("已退出管理员账号");
}

async function saveProduct(event) {
  event.preventDefault();
  const payload = {
    sku: adminById("productSku").value.trim(),
    name: adminById("productName").value.trim(),
    categoryName: adminById("productCategory").value.trim(),
    categoryDescription: adminById("productCategory").value.trim(),
    price: Number(adminById("productPrice").value),
    stock: Number(adminById("productStock").value),
    imageUrl: adminById("productImageUrl").value.trim(),
    description: adminById("productDescription").value.trim(),
  };
  const url = adminState.editingProductId
    ? `/api/admin/products/${adminState.editingProductId}`
    : "/api/admin/products";
  const method = adminState.editingProductId ? "PUT" : "POST";
  await adminFetchJson(url, {
    method,
    body: JSON.stringify(payload),
  });
  resetProductForm();
  await loadAdminData();
  setAdminStatus("商品信息已保存");
}

function readAdminOrderNote(orderId) {
  return document.querySelector(`.admin-order-note-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readRefundQueueNote(orderId) {
  return document.querySelector(`.ops-refund-note-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readOpsShippingCarrier(orderId) {
  return document.querySelector(`.ops-shipping-carrier-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readOpsTrackingNo(orderId) {
  return document.querySelector(`.ops-tracking-no-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readAdminShippingCarrier(orderId) {
  return document.querySelector(`.admin-shipping-carrier-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readAdminTrackingNo(orderId) {
  return document.querySelector(`.admin-tracking-no-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readAdminReturnAddress(orderId) {
  return document.querySelector(`.admin-return-address-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readAdminLogisticsUpdate(orderId) {
  return document.querySelector(`.admin-logistics-update-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readAssistantReplyContent() {
  return adminById("adminAssistantReplyInput")?.value?.trim() || "";
}

function clearAssistantReplyInput() {
  if (adminById("adminAssistantReplyInput")) {
    adminById("adminAssistantReplyInput").value = "";
  }
}

function setAssistantReplyState(disabled, placeholder) {
  const input = adminById("adminAssistantReplyInput");
  const replyBtn = adminById("adminAssistantReplyBtn");
  const resolveBtn = adminById("adminAssistantResolveBtn");
  if (!input || !replyBtn || !resolveBtn) {
    return;
  }
  input.disabled = disabled;
  replyBtn.disabled = disabled;
  resolveBtn.disabled = disabled;
  if (placeholder) {
    input.placeholder = placeholder;
  }
  if (disabled) {
    input.value = "";
  }
}

async function claimAssistantSession(sessionId = adminState.selectedAssistantSessionId) {
  if (!sessionId) {
    throw new Error("请先选择要认领的 AI 会话");
  }
  adminState.selectedAssistantSessionId = sessionId;
  adminState.assistantOpsFilter = "MY_QUEUE";
  await adminFetchJson(`/api/admin/assistant/sessions/${sessionId}/claim`, {
    method: "POST",
  });
  await loadAdminData();
  setAdminStatus("AI 会话已认领，可以继续跟进和发送人工回复");
}

async function assignAssistantSession() {
  const session = currentAdminAssistantSession();
  if (!session) {
    throw new Error("请先选择要分派的 AI 会话");
  }
  if (session.serviceStatus !== "ESCALATED") {
    throw new Error("只有人工跟进中的会话才支持分派");
  }
  const adminUsername = adminById("adminAssistantAssignSelect")?.value?.trim() || "";
  if (!adminUsername) {
    throw new Error("请选择要转交的客服账号");
  }
  if (session.assignedAdminUsername === adminUsername) {
    throw new Error("当前会话已经分配给该客服");
  }

  const targetAdmin = adminOperators().find((user) => user.username === adminUsername);
  await adminFetchJson(`/api/admin/assistant/sessions/${session.id}/assign`, {
    method: "POST",
    body: JSON.stringify({ adminUsername }),
  });
  adminState.assistantOpsFilter = adminState.user?.username === adminUsername ? "MY_QUEUE" : "ALL";
  await loadAdminData();
  const targetLabel = targetAdmin?.displayName || targetAdmin?.username || adminUsername;
  setAdminStatus(`${session.assignedAdminUsername ? "AI 会话已转交" : "AI 会话已分派"}给 ${targetLabel}`);
}

async function replyAssistantSession(resolve = false) {
  if (!adminState.selectedAssistantSessionId) {
    throw new Error("请先选择要回复的 AI 会话");
  }
  const content = readAssistantReplyContent();
  if (!content) {
    throw new Error("请输入人工回复内容");
  }
  await adminFetchJson(`/api/admin/assistant/sessions/${adminState.selectedAssistantSessionId}/reply`, {
    method: "POST",
    body: JSON.stringify({ content, resolve }),
  });
  adminState.assistantOpsFilter = resolve ? "RESOLVED" : "MY_QUEUE";
  clearAssistantReplyInput();
  await loadAdminData();
  setAdminStatus(resolve ? "人工客服回复已发送，并已将当前会话结案" : "人工客服回复已发送到客户端会话");
}

async function updateOrderStatus(orderId, explicitStatus = null, explicitNote = null, explicitCarrier = null, explicitTrackingNo = null) {
  const nextStatus = explicitStatus
    || document.querySelector(`.order-status-select[data-order-id="${orderId}"]`)?.value;
  if (!nextStatus) {
    throw new Error("未选择订单状态");
  }
  const note = explicitNote ?? readAdminOrderNote(orderId);
  const shippingCarrier = explicitCarrier ?? readAdminShippingCarrier(orderId);
  const trackingNo = explicitTrackingNo ?? readAdminTrackingNo(orderId);
  await adminFetchJson(`/api/admin/orders/${orderId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status: nextStatus, note, shippingCarrier, trackingNo }),
  });
  await loadAdminData();
  setAdminStatus(`订单状态已更新为 ${statusLabel(nextStatus)}`);
}

async function reviewRefund(orderId, approved, explicitNote = null) {
  const note = explicitNote ?? readAdminOrderNote(orderId);
  await adminFetchJson(`/api/admin/orders/${orderId}/refund-review`, {
    method: "PATCH",
    body: JSON.stringify({ approved, note }),
  });
  await loadAdminData();
  setAdminStatus(approved ? "退款申请已通过，库存已回补" : "退款申请已驳回，订单回到已完成");
}

async function appendLogisticsUpdate(orderId) {
  const detail = readAdminLogisticsUpdate(orderId);
  if (!detail) {
    throw new Error("请先填写物流节点说明");
  }
  await adminFetchJson(`/api/admin/orders/${orderId}/logistics-updates`, {
    method: "POST",
    body: JSON.stringify({ detail }),
  });
  await loadAdminData();
  setAdminStatus("物流节点已追加，客户端和 AI 查询都会同步看到最新进展");
}

async function provideReturnInstructions(orderId) {
  const returnAddress = readAdminReturnAddress(orderId);
  const reply = readAdminOrderNote(orderId);
  if (!returnAddress || !reply) {
    throw new Error("请先填写退货回寄地址和退货说明");
  }
  await adminFetchJson(`/api/admin/orders/${orderId}/after-sales/return-instructions`, {
    method: "POST",
    body: JSON.stringify({ returnAddress, reply }),
  });
  await loadAdminData();
  setAdminStatus("退货指引已发送，客户端会看到回寄地址和售后说明");
}

async function confirmReturnAndRefund(orderId) {
  const note = readAdminOrderNote(orderId);
  await adminFetchJson(`/api/admin/orders/${orderId}/after-sales/confirm-return-refund`, {
    method: "POST",
    body: JSON.stringify({ approved: true, note }),
  });
  await loadAdminData();
  setAdminStatus("已确认收到退货并完成退款");
}

function focusOrder(orderNo, filterStatus = "ALL") {
  adminState.orderFilterKeyword = orderNo || "";
  adminState.orderFilterStatus = filterStatus || "ALL";
  if (adminById("adminOrderSearchInput")) {
    adminById("adminOrderSearchInput").value = adminState.orderFilterKeyword;
  }
  renderOrderFilterTabs();
  renderOrders();
  adminById("orderManagement")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function bindFocusOrderButtons(host) {
  host.querySelectorAll(".ops-focus-order-btn").forEach((button) => {
    button.addEventListener("click", () => {
      focusOrder(button.dataset.orderNo || "", button.dataset.orderFilter || "ALL");
    });
  });
}

function applyOrderSearch() {
  adminState.orderFilterKeyword = adminById("adminOrderSearchInput")?.value?.trim() || "";
  renderOrderFilterTabs();
  renderOrders();
}

function clearOrderFilters() {
  adminState.orderFilterKeyword = "";
  adminState.orderFilterStatus = "ALL";
  if (adminById("adminOrderSearchInput")) {
    adminById("adminOrderSearchInput").value = "";
  }
  renderOrderFilterTabs();
  renderOrders();
}

async function saveKnowledge(event) {
  event.preventDefault();
  await adminFetchJson("/api/admin/knowledge/import", {
    method: "POST",
    body: JSON.stringify({
      title: adminById("knowledgeTitle").value.trim(),
      docType: adminById("knowledgeType").value.trim(),
      content: adminById("knowledgeContent").value.trim(),
    }),
  });
  adminById("knowledgeTitle").value = "";
  adminById("knowledgeType").value = "";
  adminById("knowledgeContent").value = "";
  await loadAdminData();
  if (adminById("knowledgeSearchInput").value.trim()) {
    await searchKnowledge();
  }
  setAdminStatus("知识文档已导入并同步到检索索引");
}

async function searchKnowledge() {
  const keyword = adminById("knowledgeSearchInput").value.trim();
  adminState.knowledgeSearchKeyword = keyword;
  if (!keyword) {
    adminState.knowledgeMatches = [];
    renderKnowledgeSearchResults();
    return;
  }
  const result = await adminFetchJson(`/api/admin/knowledge/search?keyword=${encodeURIComponent(keyword)}`);
  adminState.knowledgeSearchKeyword = result.keyword || keyword;
  adminState.knowledgeMatches = result.matches || [];
  renderKnowledgeSearchResults();
  setAdminStatus(`知识库检索完成：${adminState.knowledgeSearchKeyword}，命中 ${adminState.knowledgeMatches.length} 条片段`);
}

async function bootstrapAdmin() {
  try {
    await loadAdminMe();
  } catch (error) {
    adminState.user = null;
  }
  await loadAdminData();
}

async function runAdminAction(action) {
  try {
    await action();
  } catch (error) {
    setAdminStatus(error.message || "操作失败");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  adminById("adminLoginBtn").addEventListener("click", () => runAdminAction(adminLogin));
  adminById("adminLogoutBtn").addEventListener("click", () => runAdminAction(adminLogout));
  adminById("fillAdminBtn").addEventListener("click", () => {
    adminById("adminUsername").value = "admin";
    adminById("adminPassword").value = "admin123";
  });
  adminById("refreshDashboardBtn").addEventListener("click", () => runAdminAction(loadAdminData));
  adminById("refreshAfterSalesBtn").addEventListener("click", () => runAdminAction(loadAdminData));
  adminById("refreshAssistantOpsBtn").addEventListener("click", () => runAdminAction(loadAdminData));
  adminById("adminAssistantClaimBtn").addEventListener("click", () => runAdminAction(() => claimAssistantSession()));
  adminById("adminAssistantAssignSelect").addEventListener("change", () => syncAssistantAssignControls());
  adminById("adminAssistantAssignBtn").addEventListener("click", () => runAdminAction(assignAssistantSession));
  adminById("adminAssistantReplyBtn").addEventListener("click", () => runAdminAction(() => replyAssistantSession(false)));
  adminById("adminAssistantResolveBtn").addEventListener("click", () => runAdminAction(() => replyAssistantSession(true)));
  adminById("productForm").addEventListener("submit", (event) => runAdminAction(() => saveProduct(event)));
  adminById("resetProductBtn").addEventListener("click", resetProductForm);
  adminById("adminOrderSearchBtn").addEventListener("click", () => runAdminAction(async () => applyOrderSearch()));
  adminById("clearOrderFiltersBtn").addEventListener("click", clearOrderFilters);
  adminById("adminOrderSearchInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      runAdminAction(async () => applyOrderSearch());
    }
  });
  adminById("knowledgeForm").addEventListener("submit", (event) => runAdminAction(() => saveKnowledge(event)));
  adminById("knowledgeSearchBtn").addEventListener("click", () => runAdminAction(searchKnowledge));
  adminById("knowledgeSearchInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      runAdminAction(searchKnowledge);
    }
  });
  runAdminAction(bootstrapAdmin);
});
