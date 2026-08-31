/**
 * Genera los dos JSON que el sitio sirve, a partir de CHANGELOG.md.
 *
 *   public/version.json    lo que consulta la app para saber si hay novedad
 *   public/historial.json  lo que la pagina pinta como historial de cambios
 *
 * Ninguno de los dos se versiona: se escriben en cada compilacion. Tenerlos en
 * git garantizaria que tarde o temprano anunciaran una version distinta de la
 * que hay publicada, y ese desajuste es justo el que rompe el aviso de la app.
 *
 * Los datos del artefacto —tamano, huella, fecha— llegan por variables de
 * entorno desde el flujo de publicacion, que es quien los conoce. Sin ellas el
 * script sigue funcionando con la version del CHANGELOG y la direccion que
 * *tendra* la release: asi `npm run dev` en local produce un sitio coherente
 * sin necesidad de haber publicado nada.
 */

import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const aqui = dirname(fileURLToPath(import.meta.url))
const raiz = resolve(aqui, '../..')
const publico = resolve(aqui, '../public')

const REPOSITORIO = process.env.OLLIN_REPO ?? 'carlosalbertoxw/ollin-actividades'
const SITIO = process.env.OLLIN_SITIO ?? 'https://carlosalbertoxw.github.io/ollin-actividades/'

// ---------------------------------------------------------------- CHANGELOG

/**
 * Parte el historial en secciones `## [x.y.z] - fecha`.
 *
 * Se ignora `## [Sin publicar]` a proposito: no casa con el patron de version,
 * y anunciar como disponible algo que nadie ha etiquetado mandaria a la gente a
 * una descarga que no existe.
 */
function leeHistorial () {
  const texto = readFileSync(resolve(raiz, 'CHANGELOG.md'), 'utf8')
  const encabezado = /^##\s+\[(\d+\.\d+\.\d+)](?:\s*[-–]\s*(\S+))?\s*$/gm

  const marcas = [...texto.matchAll(encabezado)]
  if (marcas.length === 0) {
    throw new Error('CHANGELOG.md no tiene ningun encabezado `## [x.y.z]`.')
  }

  return marcas.map((marca, i) => {
    const desde = marca.index + marca[0].length
    const hasta = i + 1 < marcas.length ? marcas[i + 1].index : texto.length
    const cuerpo = texto
      .slice(desde, hasta)
      // Las referencias de enlace del final ([1.0.0]: https://…) no son
      // contenido: son la fontaneria de los enlaces de Markdown.
      .replace(/^\[[^\]]+]:\s*\S+$/gm, '')
      .trim()

    return {
      version: marca[1],
      fecha: marca[2] ?? null,
      cuerpo,
      html: aHtml(cuerpo),
      resumen: aResumen(cuerpo)
    }
  })
}

/**
 * Markdown a HTML, lo justo para lo que aparece en un changelog: encabezados de
 * tercer nivel, listas, negrita, codigo y enlaces.
 *
 * A mano y no con una biblioteca por la misma razon por la que el `.xlsx` se
 * escribe a mano en la app: el vocabulario esta bajo control y una dependencia
 * de las que arrastran cincuenta paquetes para esto no se paga sola.
 *
 * **Se escapa primero y se marca despues.** El contenido sale de un archivo del
 * repositorio, pero lo pega quien redacta una version, y una etiqueta suelta en
 * una nota de cambios no tiene por que acabar ejecutandose en el navegador de
 * quien lee.
 */
function aHtml (markdown) {
  const escapado = markdown
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // El orden importa: los enlaces antes que nada, para que su texto no se
  // reinterprete, y la negrita antes que la cursiva, porque `**` empieza por
  // `*` y el patron de la cursiva se comeria la mitad de cada marca.
  const enLinea = (linea) => linea
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" rel="noopener">$1</a>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')

  const salida = []
  let enLista = false

  const cierraLista = () => {
    if (enLista) { salida.push('</ul>'); enLista = false }
  }

  for (const linea of escapado.split('\n')) {
    const limpia = linea.trim()

    if (limpia === '') { cierraLista(); continue }

    const titulo = limpia.match(/^###\s+(.*)$/)
    if (titulo) {
      cierraLista()
      salida.push(`<h4>${enLinea(titulo[1])}</h4>`)
      continue
    }

    const punto = limpia.match(/^[-*]\s+(.*)$/)
    if (punto) {
      if (!enLista) { salida.push('<ul>'); enLista = true }
      salida.push(`<li>${enLinea(punto[1])}</li>`)
      continue
    }

    cierraLista()
    salida.push(`<p>${enLinea(limpia)}</p>`)
  }

  cierraLista()
  return salida.join('\n')
}

/**
 * Una frase para la tarjeta de la app, sin marcas de Markdown.
 *
 * La pantalla de Acerca de tiene sitio para dos o tres renglones, no para un
 * changelog entero: quien quiera el detalle tiene el enlace al sitio.
 */
function aResumen (markdown) {
  // Si la version abre con un parrafo antes del primer `###`, ese parrafo es el
  // resumen que alguien escribio a mano y siempre lee mejor que la lista de
  // cambios aplanada. Solo cuando no lo hay se recurre a los puntos.
  const entradilla = markdown.split(/^###\s+/m)[0].trim()

  const plano = (entradilla || markdown)
    .replace(/^###\s+/gm, '')
    .replace(/^[-*]\s+/gm, '')
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/[*`]/g, '')
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .join(' ')

  return plano.length > 280 ? `${plano.slice(0, 279).trimEnd()}…` : plano
}

// ------------------------------------------------------------------ salida

const historial = leeHistorial()
const ultima = historial[0]

const version = process.env.OLLIN_VERSION?.replace(/^v/, '') || ultima.version
const etiqueta = `v${version}`
const apk = process.env.OLLIN_APK_URL ||
  `https://github.com/${REPOSITORIO}/releases/download/${etiqueta}/ollin-actividades-${version}.apk`

const notasDeEsaVersion = historial.find((v) => v.version === version) ?? ultima

/**
 * El contrato con la app. Los nombres de los campos los lee
 * `ComprobadorActualizaciones.lee()`: cambiar uno aqui rompe el aviso de todas
 * las versiones ya instaladas, que no se pueden actualizar para arreglarlo.
 * Se agregan campos, no se renombran.
 */
const versionJson = {
  version,
  publicada: process.env.OLLIN_PUBLICADA || notasDeEsaVersion.fecha || null,
  apk,
  sitio: SITIO,
  tamanoBytes: Number(process.env.OLLIN_APK_BYTES || 0) || null,
  sha256: process.env.OLLIN_APK_SHA256 || null,
  notas: notasDeEsaVersion.resumen,
  release: `https://github.com/${REPOSITORIO}/releases/tag/${etiqueta}`
}

mkdirSync(publico, { recursive: true })
writeFileSync(resolve(publico, 'version.json'), `${JSON.stringify(versionJson, null, 2)}\n`)
writeFileSync(
  resolve(publico, 'historial.json'),
  `${JSON.stringify({ repositorio: REPOSITORIO, versiones: historial }, null, 2)}\n`
)

console.log(`version.json -> ${version}${versionJson.sha256 ? ' (con huella)' : ' (sin artefacto todavia)'}`)
console.log(`historial.json -> ${historial.length} versiones`)
