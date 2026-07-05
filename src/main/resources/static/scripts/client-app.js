const clientState = {
  user: null,
  products: [],
  categories: [],
  category: "全部",
  orders: [],
  cart: { items: [], totalAmount: 0, totalItems: 0 },
  sessions: [],
  sessionId: null,
  threadId: null,
  pendingDraft: null,
};

const ORDER_STATUS_LABELS = {
  DRAFT: "草稿",
  PENDING_CONFIRMATION: "待确认",
  CONFIRMED: "待发货",
  PROCESSING: "处理中",
  SHIPPED: "已发货",
  COMPLETED: "已完成",
  REFUND_REQUESTED: "退款处理中",
  REFUNDED: "已退款",
  CANCELLED: "已取消",
};

function clientById(id) {
  return document.getElementById(id);
}

async function clientFetchJson(url, options = {}) {
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
  return ORDER_STATUS_LABELS[status] || status;
}

function draftStatusLabel(status) {
  if (status === "PENDING_CONFIRMATION") {
    return "待确认";
  }
  if (status === "CONFIRMED") {
    return "已转正式订单";
  }
  if (status === "CANCELLED") {
    return "已取消";
  }
  return status || "未知状态";
}

function setStoreStatus(text) {
  const node = clientById("storeStatus");
  if (node) {
    node.textContent = text;
  }
}

function setInputValue(id, value) {
  const node = clientById(id);
  if (node) {
    node.value = value ?? "";
  }
}

function currency(value) {
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(value || 0);
}

function renderCategories() {
  const host = clientById("categoryTabs");
  if (!host) {
    return;
  }
  const categories = ["全部", ...clientState.categories.map((item) => item.name)];
  host.innerHTML = categories.map((name) => `
    <button type="button" class="${clientState.category === name ? "active" : ""}" data-category="${name}">
      ${escapeHtml(name)}
    </button>
  `).join("");
  host.querySelectorAll("button").forEach((button) => {
    button.addEventListener("click", () => {
      clientState.category = button.dataset.category;
      renderProducts();
    });
  });
}

function filteredProducts() {
  const keyword = clientById("searchInput")?.value?.trim().toLowerCase() || "";
  return clientState.products.filter((product) => {
    const matchesCategory = clientState.category === "全部" || product.category === clientState.category;
    const matchesKeyword = !keyword
      || `${product.name} ${product.description || ""} ${product.category || ""}`.toLowerCase().includes(keyword);
    return matchesCategory && matchesKeyword;
  });
}

function renderProducts() {
  const products = filteredProducts();
  const host = clientById("productGrid");
  const meta = clientById("productMeta");
  if (!host || !meta) {
    return;
  }
  meta.textContent = `共 ${products.length} 件商品，当前分类：${clientState.category}`;
  if (products.length === 0) {
    host.innerHTML = `<div class="empty-state">没有找到符合条件的商品，试试切换分类或更换关键词。</div>`;
    return;
  }
  host.innerHTML = products.map((product) => `
    <article class="product-card">
      <img src="${product.imageUrl || "/favicon.svg"}" alt="${escapeHtml(product.name)}">
      <div class="product-body">
        <div class="tag">${escapeHtml(product.category || "未分类")}</div>
        <h3>${escapeHtml(product.name)}</h3>
        <div class="product-meta">${escapeHtml(product.description || "暂无描述")}</div>
        <div class="product-price">
          <span>${currency(product.price)}</span>
          <span class="inline-meta">库存 ${product.stock}</span>
        </div>
        <button type="button" class="primary add-cart-btn" data-product-id="${product.id}">
          加入购物车
        </button>
      </div>
    </article>
  `).join("");
  host.querySelectorAll(".add-cart-btn").forEach((button) => {
    button.addEventListener("click", () => addToCart(Number(button.dataset.productId)));
  });
}

