# Historial de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y versionado según [SemVer](https://semver.org/lang/es/).

**Este archivo manda.** No es un resumen escrito después: es de donde salen las tres cosas que definen una publicación.

1. [`app/build.gradle.kts`](app/build.gradle.kts) lee de aquí el `versionName` y deriva el `versionCode`. No hay ningún número de versión escrito a mano en el build.
2. El flujo de [publicación](.github/workflows/publicacion.yml) toma la sección de la versión etiquetada y la usa como cuerpo de la release de GitHub.
3. El [sitio](docs/sitio.md) publica este historial y el `version.json` que consulta la app para avisar de una actualización.

Por eso un tag `v1.2.0` sin su `## [1.2.0]` aquí arriba **falla antes de compilar nada**. Es a propósito: una versión sin notas es una versión que nadie sabe si le conviene instalar.

Lo que todavía no se publica se va acumulando bajo `## [Sin publicar]`; al etiquetar, esa sección se renombra con el número y la fecha.

Los enlaces van con dirección completa: el mismo texto se lee en GitHub, en el sitio y en el cuerpo de la release, y una ruta relativa solo funcionaría en uno de los tres.

## [Sin publicar]

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

[Sin publicar]: https://github.com/carlosalbertoxw/ollin-actividades/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/carlosalbertoxw/ollin-actividades/releases/tag/v1.0.0
