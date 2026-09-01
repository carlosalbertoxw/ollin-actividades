# Desarrollo

## Entorno

| Pieza | Versión |
|---|---|
| Gradle wrapper | 8.14.5 |
| Android Gradle Plugin | 8.10.0 |
| Kotlin / KSP | 2.1.20 / 2.1.20-2.0.1 |
| Compose BOM | 2025.04.01 |
| Room | 2.7.1 |
| JDK del proyecto | 17 (`sourceCompatibility`, `jvmTarget`) |
| JDK para correr Gradle | **21** |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Node (solo para el sitio) | 22 |
| Vite | 6 |

### El JDK de Gradle

Gradle 8.14.5 no sabe interpretar las versiones 25 y 26 de Java y falla al compilar `build.gradle.kts` con `IllegalArgumentException`, antes de tocar una sola línea de código fuente. Hay que apuntar `JAVA_HOME` a un JDK 21:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew tasks
```

Android Studio usa su propio ajuste de *Gradle JDK* y no se ve afectado.

## Comandos

```bash
./gradlew assembleDebug        # APK de depuración
./gradlew installDebug         # instala en el dispositivo conectado
./gradlew testDebugUnitTest    # pruebas unitarias (JVM + Robolectric)
./gradlew lintDebug            # Android Lint; falla la compilacion si encuentra errores
./gradlew assembleRelease      # con minify y shrink de recursos
./gradlew clean
```

El [sitio](sitio.md) es un proyecto aparte, en `web/`, y no pasa por Gradle:

```bash
cd web && npm install && npm run dev
```

El estilo está en [`.editorconfig`](../.editorconfig) —el oficial de Kotlin, que ya declaraba `gradle.properties`— para que no dependa de la memoria de quien edite. Lint corre con `abortOnError`: un aviso que no rompe nada no se lee, y lo que marca como error son fugas de contexto, APIs por encima del `minSdk` o permisos ausentes, cosas que se notarían en el teléfono de alguien. Las quejas por traducciones ausentes están apagadas: la app es monolingüe por decisión explícita.

La variante `debug` lleva `applicationIdSuffix = ".debug"` y `versionNameSuffix = "-debug"`, así que convive con la de producción instalada.

`local.properties` (no versionado) debe apuntar al SDK de Android:

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

## Configuración notable del build

- **`localeFilters += listOf("es")`** — la app está escrita en español; no se empaquetan los recursos de las bibliotecas en los otros ochenta idiomas.
- **La versión sale de `CHANGELOG.md`** — `app/build.gradle.kts` lee el primer encabezado `## [x.y.z]` y de ahí saca el `versionName`; el `versionCode` se deriva con tres huecos de dos cifras (`1.2.3` → `10203`). No hay ningún número de versión escrito a mano. Ver [publicación](publicacion.md#el-changelog-manda).
- **`buildConfig = true`** — la pantalla de Acerca de enseña `BuildConfig.VERSION_NAME`, y de `BuildConfig.URL_ACTUALIZACIONES` sale la dirección que se consulta para saber si hay una versión más nueva. Se puede apuntar a otro sitio con `-Pollin.urlActualizaciones=…`.
- **`room.schemaLocation`** — KSP escribe los esquemas en `app/schemas/`, que sí se versionan: son la referencia contra la que se prueban las migraciones. Los mismos archivos se montan como assets de la suite instrumentada (`sourceSets.getByName("androidTest").assets.srcDir(...)`), que es de donde los lee `MigrationTestHelper`.
- **`androidx.fragment` fijado a mano** — `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`: su `FragmentActivity` rechaza los request codes de más de 16 bits que genera `activity` 1.10.1 y cualquier launcher revienta al abrirse.
- **`release`** con `isMinifyEnabled` e `isShrinkResources`, y firmado con las credenciales de [`keystore.properties`](publicacion.md). Sin ese archivo el APK sale sin firmar en vez de fallar la compilación.

## Pruebas

Hay dos suites y no corren en el mismo sitio.

### Unitarias — en la JVM

```bash
./gradlew testDebugUnitTest
```

Sin emulador. Robolectric para lo que necesita `Context`.

