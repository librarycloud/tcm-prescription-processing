import { fileURLToPath, URL } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        dts: false
      }),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: false
      })
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: Number(env.VITE_DEV_PORT || 5174),
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:3000',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        }
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            vendor: ['vue', 'vue-router', 'pinia', 'axios']
          }
        }
      }
    },
    esbuild: {
      drop: ['console', 'debugger']
    }
  };
});

