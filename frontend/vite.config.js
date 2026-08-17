import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 서버에서 서비스 경로를 API Gateway(:8000)로 프록시한다.
// 경로가 게이트웨이 라우팅 규칙(/<service-name>/**)과 동일해서 그대로 넘기면 된다.
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
