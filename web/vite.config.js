import { defineConfig } from 'vite'

/**
 * El sitio se sirve desde https://<usuario>.github.io/<repo>/, no desde la raiz
 * del dominio, asi que `base` tiene que llevar el nombre del repositorio: sin
 * el, los enlaces a los assets con hash apuntarian a / y la pagina saldria sin
 * estilos ni script.
 *
 * Se puede sobreescribir con OLLIN_BASE para servirlo en otro sitio —un dominio
 * propio en el futuro, o `/` al probarlo en local con `vite preview`—.
 */
export default defineConfig({
  base: process.env.OLLIN_BASE ?? '/ollin-actividades/',
  build: {
    outDir: 'dist',
    // Un sitio de una pagina con una hoja de estilo: dividirlo en trozos solo
    // agrega peticiones.
    assetsInlineLimit: 4096
  }
})
