import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import type { Plugin } from 'vite'

function envScriptPlugin(): Plugin {
  return {
    name: 'env-script',
    transformIndexHtml(html, ctx) {
      const buildTs = process.env.BUILD_TS
      if (ctx.server || !buildTs) return html
      return html.replace('<head>', `<head>\n    <script src="/env.${buildTs}.js"></script>`)
    },
  }
}

export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: [['babel-plugin-react-compiler']],
      },
    }),
    envScriptPlugin(),
  ],
})
