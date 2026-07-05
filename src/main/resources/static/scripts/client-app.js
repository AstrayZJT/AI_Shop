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
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(data?.error || `HTTP ${response.status}`);
  }
  return data;
}

function setStoreStatus(text) {
  const node = clientById("storeStatus");
  if (node) {
    node.textContent = text;
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
      ${name}
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
      <img src="${product.imageUrl}" alt="${product.name}">
      <div class="product-body">
        <div class="tag">${product.category || "未分类"}</div>
        <h3>${product.name}</h3>
        <div class="product-meta">${product.description || "暂无描述"}</div>
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
        <img src="${item.imageUrl}" alt="${item.productName}">
        <div>
          <strong>${item.productName}</strong>
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

function renderOrders() {
  const host = clientById("ordersList");
  if (!host) {
    return;
  }
  if (!clientState.user) {
    host.innerHTML = `<div class="empty-state">登录后可查看订单状态与下单记录。</div>`;
    return;
  }
  if (!clientState.orders.length) {
    host.innerHTML = `<div class="empty-state">你还没有订单，购物车结算或 AI 下单草稿确认后会出现在这里。</div>`;
    return;
  }
  host.innerHTML = clientState.orders.map((order) => `
    <article class="order-card">
      <div class="row-top">
        <strong>${order.orderNo}</strong>
        <div class="tag">${order.status}</div>
      </div>
      <div class="inline-meta">金额 ${currency(order.totalAmount)}</div>
      <div class="inline-meta">地址 ${order.shippingAddress}</div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${item.productName} x ${item.quantity} · ${currency(item.lineTotal)}</div>
        `).join("")}
      </div>
    </article>
  `).join("");
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
      <strong>${session.title}</strong>
      <div class="inline-meta">${session.lastIntent || "chat"}</div>
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
    host.innerHTML = `<div class="empty-state">问 AI 客服商品、订单、售后或知识库问题，它会结合业务数据回答。</div>`;
    return;
  }
  host.innerHTML = messages.map((message) => `
    <div class="message-bubble ${message.role}">
      ${message.content}
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
    host.textContent = "这里会展示 AI 命中的知识片段和订单草稿摘要。";
    return;
  }
  const sourceText = payload.sources?.length ? payload.sources.join(" | ") : "无知识片段";
  const draftText = payload.pendingOrderDraft ? `已生成下单草稿：${payload.pendingOrderDraft}` : "未生成下单草稿";
  host.textContent = `意图：${payload.intent} | ${draftText} | 来源：${sourceText}`;
}

async function loadMe() {
  clientState.user = await clientFetchJson("/api/auth/me");
  if (clientState.user) {
    setStoreStatus(`已登录：${clientState.user.displayName || clientState.user.username} · ${clientState.user.role}`);
    clientById("shippingAddress").value = clientState.user.shippingAddress || "";
  } else {
    setStoreStatus("未登录，可先浏览商品");
  }
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
    renderSessions();
    renderAssistantMessages([]);
    renderAssistantMeta(null);
    return;
  }
  clientState.sessions = await clientFetchJson("/api/assistant/sessions");
  if (!clientState.sessionId && clientState.sessions.length) {
    clientState.sessionId = clientState.sessions[0].id;
    clientState.threadId = `assistant-${clientState.sessionId}`;
  }
  renderSessions();
  if (clientState.sessionId) {
    await loadMessages(clientState.sessionId);
  } else {
    renderAssistantMessages([]);
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
  await loadMessages(session.id);
}

async function createSession() {
  const session = await clientFetchJson("/api/assistant/sessions", { method: "POST" });
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  await loadSessions();
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

async function bootstrapClient() {
  await loadMe();
  await loadCatalog();
  await Promise.all([loadCart(), loadOrders(), loadSessions()]);
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
  document.querySelectorAll(".prompt-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => sendAssistantMessage(button.dataset.prompt)));
  });
  runClientAction(bootstrapClient);
});
