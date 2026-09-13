import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const target = env.DASHBOARD_BACKEND_URL || "http://127.0.0.1:8000";
  const url = new URL(target);
  if (
    url.protocol !== "http:" ||
    !["localhost", "127.0.0.1", "[::1]"].includes(url.hostname)
  ) {
    throw new Error("Dashboard P0 proxy requires a local HTTP backend");
  }
  const proxy = { "/v1": { target, changeOrigin: true } };
  return {
    plugins: [react()],
    server: { proxy, strictPort: true },
    preview: { proxy },
    test: {
      environment: "jsdom",
      setupFiles: "./tests/setup.js",
      restoreMocks: true,
    },
  };
});
