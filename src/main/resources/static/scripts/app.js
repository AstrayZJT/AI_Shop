const state = {
  user: null,
  sessionId: null,
  threadId: null,
};

function byId(id) {
  return document.getElementById(id);
}

function setStatus(text) {
  const node = byId("status");
  if (node) {
    node.textContent = text;
  }
}

function renderMessages(items) {
  const host = byId("messages");
  if (!host) {
    return;
  }
  host.innerHTML = "";
  for (const item of items) {
    const row = document.createElement("div");
    row.className = `message ${item.role}`;
    row.textContent = item.content;
    host.appendChild(row);
  }
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    credentials: "same-origin",
    ...options,
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(text || `HTTP ${response.status}`);
  }
  return text ? JSON.parse(text) : null;
}

async function loadMe() {
  try {
    state.user = await fetchJson("/api/auth/me");
    setStatus(state.user ? `Signed in as ${state.user.username}` : "Not signed in");
    await loadSessions();
  } catch (error) {
    state.user = null;
    setStatus("Not signed in");
  }
}

async function loadSessions() {
  const list = byId("sessionList");
  if (!list) {
    return;
  }

  list.innerHTML = "";
  if (!state.user) {
    return;
  }

  const sessions = await fetchJson("/api/assistant/sessions");
  if (sessions.length === 0) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "No sessions yet";
    list.appendChild(empty);
    return;
  }

  for (const session of sessions) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `session-item ${state.sessionId === session.id ? "active" : ""}`;
    button.innerHTML = `<strong>${session.title}</strong><span>${session.summary || ""}</span>`;
    button.addEventListener("click", () => selectSession(session.id));
    list.appendChild(button);
  }
}

async function selectSession(id) {
  state.sessionId = id;
  const session = await fetchJson(`/api/assistant/sessions/${id}`);
  state.threadId = `assistant-${session.id}`;
  setStatus(`Signed in as ${state.user.username} | Session ${session.id}`);
  await loadMessages(id);
  await loadSessions();
}

async function loadMessages(id) {
  const messages = await fetchJson(`/api/assistant/sessions/${id}/messages`);
  renderMessages(messages);
}

async function createSession() {
  const created = await fetchJson("/api/assistant/sessions", {
    method: "POST",
  });
  await selectSession(created.id);
}

async function login() {
  const username = byId("username")?.value?.trim();
  const password = byId("password")?.value || "";

  if (!username || !password) {
    setStatus("Enter username and password first");
    return;
  }

  await fetchJson("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });

  await loadMe();
  if (!state.sessionId) {
    await createSession();
  }
}

async function logout() {
  await fetchJson("/api/auth/logout", { method: "POST" });
  state.user = null;
  state.sessionId = null;
  state.threadId = null;
  renderMessages([]);
  const output = byId("output");
  if (output) {
    output.textContent = "";
  }
  setStatus("Not signed in");
  await loadSessions();
}

async function sendMessage() {
  const message = byId("msg")?.value?.trim();
  if (!message) {
    return;
  }
  if (!state.user) {
    setStatus("Please sign in first");
    return;
  }
  if (!state.sessionId) {
    await createSession();
  }

  const payload = {
    sessionId: state.sessionId,
    message,
    threadId: byId("remember")?.checked ? state.threadId : null,
  };

  const data = await fetchJson("/api/assistant/chat", {
    method: "POST",
    body: JSON.stringify(payload),
  });

  state.sessionId = data.sessionId;
  state.threadId = data.threadId;

  const input = byId("msg");
  if (input) {
    input.value = "";
  }

  const output = byId("output");
  if (output) {
    output.textContent = JSON.stringify(data, null, 2);
  }

  await loadMessages(state.sessionId);
  await loadSessions();
}

document.addEventListener("DOMContentLoaded", async () => {
  byId("loginBtn")?.addEventListener("click", login);
  byId("logoutBtn")?.addEventListener("click", logout);
  byId("send")?.addEventListener("click", sendMessage);
  byId("newSessionBtn")?.addEventListener("click", async () => {
    if (!state.user) {
      setStatus("Please sign in first");
      return;
    }
    await createSession();
  });

  await loadMe();
});
