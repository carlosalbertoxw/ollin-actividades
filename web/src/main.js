/**
 * Rellena la pagina con lo que dicen los dos JSON que genera
 * `scripts/genera-datos.mjs`.
 *
 * Se piden en tiempo de ejecucion y no se hornean en el HTML a proposito: el
 * mismo sitio compilado sirve para cualquier version, asi que publicar una
 * nueva solo tiene que reescribir `version.json`. Si algo falla —el archivo no
 * esta, la red se cayo— la pagina sigue leyendose entera y solo se queda sin el
 * boton; el enlace a las releases de GitHub cubre ese hueco.
 */

const REPOSITORIO = 'carlosalbertoxw/ollin-actividades'
const RELEASES = `https://github.com/${REPOSITORIO}/releases`

/**
 * Los JSON viven junto al index, bajo la `base` del sitio —que lleva el nombre
 * del repositorio, porque GitHub Pages no sirve desde la raiz del dominio—.
 *
 * Se concatena en vez de usar `new URL(archivo, base)`: `BASE_URL` es una ruta
 * relativa, no una direccion completa, y como base de `URL` no vale. Vite
 * garantiza que termina en barra.
 */
const dato = (archivo) => `${import.meta.env.BASE_URL}${archivo}`

const $ = (selector) => document.querySelector(selector)

/** Megabytes con un decimal. Nadie decide nada con los bytes exactos. */
function tamano (bytes) {
  if (!bytes) return null
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

/** "2026-08-30" leido como fecha local, para que no se corra un dia por el huso. */
function fecha (iso) {
  if (!iso) return null
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!partes) return null

  const cuando = new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3]))
  return cuando.toLocaleDateString('es-MX', { day: 'numeric', month: 'long', year: 'numeric' })
}

async function pinta () {
  document.querySelectorAll('[data-releases], [data-releases-pie]')
    .forEach((enlace) => { enlace.href = RELEASES })

  const espera = $('[data-espera]')
  const fallo = $('[data-fallo]')

  let version
  try {
    const respuesta = await fetch(dato('version.json'), { cache: 'no-cache' })
    if (!respuesta.ok) throw new Error(`El sitio respondió ${respuesta.status}`)
    version = await respuesta.json()
  } catch (error) {
    console.warn('No se pudo leer version.json', error)
    espera.hidden = true
    fallo.hidden = false
    return
  }

  const boton = $('[data-apk]')
  boton.href = version.apk
  boton.hidden = false
  espera.hidden = true

  $('[data-apk-detalle]').textContent = [
    `Versión ${version.version}`,
    tamano(version.tamanoBytes),
    fecha(version.publicada)
  ].filter(Boolean).join(' · ')

  // La huella solo cuando existe: una linea vacia bajo "comprueba que es el
  // archivo publicado" es peor que no prometer la comprobacion.
  if (version.sha256) {
    const huella = $('[data-huella]')
    huella.textContent = `SHA-256 de la ${version.version}: ${version.sha256}`
    huella.hidden = false

    const nombre = version.apk.split('/').pop() || 'ollin-actividades.apk'
    $('[data-huella-comando]').textContent = `sha256sum ${nombre}`
  }

  await pintaHistorial(version.version)
}

async function pintaHistorial (publicada) {
  const contenedor = $('[data-historial]')

  let historial
  try {
    const respuesta = await fetch(dato('historial.json'), { cache: 'no-cache' })
    if (!respuesta.ok) throw new Error(`El sitio respondió ${respuesta.status}`)
    historial = await respuesta.json()
  } catch (error) {
    console.warn('No se pudo leer historial.json', error)
    contenedor.innerHTML =
      `<p class="parrafo">El historial completo está ` +
      `<a href="${RELEASES}" rel="noopener">en GitHub</a>.</p>`
    return
  }

  // El HTML de cada version lo genera el script de compilacion a partir del
  // CHANGELOG, escapando antes de marcar. Ver genera-datos.mjs.
  contenedor.innerHTML = historial.versiones.map((v) => `
    <article class="version">
      <h3 class="version__titulo">
        <span>${v.version}</span>
        ${v.version === publicada ? '<span class="version__insignia">Actual</span>' : ''}
        ${v.fecha ? `<span class="version__fecha">${fecha(v.fecha) ?? v.fecha}</span>` : ''}
      </h3>
      ${v.html}
    </article>
  `).join('')
}

pinta()
