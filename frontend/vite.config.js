import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: 'localhost',
    proxy: {
      '/product-service': { target: 'http://localhost:8000', changeOrigin: true },
      '/commerce-service': { target: 'http://localhost:8000', changeOrigin: true },
      '/recommendation-service': { target: 'http://localhost:8000', changeOrigin: true },
      '/stock-service': { target: 'http://localhost:8000', changeOrigin: true },
      '/pos-sync-service': { target: 'http://localhost:8000', changeOrigin: true },
    },
  },
})