function renderCart() {
  const host = clientById("cartItems");
  const summary = clientById("cartSummary");
  if (!host || !summary) {
    return;
  }
  if (!clientState.user) {
    summary.textContent = "登录后可加入购物车并提交订单";
    host.innerHTML = `<div class="empty-state">当前未登录，先登录再开始购物。</div>`;
    return;
  }
  const cart = clientState.cart || { items: [], totalAmount: 0, totalItems: 0 };
  summary.textContent = `共 ${cart.totalItems || 0} 件商品，合计 ${currency(cart.totalAmount || 0)}`;
  if (!cart.items || cart.items.length === 0) {
    host.innerHTML = `<div class="empty-state">购物车还是空的，去挑几件商品吧。</div>`;
    return;
  }
  host.innerHTML = cart.items.map((item) => `
    <div class="cart-row">
      <div class="thumb-row">
        <img src="${item.imageUrl || "/favicon.svg"}" alt="${escapeHtml(item.productName)}">
        <div>
          <strong>${escapeHtml(item.productName)}</strong>
          <div class="inline-meta">${currency(item.unitPrice)} x ${item.quantity}</div>
        </div>
      </div>
      <div class="row-bottom">
        <div class="tag">${currency(item.lineTotal)}</div>
        <div class="auth-actions">
          <button type="button" class="ghost cart-qty-btn" data-item-id="${item.itemId}" data-quantity="${item.quantity - 1}">-</button>
          <button type="button" class="ghost cart-qty-btn" data-item-id="${item.itemId}" data-quantity="${item.quantity + 1}">+</button>
          <button type="button" class="ghost cart-remove-btn" data-item-id="${item.itemId}">删除</button>
        </div>
      </div>
    </div>
  `).join("");
  host.querySelectorAll(".cart-qty-btn").forEach((button) => {
    button.addEventListener("click", () => updateCartItem(Number(button.dataset.itemId), Number(button.dataset.quantity)));
  });
  host.querySelectorAll(".cart-remove-btn").forEach((button) => {
    button.addEventListener("click", () => removeCartItem(Number(button.dataset.itemId)));
  });
}

function renderProfile() {
  const hint = clientById("profileHint");
  const fields = [
    clientById("profileDisplayName"),
    clientById("profilePhone"),
    clientById("profileShippingAddress"),
    clientById("profilePreferences"),
    clientById("saveProfileBtn"),
    clientById("syncAddressBtn"),
  ];
  if (!clientState.user) {
    if (hint) {
      hint.textContent = "登录后可保存常用资料，后续下单会自动带出默认地址。";
    }
    fields.forEach((field) => {
      if (field) {
        field.disabled = true;
      }
    });
    setInputValue("profileDisplayName", "");
    setInputValue("profilePhone", "");
    setInputValue("profileShippingAddress", "");
    setInputValue("profilePreferences", "");
    return;
  }
  if (hint) {
    hint.textContent = "默认地址会同步给购物车结算，AI 客服也会结合偏好和地址给出更贴近业务的建议。";
  }
  fields.forEach((field) => {
    if (field) {
      field.disabled = false;
    }
  });
  setInputValue("profileDisplayName", clientState.user.displayName || "");
  setInputValue("profilePhone", clientState.user.phone || "");
  setInputValue("profileShippingAddress", clientState.user.shippingAddress || "");
  setInputValue("profilePreferences", clientState.user.preferencesSummary || "");
}

function renderOrders() {
  const host = clientById("ordersList");
  if (!host) {
    return;
  }
  if (!clientState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看订单状态、收货地址和售后动作。</div>`;
    return;
  }
  if (!clientState.orders.length) {
    host.innerHTML = `<div class="empty-state">你还没有订单，购物车结算或确认 AI 下单草稿后就会出现在这里。</div>`;
    return;
  }
  host.innerHTML = clientState.orders.map((order) => `
    <article class="order-card">
      <div class="row-top">
        <strong>${escapeHtml(order.orderNo)}</strong>
        <div class="tag">${escapeHtml(statusLabel(order.status))}</div>
      </div>
      <div class="order-summary">
        <div class="inline-meta">状态编码 ${escapeHtml(order.status)}</div>
        <div class="inline-meta">金额 ${currency(order.totalAmount)}</div>
        <div class="inline-meta">地址 ${escapeHtml(order.shippingAddress || "待补充收货地址")}</div>
        ${order.riskNote ? `<div class="inline-meta">备注 ${escapeHtml(order.riskNote)}</div>` : ""}
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)} x ${item.quantity} · ${currency(item.lineTotal)}</div>
        `).join("")}
      </div>
      ${renderOrderActionArea(order)}
    </article>
  `).join("");
  host.querySelectorAll(".order-action-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => performOrderAction(Number(button.dataset.orderId), button.dataset.action)));
  });
  host.querySelectorAll(".order-ai-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => sendAssistantMessage(`请结合订单 ${button.dataset.orderNo} 的当前状态，告诉我下一步可以做什么`)));
  });
}

