import { defineConfig, loadEnv } from "vite";
import vue from "@vitejs/plugin-vue";
import { localDemo } from "./local-demo.mjs";
export default defineConfig(({ mode }) => {
  const gateway = loadEnv(mode, ".", "HW_").HW_GATEWAY_URL || "http://127.0.0.1:8080";
  return {
    plugins: [vue(), localDemo(gateway)],
    server: {
      port: 5173,
      strictPort: true,
      proxy: { "/api": { target: gateway, changeOrigin: true } },
    },
  };
});
