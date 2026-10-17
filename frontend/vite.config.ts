/// <reference types="vitest/config" />
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";

const backend = "http://localhost:8080";
const storage = "http://localhost:8333";

export default defineConfig({
 plugins: [react(), tailwindcss()],
 resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
 server: {
   port: 5173,
   proxy: {
     "/api": { target: backend },
     "/oauth2": { target: backend },
     "/login/oauth2": { target: backend },
     "/ical": { target: backend },
     "/ws": { target: backend, ws: true },
     "/media-public": { target: storage },
     "/media-private": { target: storage },
   },
 },
 test: { environment: "jsdom" },
});
