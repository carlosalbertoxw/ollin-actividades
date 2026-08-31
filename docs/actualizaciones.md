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

Lo publica el sitio en `https://carlosalbertoxw.github.io/ollin-actividades/version.json` y lo genera [`genera-datos.mjs`](../web/scripts/genera-datos.mjs). Ver [el sitio](sitio.md).

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
| `apk` | Sí, o `sitio` | Se usa `sitio`; sin ninguno de los dos, se descarta |
| `notas` | No | La tarjeta enseña solo el número de versión |
| `publicada`, `tamanoBytes`, `sha256`, `release` | No | Solo los usa la página web |

**Los nombres de los campos no se renombran, se agregan.** Los lee `ComprobadorActualizaciones.lee()`, y cambiar uno rompe el aviso de todas las versiones que ya están instaladas, que por definición no se pueden actualizar para arreglarlo.

### Solo `https`

Un enlace en claro que llegara desde fuera acabaría abriendo el navegador en una descarga manipulable por cualquiera que esté en medio de la red. Si `apk` no empieza por `https://`, se ignora y se cae a `sitio`; si tampoco, el archivo se descarta entero.

Por lo mismo la petición va con `instanceFollowRedirects = false`: una redirección desde `https` puede terminar en `http`, y entonces la respuesta viaja en claro. Y el manifiesto declara `usesCleartextTraffic="false"`, que lo prohíbe a nivel de plataforma.

## La descarga

`HttpURLConnection` del propio Android, sin OkHttp ni Retrofit: son megabytes y miles de métodos para una petición que ocurre una vez al día y devuelve un objeto de cinco campos.

Tiene un tope de 64 KB. El archivo real ronda los 400 bytes; el tope está porque es lo único que entra a la app desde la red, y sin límite un archivo enorme —o un servidor que nunca cierra la respuesta— agota la memoria del teléfono. Es el mismo razonamiento que con el `.xlsx`, ver [Excel](excel.md).

## Pruebas

[`ActualizacionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ActualizacionesTest.kt), en la JVM y sin red: la descarga entra por parámetro, así que todo lo que decide algo se prueba con un JSON escrito a mano. Cubre el orden de las versiones, el rechazo de enlaces en claro, la ventana de un día, el reloj movido hacia atrás y que un fallo no gaste el día.

Lo único sin cubrir es el `HttpURLConnection` en sí, que no toma ninguna decisión.
