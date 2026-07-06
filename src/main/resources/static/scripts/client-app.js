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
  assistantContext: null,
  productSort: "recommended",
  recommendationScene: "AUTO",
  selectedProductId: null,
  selectedProduct: null,
};

const ORDER_STATUS_LABELS = {
  DRAFT: "草稿",
  PENDING_CONFIRMATION: "待确认",
  PENDING_PAYMENT: "待支付",
  CONFIRMED: "待发货",
  PROCESSING: "处理中",
  SHIPPED: "已发货",
  COMPLETED: "已完成",
  REFUND_REQUESTED: "退款处理中",
  REFUNDED: "已退款",
  CANCELLED: "已取消",
};

const AFTER_SALES_STATUS_LABELS = {
  REQUESTED: "等待平台审核",
  AWAITING_CUSTOMER_RETURN: "等待用户回寄",
  RETURN_SHIPPED: "已提交回寄物流",
  REFUNDED: "售后已完成",
  REJECTED: "售后已驳回",
};

const ASSISTANT_SERVICE_STATUS_LABELS = {
  ACTIVE: "AI 在线服务中",
  ESCALATED: "人工跟进中",
  RESOLVED: "人工已回复",
};

const RECOMMENDATION_SCENES = [
  { key: "AUTO", label: "为你推荐", keywords: [] },
  { key: "COMMUTE", label: "通勤轻便", keywords: ["通勤", "便携", "轻便", "续航", "降噪", "耳机"] },
  { key: "FOCUS", label: "专注办公", keywords: ["办公", "会议", "降噪", "轻办公", "平板", "键盘"] },
  { key: "MOBILE", label: "手机拍照", keywords: ["手机", "拍照", "影像", "旗舰", "续航"] },
  { key: "ENTERTAINMENT", label: "影音娱乐", keywords: ["娱乐", "游戏", "音箱", "耳机", "沉浸", "屏幕"] },
];
const PAYMENT_METHOD_OPTIONS = ["模拟支付", "支付宝", "微信支付", "银行卡"];
const CLIENT_AUTH_SCOPE = "customer";

function clientById(id) {
  return document.getElementById(id);
}

