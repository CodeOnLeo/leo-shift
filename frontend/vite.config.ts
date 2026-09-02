import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      // 이전 구현에는 서비스 워커가 아예 없어서 오프라인이 전혀 안 됐다.
      // 병동·현장·지하처럼 근무표를 실제로 확인하는 곳이 대부분 네트워크가 나쁘다.
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        navigateFallback: '/index.html',
        runtimeCaching: [
          {
            // 달력 데이터는 캐시를 먼저 보여주고 뒤에서 갱신한다.
            //
            // 정규식은 경로가 아니라 <b>주소 전체</b>에 맞춰진다. `^\/api\/`로 적으면
            // https://호스트/api/... 와 어긋나서 규칙이 통째로 죽는다. 그러면
            // 서비스 워커는 있는데 오프라인에서 아무것도 안 보이는 상태가 된다.
            urlPattern: /\/api\/(calendars|schedule|events|external\/events)/,
            handler: 'StaleWhileRevalidate',
            options: {
              cacheName: 'schedule-api',
              expiration: { maxEntries: 200, maxAgeSeconds: 60 * 60 * 24 * 30 },
              // 오류 응답을 캐시에 넣으면 오프라인에서 그 오류가 계속 보인다
              cacheableResponse: { statuses: [200] },
            },
          },
        ],
      },
      manifest: {
        id: '/',
        name: 'Leo Shift',
        short_name: 'Leo Shift',
        description: '근무표를 만들고 함께 볼 사람과 일정을 겹쳐 봅니다',
        lang: 'ko-KR',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#ffffff',
        theme_color: '#2563eb',
        icons: [
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
        shortcuts: [
          { name: '오늘', url: '/day/today' },
          { name: '이번 주', url: '/week' },
        ],
      },
    }),
  ],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  build: {
    // dist에 만들고 Gradle이 jar 안으로 복사한다.
    // src/ 아래에 빌드 산출물을 두지 않는다.
    outDir: 'dist',
    emptyOutDir: true,
    sourcemap: true,
  },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080' },
  },
})
