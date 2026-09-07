import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/auth': 'http://localhost:8080',
      '/doc': 'http://localhost:8080',
      '/file': 'http://localhost:8080',
      '/search': 'http://localhost:8080',
      '/audit': 'http://localhost:8080',
      '/agent': 'http://localhost:8080'
    }
  }
})