function clientAuthUrl(url) {
  return `${url}${url.includes("?") ? "&" : "?"}scope=${CLIENT_AUTH_SCOPE}`;
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

function afterSalesStatusLabel(status) {
  return AFTER_SALES_STATUS_LABELS[status] || "售后处理中";
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

function assistantServiceStatusLabel(status) {
  return ASSISTANT_SERVICE_STATUS_LABELS[status] || ASSISTANT_SERVICE_STATUS_LABELS.ACTIVE;
}

function currentAssistantSession() {
  return clientState.sessions.find((session) => session.id === clientState.sessionId) || null;
}

function assistantRoleLabel(role) {
  if (role === "user") {
    return "我";
  }
  if (role === "support") {
    return "人工客服";
  }
  return "AI 客服";
}

function formatMessageTime(value) {
  if (!value) {
    return "";
  }
  return new Date(value).toLocaleString("zh-CN");
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

function normalizeText(value) {
  return String(value ?? "").trim().toLowerCase();
}

function sceneDefinition(sceneKey = clientState.recommendationScene) {
  return RECOMMENDATION_SCENES.find((scene) => scene.key === sceneKey) || RECOMMENDATION_SCENES[0];
}

function effectiveScene() {
  if (clientState.recommendationScene !== "AUTO") {
    return sceneDefinition(clientState.recommendationScene);
  }
  const userText = normalizeText(clientState.user?.preferencesSummary);
  const searchText = normalizeText(clientById("searchInput")?.value);
  const categoryText = normalizeText(clientState.category === "全部" ? "" : clientState.category);
  const combined = `${userText} ${searchText} ${categoryText}`;
  if (combined.includes("拍照") || combined.includes("手机") || combined.includes("影像")) {
    return sceneDefinition("MOBILE");
  }
  if (combined.includes("游戏") || combined.includes("音箱") || combined.includes("娱乐")) {
    return sceneDefinition("ENTERTAINMENT");
  }
  if (combined.includes("办公") || combined.includes("会议") || combined.includes("轻办公")) {
    return sceneDefinition("FOCUS");
  }
  if (combined.includes("通勤") || combined.includes("便携") || combined.includes("降噪")) {
    return sceneDefinition("COMMUTE");
  }
  return sceneDefinition("COMMUTE");
}

function recommendationTokens() {
  const keyword = normalizeText(clientById("searchInput")?.value);
  const scene = effectiveScene();
  const preferences = normalizeText(clientState.user?.preferencesSummary);
  return Array.from(new Set(
    [keyword, preferences, normalizeText(clientState.category === "全部" ? "" : clientState.category), ...scene.keywords]
      .join(" ")
      .split(/\s+/)
      .filter((token) => token && token.length >= 2),
  ));
}

function productSearchPool(product) {
  return normalizeText([product.name, product.description, product.category, product.sku].join(" "));
}

function productRecommendationScore(product) {
  const pool = productSearchPool(product);
  const tokens = recommendationTokens();
  const scene = effectiveScene();
  let score = 0;
  if (clientState.category !== "全部" && product.category === clientState.category) {
    score += 40;
  }
  if (clientState.selectedProductId === product.id) {
    score += 18;
  }
  if (scene.keywords.length && scene.keywords.some((keyword) => pool.includes(normalizeText(keyword)))) {
    score += 55;
  }
  tokens.forEach((token) => {
    if (pool.includes(token)) {
      score += token.length >= 4 ? 32 : 16;
    }
  });
  if (clientState.user?.preferencesSummary && product.category && normalizeText(clientState.user.preferencesSummary).includes(normalizeText(product.category))) {
    score += 22;
  }
  score += Math.min(Number(product.stock || 0), 20);
  score += Math.max(0, 18 - Math.floor(Number(product.price || 0) / 500));
  return score;
}

function sortProducts(products) {
  const sorted = [...products];
  switch (clientState.productSort) {
    case "price-asc":
      return sorted.sort((left, right) => Number(left.price || 0) - Number(right.price || 0));
    case "price-desc":
      return sorted.sort((left, right) => Number(right.price || 0) - Number(left.price || 0));
    case "stock-desc":
      return sorted.sort((left, right) => Number(right.stock || 0) - Number(left.stock || 0));
    case "name":
      return sorted.sort((left, right) => String(left.name || "").localeCompare(String(right.name || ""), "zh-CN"));
    case "recommended":
    default:
      return sorted.sort((left, right) => productRecommendationScore(right) - productRecommendationScore(left));
  }
}

function recommendationReasonTags(product) {
  const tags = [];
  const scene = effectiveScene();
  const pool = productSearchPool(product);
  if (scene.keywords.some((keyword) => pool.includes(normalizeText(keyword)))) {
    tags.push(scene.label);
  }
  if (clientState.user?.preferencesSummary) {
    const preferences = normalizeText(clientState.user.preferencesSummary);
    if (preferences.includes("性价比") && Number(product.price || 0) <= 2000) {
      tags.push("性价比友好");
    }
    if (preferences.includes("降噪") && pool.includes("降噪")) {
      tags.push("降噪匹配");
    }
    if (preferences.includes("轻办公") && (pool.includes("办公") || pool.includes("平板"))) {
      tags.push("轻办公匹配");
    }
  }
  if (Number(product.stock || 0) <= 5) {
    tags.push("库存紧张");
  } else if (Number(product.stock || 0) >= 15) {
    tags.push("现货充足");
  }
  if (Number(product.price || 0) >= 3000) {
    tags.push("高阶配置");
  } else if (Number(product.price || 0) <= 1500) {
    tags.push("入门友好");
  }
  return Array.from(new Set(tags)).slice(0, 3);
}

function recommendationNarrative(product) {
  const tags = recommendationReasonTags(product);
  if (tags.length) {
    return `这件商品更贴近当前的选品场景，重点理由：${tags.join("、")}。`;
  }
  return "这件商品在当前目录里综合匹配度更高，适合作为优先了解对象。";
}

function recommendedProducts(limit = 4) {
  return sortProducts(filteredProducts()).slice(0, limit);
}

function selectedCatalogProduct() {
  return clientState.selectedProduct || clientState.products.find((product) => product.id === clientState.selectedProductId) || null;
}

function ensureSelectedProduct(products = filteredProducts()) {
  const selected = selectedCatalogProduct();
  if (selected && products.some((product) => product.id === selected.id)) {
    return selected;
  }
  const fallback = recommendedProducts(1)[0] || products[0] || null;
  clientState.selectedProductId = fallback?.id || null;
  clientState.selectedProduct = fallback;
  return fallback;
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
      renderCatalogExperience();
    });
  });
}

