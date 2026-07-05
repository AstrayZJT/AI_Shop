const adminState = {
  user: null,
  dashboard: null,
  products: [],
  orders: [],
  knowledgeDocs: [],
  users: [],
  editingProductId: null,
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
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(data?.error || `HTTP ${response.status}`);
  }
  return data;
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
      <div class="inline-meta">${label}</div>
      <div class="metric-value">${value}</div>
    </article>
  `;
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
  const dashboard = adminState.dashboard;
  host.innerHTML = [
    metricCard("用户数", dashboard.userCount),
    metricCard("商品数", dashboard.productCount),
    metricCard("订单数", dashboard.orderCount),
    metricCard("累计销售额", money(dashboard.totalRevenue)),
    metricCard("知识文档", dashboard.knowledgeDocumentCount),
    metricCard("低库存商品", dashboard.lowStockProductCount),
    metricCard("待发货订单", dashboard.pendingShipmentCount),
  ].join("");
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
  host.innerHTML = adminState.products.map((product) => `
    <div class="table-row">
      <div class="row-top">
        <div class="thumb-row">
          <img src="${product.imageUrl}" alt="${product.name}">
          <div>
            <strong>${product.name}</strong>
            <div class="inline-meta">${product.sku} · ${product.category || "未分类"}</div>
          </div>
        </div>
        <button type="button" class="ghost edit-product-btn" data-product-id="${product.id}">编辑</button>
      </div>
      <div class="row-bottom">
        <div class="inline-meta">${product.description || "暂无描述"}</div>
        <div class="tag">${money(product.price)} · 库存 ${product.stock}</div>
      </div>
    </div>
  `).join("");
  host.querySelectorAll(".edit-product-btn").forEach((button) => {
    button.addEventListener("click", () => fillProductForm(Number(button.dataset.productId)));
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
  if (!adminState.orders.length) {
    host.innerHTML = `<div class="empty-state">暂时没有订单数据。</div>`;
    return;
  }
  const statusOptions = ["DRAFT", "PENDING_CONFIRMATION", "CONFIRMED", "PROCESSING", "SHIPPED", "COMPLETED", "REFUND_REQUESTED", "CANCELLED"];
  host.innerHTML = adminState.orders.map((order) => `
    <div class="table-row">
      <div class="row-top">
        <div>
          <strong>${order.orderNo}</strong>
          <div class="inline-meta">${order.displayName} / ${order.username}</div>
        </div>
        <div class="auth-actions">
          <select class="order-status-select" data-order-id="${order.id}">
            ${statusOptions.map((status) => `<option value="${status}" ${status === order.status ? "selected" : ""}>${status}</option>`).join("")}
          </select>
          <button type="button" class="primary save-order-status-btn" data-order-id="${order.id}">更新状态</button>
        </div>
      </div>
      <div class="row-bottom">
        <div class="inline-meta">金额 ${money(order.totalAmount)} · 地址 ${order.shippingAddress}</div>
        <div class="tag">${order.status}</div>
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `<div>${item.productName} x ${item.quantity} · ${money(item.lineTotal)}</div>`).join("")}
      </div>
    </div>
  `).join("");
  host.querySelectorAll(".save-order-status-btn").forEach((button) => {
    button.addEventListener("click", () => updateOrderStatus(Number(button.dataset.orderId)));
  });
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
    host.innerHTML = `<div class="empty-state">还没有知识文档，先导入一条售后或物流规则。</div>`;
    return;
  }
  host.innerHTML = adminState.knowledgeDocs.map((doc) => `
    <div class="knowledge-item">
      <div class="row-top">
        <strong>${doc.title}</strong>
        <div class="tag">${doc.docType}</div>
      </div>
      <div class="inline-meta">${doc.contentPreview}</div>
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
  host.innerHTML = adminState.users.map((user) => `
    <div class="user-row">
      <div class="row-top">
        <strong>${user.displayName}</strong>
        <div class="tag">${user.role}</div>
      </div>
      <div class="inline-meta">${user.username}</div>
      <div class="inline-meta">${user.shippingAddress || "未填写地址"}</div>
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

function fillProductForm(productId) {
  const product = adminState.products.find((item) => item.id === productId);
  if (!product) {
    return;
  }
  adminState.editingProductId = product.id;
  adminById("productFormTitle").textContent = `编辑商品 · ${product.name}`;
  adminById("productSku").value = product.sku;
  adminById("productName").value = product.name;
  adminById("productCategory").value = product.category || "";
  adminById("productPrice").value = product.price;
  adminById("productStock").value = product.stock;
  adminById("productImageUrl").value = product.imageUrl || "";
  adminById("productDescription").value = product.description || "";
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

async function loadAdminData() {
  if (!adminState.user || adminState.user.role !== "ADMIN") {
    renderDashboard();
    renderProducts();
    renderOrders();
    renderKnowledgeDocs();
    renderUsers();
    return;
  }
  const [dashboard, products, orders, knowledgeDocs, users] = await Promise.all([
    adminFetchJson("/api/admin/dashboard"),
    adminFetchJson("/api/admin/products"),
    adminFetchJson("/api/admin/orders"),
    adminFetchJson("/api/admin/knowledge/documents"),
    adminFetchJson("/api/admin/users"),
  ]);
  adminState.dashboard = dashboard;
  adminState.products = products || [];
  adminState.orders = orders || [];
  adminState.knowledgeDocs = knowledgeDocs || [];
  adminState.users = users || [];
  renderDashboard();
  renderProducts();
  renderOrders();
  renderKnowledgeDocs();
  renderUsers();
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
  await bootstrapAdmin();
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
}

async function updateOrderStatus(orderId) {
  const select = document.querySelector(`.order-status-select[data-order-id="${orderId}"]`);
  await adminFetchJson(`/api/admin/orders/${orderId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status: select.value }),
  });
  await loadAdminData();
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
}

async function bootstrapAdmin() {
  try {
    await loadAdminMe();
  } catch (error) {
    adminState.user = null;
    adminState.dashboard = null;
    adminState.products = [];
    adminState.orders = [];
    adminState.knowledgeDocs = [];
    adminState.users = [];
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
  adminById("productForm").addEventListener("submit", (event) => runAdminAction(() => saveProduct(event)));
  adminById("resetProductBtn").addEventListener("click", resetProductForm);
  adminById("knowledgeForm").addEventListener("submit", (event) => runAdminAction(() => saveKnowledge(event)));
  runAdminAction(bootstrapAdmin);
});
