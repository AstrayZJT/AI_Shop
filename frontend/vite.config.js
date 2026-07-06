import { fileURLToPath, URL } from "node:url";

import { defineConfig, loadEnv } from "vite";

const rootDir = fileURLToPath(new URL(".", import.meta.url));
const staticDir = fileURLToPath(new URL("../src/main/resources/static", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, rootDir, "");
  const backendTarget = env.VITE_BACKEND_TARGET || "http://localhost:8080";

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
