# Historial de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado según [SemVer](https://semver.org/lang/es/).

**Este archivo manda.** No es un resumen escrito después: es de donde salen las tres cosas que definen una publicación.

1. [`app/build.gradle.kts`](app/build.gradle.kts) lee de aquí el `versionName` y deriva el `versionCode`. No hay ningún número de versión escrito a mano en el build.
2. El flujo de [publicación](.github/workflows/publicacion.yml) toma la sección de la versión etiquetada y la usa como cuerpo de la release de GitHub.
3. El [sitio](docs/sitio.md) publica este historial y el `version.json` que consulta la app para avisar de una actualización.

Por eso un tag `v1.2.0` sin su `## [1.2.0]` aquí arriba **falla antes de compilar nada**. Es a propósito: una versión sin notas es una versión que nadie sabe si le conviene instalar.

Lo que todavía no se publica se va acumulando bajo `## [Sin publicar]

### Añadido

- **Los hábitos periódicos eligen qué pasa si los haces tarde.** Hasta ahora el calendario era siempre fijo: uno cada quince días anclado al 1 tocaba el 16 y el 31, y hacerlo el 20 no movía nada — el siguiente seguía siendo el 31, once días después. Ahora cada hábito elige entre **fechas fijas**, que es lo de antes y sigue siendo lo de fábrica, y **desde que lo hice**, donde el intervalo vuelve a empezar en cada cumplimiento. Lo primero es para la renta del día 1; lo segundo, para cambiar el filtro cada quince días. El modo viaja en el `.xlsx`, en la columna *Si se hace tarde* de la pestaña *Habitos*.
- **Lo vencido se queda a la vista.** Un hábito periódico que tocó y no se hizo sigue apareciendo en Hoy y en la lista, con la fecha en que tocaba, hasta que se haga o llegue la siguiente ocurrencia. Antes solo era cierto el día exacto, así que uno cada tres meses que se pasaba un día no volvía a asomar en tres meses: no se fallaba, se perdía de vista. Los recordatorios siguen avisando **solo el día que toca**, para que un olvido no se convierta en una campana diaria.
- Una prueba de actualización sobre emulador: instala la versión de la etiqueta anterior, la abre para que escriba sus preferencias, instala la nueva encima sin desinstalar y comprueba que sigue abriéndose. **Bloquea la publicación**, junto a las migraciones.
- `EsquemaTest` vigila dos cosas que antes no vigilaba nadie: que la versión del esquema **nunca retroceda**, y que **una versión que ya salió no cambie de forma**. Son las dos caras del fallo de abajo.

### Arreglado

- **La app se cerraba al abrirse en los teléfonos que venían de una compilación de desarrollo.** El esquema de la base había usado la versión 2 antes de la primera publicación, y al preparar la 1.0.0 se bajó a 1 porque no había nada publicado que migrar. No había nada publicado, pero sí teléfonos con una base marcada como 2: para esos, instalar la 1.0.0 o la 1.0.1 era un downgrade, y Room se niega a abrir una base más nueva que la app. La versión volvió a subir, con una migración de la 1 a la 2 que no hace nada porque las dos describen el mismo esquema. Nadie pierde datos y las dos procedencias abren.
- **Un fallo al arrancar ya no mata la app en silencio.** La excepción salía del `launch` de `OllinApp` y el proceso desaparecía sin diálogo ni mensaje. Ahora se atrapa y se enseña una pantalla que dice qué pasó, que los datos siguen intactos y qué hacer.

### Cambiado

- Las lecturas de preferencias comprueban el tipo en tiempo de ejecución y tratan como ausente lo que no cuadre. Ninguna clave ha cambiado de tipo aquí, así que no había nada roto: es la red que le faltó a [Ollin Finanzas](https://github.com/carlosalbertoxw/ollin-finanzas), donde el mismo descuido cerró la app al arrancar en los teléfonos que venían de la versión anterior. La regla sigue siendo que una clave no cambia de tipo nunca.

## [1.0.1] - 2026-08-30

### Arreglado

- **El aviso de actualizaciones no llegaba a preguntar nada.** La dirección que la app consulta va compilada dentro del APK, y al poner un dominio propio delante de GitHub Pages el `.github.io` empezó a responder con un 301. La app no seguía redirecciones —para que un salto no pudiera acabar en `http`— y trataba cualquier respuesta que no fuera 200 como un fallo, así que la 1.0.0 nunca se entera de que hay versión nueva. Ahora se sigue **un** salto, y solo si el destino también es `https`.
- El campo `sitio` del `version.json` salía como `http://` mientras «Enforce HTTPS» estuviera sin activar en Pages. Se fuerza a `https` al generarlo.

## [1.0.0] - 2026-08-30

Primera versión pública.

### Añadido

- **Registro con cronómetro o a mano.** Una actividad se puede medir mientras pasa o anotarse después con los minutos que recuerdes. Solo puede haber un cronómetro corriendo: arrancar otro cierra el anterior.
- **Hábitos con rachas.** Cadencia diaria, en días elegidos de la semana, cierto número de veces por semana, o cada tantos días o meses contando desde un ancla. La racha del día en curso es de cortesía: un hábito sin marcar está pendiente, no fallado.
- **Analítica sobre lo completado.** Minutos por día, por categoría y por ámbito en ventanas de 7, 30 o 90 días. Lo pendiente no infla ninguna cifra.
- **Exportación e importación en Excel.** Un `.xlsx` con fórmulas vivas (SUMIFS, COUNTIFS), escrito y leído sin dependencias externas. Lo que sale puede volver a entrar, catálogos incluidos.
- **Recordatorios.** Un aviso por cada hábito que toque y no hayas cumplido, a la hora que le pongas, y por cada tarea pendiente a su hora de inicio. Nacen apagados.
- **Bloqueo opcional.** Con la credencial del teléfono (patrón, PIN, huella) o con un PIN propio de Ollin, con espera creciente ante los intentos fallidos.
- **Base cifrada.** AES-256 con SQLCipher y la frase envuelta en el Keystore de Android. No hay camino sin cifrar.
- **Aviso de actualizaciones.** Ollin consulta una vez al día si hay una versión más nueva publicada y lo enseña en *Acerca de*. Se apaga en Ajustes. Ver [seguridad y privacidad](https://github.com/carlosalbertoxw/ollin-actividades/blob/main/docs/seguridad.md).
- **Sitio de descarga** en GitHub Pages, con el APK firmado, su huella y las instrucciones de instalación fuera de la tienda.

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-actividades/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/carlosalbertoxw/ollin-actividades/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/carlosalbertoxw/ollin-actividades/releases/tag/v1.0.0
