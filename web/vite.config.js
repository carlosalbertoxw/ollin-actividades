import { defineConfig } from 'vite'

/**
 * Politica de contenido del sitio, solo en el build.
 *
 * GitHub Pages no deja poner cabeceras propias, asi que va como `<meta>`. La
 * pagina no carga nada de fuera ni tiene scripts o estilos en linea: todo sale
 * de sus propios archivos, y la politica lo dice para que un script inyectado
 * --si algun dia se cuela contenido de terceros-- no pueda hacer nada desde la
 * pagina que entrega el APK.
 *
 * No va en el index.html de origen porque `npm run dev` si inyecta estilos y
 * abre un websocket para recargar, y la politica rompería el servidor local.
 * `frame-ancestors` no se puede declarar en un `<meta>`; eso solo lo admite la
 * cabecera.
 */
const POLITICA = [
  "default-src 'self'",
  "img-src 'self' data:",
  "style-src 'self'",
  "script-src 'self'",
  "connect-src 'none'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'none'"
].join('; ')

const politicaDeContenido = {
  name: 'politica-de-contenido',
  apply: 'build',
  transformIndexHtml: () => [
    { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: POLITICA }, injectTo: 'head-prepend' },
    { tag: 'meta', attrs: { name: 'referrer', content: 'strict-origin-when-cross-origin' }, injectTo: 'head-prepend' }
  ]
}

/**
 * El sitio vive en https://carlosalbertoxw.com/ollin-actividades/, que es un
 * subdirectorio: sin `base` los assets se pedirian a la raiz del dominio y la
 * pagina saldria sin estilos. (El github.io de la cuenta redirige a ese dominio
 * propio, y las paginas de proyecto se sirven bajo el mismo.)
 *
 * Se puede sobreescribir con OLLIN_BASE. El flujo de despliegue le pasa la ruta
 * que reporta `actions/configure-pages`, que es quien sabe de verdad donde va a
 * quedar publicado; en local sirve para probarlo en la raiz.
 */
export default defineConfig({
  base: process.env.OLLIN_BASE ?? '/ollin-actividades/',
  plugins: [politicaDeContenido],
  build: {
    outDir: 'dist',
    emptyOutDir: true
  }
})
