import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Le port 5173 n'est pas un hasard : c'est l'origine autorisée par le client Keycloak "atlas-web"
// (redirectUris) et par le CORS de la gateway. strictPort évite que Vite prenne un autre port en silence.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'node',
  },
});
