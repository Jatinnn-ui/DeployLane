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
        // Charting is only needed on the monitoring screen, and the 3D renderer only on the
        // marketing hero, so neither should sit in the bundle that gates first paint.
        manualChunks: (id: string) => {
          if (id.includes('node_modules/recharts') || id.includes('node_modules/d3')) {
            return 'charts';
          }
          const threeDeps = [
            'node_modules/three',
            'node_modules/@react-three',
            'node_modules/its-fine',
            'node_modules/meshline',
            'node_modules/maath',
            'node_modules/suspend-react',
            'node_modules/camera-controls',
          ];
          if (threeDeps.some((dep) => id.includes(dep))) {
            return 'three';
          }
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
