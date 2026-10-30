import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, mode }) => {
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env };
  if (command === 'build' && env.RENDER === 'true') {
    let url;
    try { url = new URL(env.VITE_API_URL); } catch { throw new Error('Set VITE_API_URL to the public HTTPS backend URL before building on Render.'); }
    if (url.protocol !== 'https:' || ['localhost', '127.0.0.1', '0.0.0.0'].includes(url.hostname) || url.pathname !== '/' || url.search || url.hash)
      throw new Error('VITE_API_URL must be the public HTTPS backend origin, without /api, a query, or a fragment.');
  }
  return {
    plugins: [react()],
    define: { global: 'globalThis' },
    server: {
      proxy: {
        '/api': 'http://localhost:8080',
        '/ws': { target: 'http://localhost:8080', ws: true },
      },
    },
  };
});
