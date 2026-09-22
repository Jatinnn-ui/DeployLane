import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    allowedHosts: true,
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
    rollupOptions: {
      output: {
        // Split heavy third-party libraries into their own chunks so they load only when
        // the code that needs them does, and so the bundle that gates first paint stays small.
        manualChunks: (id: string) => {
          // Charting is only needed on the monitoring screen.
          if (id.includes('node_modules/recharts') || id.includes('node_modules/d3')) {
            return 'charts';
          }
          // Animation library — heavy, mostly used by the landing page hero.
          if (id.includes('node_modules/framer-motion') || id.includes('node_modules/motion')) {
            return 'motion';
          }
          // Radix UI primitives — dialogs, dropdowns, tabs, tooltips, etc.
          if (id.includes('node_modules/@radix-ui')) {
            return 'radix';
          }
          // Data layer used across the app.
          if (id.includes('node_modules/@tanstack')) {
            return 'query';
          }
          // Core React runtime + router — shared by every screen.
          if (id.includes('node_modules/react-dom') || id.includes('node_modules/react-router')) {
            return 'vendor';
          }
          return undefined;
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    // Absolute so it resolves regardless of how the project directory is reached.
    setupFiles: [path.resolve(__dirname, './src/test/setup.ts')],
    css: false,
    // Parallel workers fail to hand-shake on constrained machines ("Timeout waiting for
    // worker to respond"), which reads as a suite failure when nothing is actually broken.
    // Two small suites gain nothing from parallelism, so run them sequentially.
    fileParallelism: false,
  },
});
