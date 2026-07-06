function byId(id) {
  return document.getElementById(id);
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function aiModeLabel(runtime) {
  return runtime?.mode === "REMOTE_MODEL" ? "真实模型" : "本地联调";
}

function providerLabel(runtime) {
  if (!runtime?.provider) {
    return "未知提供方";
  }
  if (runtime.provider === "DASHSCOPE_COMPATIBLE") {
    return "DashScope 兼容接口";
  }
  if (runtime.provider === "OPENAI") {
    return "OpenAI";
  }
  if (runtime.provider === "LOCAL_RULES") {
    return "本地兜底逻辑";
  }
  return "OpenAI 兼容接口";
}

function vectorStoreLabel(runtime) {
  if (!runtime) {
    return "未连接";
  }
  if (runtime.vectorStoreType === "PGVECTOR") {
    return `pgvector / ${runtime.vectorTable || "knowledge_embeddings"}`;
  }
  return "内存向量库";
}

function sessionLabel(customerUser, adminUser) {
  const labels = [];
  if (customerUser?.username) {
    labels.push(`客户端 ${customerUser.username}`);
  }
  if (adminUser?.username) {
    labels.push(`管理端 ${adminUser.username}`);
  }
  return labels.length ? labels.join(" · ") : "当前未登录";
}

function setText(id, value) {
  const node = byId(id);
  if (node) {
    node.textContent = value;
  }
}

function setStatusChip(text, healthy) {
  const node = byId("frontendBackendStatus");
  if (!node) {
    return;
  }
  node.textContent = text;
  node.style.background = healthy ? "rgba(15, 118, 110, 0.12)" : "rgba(194, 65, 12, 0.12)";
  node.style.color = healthy ? "#115e59" : "#c2410c";
}

function setRuntimeNote(text, warning = false) {
  const node = byId("frontendRuntimeNote");
  if (!node) {
    return;
  }
  node.textContent = text;
  node.classList.toggle("warning", warning);
}

function setSessionNote(text, warning = false) {
  const node = byId("frontendSessionNote");
  if (!node) {
    return;
  }
  node.textContent = text;
  node.classList.toggle("warning", warning);
}

async function loadWorkspaceStatus() {
  const [runtimeResult, productsResult, customerResult, adminResult] = await Promise.allSettled([
    fetchJson("/api/assistant/health"),
    fetchJson("/api/products"),
    fetchJson("/api/auth/me?scope=customer"),
    fetchJson("/api/auth/me?scope=admin"),
  ]);

  if (runtimeResult.status !== "fulfilled") {
    setStatusChip("前端已启动，但后端代理暂不可用", false);
    setText("runtimeModeValue", "未连接");
    setText("runtimeVectorValue", "未连接");
    setText("runtimeChatValue", "未连接");
    setText("runtimeDocsValue", "未连接");
    setText("productCountValue", "未连接");
    setText("sessionSummaryValue", "未连接");
    setRuntimeNote("当前无法访问 /api/assistant/health。先启动 Spring Boot，或者在 frontend/.env 中设置正确的 VITE_BACKEND_TARGET。", true);
    setSessionNote("后端未连接时，这个页面本身能打开，但客户端/管理端里的真实业务接口不会工作。", true);
    return;
  }

  const runtime = runtimeResult.value;
  const products = productsResult.status === "fulfilled" ? (productsResult.value || []) : [];
  const customerUser = customerResult.status === "fulfilled" ? customerResult.value : null;
  const adminUser = adminResult.status === "fulfilled" ? adminResult.value : null;

  setStatusChip("后端代理已连接", true);
  setText("runtimeModeValue", aiModeLabel(runtime));
  setText("runtimeVectorValue", vectorStoreLabel(runtime));
  setText("runtimeChatValue", runtime.chatModelName || runtime.chatModelClass || "未知");
  setText("runtimeDocsValue", `${runtime.knowledgeDocumentCount || 0} 篇 / ${runtime.indexedSegmentCount || 0} 片`);
  setText("productCountValue", `${products.length} 件商品`);
  setText("sessionSummaryValue", sessionLabel(customerUser, adminUser));

  const warningText = Array.isArray(runtime.warnings) && runtime.warnings.length
    ? runtime.warnings.join(" ")
    : `当前提供方：${providerLabel(runtime)}。`;
  setRuntimeNote(warningText, Array.isArray(runtime.warnings) && runtime.warnings.length > 0);

  if (customerUser?.username || adminUser?.username) {
    setSessionNote(`当前浏览器里已检测到登录态：${sessionLabel(customerUser, adminUser)}。`);
  } else {
    setSessionNote("当前浏览器里还没有客户或管理员登录态。可以分别去客户端和管理端登录，不会互相覆盖。");
  }
}

loadWorkspaceStatus().catch((error) => {
  setStatusChip("工作台检测失败", false);
  setRuntimeNote(error?.message || "读取运行态失败，请检查前端代理和后端服务。", true);
});