function renderOrderActionArea(order) {
  const buttons = [];
  if (canCancelOrder(order.status)) {
    buttons.push(`<button type="button" class="ghost order-action-btn" data-order-id="${order.id}" data-action="cancel">取消订单</button>`);
  }
  if (canConfirmReceipt(order.status)) {
    buttons.push(`<button type="button" class="primary order-action-btn" data-order-id="${order.id}" data-action="confirm-receipt">确认收货</button>`);
  }
  if (canRequestRefund(order.status)) {
    buttons.push(`<button type="button" class="ghost order-action-btn" data-order-id="${order.id}" data-action="refund">申请退款</button>`);
  }
  buttons.push(`<button type="button" class="ghost order-ai-btn" data-order-no="${escapeHtml(order.orderNo)}">问 AI</button>`);
  const needsNote = canCancelOrder(order.status) || canRequestRefund(order.status);
  return `
    <div class="order-action-block">
      ${needsNote ? `<textarea class="order-note-input" data-order-id="${order.id}" placeholder="${escapeHtml(orderActionPlaceholder(order.status))}"></textarea>` : ""}
      <div class="order-actions">
        ${buttons.join("")}
      </div>
      <div class="panel-hint">${escapeHtml(orderActionHint(order.status))}</div>
    </div>
  `;
}

function orderActionPlaceholder(status) {
  if (canCancelOrder(status)) {
    return "取消原因，例如重复下单、地址填写有误";
  }
  if (canRequestRefund(status)) {
    return "退款原因，例如商品问题、与预期不符";
  }
  return "可填写售后说明";
}

function orderActionHint(status) {
  if (canCancelOrder(status)) {
    return "待发货和处理中订单可以直接在线取消。";
  }
  if (canConfirmReceipt(status)) {
    return "确认收货后，订单会变成已完成。";
  }
  if (canRequestRefund(status)) {
    return "已发货或已完成订单支持发起退款申请。";
  }
  if (status === "REFUND_REQUESTED") {
    return "退款申请已经提交，等待平台处理。";
  }
  if (status === "REFUNDED") {
    return "退款已经处理完成，库存和售后记录已在平台侧更新。";
  }
  return "当前状态暂无可执行的在线售后动作。";
}

function canCancelOrder(status) {
  return status === "CONFIRMED" || status === "PROCESSING";
}

function canConfirmReceipt(status) {
  return status === "SHIPPED";
}

function canRequestRefund(status) {
  return status === "SHIPPED" || status === "COMPLETED";
}

function renderSessions() {
  const host = clientById("sessionList");
  if (!host) {
    return;
  }
  if (!clientState.user) {
    host.innerHTML = `<div class="empty-state">登录后可开启客服会话。</div>`;
    return;
  }
  if (!clientState.sessions.length) {
    host.innerHTML = "";
    return;
  }
  host.innerHTML = clientState.sessions.map((session) => `
    <button type="button" class="session-pill ${clientState.sessionId === session.id ? "active" : ""}" data-session-id="${session.id}">
      <strong>${escapeHtml(session.title)}</strong>
      <div class="inline-meta">${escapeHtml(session.lastIntent || "chat")}</div>
    </button>
  `).join("");
  host.querySelectorAll(".session-pill").forEach((button) => {
    button.addEventListener("click", () => selectSession(Number(button.dataset.sessionId)));
  });
}

function renderAssistantMessages(messages) {
  const host = clientById("assistantMessages");
  if (!host) {
    return;
  }
  if (!messages.length) {
    host.innerHTML = `<div class="empty-state">问 AI 客服商品、订单、售后或知识库问题，它会结合业务数据来回答。</div>`;
    return;
  }
  host.innerHTML = messages.map((message) => `
    <div class="message-bubble ${message.role}">
      ${escapeHtml(message.content).replaceAll("\n", "<br>")}
    </div>
  `).join("");
  host.scrollTop = host.scrollHeight;
}

