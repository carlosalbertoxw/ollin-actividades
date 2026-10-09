# Aviso de actualizaciones

Ollin pregunta una vez al día si hay una versión más nueva y lo enseña en **Acerca de**. Es lo único para lo que la app usa la red.

## Por qué existe

Ollin se instala fuera de la tienda. Google Play avisa de una versión nueva y la instala sola; un APK descargado de una página no lo hace nadie. Sin esto, quien instaló Ollin en marzo sigue con el APK de marzo para siempre, incluidos los fallos que se hayan corregido desde entonces y que le afecten.

Por eso **nace encendido**, al revés que los recordatorios. No resuelven lo mismo: un aviso de hábito lo puede dar la propia memoria; enterarse de que se corrigió algo, no. Se apaga en `Ajustes → Actualizaciones`.

## Qué sale del teléfono

Una petición `GET` a un archivo estático. Nada más.

- **No lleva identificador**, ni de instalación ni de dispositivo.
- **No lleva la versión instalada.** La comparación ocurre en el teléfono, con el JSON ya descargado.
- **No lleva nada de la bitácora**, evidentemente.

Lo único que el otro extremo puede deducir es que alguien, desde una dirección IP, pidió ese archivo: exactamente lo mismo que si se abriera la dirección en el navegador. Ver [seguridad](seguridad.md#la-comprobación-de-actualizaciones).

## Qué no hace

**No descarga ni instala nada.** Cuando hay versión nueva, la tarjeta de *Acerca de* enseña un botón que abre el sitio en el navegador; a partir de ahí decide la persona.

Una app que se actualiza sola necesita el permiso de instalar paquetes, y con él se convierte en un canal de entrega de software: cualquiera que comprometa el servidor de actualizaciones entrega código arbitrario a todos los teléfonos que lo consultan. Abrir un enlace no tiene ese alcance.

## Cómo funciona

Tres piezas, en `data/actualizaciones/`:

| Pieza | Qué hace |
|---|---|
| [`Version`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/actualizaciones/Version.kt) | Un semver comparable |
| [`ComprobadorActualizaciones`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/actualizaciones/ComprobadorActualizaciones.kt) | Decide si toca, descarga, interpreta y compara |
| [`AcercaDeVm`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/screens/AcercaDeVm.kt) | Junta lo guardado con lo que está pasando al pulsar el botón |

### Las versiones se comparan como números, no como texto

`"1.10.0" < "1.9.0"` es cierto en orden alfabético y falso en la realidad. Comparar como texto funciona hasta la décima versión menor y a partir de ahí deja a todo el mundo sin enterarse de nada, que es un fallo especialmente feo: se nota tarde y en teléfonos ajenos.

`Version.de()` acepta `1.2.3`, `v1.2.3` y `1.2.3-debug` como la misma versión. La `v` es como se escriben los tags y el `-debug` es el sufijo de la variante de depuración; tratarlos como texto distinto dejaría a la compilación de depuración creyéndose siempre desactualizada.

### Una vez al día

`compruebaSiToca()` mira cuánto ha pasado desde la última consulta. Se apoya en el reloj del teléfono, que se puede mover, y da igual: lo peor que consigue quien lo adelante es preguntar de más, y son 400 bytes.

Se compara el **valor absoluto** del tiempo transcurrido. Sin eso, atrasar la fecha del teléfono dejaría la comprobación congelada hasta que el reloj volviera a alcanzar la marca guardada, que puede ser dentro de años.

El botón *Buscar ahora* de *Acerca de* llama a `compruebaAhora()`, que no mira el reloj: quien lo pulsa quiere saberlo ahora, y hacerle esperar al día siguiente convierte un botón en un adorno.

### Un fallo no gasta el día

La marca de tiempo solo se guarda cuando la respuesta se entendió. Si no hubo red, se reintenta al siguiente arranque en vez de esperar otro día entero.

### Mover el interruptor olvida lo que se supo

En los dos sentidos, por razones distintas. Al **apagarlo**, porque si no quedaría en pantalla el aviso de una versión nueva que ya nadie va a volver a comprobar. Al **encenderlo**, porque se borra también la marca de tiempo: quien acaba de activarlo espera enterarse ahora, no cuando venza el día que corría desde una consulta de hace meses.

## El contrato: `version.json`

Lo publica el sitio en `https://carlosalbertoxw.com/ollin-actividades/version.json` —la dirección de `github.io` redirige ahí— y lo genera [`genera-version.mjs`](../web/scripts/genera-version.mjs). Ver [el sitio](sitio.md).

```json
{
  "version": "1.0.0",
  "publicada": "2026-08-30",
  "apk": "https://github.com/carlosalbertoxw/ollin-actividades/releases/download/v1.0.0/ollin-actividades-1.0.0.apk",
  "sitio": "https://carlosalbertoxw.github.io/ollin-actividades/",
  "tamanoBytes": 11534336,
  "sha256": "…",
  "notas": "Primera versión pública.",
  "release": "https://github.com/carlosalbertoxw/ollin-actividades/releases/tag/v1.0.0"
}
```

| Campo | Obligatorio | Qué pasa si falta |
|---|---|---|
| `version` | Sí | El archivo entero se descarta |
| `apk` | Sí | El archivo entero se descarta. Tiene que ser una descarga de las releases de este repositorio |
| `notas` | No | La tarjeta enseña solo el número de versión. Pasados 300 caracteres se recorta |
| `sitio`, `publicada`, `tamanoBytes`, `sha256`, `release` | No | Solo los usa la página web. La app ya no usa `sitio` (ver abajo) |

**Los nombres de los campos no se renombran, se agregan.** Los lee `ComprobadorActualizaciones.lee()`, y cambiar uno rompe el aviso de todas las versiones que ya están instaladas, que por definición no se pueden actualizar para arreglarlo.

### Solo una descarga de las releases

El enlace acaba abriéndose en el navegador de alguien, y viene de fuera. Hasta la 1.2.1 bastaba con que fuera `https`, pero eso no protegía de lo que más probablemente puede salir mal: el JSON llega por el dominio propio, y quien se quedara con él —un dominio vencido, un DNS secuestrado— podría anunciar a **todas** las instalaciones una «versión nueva» con el enlace que quisiera. Android no instalaría encima un APK con otra firma, pero las notas podrían pedir desinstalar primero, y eso borra la bitácora.

Por eso `apk` tiene que empezar por `https://github.com/carlosalbertoxw/ollin-actividades/releases/download/` (`esDescargaOficial`). Las releases de GitHub no cambian de dueño con el dominio. La comprobación se hace sobre la URL interpretada y no solo sobre el texto: se rechazan usuario en la URL (`github.com@otro.sitio`), un puerto, y los `..` —también escritos como `%2e`, que el navegador resuelve igual— que sacarían la ruta del repositorio.

`sitio` dejó de servir de respaldo por la misma razón: el sitio vive en el dominio propio. Sin un `apk` válido el archivo se descarta. La URL que quedó guardada de una comprobación anterior se vuelve a validar al pintar *Acerca de*.

### Un salto, solo hacia `https` y solo a casa

La petición va con `instanceFollowRedirects = false`, pero no para rechazar las redirecciones: para seguirlas a mano y poder exigir que el destino siga siendo `https`, esté en `carlosalbertoxw.com` o `carlosalbertoxw.github.io` (`HOSTS_DEL_SITIO`) y caiga dentro de `/ollin-actividades/` (`RUTA_DEL_SITIO`). Cualquier otro destino no es una mudanza del sitio. La ruta cuenta porque esos dos hosts sirven también las páginas de otros repositorios de la cuenta; se compara la dirección ya desarmada, sin usuario, puerto, consulta ni fragmento, y con una ruta de solo letras, cifras y `. _ - /`, para que no pasen los `..` ni los `%2e`. `HttpURLConnection` ni siquiera sigue por su cuenta las que cambian de protocolo, y una que se quedara en `http` dejaría la respuesta viajando en claro.

Se sigue **un** salto. Hace falta porque la dirección va compilada dentro de cada APK y no se puede corregir en los que ya están instalados: poner un dominio propio delante de GitHub Pages deja el `.github.io` devolviendo un `301` para siempre, y sin seguirlo el aviso se apaga en todas las instalaciones a la vez. Más de un salto no aporta nada para eso y sí permite que una cadena de redirecciones dé vueltas sin fin.

La decisión vive en `siguienteSalto(codigo, destino)`, separada de la conexión para poder probarla sin levantar un servidor.

Y el manifiesto declara `usesCleartextTraffic="false"`, que prohíbe el texto en claro a nivel de plataforma por si todo lo anterior fallara.

## La descarga

`HttpURLConnection` del propio Android, sin OkHttp ni Retrofit: son megabytes y miles de métodos para una petición que ocurre una vez al día y devuelve un objeto de cinco campos.

Tiene un tope de 64 KB. El archivo real ronda los 400 bytes; el tope está porque es lo único que entra a la app desde la red, y sin límite un archivo enorme —o un servidor que nunca cierra la respuesta— agota la memoria del teléfono. Es el mismo razonamiento que con el `.xlsx`, ver [Excel](excel.md).

## Pruebas

[`ActualizacionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ActualizacionesTest.kt), en la JVM y sin red: la descarga entra por parámetro, así que todo lo que decide algo se prueba con un JSON escrito a mano. Cubre el orden de las versiones, el rechazo de enlaces en claro o fuera de las releases, el tope de las notas, qué redirecciones se siguen y cuáles no, la ventana de un día, el reloj movido hacia atrás y que un fallo no gaste el día.

Lo único sin cubrir es el `HttpURLConnection` en sí, que no toma ninguna decisión.
