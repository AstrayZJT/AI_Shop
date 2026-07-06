import { fileURLToPath, URL } from "node:url";

import { defineConfig, loadEnv } from "vite";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const staticDir = fileURLToPath(new URL("../src/main/resources/static", import.meta.url));

async function probeBackendTarget(target) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 800);
  try {
    const response = await fetch(new URL("/api/products", target), {
      signal: controller.signal,
    });
    return response.ok;
  } catch (error) {
    return false;
  } finally {
    clearTimeout(timeout);
  }
}

async function resolveBackendTarget(configuredTarget) {
  if (configuredTarget) {
    return configuredTarget;
  }
  const candidates = [
    "http://localhost:8080",
    "http://127.0.0.1:8080",
    "http://localhost:8082",
    "http://127.0.0.1:8082",
  ];
  for (const candidate of candidates) {
    if (await probeBackendTarget(candidate)) {
      return candidate;
    }
  }
  return "http://localhost:8080";
}

export default defineConfig(async ({ mode }) => {
  const env = loadEnv(mode, rootDir, "");
  const backendTarget = await resolveBackendTarget(env.VITE_BACKEND_TARGET);

  return {
    publicDir: staticDir,
    server: {
      port: Number(env.VITE_FRONTEND_PORT || 5173),
      strictPort: false,
      proxy: {
        "/api": {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
    preview: {
      port: Number(env.VITE_FRONTEND_PREVIEW_PORT || 4173),
      strictPort: false,
    },
    build: {
      outDir: "dist",
      emptyOutDir: true,
    },
  };
});