function renderRecommendationScenes() {
  const host = clientById("recommendationScenes");
  if (!host) {
    return;
  }
  host.innerHTML = RECOMMENDATION_SCENES.map((scene) => `
    <button type="button" class="${clientState.recommendationScene === scene.key ? "active" : ""}" data-scene-key="${scene.key}">
      ${escapeHtml(scene.label)}
    </button>
  `).join("");
  host.querySelectorAll("[data-scene-key]").forEach((button) => {
    button.addEventListener("click", () => {
      clientState.recommendationScene = button.dataset.sceneKey || "AUTO";
      clientState.selectedProduct = null;
      renderCatalogExperience();
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

function renderRecommendationPanel() {
  const host = clientById("recommendationPanel");
  if (!host) {
    return;
  }
  const recommended = recommendedProducts(4);
  const selected = ensureSelectedProduct(filteredProducts());
  const scene = effectiveScene();
  if (!recommended.length || !selected) {
    host.innerHTML = `<div class="empty-state">当前条件下还没有可推荐的商品，试试切换分类、场景或关键词。</div>`;
    return;
  }
  host.innerHTML = `
    <section class="recommendation-hero">
      <img src="${selected.imageUrl || "/favicon.svg"}" alt="${escapeHtml(selected.name)}">
      <div class="recommendation-copy">
        <span class="eyebrow">Smart Picks</span>
        <h3>${escapeHtml(selected.name)}</h3>
        <div class="product-meta">${escapeHtml(selected.description || "暂无更多描述")}</div>
        <div class="recommendation-stats">
          <div class="tag">${escapeHtml(scene.label)}</div>
          <div class="tag">${escapeHtml(selected.category || "未分类")}</div>
          <div class="tag">${currency(selected.price)}</div>
          <div class="tag">${escapeHtml(Number(selected.stock || 0) > 0 ? `库存 ${selected.stock}` : "暂时缺货")}</div>
        </div>
        <div class="panel-hint">${escapeHtml(recommendationNarrative(selected))}</div>
        <div class="recommendation-tags">
          ${recommendationReasonTags(selected).map((tag) => `<span class="tag warning">${escapeHtml(tag)}</span>`).join("")}
        </div>
        <div class="order-actions">
          <button type="button" class="primary product-add-cart-btn" data-product-id="${selected.id}">加入购物车</button>
          <button type="button" class="ghost product-detail-btn" data-product-id="${selected.id}">查看详情</button>
          <button type="button" class="ghost product-ai-btn" data-product-id="${selected.id}" data-mode="guide">问 AI 适不适合我</button>
        </div>
      </div>
    </section>
    <section class="recommendation-detail">
      <div class="row-top">
        <div>
          <h3>选品决策区</h3>
          <p class="panel-hint">把推荐理由、购买提示和 AI 快捷提问集中到一起，减少在列表里来回比较。</p>
        </div>
      </div>
      <div class="recommendation-detail-grid">
        <div class="recommendation-detail-item">
          <strong>为什么推荐</strong>
          <div class="panel-hint">${escapeHtml(recommendationNarrative(selected))}</div>
        </div>
        <div class="recommendation-detail-item">
          <strong>当前人群</strong>
          <div class="panel-hint">${escapeHtml(productAudienceText(selected))}</div>
        </div>
        <div class="recommendation-detail-item">
          <strong>购买提示</strong>
          <div class="panel-hint">${escapeHtml(productPurchaseHint(selected))}</div>
        </div>
      </div>
      <div class="order-actions">
        <button type="button" class="ghost product-ai-btn" data-product-id="${selected.id}" data-mode="compare">让 AI 比较同类商品</button>
        <button type="button" class="ghost product-ai-btn" data-product-id="${selected.id}" data-mode="draft">生成下单草稿</button>
      </div>
      <div class="recommendation-mini-grid">
        ${recommended.map((product) => `
          <article class="recommendation-mini">
            <img src="${product.imageUrl || "/favicon.svg"}" alt="${escapeHtml(product.name)}">
            <div class="tag">${escapeHtml(product.category || "未分类")}</div>
            <h4>${escapeHtml(product.name)}</h4>
            <div class="inline-meta">${currency(product.price)} · 库存 ${escapeHtml(product.stock)}</div>
            <div class="panel-hint">${escapeHtml(recommendationNarrative(product))}</div>
            <div class="product-actions-compact">
              <button type="button" class="ghost product-select-btn" data-product-id="${product.id}">查看</button>
              <button type="button" class="primary product-add-cart-btn" data-product-id="${product.id}">加入购物车</button>
            </div>
          </article>
        `).join("")}
      </div>
    </section>
  `;
  bindCatalogActionButtons(host);
}

function renderProducts() {
  const products = sortProducts(filteredProducts());
  const host = clientById("productGrid");
  const meta = clientById("productMeta");
  if (!host || !meta) {
    return;
  }
  const scene = effectiveScene();
  const sortLabel = clientById("productSortSelect")?.selectedOptions?.[0]?.textContent?.trim() || "智能推荐";
  meta.textContent = `共 ${products.length} 件商品 · 当前分类 ${clientState.category} · 场景 ${scene.label} · 排序 ${sortLabel}`;
  if (products.length === 0) {
    host.innerHTML = `<div class="empty-state">没有找到符合条件的商品，试试切换分类或更换关键词。</div>`;
    return;
  }
  ensureSelectedProduct(products);
  host.innerHTML = products.map((product) => `
    <article class="product-card">
      <img src="${product.imageUrl || "/favicon.svg"}" alt="${escapeHtml(product.name)}">
      <div class="product-body">
        <div class="tag">${escapeHtml(product.category || "未分类")}</div>
        <h3>${escapeHtml(product.name)}</h3>
        <div class="product-meta">${escapeHtml(product.description || "暂无描述")}</div>
        <div class="recommendation-tags">
          ${recommendationReasonTags(product).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("")}
        </div>
        <div class="product-price">
          <span>${currency(product.price)}</span>
          <span class="inline-meta">库存 ${product.stock}</span>
        </div>
        <div class="product-actions-compact">
          <button type="button" class="primary product-add-cart-btn" data-product-id="${product.id}">
            加入购物车
          </button>
          <button type="button" class="ghost product-detail-btn" data-product-id="${product.id}">
            查看详情
          </button>
        </div>
      </div>
    </article>
  `).join("");
  bindCatalogActionButtons(host);
}

function productAudienceText(product) {
  const pool = productSearchPool(product);
  if (pool.includes("降噪") || pool.includes("通勤")) {
    return "更适合需要频繁通勤、会议或希望隔绝环境噪音的人。";
  }
  if (pool.includes("拍照") || pool.includes("影像")) {
    return "更适合在意手机拍照、视频记录和移动内容创作的人。";
  }
  if (pool.includes("办公") || pool.includes("平板")) {
    return "更适合轻办公、课堂记录和移动处理文档的场景。";
  }
  if (pool.includes("音箱") || pool.includes("娱乐")) {
    return "更适合在宿舍、客厅或小型桌面娱乐环境使用。";
  }
  return "适合想先从当前场景里快速锁定候选商品的用户。";
}

function productPurchaseHint(product) {
  if (Number(product.stock || 0) <= 3) {
    return "库存已经比较紧，适合先加入购物车或让 AI 帮你生成下单草稿。";
  }
  if (Number(product.price || 0) >= 3000) {
    return "客单价相对更高，建议先让 AI 对比同类产品后再决定。";
  }
  if (Number(product.price || 0) <= 1500) {
    return "预算门槛更友好，适合先作为入门候选。";
  }
  return "可以先查看详情和适用场景，再决定直接下单还是交给 AI 帮你比较。";
}

function buildProductAssistantPrompt(product, mode = "guide") {
  if (!product) {
    return "帮我推荐一个当前更适合我的商品。";
  }
  if (mode === "compare") {
    return `请把商品 ${product.name} 和当前目录里同类商品做一个简短对比，重点说适合人群、价格和购买建议。`;
  }
  if (mode === "draft") {
    return `帮我生成一个商品 ${product.name} 的下单草稿，数量 1。`;
  }
  return `请详细介绍商品 ${product.name}，并结合它的价格、库存、适合人群告诉我是否值得买。`;
}

async function openProductDetail(productId) {
  const cached = clientState.products.find((product) => product.id === productId) || null;
  try {
    clientState.selectedProduct = await clientFetchJson(`/api/products/${productId}`);
  } catch (error) {
    clientState.selectedProduct = cached;
  }
  clientState.selectedProductId = productId;
  renderCatalogExperience();
}

async function askAiAboutProduct(productId, mode) {
  const product = clientState.products.find((item) => item.id === productId) || clientState.selectedProduct;
  const prompt = buildProductAssistantPrompt(product, mode);
  clientById("assistantInput").value = prompt;
  clientById("assistant")?.scrollIntoView({ behavior: "smooth", block: "start" });
  if (!clientState.user) {
    setStoreStatus("已帮你写好商品问题，登录后可直接发送给 AI 客服");
    return;
  }
  await sendAssistantMessage(prompt);
}

function bindCatalogActionButtons(host) {
  host.querySelectorAll(".product-add-cart-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => addToCart(Number(button.dataset.productId))));
  });
  host.querySelectorAll(".product-detail-btn, .product-select-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => openProductDetail(Number(button.dataset.productId))));
  });
  host.querySelectorAll(".product-ai-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => askAiAboutProduct(Number(button.dataset.productId), button.dataset.mode)));
  });
}

function renderCatalogExperience() {
  renderCategories();
  renderRecommendationScenes();
  renderRecommendationPanel();
  renderProducts();
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
        ${renderOrderPaymentMeta(order)}
        ${renderOrderShippingMeta(order)}
        ${order.riskNote ? `<div class="inline-meta">备注 ${escapeHtml(order.riskNote)}</div>` : ""}
      </div>
      <div class="order-items">
        ${(order.items || []).map((item) => `
          <div>${escapeHtml(item.productName)} x ${item.quantity} · ${currency(item.lineTotal)}</div>
        `).join("")}
      </div>
      ${renderOrderTimeline(order)}
      ${renderOrderActionArea(order)}
    </article>
  `).join("");
  host.querySelectorAll(".order-action-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => performOrderAction(Number(button.dataset.orderId), button.dataset.action)));
  });
  host.querySelectorAll(".order-ai-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => sendAssistantMessage(`请结合订单 ${button.dataset.orderNo} 的当前状态，告诉我下一步可以做什么`)));
  });
  host.querySelectorAll(".after-sales-return-btn").forEach((button) => {
    button.addEventListener("click", () => runClientAction(() => submitReturnShipment(Number(button.dataset.orderId))));
  });
}

function renderOrderActionArea(order) {
  const buttons = [];
  if (canPayOrder(order.status)) {
    buttons.push(`<button type="button" class="primary order-action-btn" data-order-id="${order.id}" data-action="pay">模拟支付</button>`);
  }
  if (canCancelOrder(order.status)) {
    buttons.push(`<button type="button" class="ghost order-action-btn" data-order-id="${order.id}" data-action="cancel">取消订单</button>`);
  }
  if (canUpdateShippingAddress(order.status)) {
    buttons.push(`<button type="button" class="ghost order-action-btn" data-order-id="${order.id}" data-action="update-address">修改地址</button>`);
  }
  if (canConfirmReceipt(order.status)) {
    buttons.push(`<button type="button" class="primary order-action-btn" data-order-id="${order.id}" data-action="confirm-receipt">确认收货</button>`);
  }
  if (canRequestRefund(order.status)) {
    buttons.push(`<button type="button" class="ghost order-action-btn" data-order-id="${order.id}" data-action="refund">申请退款</button>`);
  }
  buttons.push(`<button type="button" class="ghost order-ai-btn" data-order-no="${escapeHtml(order.orderNo)}">问 AI</button>`);
  const needsNote = canPayOrder(order.status) || canCancelOrder(order.status) || canRequestRefund(order.status);
  return `
    <div class="order-action-block">
      ${canPayOrder(order.status)
        ? `<select class="order-payment-method-select" data-order-id="${order.id}">
            ${PAYMENT_METHOD_OPTIONS.map((method) => `<option value="${escapeHtml(method)}" ${method === (order.paymentMethod || "模拟支付") ? "selected" : ""}>${escapeHtml(method)}</option>`).join("")}
          </select>`
        : ""}
      ${canUpdateShippingAddress(order.status)
        ? `<input class="order-address-input" data-order-id="${order.id}" placeholder="发货前可修改当前订单收货地址" value="${escapeHtml(order.shippingAddress || "")}">`
        : ""}
      ${needsNote ? `<textarea class="order-note-input" data-order-id="${order.id}" placeholder="${escapeHtml(orderActionPlaceholder(order.status))}"></textarea>` : ""}
      <div class="order-actions">
        ${buttons.join("")}
      </div>
      <div class="panel-hint">${escapeHtml(orderActionHint(order))}</div>
    </div>
    ${renderAfterSalesPanel(order)}
  `;
}

function orderActionPlaceholder(status) {
  if (canPayOrder(status) && canCancelOrder(status)) {
    return "备注，例如支付说明、取消原因或地址调整提醒";
  }
  if (canPayOrder(status)) {
    return "支付备注，例如模拟付款说明";
  }
  if (canCancelOrder(status)) {
    return "取消原因，例如重复下单、地址填写有误";
  }
  if (canRequestRefund(status)) {
    return "退款原因，例如商品问题、与预期不符";
  }
  return "可填写售后说明";
}

function orderActionHint(order) {
  const status = typeof order === "string" ? order : order.status;
  if (canPayOrder(status)) {
    return "当前订单还没完成支付。你可以先选择支付方式完成模拟支付，也可以趁发货前继续改地址或取消订单。";
  }
  if (canCancelOrder(status)) {
    return "待发货和处理中订单可以直接在线取消，也支持先改当前订单地址。";
  }
  if (canConfirmReceipt(status)) {
    return "确认收货后，订单会变成已完成。";
  }
  if (canRequestRefund(status)) {
    return "已发货或已完成订单支持发起退款申请。";
  }
  if (status === "REFUND_REQUESTED") {
    if (order?.afterSales?.status === "AWAITING_CUSTOMER_RETURN") {
      return "平台已经给出退货指引，寄回商品后记得提交回寄单号。";
    }
    if (order?.afterSales?.status === "RETURN_SHIPPED") {
      return "你已经提交回寄物流，等待平台确认收货退款。";
    }
    return "退款申请已经提交，等待平台处理。";
  }
  if (status === "REFUNDED") {
    return "退款已经处理完成，库存和售后记录已在平台侧更新。";
  }
  return "当前状态暂无可执行的在线售后动作。";
}

function canCancelOrder(status) {
  return status === "PENDING_PAYMENT" || status === "CONFIRMED" || status === "PROCESSING";
}

function canPayOrder(status) {
  return status === "PENDING_PAYMENT";
}

function canConfirmReceipt(status) {
  return status === "SHIPPED";
}

function canRequestRefund(status) {
  return status === "SHIPPED" || status === "COMPLETED";
}

function canUpdateShippingAddress(status) {
  return status === "PENDING_PAYMENT" || status === "CONFIRMED" || status === "PROCESSING";
}

function renderOrderPaymentMeta(order) {
  const lines = [];
  if (order.paymentMethod) {
    lines.push(`<div class="inline-meta">支付方式 ${escapeHtml(order.paymentMethod)}</div>`);
  }
  if (order.paymentReference) {
    lines.push(`<div class="inline-meta">支付流水 ${escapeHtml(order.paymentReference)}</div>`);
  }
  if (order.paidAt) {
    lines.push(`<div class="inline-meta">支付时间 ${escapeHtml(new Date(order.paidAt).toLocaleString("zh-CN"))}</div>`);
  } else if (order.status === "PENDING_PAYMENT") {
    lines.push(`<div class="inline-meta">支付状态 待完成模拟支付</div>`);
  }
  return lines.join("");
}

function renderOrderShippingMeta(order) {
  const lines = [];
  if (order.shippingCarrier) {
    lines.push(`<div class="inline-meta">物流公司 ${escapeHtml(order.shippingCarrier)}</div>`);
  }
  if (order.trackingNo) {
    lines.push(`<div class="inline-meta">运单号 ${escapeHtml(order.trackingNo)}</div>`);
  }
  if (order.shippedAt) {
    lines.push(`<div class="inline-meta">发货时间 ${escapeHtml(new Date(order.shippedAt).toLocaleString("zh-CN"))}</div>`);
  }
  const latest = latestLogisticsEvent(order);
  if (latest?.detail) {
    lines.push(`<div class="inline-meta">最新物流 ${escapeHtml(latest.detail)}</div>`);
  }
  return lines.join("");
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

function renderAfterSalesPanel(order) {
  const afterSales = order.afterSales;
  if (!afterSales && !["REFUND_REQUESTED", "REFUNDED"].includes(order.status)) {
    return "";
  }
  const status = afterSalesStatusLabel(afterSales?.status);
  let hint = "当前售后工单会随着平台审核、退货回寄和退款完成持续更新。";
  if (afterSales?.status === "REQUESTED") {
    hint = "退款申请已经提交，正在等待平台审核。";
  } else if (afterSales?.status === "AWAITING_CUSTOMER_RETURN") {
    hint = "平台已经同意退货退款，请按回寄地址寄回商品并提交回寄单号。";
  } else if (afterSales?.status === "RETURN_SHIPPED") {
    hint = "你已经提交回寄物流，平台确认收到退货后会继续处理退款。";
  } else if (afterSales?.status === "REFUNDED" || order.status === "REFUNDED") {
    hint = "退款已经处理完成，可以在时间线里回看整个售后过程。";
  } else if (afterSales?.status === "REJECTED") {
    hint = "平台已经驳回这笔售后申请，可以查看管理员说明。";
  }
  return `
    <div class="after-sales-panel">
      <div class="row-top">
        <strong>售后工单</strong>
        <div class="tag warning">${escapeHtml(status)}</div>
      </div>
      <div class="after-sales-grid">
        ${afterSales?.customerReason ? `<div class="inline-meta">退款原因 ${escapeHtml(afterSales.customerReason)}</div>` : ""}
        ${afterSales?.adminReply ? `<div class="inline-meta">管理员说明 ${escapeHtml(afterSales.adminReply)}</div>` : ""}
        ${afterSales?.returnAddress ? `<div class="inline-meta">回寄地址 ${escapeHtml(afterSales.returnAddress)}</div>` : ""}
        ${afterSales?.returnCarrier ? `<div class="inline-meta">回寄物流 ${escapeHtml(afterSales.returnCarrier)}</div>` : ""}
        ${afterSales?.returnTrackingNo ? `<div class="inline-meta">回寄单号 ${escapeHtml(afterSales.returnTrackingNo)}</div>` : ""}
        ${afterSales?.returnNote ? `<div class="inline-meta">回寄备注 ${escapeHtml(afterSales.returnNote)}</div>` : ""}
        ${afterSales?.requestedAt ? `<div class="inline-meta">申请时间 ${escapeHtml(formatMessageTime(afterSales.requestedAt))}</div>` : ""}
        ${afterSales?.adminRespondedAt ? `<div class="inline-meta">审核时间 ${escapeHtml(formatMessageTime(afterSales.adminRespondedAt))}</div>` : ""}
        ${afterSales?.customerShippedAt ? `<div class="inline-meta">回寄时间 ${escapeHtml(formatMessageTime(afterSales.customerShippedAt))}</div>` : ""}
        ${afterSales?.resolvedAt ? `<div class="inline-meta">完成时间 ${escapeHtml(formatMessageTime(afterSales.resolvedAt))}</div>` : ""}
      </div>
      ${renderAfterSalesReturnForm(order, afterSales)}
      <div class="panel-hint">${escapeHtml(hint)}</div>
    </div>
  `;
}

function renderAfterSalesReturnForm(order, afterSales) {
  if (afterSales?.status !== "AWAITING_CUSTOMER_RETURN") {
    return "";
  }
  return `
    <div class="after-sales-return-form">
      <input class="return-carrier-input" data-order-id="${order.id}" placeholder="回寄物流公司，例如顺丰">
      <input class="return-tracking-input" data-order-id="${order.id}" placeholder="回寄运单号">
      <textarea class="return-note-input" data-order-id="${order.id}" placeholder="回寄说明，例如包装完好、已附带发票"></textarea>
      <div class="order-actions">
        <button type="button" class="secondary after-sales-return-btn" data-order-id="${order.id}">提交回寄单号</button>
      </div>
    </div>
  `;
}

function renderOrderTimeline(order) {
  const timeline = order.timeline || [];
  if (!timeline.length) {
    return `<div class="timeline-empty">当前订单还没有可展示的履约轨迹。</div>`;
  }
  return `
    <div class="timeline-list">
      ${timeline.map((event) => `
        <div class="timeline-item">
          <div class="timeline-marker"></div>
          <div class="timeline-content">
            <div class="row-top">
              <strong class="timeline-title">${escapeHtml(event.title || "订单进度更新")}</strong>
              <div class="inline-meta">${escapeHtml(formatMessageTime(event.occurredAt))}</div>
            </div>
            <div class="inline-meta">处理方 ${escapeHtml(event.actorLabel || "系统")}</div>
            ${event.detail ? `<div class="timeline-detail">${escapeHtml(event.detail)}</div>` : ""}
          </div>
        </div>
      `).join("")}
    </div>
  `;
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
      <div class="inline-meta">${escapeHtml(assistantServiceStatusLabel(session.serviceStatus))}</div>
      <div class="inline-meta">${escapeHtml(session.lastIntent || "chat")}${session.supportAgentDisplayName ? ` · ${escapeHtml(session.supportAgentDisplayName)}` : ""}</div>
      ${Number(session.unreadSupportCount || 0) > 0 ? `<div class="inline-meta">人工新消息 ${session.unreadSupportCount} 条</div>` : ""}
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
      <div class="message-role">${escapeHtml(assistantRoleLabel(message.role))}</div>
      <div>${escapeHtml(message.content).replaceAll("\n", "<br>")}</div>
      <div class="inline-meta">${escapeHtml(formatMessageTime(message.createdAt))}</div>
    </div>
  `).join("");
  host.scrollTop = host.scrollHeight;
}

function renderAssistantMeta(payload = clientState.assistantContext) {
  clientState.assistantContext = payload;
  const host = clientById("assistantMeta");
  if (!host) {
    return;
  }
  const session = currentAssistantSession();
  if (!payload && !session) {
    host.textContent = "这里会展示会话状态、AI 命中的知识片段、下单草稿摘要，以及是否已经转人工。";
    return;
  }
  const status = assistantServiceStatusLabel(session?.serviceStatus);
  const intent = payload?.intent || session?.lastIntent || "chat";
  const sourceText = payload?.sources?.length
    ? payload.sources.join(" | ")
    : "本会话支持商品咨询、订单查询、知识库检索、AI 代办和人工接管。";
  const draftText = payload?.pendingOrderDraft
    ? "本轮已生成下单草稿"
    : clientState.pendingDraft?.status === "PENDING_CONFIRMATION"
      ? "当前有待确认的 AI 下单草稿"
      : "当前没有待确认下单草稿";
  let statusHint = "你可以继续让 AI 查询商品、订单、物流和售后规则。";
  if (session?.serviceStatus === "ESCALATED") {
    statusHint = session?.supportAgentDisplayName
      ? `当前会话已经由 ${session.supportAgentDisplayName} 跟进，管理员回复后会直接回流到这里。`
      : "当前会话已经进入人工跟进队列，管理员在后台回复后会直接回流到这里。";
  } else if (session?.serviceStatus === "RESOLVED") {
    statusHint = Number(session?.unreadSupportCount || 0) > 0
      ? `人工客服已经回复，当前有 ${session.unreadSupportCount} 条新消息。`
      : "人工客服已经回复，你可以继续补充问题，系统会重新回到正常 AI 会话。";
  }
  host.innerHTML = `
    <div><strong>会话状态：</strong>${escapeHtml(status)}</div>
    <div><strong>当前意图：</strong>${escapeHtml(intent)}</div>
    <div><strong>人工跟进：</strong>${escapeHtml(session?.supportAgentDisplayName || "暂未分配人工客服")}</div>
    <div><strong>草稿状态：</strong>${escapeHtml(draftText)}</div>
    <div><strong>提示：</strong>${escapeHtml(statusHint)}</div>
    <div><strong>知识命中：</strong>${escapeHtml(sourceText)}</div>
  `;
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
      <div class="panel-hint">${escapeHtml(draft.note || "确认后会创建正式订单，并先进入待支付流程。")}</div>
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
  clientState.user = await clientFetchJson(clientAuthUrl("/api/auth/me"));
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
  const [categoriesResult, productsResult] = await Promise.allSettled([
    clientFetchJson("/api/categories"),
    clientFetchJson("/api/products"),
  ]);
  if (productsResult.status !== "fulfilled") {
    throw productsResult.reason;
  }
  clientState.categories = categoriesResult.status === "fulfilled" ? (categoriesResult.value || []) : [];
  clientState.products = productsResult.value || [];
  renderCatalogExperience();
  if (categoriesResult.status !== "fulfilled") {
    setStoreStatus("商品已加载，分类接口暂时不可用，已自动降级为无分类浏览");
  }
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
    clientState.assistantContext = null;
    renderSessions();
    renderAssistantMessages([]);
    renderAssistantMeta(null);
    renderAssistantDraft();
    return;
  }
  clientState.sessions = await clientFetchJson("/api/assistant/sessions");
  const sessionStillExists = clientState.sessions.some((session) => session.id === clientState.sessionId);
  if (!sessionStillExists) {
    clientState.sessionId = null;
    clientState.threadId = null;
  }
  if (!clientState.sessionId && clientState.sessions.length) {
    clientState.sessionId = clientState.sessions[0].id;
    clientState.threadId = `assistant-${clientState.sessionId}`;
  }
  renderSessions();
  renderAssistantMeta(clientState.assistantContext);
  if (clientState.sessionId) {
    await Promise.all([loadMessages(clientState.sessionId), loadPendingDraft()]);
  } else {
    clientState.assistantContext = null;
    renderAssistantMessages([]);
    renderAssistantMeta(null);
    clientState.pendingDraft = null;
    renderAssistantDraft();
  }
}