function renderAssistantMeta(payload) {
  const host = clientById("assistantMeta");
  if (!host) {
    return;
  }
  if (!payload) {
    host.textContent = "这里会展示 AI 命中的知识片段、会话意图和下单草稿摘要。";
    return;
  }
  const sourceText = payload.sources?.length ? payload.sources.join(" | ") : "无知识片段";
  const draftText = payload.pendingOrderDraft ? "已生成下单草稿" : "未生成下单草稿";
  host.textContent = `意图：${payload.intent} | ${draftText} | 来源：${sourceText}`;
}

function renderAssistantDraft() {
  const host = clientById("assistantDraft");
  if (!host) {
    return;
  }
  if (!clientState.user) {
    host.innerHTML = `<div class="empty-state">登录后，AI 生成的下单草稿会出现在这里。</div>`;
    return;
  }
  const draft = clientState.pendingDraft;
  if (!draft || draft.status !== "PENDING_CONFIRMATION") {
    host.innerHTML = `<div class="empty-state">当前会话还没有待确认的 AI 下单草稿。</div>`;
    return;
  }
  host.innerHTML = `
    <div class="draft-card">
      <div class="row-top">
        <div>
          <strong>AI 下单草稿</strong>
          <div class="inline-meta">线程 ${escapeHtml(draft.threadId)} · ${escapeHtml(draftStatusLabel(draft.status))}</div>
        </div>
        <div class="tag success">${escapeHtml(draftStatusLabel(draft.status))}</div>
      </div>
      <div class="draft-summary">
        <div class="inline-meta">商品 ${escapeHtml(draft.productName)}</div>
        <div class="inline-meta">数量 ${draft.quantity}</div>
        <div class="inline-meta">单价 ${currency(draft.unitPrice)}</div>
        <div class="inline-meta">合计 ${currency(draft.totalAmount)}</div>
      </div>
      <div class="panel-hint">${escapeHtml(draft.note || "确认后会创建正式订单，并进入待发货流程。")}</div>
      <div class="order-actions">
        <button id="confirmDraftBtn" type="button" class="primary">确认下单</button>
        <button id="cancelDraftBtn" type="button" class="ghost">取消草稿</button>
      </div>
    </div>
  `;
  clientById("confirmDraftBtn")?.addEventListener("click", () => runClientAction(confirmAssistantDraft));
  clientById("cancelDraftBtn")?.addEventListener("click", () => runClientAction(cancelAssistantDraft));
}

async function loadMe() {
  clientState.user = await clientFetchJson("/api/auth/me");
  if (clientState.user) {
    setStoreStatus(`已登录：${clientState.user.displayName || clientState.user.username} · ${clientState.user.role}`);
    clientById("shippingAddress").value = clientState.user.shippingAddress || "";
  } else {
    setStoreStatus("未登录，可先浏览商品");
    clientById("shippingAddress").value = "";
  }
  renderProfile();
}

async function loadCatalog() {
  const [categories, products] = await Promise.all([
    clientFetchJson("/api/categories"),
    clientFetchJson("/api/products"),
  ]);
  clientState.categories = categories || [];
  clientState.products = products || [];
  renderCategories();
  renderProducts();
}

async function loadCart() {
  if (!clientState.user) {
    clientState.cart = { items: [], totalAmount: 0, totalItems: 0 };
    renderCart();
    return;
  }
  clientState.cart = await clientFetchJson("/api/cart");
  renderCart();
}

async function loadOrders() {
  if (!clientState.user) {
    clientState.orders = [];
    renderOrders();
    return;
  }
  clientState.orders = await clientFetchJson("/api/orders");
  renderOrders();
}

async function loadSessions() {
  if (!clientState.user) {
    clientState.sessions = [];
    clientState.sessionId = null;
    clientState.threadId = null;
    clientState.pendingDraft = null;
    renderSessions();
    renderAssistantMessages([]);
    renderAssistantMeta(null);
    renderAssistantDraft();
    return;
  }
  clientState.sessions = await clientFetchJson("/api/assistant/sessions");
  if (!clientState.sessionId && clientState.sessions.length) {
    clientState.sessionId = clientState.sessions[0].id;
    clientState.threadId = `assistant-${clientState.sessionId}`;
  }
  renderSessions();
  if (clientState.sessionId) {
    await Promise.all([loadMessages(clientState.sessionId), loadPendingDraft()]);
  } else {
    renderAssistantMessages([]);
    clientState.pendingDraft = null;
    renderAssistantDraft();
  }
}

