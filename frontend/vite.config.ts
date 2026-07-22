import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

const backendOrigin = process.env.CANVAS_BACKEND_ORIGIN ?? "http://backend:8080";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    globals: true,
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
  },
  server: {
    host: "0.0.0.0",
    proxy: {
      "/api": backendOrigin,
      "/public": backendOrigin,
    },
  },
});
