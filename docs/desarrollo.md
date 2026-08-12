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
./gradlew assembleRelease      # con minify y shrink de recursos
./gradlew clean
```

La variante `debug` lleva `applicationIdSuffix = ".debug"` y `versionNameSuffix = "-debug"`, así que convive con la de producción instalada.

`local.properties` (no versionado) debe apuntar al SDK de Android:

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

## Configuración notable del build

- **`localeFilters += listOf("es")`** — la app está escrita en español; no se empaquetan los recursos de las bibliotecas en los otros ochenta idiomas.
- **`buildConfig = true`** — la pantalla de Acerca de enseña `BuildConfig.VERSION_NAME`.
- **`room.schemaLocation`** — KSP escribe los esquemas en `app/schemas/`, que sí se versionan: son la referencia contra la que se prueban las migraciones.
- **`androidx.fragment` fijado a mano** — `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`: su `FragmentActivity` rechaza los request codes de más de 16 bits que genera `activity` 1.10.1 y cualquier launcher revienta al abrirse.
- **`release`** con `isMinifyEnabled` e `isShrinkResources`.

## Pruebas

Todas corren en la JVM, sin emulador. Robolectric para lo que necesita `Context`.

| Prueba | Qué cubre |
|---|---|
| [`RachasTest`](../app/src/test/java/mx/ollin/actividades/RachasTest.kt) | Rachas por día, por semana y por ciclos |
| [`TiempoTest`](../app/src/test/java/mx/ollin/actividades/TiempoTest.kt) | Formato de duración y cronómetro, redondeo de minutos, máscara de días, unidades |
| [`RepositorioTest`](../app/src/test/java/mx/ollin/actividades/RepositorioTest.kt) | Reglas de escritura del repositorio sobre una base en memoria |
| [`MigracionTest`](../app/src/test/java/mx/ollin/actividades/MigracionTest.kt) | La migración 1 → 2 contra el esquema real de la versión 1 |
| [`ExcelRoundTripTest`](../app/src/test/java/mx/ollin/actividades/ExcelRoundTripTest.kt) | Escritor y lector de `.xlsx` |
| [`ImportadorTest`](../app/src/test/java/mx/ollin/actividades/ImportadorTest.kt) | Exportar e importar deja los datos iguales |

Las pruebas con Room arrancan con una `Application` pelona en vez de `OllinApp`: la de verdad siembra el catálogo contra la base cifrada, y SQLCipher es una biblioteca nativa de Android que en la JVM no existe.

`app/src/test/resources/robolectric.properties` fija dos cosas:

- `sdk=34` — Robolectric todavía no trae imagen del 36 al que apunta la app, y la base no cambia de comportamiento entre esas versiones.
- `sqliteMode=NATIVE` — el SQLite emulado sirve una sola conexión por hilo, y Room reparte sus consultas entre varios hilos de disco. Con `LEGACY`, cualquier prueba que lea desde un `Flow` revienta con *"Illegal connection pointer"*.

## Convenciones del código

- **Todo en español**: nombres de clases, funciones, variables y comentarios. Los nombres de prueba van en backticks y en prosa (`` `un dia saltado si la rompe` ``).
- **Los comentarios explican el porqué, no el qué.** Si una decisión tiene una alternativa obvia que se descartó, el comentario dice por qué se descartó.
- **Sin acentos en los comentarios y literales del código** (la documentación de `docs/` sí los usa).
- **Una pantalla por archivo**, con su ViewModel arriba y los composables privados abajo.
- **La escritura pasa por el repositorio.** Las pantallas no tocan los DAO.
- Las cadenas visibles están en el código, no en `strings.xml`: la app es monolingüe por diseño. En `strings.xml` solo viven el nombre y el lema.

## Añadir una pantalla

1. Crea el archivo en `ui/screens/` con su `ViewModel` y su composable.
2. Declara la ruta en `ui/nav/Destinos.kt` (`Destino` si es pestaña, `Rutas` si cuelga de una).
3. Regístrala en el `NavHost` de `ui/OllinRaiz.kt`.
4. Si abre con tarjeta de ayuda, agrega la entrada a `Tutorial` en `ui/components/Tutoriales.kt`. Ojo: la clave se guarda en preferencias, así que renombrarla haría reaparecer una tarjeta que alguien ya había descartado. Los textos sí se pueden cambiar.

## Añadir una versión de la base

Ver el final de [modelo de datos](modelo-de-datos.md#migraciones).
