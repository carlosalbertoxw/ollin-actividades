# Arquitectura

Módulo único (`:app`), Kotlin y Jetpack Compose. Tres capas delgadas y una regla: la escritura pasa siempre por el repositorio, y las pantallas nunca tocan los DAO.

```
Compose (pantallas + ViewModels)
        │  Flow / suspend
        ▼
ActividadesRepositorio  ──►  ExportadorExcel / ImportadorExcel
        │
        ▼
Room (OllinDatabase, cifrada con SQLCipher)
```

## Capas

### `ui/`

Una pantalla por archivo en `ui/screens/`, cada una con su `ViewModel` declarado en el mismo archivo. Los estados se exponen como `StateFlow` y se consumen con `collectAsStateWithLifecycle`.

No hay `Factory` por pantalla: [`recuerdaVm`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/Fabrica.kt) crea el ViewModel pasándole el `Contenedor` a mano.

```kotlin
val vm = recuerdaVm("analitica") { AnaliticaVm(contenedor) }
```

La navegación vive en [`OllinRaiz`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/OllinRaiz.kt), con un `NavHost` de Navigation Compose. Las cuatro pestañas inferiores son el enum [`Destino`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/nav/Destinos.kt) —Hoy, Registro, Hábitos, Analítica— y el resto de las rutas (captura, categorías, ajustes, archivo, acerca de) son constantes en `Rutas`. El botón flotante de captura se oculta en Hábitos, que tiene el suyo.

### `domain/`

Sin dependencias de Android. Contiene los enums del modelo ([`Ambito`, `EstadoActividad`, `Unidad`, `Frecuencia`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/model/Enums.kt)), las utilidades de calendario y reloj ([`Tiempo`, `DiasSemana`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/model/Tiempo.kt)) y el cálculo de [rachas](rachas.md).

### `data/`

- `db/` — Room. Las entidades son también el modelo de dominio: con un solo módulo, duplicarlas en otra capa solo agregaría mapeo. Ver [modelo de datos](modelo-de-datos.md).
- `repo/` — [`ActividadesRepositorio`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/repo/ActividadesRepositorio.kt). Único punto de escritura; ahí viven las reglas que mantienen coherentes inicio, fin, día y duración.
- `excel/` — lector y escritor de `.xlsx` propios, más el exportador e importador de la bitácora. Ver [Excel](excel.md).
- `prefs/` — preferencias en DataStore, expuestas como un `Flow<Ajustes>`.
- `seguridad/` — llave de la base, derivación del PIN y control de bloqueo. Ver [seguridad](seguridad.md).

## Inyección de dependencias

Manual, en [`Contenedor`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/di/Contenedor.kt): base de datos, repositorio, ajustes, control de bloqueo y sembrador, todos `by lazy`. Se construye una vez en [`OllinApp`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/OllinApp.kt) y se pasa por parámetro a las pantallas.

Con tres objetos compartidos, Hilt aportaría anotaciones y tiempo de compilación sin resolver ningún problema real.

`OllinApp` también siembra el catálogo inicial de categorías en un `CoroutineScope` de IO. El sembrador es idempotente: si ya hay categorías, no toca nada.

## Arranque y bloqueo

[`MainActivity`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/MainActivity.kt) es una `FragmentActivity` y no una `ComponentActivity`, porque el diálogo de huella y credencial del sistema se monta sobre el gestor de fragmentos.

El árbol que se compone depende de dos señales:

| Estado | Qué se dibuja |
|---|---|
| Preferencias sin leer (`null`) | Telón: fondo liso, nunca datos |
| Bloqueado y con modo de bloqueo activo | `BloqueoPantalla` |
| Bloqueado pero sin modo definido aún | Telón |
| Desbloqueado | `OllinRaiz` |

`BloqueoPantalla` **sustituye** al árbol de la app, no lo tapa: si fuera una capa encima, el contenido seguiría compuesto debajo y asomaría en la vista de apps recientes. Mientras hay candado configurado, la ventana lleva `FLAG_SECURE`.

## Flujo de datos

Las consultas de Room devuelven `Flow`, que los ViewModels transforman con `flatMapLatest` sobre el filtro o la ventana elegida y publican con `stateIn(SharingStarted.WhileSubscribed(5_000))`.

Las rachas se calculan en Kotlin y no en SQL porque dependen de qué días toca cada hábito, y eso vive en la plantilla del hábito; SQLite tendría que reconstruir el calendario.

## Dependencias principales

| Qué | Para qué |
|---|---|
| Compose BOM + Material 3 | Interfaz |
| Navigation Compose | Navegación |
| Room + KSP | Persistencia y esquemas exportados |
| SQLCipher (`net.zetetic:sqlcipher-android`) | Cifrado de la base |
| DataStore Preferences | Ajustes |
| AndroidX Biometric | Credencial del sistema |
| Robolectric + JUnit 4 | Pruebas en la JVM |

`androidx.fragment` se fija explícitamente en `libs.versions.toml`: `biometric` 1.1.0 arrastra `fragment` 1.2.5, anterior a la API de `ActivityResult`, y con esa versión cualquier launcher revienta al abrirse.