| Prueba | Qué cubre |
|---|---|
| [`RachasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/RachasTest.kt) | Rachas por día, por semana y por ciclos |
| [`TiempoTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/TiempoTest.kt) | Formato de duración y cronómetro, redondeo de minutos, máscara de días, unidades |
| [`RepositorioTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/RepositorioTest.kt) | Reglas de escritura del repositorio sobre una base en memoria |
| [`SembradorTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/SembradorTest.kt) | El catálogo semilla y su idempotencia |
| [`ClavePinTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ClavePinTest.kt) | Derivación del PIN: sal, determinismo y comparación |
| [`BloqueoTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/BloqueoTest.kt) | Preferencias del candado y la gracia de `ControlBloqueo`: solo para el viaje al selector, y se gasta al usarla |
| [`AjustesRepositorioTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/AjustesRepositorioTest.kt) | Valores de fábrica, acotado de metas y selección de hojas |
| [`ExcelRoundTripTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ExcelRoundTripTest.kt) | Escritor y lector de `.xlsx` |
| [`ImportadorTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ImportadorTest.kt) | La hoja de Registros: exportar e importar deja los datos iguales |
| [`ImportadorCatalogosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ImportadorCatalogosTest.kt) | Las pestañas de Categorias, Habitos y Diccionarios, y la cadencia de ida y vuelta |
| [`XlsxLectorBlindajeTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/XlsxLectorBlindajeTest.kt) | Que el lector siga abriendo libros aunque el SAX del sistema rechace las banderas de seguridad |
| [`RecordatoriosTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/RecordatoriosTest.kt) | Qué avisa el planificador y qué no: hora, cadencia, meta diaria, hábito cumplido o pausado, tareas pendientes |
| [`ActualizacionesTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/ActualizacionesTest.kt) | Orden de las versiones, lectura del `version.json`, la ventana de un día y el reloj movido hacia atrás |
| [`EsquemaTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/EsquemaTest.kt) | Que la versión de la base, sus esquemas exportados y sus migraciones cuadren |
| [`PreferenciasHeredadasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/PreferenciasHeredadasTest.kt) | Que lo que dejó escrito una versión anterior se siga leyendo, y que una clave con el tipo equivocado se trate como ausente en vez de cerrar la app |

Las pruebas con Room arrancan con una `Application` pelona en vez de `OllinApp`: la de verdad siembra el catálogo contra la base cifrada, y SQLCipher es una biblioteca nativa de Android que en la JVM no existe.

`app/src/test/resources/robolectric.properties` fija dos cosas:

- `sdk=34` — Robolectric todavía no trae imagen del 36 al que apunta la app, y la base no cambia de comportamiento entre esas versiones.
- `sqliteMode=NATIVE` — el SQLite emulado sirve una sola conexión por hilo, y Room reparte sus consultas entre varios hilos de disco. Con `LEGACY`, cualquier prueba que lea desde un `Flow` revienta con *"Illegal connection pointer"*.

### De interfaz — sobre un teléfono o un emulador

```bash
./gradlew connectedDebugAndroidTest
```

Viven en `app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/`, **no** en `src/test/`, así que `testDebugUnitTest` no las corre. Se intentaron en la JVM con Robolectric y no salió: su reloj virtual no conversa con el cronómetro de la pantalla de hoy ni con los diálogos.

| Prueba | Qué cubre |
|---|---|
| [`NavegacionTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/NavegacionTest.kt) | Que cada pantalla siga siendo alcanzable con el dedo |
| [`HoyPantallaTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/HoyPantallaTest.kt) | Cronómetro, pendientes y marcado de hábitos, de la pulsación a la base |
| [`CapturaPantallaTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/CapturaPantallaTest.kt) | Validación del título, alta, edición y borrado |
| [`HabitosPantallaTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/HabitosPantallaTest.kt) | Alta con cadencia, pausa y reanudación, borrado |
| [`AjustesYArchivoTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/AjustesYArchivoTest.kt) | Que lo que se toca en Ajustes y Archivo quede escrito en preferencias |
| [`MigracionesTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/db/MigracionesTest.kt) | Que el SQL de cada migración deje la base como Room la espera |

`MigracionesTest` no es de interfaz y no vive en `ui/`: necesita un emulador por otra razón —la validación usa el SQLite del sistema—. Es la única de esta carpeta que **bloquea una publicación**; ver [modelo de datos](modelo-de-datos.md#versiones-y-migraciones).

[`BancoDePruebas`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/ui/BancoDePruebas.kt) es el andamio común. Levanta un `Contenedor` de verdad —mismos ViewModel, mismo repositorio, mismas preferencias— y solo sustituye la base por una en memoria, por la costura `abreBase` del contenedor: la de producción va cifrada y compartirla entre pruebas dejaría a cada una heredando los datos de la anterior. Dos detalles que costaron encontrarse:

- **`espera(...)` en vez de `waitUntil`.** La condición no siempre es lo que hay en pantalla: muchas veces es lo que quedó escrito en la base o en las preferencias, y eso ocurre en hilos que el reloj de Compose no mueve. Se sondea en tiempo real y se cierra con un `waitForIdle`, porque que un texto ya se vea no significa que haya terminado de animarse.
- **`restauraDeFabrica()` antes de cada prueba.** El DataStore de la app es uno solo y sobrevive de una prueba a la siguiente; sin esto, lo que una guarda condiciona a la que corra después y el resultado de la suite depende del orden.

## Integración continua

Cuatro flujos, en [`.github/workflows/`](../.github/workflows/):

| Flujo | Cuándo | Qué hace |
|---|---|---|
| [`pruebas.yml`](../.github/workflows/pruebas.yml) | Push y PR a `main` | Pruebas unitarias, Lint, compila la suite instrumentada sin ejecutarla, `assembleRelease` y el sitio |
| [`publicacion.yml`](../.github/workflows/publicacion.yml) | Tag `v*` | Valida la etiqueta contra el `CHANGELOG`, invoca `pruebas.yml`, corre `MigracionesTest`, firma, publica la release y despliega el sitio |
| [`sitio.yml`](../.github/workflows/sitio.yml) | Cambios en `web/`, a mano, y al publicar | Compila el sitio y lo despliega en GitHub Pages |
| [`pruebas-instrumentadas.yml`](../.github/workflows/pruebas-instrumentadas.yml) | Lunes y a mano | La suite de interfaz completa, en emuladores API 26 y 34 |
| [`actualizacion.yml`](../.github/workflows/actualizacion.yml) | Al etiquetar, lunes y a mano | Instala la versión nueva sobre la anterior y comprueba que abre |

Tres decisiones que explican el reparto:

- **`assembleRelease` corre en cada push.** R8 y el shrink de recursos rompen cosas que la compilación de depuración no enseña, y descubrirlo al etiquetar es descubrirlo tarde.
- **`publicacion.yml` invoca `pruebas.yml`, no lo copia.** Una etiqueta no puede pasar por una comprobación más floja que un pull request cualquiera, y dos copias de los mismos pasos divergen.
- **Las pruebas de interfaz no bloquean nada.** Dependen de animaciones, diálogos y relojes; su intermitencia acabaría enseñando a ignorar el aspa roja, que es peor que no tenerlas. Van una vez por semana. Las de migración sí bloquean, por la razón contraria: un fallo ahí no se puede arreglar desde fuera.

Los secretos de firma y el proceso completo, en [publicación](publicacion.md).

### El bit de ejecución, si desarrollas en Windows

Git en Windows corre con `core.filemode = false` y guarda todo como `100644`, sin permiso de ejecución. Da igual en Windows y rompe en el runner, que es Linux: `./gradlew` responde *Permission denied* y el flujo se cae antes de compilar nada.

`gradlew` ya está marcado como `100755` en el índice. Cualquier script que se agregue desde Windows y tenga que ejecutarse en CI necesita lo mismo:

```bash
git update-index --chmod=+x <archivo>
```

`gradlew.bat` no: es un `.bat` y nunca se ejecuta en Linux.

## Convenciones del código

- **Todo en español**: nombres de clases, funciones, variables y comentarios. Los nombres de prueba van en backticks y en prosa (`` `un dia saltado si la rompe` ``).
- **Los comentarios explican el porqué, no el qué.** Si una decisión tiene una alternativa obvia que se descartó, el comentario dice por qué se descartó.
- **Sin acentos en los comentarios**; el texto que ve el usuario sí va acentuado. La excepción son los nombres de hoja y los encabezados de columna del `.xlsx`: esos son el formato del archivo, no copy, y cambiarlos rompería la reimportación de libros ya exportados.
- **Una pantalla por archivo** en `ui/screens/`, con los composables privados abajo, y su ViewModel en el archivo hermano `XxxVm.kt`.
- **El ViewModel recibe sus colaboradores** (`repositorio`, `ajustes`), no el `Contenedor` entero.
- **La escritura pasa por el repositorio.** Las pantallas no tocan los DAO, y lo que borra en bloque va en transacción.
- Las cadenas visibles están en el código, no en `strings.xml`: la app es monolingüe por diseño. En `strings.xml` solo viven el nombre y el lema.

## Añadir una pantalla

1. Crea `ui/screens/XxxPantalla.kt` con el composable y `ui/screens/XxxVm.kt` con su ViewModel.
2. Declara la ruta en `ui/nav/Destinos.kt` (`Destino` si es pestaña, `Rutas` si cuelga de una).
3. Regístrala en el `NavHost` de `ui/OllinRaiz.kt`.
4. Si abre con tarjeta de ayuda, agrega la entrada a `Tutorial` en `ui/components/Tutoriales.kt`. Ojo: la clave se guarda en preferencias, así que renombrarla haría reaparecer una tarjeta que alguien ya había descartado. Los textos sí se pueden cambiar.

## Añadir una versión de la base

La versión y sus migraciones viven juntas en `Migraciones.kt`. Los cinco pasos, en [modelo de datos](modelo-de-datos.md#agregar-una-versión).

## Publicar una versión

Renombrar `## [Sin publicar]` en el `CHANGELOG` con el número y la fecha, empujar a `main` y etiquetar con `v<version>`. El resto lo hace el flujo. Ver [publicación](publicacion.md).
