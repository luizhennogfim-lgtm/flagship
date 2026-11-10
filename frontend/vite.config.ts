import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend has no CORS configuration, so in development the browser talks to
// Vite (same origin) and Vite forwards /api to Spring Boot.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: process.env.VITE_API_TARGET ?? 'http://localhost:8080', changeOrigin: true } },
  },
});