async function loadMessages(sessionId) {
  const messages = await clientFetchJson(`/api/assistant/sessions/${sessionId}/messages`);
  const currentSession = currentAssistantSession();
  if (currentSession && currentSession.id === sessionId && Number(currentSession.unreadSupportCount || 0) > 0) {
    currentSession.unreadSupportCount = 0;
    renderSessions();
    renderAssistantMeta(clientState.assistantContext);
  }
  renderAssistantMessages(messages || []);
}

async function selectSession(sessionId) {
  const session = await clientFetchJson(`/api/assistant/sessions/${sessionId}`);
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  clientState.assistantContext = {
    intent: session.lastIntent,
    serviceStatus: session.serviceStatus,
    sources: [],
    pendingOrderDraft: null,
  };
  renderSessions();
  renderAssistantMeta(clientState.assistantContext);
  await Promise.all([loadMessages(session.id), loadPendingDraft()]);
}

async function createSession() {
  const session = await clientFetchJson("/api/assistant/sessions", { method: "POST" });
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  clientState.pendingDraft = null;
  clientState.assistantContext = {
    intent: session.lastIntent,
    serviceStatus: session.serviceStatus,
    sources: [],
    pendingOrderDraft: null,
  };
  renderAssistantMeta(clientState.assistantContext);
  renderAssistantDraft();
  await loadSessions();
}

