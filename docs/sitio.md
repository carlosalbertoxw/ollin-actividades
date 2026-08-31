# El sitio

`https://carlosalbertoxw.github.io/ollin-actividades/`

Una sola página, en [`web/`](../web/), construida con Vite y publicada en GitHub Pages desde este mismo repositorio. Hace dos cosas:

1. **Reparte el APK.** Ollin no está en ninguna tienda, así que este es el único sitio de descarga oficial. Lleva la versión, su tamaño, su huella SHA-256 y las instrucciones para instalar fuera de la tienda.
2. **Publica `version.json`**, que es lo que la app consulta una vez al día para saber si hay algo más nuevo. Ver [actualizaciones](actualizaciones.md).

## Por qué Vite y no un HTML suelto

Por el hash de los assets. La página se sirve desde un CDN que cachea con ganas; sin nombres versionados, cambiar el CSS deja a media gente viendo el anterior durante horas. Vite lo resuelve solo y de paso agrupa y minifica.

Lo que **no** hay es framework. Es una página de descarga: el DOM lo toca un archivo de un centenar de líneas ([`src/main.js`](../web/src/main.js)) y no hay estado que gestionar. Es el mismo criterio con el que la app escribe sus `.xlsx` a mano en vez de traer Apache POI.

## Estructura

```
web/
├── index.html               La página entera, con el texto ya escrito
├── src/
│   ├── main.js              Pide los dos JSON y rellena los huecos
│   └── estilo.css           Paleta de la app, tema claro y oscuro
├── scripts/
│   └── genera-datos.mjs     Genera los JSON desde CHANGELOG.md
├── public/                  Lo que se copia tal cual (los JSON generados)
└── vite.config.js
```

### Los dos JSON no se versionan

`public/version.json` y `public/historial.json` los escribe [`genera-datos.mjs`](../web/scripts/genera-datos.mjs) en cada compilación, y están en el `.gitignore`. Tenerlos en git garantizaría que tarde o temprano anunciaran una versión distinta de la que hay publicada, y ese desajuste es justo el que rompe el aviso de la app.

### De dónde salen los datos

| Dato | Origen |
|---|---|
| Versión, fecha, notas, historial | `CHANGELOG.md` |
| Dirección del APK, tamaño, SHA-256 | La release de GitHub, consultada con `gh` durante el despliegue |
| Dirección base del sitio | `actions/configure-pages`, que sabe dónde va a quedar publicado |

El flujo de despliegue **descarga el APK publicado** para medirlo y sacarle la huella, en vez de arrastrar esos datos desde la compilación. Es a propósito: la huella que se publica es la del archivo que la gente va a bajar, que es lo único que hace que comprobarla signifique algo.

Si todavía no hay ninguna release, el script no falla: se apoya solo en el `CHANGELOG` y el botón apunta a la dirección que *tendrá* la descarga. Así `npm run dev` produce un sitio coherente sin haber publicado nada.

## Cuándo se despliega

[`sitio.yml`](../.github/workflows/sitio.yml) corre en tres momentos:

- **Al publicar una versión.** El último job de [`publicacion.yml`](../.github/workflows/publicacion.yml) lo lanza con `gh workflow run sitio.yml --ref main`. Es el despliegue importante: reescribe el `version.json` que consultan las instalaciones que ya están por ahí.
- **Al empujar a `main`** algo bajo `web/` o el `CHANGELOG.md`.
- **A mano**, desde la pestaña Actions.

No recibe nada por parámetro: le pregunta a GitHub cuál es la última release. Por eso se puede relanzar en cualquier momento —tras corregir una errata, tras borrar una release equivocada— y siempre publica datos que corresponden con la realidad.

### Siempre desde `main`, nunca desde la etiqueta

El flujo de publicación lo **lanza** en vez de invocarlo como flujo reutilizable, y eso cambia el ref con el que corre. Hay dos razones y apuntan al mismo sitio.

La práctica: el entorno `github-pages` solo admite despliegues desde la rama por omisión. Invocado desde el flujo de publicación, correría en el ref de la etiqueta y GitHub lo rechaza con *«Tag v1.0.0 is not allowed to deploy to github-pages due to environment protection rules»*.

La de fondo: el contenido del sitio —el HTML, los estilos, los textos— debe salir de `main` y no del árbol al que apunte una etiqueta. Si saliera de la etiqueta, relanzar la publicación de una versión vieja republicaría el sitio de entonces y se llevaría por delante cualquier corrección posterior.

Los datos de la descarga no se pierden por lanzarlo desde `main`: no viajan por el ref, sino que salen de preguntarle a GitHub cuál es la última release.

Como contrapartida, el despliegue corre **aparte** y el flujo de publicación no lo espera: termina en verde en cuanto lo lanza. Si el sitio fallara, se ve en su propia ejecución.

## Trabajar en el sitio

```bash
cd web
npm install
npm run dev
```

Queda en `http://localhost:5173/ollin-actividades/`. **Con la ruta**, no en la raíz: `base` lleva el nombre del repositorio porque Pages no sirve desde la raíz del dominio, y sin esa ruta la página carga sin estilos.

```bash
npm run build     # a web/dist
npm run preview   # sirve lo compilado
```

Para probarlo en la raíz (otro alojamiento, un dominio propio):

```bash
OLLIN_BASE=/ npm run build
```

## El renderizador de Markdown

`genera-datos.mjs` convierte cada sección del `CHANGELOG` a HTML con unas pocas expresiones regulares: encabezados de tercer nivel, listas, negrita, cursiva, código y enlaces. Es todo el vocabulario que aparece en un historial de cambios.

**Escapa primero y marca después.** El contenido sale de un archivo del repositorio, pero lo redacta una persona, y una etiqueta suelta en unas notas de versión no tiene por qué acabar ejecutándose en el navegador de quien las lee.

El orden de las sustituciones importa: los enlaces antes que nada, para que su texto no se reinterprete, y la negrita antes que la cursiva, porque `**` empieza por `*` y el patrón de la cursiva se comería la mitad de cada marca.

## Privacidad del sitio

Sin analítica, sin cookies, sin rastreadores, sin fuentes remotas —la tipografía es la del sistema—. La única petición que hace la página además de sus propios assets son los dos JSON, que están en el mismo origen.

Lo sirve GitHub Pages, que como cualquier servidor ve la dirección IP de quien pide una página. Está dicho en la propia página, en la sección de privacidad: sería incoherente prometer que la bitácora no sale del teléfono y callar lo que sí se puede saber por visitar el sitio.
