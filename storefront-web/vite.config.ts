import react from '@vitejs/plugin-react'
import { defineConfig, type PreviewServer, type ViteDevServer } from 'vite'
import tailwindcss from '@tailwindcss/vite'
import { resolve } from 'path'

function adminEntry(server: ViteDevServer | PreviewServer) {
  server.middlewares.use((request, response, next) => {
    if (request.method === 'GET' && request.headers.accept?.includes('text/html')) {
      if (/^\/admin\.html(?:\/|\?|$)/.test(request.url ?? '')) {
        response.writeHead(302, { Location: request.url!.replace(/^\/admin\.html/, '/admin') })
        response.end()
        return
      }
      if (/^\/admin(?:\/|\?|$)/.test(request.url ?? '')) request.url = '/admin.html'
    }
    next()
  })
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), {
    name: 'admin-html-entry',
    configureServer: adminEntry,
    configurePreviewServer: adminEntry,
  }, {
    name: 'shopper-bundle-boundary',
    generateBundle(_options, bundle) {
      const visited = new Set<string>()
      const visit = (file: string) => {
        if (visited.has(file)) return
        visited.add(file)
        const chunk = bundle[file]
        if (!chunk || chunk.type !== 'chunk') return
        for (const id of Object.keys(chunk.modules)) {
          const path = id.replaceAll('\\', '/')
          if (path.includes('/src/admin/') || path.includes('/src/pages/DevHarnessPage')) {
            this.error(`Shopper bundle includes restricted module: ${id}`)
          }
        }
        for (const dependency of [...chunk.imports, ...chunk.dynamicImports]) visit(dependency)
      }
      for (const chunk of Object.values(bundle)) {
        if (chunk.type === 'chunk' && chunk.isEntry && chunk.name === 'main') visit(chunk.fileName)
      }
    },
  }],
  build: {
    rollupOptions: {
      input: {
        main: resolve(import.meta.dirname, 'index.html'),
        admin: resolve(import.meta.dirname, 'admin.html'),
      },
    },
  },
  server: {
    host: '127.0.0.1',
    proxy: {
      '/v1': 'http://localhost:8089',
      '/oauth2': 'http://localhost:8089',
      '/login/oauth2': 'http://localhost:8089',
      '/logout': 'http://localhost:8089',
    },
  },
})