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
};

const ADMIN_STATUS_LABELS = {
  DRAFT: "草稿",
  PENDING_CONFIRMATION: "待确认",
  CONFIRMED: "待发货",
  PROCESSING: "处理中",
  SHIPPED: "已发货",
  COMPLETED: "已完成",
  REFUND_REQUESTED: "退款审核中",
  REFUNDED: "已退款",
  CANCELLED: "已取消",
};

const ORDER_FILTERS = [
  { key: "ALL", label: "全部订单" },
  { key: "REFUND_REQUESTED", label: "退款审核" },
  { key: "PENDING_SHIPMENT", label: "待发货" },
  { key: "SHIPPED", label: "已发货" },
  { key: "COMPLETED", label: "已完成" },
  { key: "REFUNDED", label: "已退款" },
  { key: "CANCELLED", label: "已取消" },
];

const ASSISTANT_SERVICE_STATUS_LABELS = {
  ACTIVE: "AI 在线服务中",
  ESCALATED: "人工跟进中",
  RESOLVED: "人工已回复",
};

function adminById(id) {
  return document.getElementById(id);
}

async function adminFetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    credentials: "same-origin",
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
    order.riskNote,
    statusLabel(order.status),
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
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity}</div>
        `).join("")}
      </div>
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

function renderAssistantOperations() {
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
  if (!adminState.assistantSessions.length) {
    host.innerHTML = `<div class="empty-state">当前还没有 AI 客服会话记录。</div>`;
    return;
  }

  host.innerHTML = adminState.assistantSessions.map((session) => `
    <button type="button" class="session-pill admin-session-pill ${adminState.selectedAssistantSessionId === session.id ? "active" : ""}" data-assistant-session-id="${session.id}">
      <div class="row-top">
        <strong>${escapeHtml(session.displayName || session.username)}</strong>
        <div class="tag ${assistantServiceTagClass(session.serviceStatus)}">${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
      </div>
      <div class="inline-meta">${escapeHtml(session.lastIntent || "chat")} · ${escapeHtml(formatDateTime(session.createdAt))}</div>
      <div class="inline-meta">${escapeHtml(session.summary || "暂无摘要")}</div>
      <div class="inline-meta">${escapeHtml(session.threadId)} · ${session.messageCount} 条消息</div>
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
  if (!meta || !host || !replyInput || !replyBtn || !resolveBtn) {
    return;
  }
  if (!adminState.user) {
    meta.textContent = "管理员登录后可查看 AI 会话摘要。";
    host.innerHTML = `<div class="empty-state">登录后可查看 AI 会话消息。</div>`;
    setAssistantReplyState(true, "登录后可发送人工回复");
    return;
  }
  const session = currentAdminAssistantSession();
  if (!session) {
    meta.textContent = "选择左侧会话后，这里会展示用户、线程、摘要和意图。";
    host.innerHTML = `<div class="empty-state">当前还没有选中的 AI 会话。</div>`;
    setAssistantReplyState(true, "先选择一条 AI 会话");
    return;
  }

  setAssistantReplyState(false, session.serviceStatus === "ESCALATED"
    ? "当前会话已转人工，输入回复后客户端会立即看到"
    : "可以主动给用户发送人工回复，也可以发送并结案");
  meta.innerHTML = `
    <div><strong>用户：</strong>${escapeHtml(session.displayName || session.username)} / ${escapeHtml(session.username)}</div>
    <div><strong>会话：</strong>${escapeHtml(session.threadId)} · <strong>状态：</strong>${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
    <div><strong>意图：</strong>${escapeHtml(session.lastIntent || "chat")} · <strong>消息数：</strong>${session.messageCount}</div>
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
        <div class="inline-meta">意图 ${escapeHtml(session.lastIntent || "chat")} · 消息 ${session.messageCount} 条</div>
      </div>
      <div class="panel-hint">${escapeHtml(session.summary || "等待客服查看详情")}</div>
      <div class="order-actions">
        <button type="button" class="ghost assistant-focus-session-btn" data-assistant-session-id="${session.id}">查看会话</button>
      </div>
    </article>
  `).join("");

  host.querySelectorAll(".assistant-focus-session-btn").forEach((button) => {
    button.addEventListener("click", () => runAdminAction(() => selectAssistantSession(Number(button.dataset.assistantSessionId), true)));
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
      ${renderAdminOrderShippingMeta(order)}
      ${order.riskNote ? `<div class="inline-meta">备注 ${escapeHtml(order.riskNote)}</div>` : ""}
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)}${item.productSku ? ` · SKU ${escapeHtml(item.productSku)}` : ""} x ${item.quantity} · ${money(item.lineTotal)}</div>
        `).join("")}
      </div>
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
}

function renderAdminOrderControls(order) {
  const nextStatuses = availableNextStatuses(order.status);
  const notePlaceholder = order.status === "REFUND_REQUESTED"
    ? "审核意见，例如同意退款原因或驳回说明"
    : "运营备注，例如催发货、用户沟通结果";

  if (order.status === "REFUND_REQUESTED") {
    return `
      <div class="order-action-block">
        <textarea class="admin-order-note-input" data-order-id="${order.id}" placeholder="${escapeHtml(notePlaceholder)}"></textarea>
        <div class="order-actions">
          <button type="button" class="primary refund-review-btn" data-order-id="${order.id}" data-approved="true">同意退款</button>
          <button type="button" class="ghost refund-review-btn" data-order-id="${order.id}" data-approved="false">驳回退款</button>
        </div>
        <div class="panel-hint">同意退款后订单会变成“已退款”并回补库存；驳回后订单会回到“已完成”。</div>
      </div>
    `;
  }

  if (!nextStatuses.length) {
    return `<div class="panel-hint">当前订单已经进入最终状态，暂无可继续推进的履约动作。</div>`;
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
  `;
}

function availableNextStatuses(status) {
  switch (status) {
    case "DRAFT":
      return ["PENDING_CONFIRMATION", "CONFIRMED", "CANCELLED"];
    case "PENDING_CONFIRMATION":
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

function shouldShowAdminShippingFields(order, nextStatuses) {
  return nextStatuses.includes("SHIPPED") || Boolean(order.shippingCarrier) || Boolean(order.trackingNo);
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
  return lines.join("");
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
  adminState.user = await adminFetchJson("/api/auth/me");
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
}

async function loadAdminData() {
  if (!adminState.user || adminState.user.role !== "ADMIN") {
    clearAdminDataState();
    renderDashboard();
    renderOperationsWorkbench();
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

  const sessionStillExists = adminState.assistantSessions.some((session) => session.id === adminState.selectedAssistantSessionId);
  if (!sessionStillExists) {
    adminState.selectedAssistantSessionId = adminState.assistantSessions[0]?.id || null;
  }
  if (adminState.selectedAssistantSessionId) {
    adminState.assistantMessages = await adminFetchJson(`/api/admin/assistant/sessions/${adminState.selectedAssistantSessionId}/messages`);
  } else {
    adminState.assistantMessages = [];
  }

  renderDashboard();
  renderOperationsWorkbench();
  renderProducts();
  renderOrderFilterTabs();
  renderOrders();
  renderAssistantOperations();
  renderKnowledgeDocs();
  renderKnowledgeSearchResults();
  renderUsers();
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
  await adminFetchJson("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  await bootstrapAdmin();
}

async function adminLogout() {
  await adminFetchJson("/api/auth/logout", { method: "POST" });
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
  adminById("refreshAssistantOpsBtn").addEventListener("click", () => runAdminAction(loadAdminData));
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
