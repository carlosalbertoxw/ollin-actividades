# Ollin Actividades

Bitácora personal de tiempo para Android: cronometra o captura a mano lo que haces, lleva hábitos con la cadencia que quieras y mira en qué se te fue la semana. Todo vive en el teléfono, en una base cifrada; no hay cuenta, nube ni publicidad.

*Ollin* es "movimiento" en náhuatl, el glifo mexica del cambio.

## Qué hace

- **Registro con cronómetro o a mano.** Una actividad se puede medir mientras pasa o anotarse después con los minutos que recuerdes. Solo puede haber un cronómetro corriendo: arrancar otro cierra el anterior.
- **Hábitos con rachas.** Diarios, en días elegidos de la semana, cierto número de veces por semana, o cada tantos días o meses. La racha del día en curso es de cortesía: un hábito sin marcar está pendiente, no fallado.
- **Analítica sobre lo completado.** Minutos por día, por categoría y por ámbito en ventanas de 7, 30 o 90 días. Lo pendiente no infla ninguna cifra.
- **Exportación e importación en Excel.** Un `.xlsx` con fórmulas vivas (SUMIFS, COUNTIFS), escrito y leído sin dependencias externas. Lo que sale puede volver a entrar.
- **Bloqueo opcional.** Con la credencial del teléfono (patrón, PIN, huella) o con un PIN propio de Ollin.

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

El APK queda en `app/build/outputs/apk/debug/`. La variante de depuración se instala junto a la de producción: usa el `applicationId` `mx.ollin.actividades.debug`.

Para instalar en un dispositivo conectado:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew installDebug
```

## Pruebas

Las pruebas unitarias corren en la JVM con Robolectric, sin emulador ni dispositivo:

```bash
JAVA_HOME="$HOME/.jdks/jbr-21.0.11" ./gradlew testDebugUnitTest
```

Cubren el cálculo de rachas, las reglas del repositorio, el formateo de tiempo, la migración de esquema de la base y el viaje de ida y vuelta a Excel. El reporte HTML queda en `app/build/reports/tests/`.

## Estructura

```
app/src/main/java/mx/ollin/actividades/
├── data/
│   ├── db/          Room: entidades, DAOs, proyecciones, migraciones, catálogo semilla
│   ├── excel/       Lector y escritor de .xlsx propios, exportador e importador
│   ├── prefs/       Preferencias en DataStore
│   ├── repo/        ActividadesRepositorio: toda la escritura pasa por aquí
│   └── seguridad/   Llave de la base, PIN, control de bloqueo, migración a cifrado
├── di/              Contenedor de dependencias, a mano
├── domain/
│   ├── model/       Enums, utilidades de tiempo y días de la semana
│   └── usecase/     Cálculo de rachas
└── ui/              Compose: pantallas, navegación, tema, componentes
```

## Documentación

- [Arquitectura](docs/arquitectura.md) — capas, flujo de datos, navegación y por qué no hay framework de inyección.
- [Modelo de datos](docs/modelo-de-datos.md) — tablas, invariantes de las marcas de tiempo, migraciones.
- [Rachas y hábitos](docs/rachas.md) — cadencias soportadas y cómo se cuenta cada tipo de racha.
- [Excel](docs/excel.md) — formato del libro exportado, hojas, esquemas y reglas de importación.
- [Seguridad y privacidad](docs/seguridad.md) — cifrado de la base, Keystore, PIN, bloqueo y respaldos.
- [Desarrollo](docs/desarrollo.md) — entorno, comandos, pruebas y convenciones del código.

## Privacidad

La bitácora no sale del teléfono. La base va cifrada con AES-256 (SQLCipher) y la frase se guarda envuelta con una llave del Keystore de Android, así que el archivo `.db` no dice nada fuera de este dispositivo. Por lo mismo el respaldo automático del sistema está desactivado para la base: una llave del Keystore no se restaura en otro teléfono. **El respaldo real es la exportación a `.xlsx`.**