async function loadPendingDraft() {
  if (!clientState.user || !clientState.threadId) {
    clientState.pendingDraft = null;
    renderAssistantDraft();
    renderAssistantMeta(clientState.assistantContext);
    return;
  }
  clientState.pendingDraft = await clientFetchJson(`/api/orders/draft/current?threadId=${encodeURIComponent(clientState.threadId)}`);
  renderAssistantDraft();
  renderAssistantMeta(clientState.assistantContext);
}

async function login() {
  const username = clientById("loginUsername").value.trim();
  const password = clientById("loginPassword").value;
  if (!username || !password) {
    throw new Error("请先输入用户名和密码");
  }
  await clientFetchJson(clientAuthUrl("/api/auth/login"), {
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
  await clientFetchJson(clientAuthUrl("/api/auth/logout"), { method: "POST" });
  clientState.user = null;
  clientState.sessionId = null;
  clientState.threadId = null;
  clientState.pendingDraft = null;
  clientState.assistantContext = null;
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
  setStoreStatus("订单已创建，待支付订单会出现在订单列表里");
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
  clientState.assistantContext = payload;
  clientById("assistantInput").value = "";
  renderAssistantMeta(payload);
  await Promise.all([loadSessions(), loadOrders()]);
}

async function requestHumanSupport() {
  if (!clientState.user) {
    throw new Error("请先登录再转人工");
  }
  if (!clientState.sessionId) {
    await createSession();
  }
  const note = clientById("assistantInput").value.trim();
  const session = await clientFetchJson(`/api/assistant/sessions/${clientState.sessionId}/escalate`, {
    method: "POST",
    body: JSON.stringify({ note }),
  });
  clientState.sessionId = session.id;
  clientState.threadId = `assistant-${session.id}`;
  clientState.assistantContext = {
    intent: session.lastIntent,
    serviceStatus: session.serviceStatus,
    sources: [],
    pendingOrderDraft: null,
  };
  clientById("assistantInput").value = "";
  await loadSessions();
  setStoreStatus(note ? "已转人工，并把你的补充说明一并提交给客服" : "已转人工客服，等待后台跟进");
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
  setStoreStatus(`AI 草稿已转成正式订单：${createdOrder.orderNo}，请继续完成支付`);
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
  renderCatalogExperience();
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

function readOrderAddress(orderId) {
  return document.querySelector(`.order-address-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readPaymentMethod(orderId) {
  return document.querySelector(`.order-payment-method-select[data-order-id="${orderId}"]`)?.value?.trim() || "模拟支付";
}

function readReturnCarrier(orderId) {
  return document.querySelector(`.return-carrier-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readReturnTrackingNo(orderId) {
  return document.querySelector(`.return-tracking-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

function readReturnNote(orderId) {
  return document.querySelector(`.return-note-input[data-order-id="${orderId}"]`)?.value?.trim() || "";
}

async function performOrderAction(orderId, action) {
  let url = "";
  let options = { method: "PATCH" };
  if (action === "pay") {
    url = `/api/orders/${orderId}/pay`;
    options.body = JSON.stringify({ paymentMethod: readPaymentMethod(orderId), note: readOrderNote(orderId) });
  } else if (action === "cancel") {
    url = `/api/orders/${orderId}/cancel`;
    options.body = JSON.stringify({ note: readOrderNote(orderId) });
  } else if (action === "update-address") {
    url = `/api/orders/${orderId}/shipping-address`;
    options.body = JSON.stringify({ shippingAddress: readOrderAddress(orderId), note: "" });
  } else if (action === "confirm-receipt") {
    url = `/api/orders/${orderId}/confirm-receipt`;
  } else if (action === "refund") {
    url = `/api/orders/${orderId}/refund`;
    options.body = JSON.stringify({ note: readOrderNote(orderId) });
  } else {
    throw new Error("不支持的订单动作");
  }
  const updatedOrder = await clientFetchJson(url, options);
  if (action === "update-address") {
    setStoreStatus(`订单 ${updatedOrder.orderNo} 的收货地址已更新`);
  } else if (action === "pay") {
    setStoreStatus(`订单 ${updatedOrder.orderNo} 已完成支付，当前状态 ${statusLabel(updatedOrder.status)}`);
  } else {
    setStoreStatus(`订单 ${updatedOrder.orderNo} 已更新为 ${statusLabel(updatedOrder.status)}`);
  }
  await loadOrders();
}

async function submitReturnShipment(orderId) {
  const carrier = readReturnCarrier(orderId);
  const trackingNo = readReturnTrackingNo(orderId);
  if (!carrier || !trackingNo) {
    throw new Error("请先填写回寄物流公司和运单号");
  }
  const updatedOrder = await clientFetchJson(`/api/orders/${orderId}/return-shipment`, {
    method: "POST",
    body: JSON.stringify({
      carrier,
      trackingNo,
      note: readReturnNote(orderId),
    }),
  });
  setStoreStatus(`订单 ${updatedOrder.orderNo} 的回寄物流已提交，等待平台确认收货退款`);
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
  clientById("searchBtn").addEventListener("click", renderCatalogExperience);
  clientById("searchInput").addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      renderCatalogExperience();
    }
  });
  clientById("searchInput").addEventListener("input", () => {
    renderCatalogExperience();
  });
  clientById("productSortSelect").addEventListener("change", (event) => {
    clientState.productSort = event.target.value || "recommended";
    renderCatalogExperience();
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
  clientById("assistantEscalateBtn").addEventListener("click", () => runClientAction(requestHumanSupport));
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