async function loadMessages(sessionId) {
  const messages = await clientFetchJson(`/api/assistant/sessions/${sessionId}/messages`);
  renderAssistantMessages(messages || []);
}

async function selectSession(sessionId) {
  const session = await clientFetchJson(`/api/assistant/sessions/${sessionId}`);
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  renderSessions();
  await Promise.all([loadMessages(session.id), loadPendingDraft()]);
}

async function createSession() {
  const session = await clientFetchJson("/api/assistant/sessions", { method: "POST" });
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  clientState.pendingDraft = null;
  renderAssistantDraft();
  await loadSessions();
}

async function loadPendingDraft() {
  if (!clientState.user || !clientState.threadId) {
    clientState.pendingDraft = null;
    renderAssistantDraft();
    return;
  }
  clientState.pendingDraft = await clientFetchJson(`/api/orders/draft/current?threadId=${encodeURIComponent(clientState.threadId)}`);
  renderAssistantDraft();
}

async function login() {
  const username = clientById("loginUsername").value.trim();
  const password = clientById("loginPassword").value;
  if (!username || !password) {
    throw new Error("请先输入用户名和密码");
  }
  await clientFetchJson("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  await bootstrapClient();
}

async function registerUser() {
  const username = clientById("loginUsername").value.trim();
  const password = clientById("loginPassword").value;
  if (!username || !password) {
    throw new Error("注册需要填写用户名和密码");
  }
  await clientFetchJson("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({
      username,
      password,
      displayName: username,
    }),
  });
  await login();
}

async function logout() {
  await clientFetchJson("/api/auth/logout", { method: "POST" });
  clientState.user = null;
  clientState.sessionId = null;
  clientState.threadId = null;
  clientState.pendingDraft = null;
  await bootstrapClient();
}

async function addToCart(productId) {
  if (!clientState.user) {
    throw new Error("请先登录再加入购物车");
  }
  await clientFetchJson("/api/cart/items", {
    method: "POST",
    body: JSON.stringify({ productId, quantity: 1 }),
  });
  await Promise.all([loadCart(), loadCatalog()]);
}

async function updateCartItem(itemId, quantity) {
  await clientFetchJson(`/api/cart/items/${itemId}`, {
    method: "PATCH",
    body: JSON.stringify({ quantity }),
  });
  await Promise.all([loadCart(), loadCatalog()]);
}

async function removeCartItem(itemId) {
  await clientFetchJson(`/api/cart/items/${itemId}`, {
    method: "DELETE",
  });
  await loadCart();
}

async function checkout() {
  if (!clientState.user) {
    throw new Error("请先登录再提交订单");
  }
  await clientFetchJson("/api/cart/checkout", {
    method: "POST",
    body: JSON.stringify({
      shippingAddress: clientById("shippingAddress").value.trim(),
    }),
  });
  setStoreStatus("订单已创建，正在刷新购物车与订单列表");
  await Promise.all([loadCart(), loadOrders(), loadCatalog()]);
}

async function sendAssistantMessage(message) {
  if (!clientState.user) {
    throw new Error("请先登录再使用 AI 客服");
  }
  const finalMessage = (message || clientById("assistantInput").value).trim();
  if (!finalMessage) {
    return;
  }
  if (!clientState.sessionId) {
    await createSession();
  }
  const payload = await clientFetchJson("/api/assistant/chat", {
    method: "POST",
    body: JSON.stringify({
      sessionId: clientState.sessionId,
      message: finalMessage,
      threadId: clientState.threadId,
    }),
  });
  clientState.sessionId = payload.sessionId;
  clientState.threadId = payload.threadId;
  clientById("assistantInput").value = "";
  renderAssistantMeta(payload);
  await Promise.all([loadSessions(), loadOrders()]);
}

async function confirmAssistantDraft() {
  if (!clientState.pendingDraft?.threadId) {
    throw new Error("当前没有可确认的 AI 下单草稿");
  }
  const createdOrder = await clientFetchJson("/api/orders/confirm", {
    method: "POST",
    body: JSON.stringify({ threadId: clientState.pendingDraft.threadId }),
  });
  clientState.pendingDraft = null;
  renderAssistantDraft();
  setStoreStatus(`AI 草稿已转成正式订单：${createdOrder.orderNo}`);
  await Promise.all([loadCatalog(), loadOrders(), loadPendingDraft(), loadSessions()]);
}

