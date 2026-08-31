# Ollin Actividades

Bitácora personal de tiempo para Android: cronometra o captura a mano lo que haces, lleva hábitos con la cadencia que quieras y mira en qué se te fue la semana. Todo vive en el teléfono, en una base cifrada; no hay cuenta, nube ni publicidad.

*Ollin* es "movimiento" en náhuatl, el glifo mexica del cambio.

**[Descargar el APK](https://carlosalbertoxw.github.io/ollin-actividades/)** · [Historial de cambios](CHANGELOG.md) · [Todas las versiones](https://github.com/carlosalbertoxw/ollin-actividades/releases)

Ollin no está en ninguna tienda. Se instala desde el sitio, que publica la huella SHA-256 de cada versión para que se pueda comprobar. La app avisa sola cuando sale una nueva.

## Qué hace

- **Registro con cronómetro o a mano.** Una actividad se puede medir mientras pasa o anotarse después con los minutos que recuerdes. Solo puede haber un cronómetro corriendo: arrancar otro cierra el anterior.
- **Hábitos con rachas.** Diarios, en días elegidos de la semana, cierto número de veces por semana, o cada tantos días o meses. La racha del día en curso es de cortesía: un hábito sin marcar está pendiente, no fallado.
- **Analítica sobre lo completado.** Minutos por día, por categoría y por ámbito en ventanas de 7, 30 o 90 días. Lo pendiente no infla ninguna cifra.
- **Exportación e importación en Excel.** Un `.xlsx` con fórmulas vivas (SUMIFS, COUNTIFS), escrito y leído sin dependencias externas. Lo que sale puede volver a entrar.
- **Recordatorios.** Un aviso por cada hábito que toque y no hayas cumplido, a la hora que le pongas, y por cada tarea pendiente a su hora de inicio. Nacen apagados.
- **Bloqueo opcional.** Con la credencial del teléfono (patrón, PIN, huella) o con un PIN propio de Ollin.
- **Aviso de versiones nuevas.** Una vez al día Ollin pregunta al sitio si salió algo más reciente. Es lo único que usa la red, no lleva nada tuyo dentro y se apaga en Ajustes.

## Requisitos

- **JDK 21.** Gradle 8.14.5 no compila los scripts de Kotlin DSL con JDK 25/26; el error aparece antes de tocar el código fuente.
- **Android SDK 36** (`compileSdk`/`targetSdk` 36). La app corre desde **Android 8.0 (API 26)**.
- Android Studio reciente, o el wrapper de Gradle incluido.

La ruta del SDK va en `local.properties` (no versionado):

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

## Compilar

Desde Android Studio basta con abrir el proyecto. Desde la terminal, apuntando `JAVA_HOME` a un JDK 21:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. La variante de depuración se instala junto a la de producción: usa el `applicationId` `com.carlosalbertoxw.ollin.actividades.debug`.

Para instalar en un dispositivo conectado:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew installDebug
```

## Pruebas

Las pruebas unitarias corren en la JVM con Robolectric, sin emulador ni dispositivo:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest
```

Cubren el cálculo de rachas, las reglas del repositorio, el formateo de tiempo, el candado y el PIN, el catálogo semilla, el planificador de recordatorios, el aviso de actualizaciones, la contabilidad del esquema de la base y el viaje de ida y vuelta a Excel. El reporte HTML queda en `app/build/reports/tests/`.

Las de interfaz, hechas con Compose UI Test, recorren las pantallas de principio a fin y sí necesitan un teléfono o un emulador conectado:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew connectedDebugAndroidTest
```

En cada push a `main` corren las unitarias, Lint y `assembleRelease`; la suite de interfaz va los lunes, y las pruebas de migración bloquean cualquier publicación. Ver [desarrollo](docs/desarrollo.md#integración-continua).

## El sitio

La página de descarga vive en [`web/`](web/), hecha con Vite y publicada en GitHub Pages desde este mismo repositorio:

```bash
cd web && npm install && npm run dev
```

Queda en `http://localhost:5173/ollin-actividades/`, **con la ruta**: `base` lleva el nombre del repositorio porque Pages no sirve desde la raíz. Ver [el sitio](docs/sitio.md).

## Publicar

La versión sale de [`CHANGELOG.md`](CHANGELOG.md): el build lee de ahí el `versionName` y deriva el `versionCode`, y el flujo de publicación toma de ahí las notas de la release. Publicar es renombrar la sección `[Sin publicar]` y empujar una etiqueta:

```bash
git tag -a v1.1.0 -m "Ollin Actividades 1.1.0" && git push origin v1.1.0
```

Ver [publicación](docs/publicacion.md).

## Estructura

```
app/src/main/java/com/carlosalbertoxw/ollin/actividades/
├── data/
│   ├── db/             Room: entidades, DAOs, proyecciones, catálogo semilla
│   ├── excel/          Lector y escritor de .xlsx propios, exportador e importador
│   ├── actualizaciones/ Aviso de versiones nuevas: lo único que usa la red
│   ├── prefs/          Preferencias en DataStore
│   ├── recordatorios/  Planificador de avisos, alarma del sistema y notificaciones
│   ├── repo/           ActividadesRepositorio: toda la escritura pasa por aquí
│   └── seguridad/      Llave de la base, PIN, control de bloqueo
├── di/                 Contenedor de dependencias, a mano
├── domain/
│   ├── model/          Enums, utilidades de tiempo y días de la semana
│   └── usecase/        Cálculo de rachas
└── ui/                 Compose: pantallas y sus ViewModels, navegación, tema, componentes
```

## Documentación

- [Arquitectura](docs/arquitectura.md) — capas, flujo de datos, navegación y por qué no hay framework de inyección.
- [Modelo de datos](docs/modelo-de-datos.md) — tablas, invariantes de las marcas de tiempo, versiones del esquema.
- [Rachas y hábitos](docs/rachas.md) — cadencias soportadas y cómo se cuenta cada tipo de racha.
- [Recordatorios](docs/recordatorios.md) — qué avisa, cómo se programan las alarmas y qué permisos hacen falta.
- [Actualizaciones](docs/actualizaciones.md) — qué se consulta, qué no sale del teléfono y el contrato del `version.json`.
- [Excel](docs/excel.md) — formato del libro exportado, hojas, esquemas y reglas de importación.
- [Seguridad y privacidad](docs/seguridad.md) — cifrado de la base, Keystore, PIN, bloqueo y respaldos.
- [Desarrollo](docs/desarrollo.md) — entorno, comandos, pruebas y convenciones del código.
- [Publicación](docs/publicacion.md) — el CHANGELOG como fuente de la versión, los flujos de CI, el keystore y la firma.
- [El sitio](docs/sitio.md) — la página de descarga con Vite, GitHub Pages y de dónde salen sus datos.

## Privacidad

La bitácora no sale del teléfono. La base va cifrada con AES-256 (SQLCipher) y la frase se guarda envuelta con una llave del Keystore de Android, así que el archivo `.db` no dice nada fuera de este dispositivo. Por lo mismo el respaldo automático del sistema está desactivado para la base: una llave del Keystore no se restaura en otro teléfono. **El respaldo real es la exportación a `.xlsx`.**

La única vez que Ollin usa la red es para preguntar una vez al día si hay una versión más nueva. Esa petición no lleva identificador, ni la versión instalada —la comparación ocurre en el teléfono— ni nada de la bitácora, y se apaga en Ajustes. No hay cuenta, ni nube, ni publicidad, ni analítica. Ver [seguridad y privacidad](docs/seguridad.md).