async function cancelAssistantDraft() {
  if (!clientState.pendingDraft?.threadId) {
    throw new Error("当前没有可取消的 AI 下单草稿");
  }
  await clientFetchJson(`/api/orders/draft?threadId=${encodeURIComponent(clientState.pendingDraft.threadId)}`, {
    method: "DELETE",
  });
  clientState.pendingDraft = null;
  renderAssistantDraft();
  setStoreStatus("AI 下单草稿已取消");
  await loadPendingDraft();
}

async function saveProfile(event) {
  event.preventDefault();
  if (!clientState.user) {
    throw new Error("请先登录再保存资料");
  }
  const updated = await clientFetchJson("/api/auth/profile", {
    method: "PUT",
    body: JSON.stringify({
      displayName: clientById("profileDisplayName").value.trim(),
      phone: clientById("profilePhone").value.trim(),
      shippingAddress: clientById("profileShippingAddress").value.trim(),
      preferencesSummary: clientById("profilePreferences").value.trim(),
    }),
  });
  clientState.user = updated;
  renderProfile();
  clientById("shippingAddress").value = updated.shippingAddress || "";
  setStoreStatus("资料已保存，新的默认地址会用于后续结算");
}

function syncProfileAddressToCheckout() {
  const address = clientById("profileShippingAddress").value.trim();
  clientById("shippingAddress").value = address;
  setStoreStatus(address ? "已同步默认地址到本次结算" : "默认地址为空，已清空本次结算地址");
}

function readOrderNote(orderId) {
  return document.querySelector(`.order-note-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

async function performOrderAction(orderId, action) {
  let url = "";
  let options = { method: "PATCH" };
  if (action === "cancel") {
    url = `/api/orders/${orderId}/cancel`;
    options.body = JSON.stringify({ note: readOrderNote(orderId) });
  } else if (action === "confirm-receipt") {
    url = `/api/orders/${orderId}/confirm-receipt`;
  } else if (action === "refund") {
    url = `/api/orders/${orderId}/refund`;
    options.body = JSON.stringify({ note: readOrderNote(orderId) });
  } else {
    throw new Error("不支持的订单动作");
  }
  const updatedOrder = await clientFetchJson(url, options);
  setStoreStatus(`订单 ${updatedOrder.orderNo} 已更新为 ${statusLabel(updatedOrder.status)}`);
  await loadOrders();
}

async function bootstrapClient() {
  await loadMe();
  await loadCatalog();
  await Promise.all([loadCart(), loadOrders(), loadSessions()]);
  if (window.location.hash === "#assistant") {
    clientById("assistant")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }
}

async function runClientAction(action) {
  try {
    await action();
  } catch (error) {
    setStoreStatus(error.message || "操作失败");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  clientById("searchBtn").addEventListener("click", renderProducts);
  clientById("searchInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      renderProducts();
    }
  });
  clientById("loginBtn").addEventListener("click", () => runClientAction(login));
  clientById("registerBtn").addEventListener("click", () => runClientAction(registerUser));
  clientById("logoutBtn").addEventListener("click", () => runClientAction(logout));
  clientById("demoAccountBtn").addEventListener("click", () => {
    clientById("loginUsername").value = "demo";
    clientById("loginPassword").value = "demo123";
  });
  clientById("checkoutBtn").addEventListener("click", () => runClientAction(checkout));
  clientById("assistantSendBtn").addEventListener("click", () => runClientAction(() => sendAssistantMessage("")));
  clientById("assistantInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      runClientAction(() => sendAssistantMessage(""));
    }
  });
  clientById("newSessionBtn").addEventListener("click", () => runClientAction(createSession));
  clientById("refreshOrdersBtn").addEventListener("click", () => runClientAction(loadOrders));
  clientById("profileForm").addEventListener("submit", (event) => runClientAction(() => saveProfile(event)));
  clientById("syncAddressBtn").addEventListener("click", syncProfileAddressToCheckout);
  document.querySelectorAll(".prompt-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => sendAssistantMessage(button.dataset.prompt)));
  });
  runClientAction(bootstrapClient);
});
