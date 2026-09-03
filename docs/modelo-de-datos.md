# Modelo de datos

Room sobre SQLite cifrado. Tres tablas, versión de esquema **3**, esquemas exportados y versionados en `app/schemas/`.

Las entidades de Room son también el modelo de dominio: [`Entidades.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/db/Entidades.kt).

## Tablas

### `categoria`

El catálogo con el que se clasifica la bitácora. Índice único por `nombre`.

| Columna | Tipo | Notas |
|---|---|---|
| `id` | Long | Autogenerado |
| `nombre` | String | Único |
| `ambito` | `Ambito` | Decide en qué analítica suma y qué icono lleva |
| `colorHex` | String? | `#RRGGBB` |
| `archivada` | Boolean | Se conserva pero deja de ofrecerse |
| `orden` | Int | Orden manual en las listas |

El ámbito (`TRABAJO`, `FISICO`, `HABITO`, `PERSONAL`) es el lente con el que se mira un registro: permite que una sola tabla sirva de bitácora de trabajo, monitor de ejercicio y tracker de hábitos sin tres modelos paralelos.

Una instalación nueva arranca con 16 categorías de ejemplo ([`Semilla.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/db/Semilla.kt)), renombrables y borrables sin consecuencias. Una app de registro que abre vacía obliga a inventar la taxonomía antes de poder anotar nada.

### `habito`

La plantilla de un hábito. **No guarda cumplimientos**: un hábito cumplido es una actividad completada que apunta aquí. Así la racha y el historial salen de la misma tabla que todo lo demás.

| Columna | Notas |
|---|---|
| `nombre` | Único |
| `categoriaId` | FK a `categoria`, `SET NULL` al borrar |
| `frecuencia` | `DIARIA`, `DIAS_ELEGIDOS`, `SEMANAL`, `CADA_DIAS`, `CADA_MESES` |
| `metaDiaria` | Veces al día que cuentan como cumplido |
| `metaSemanal` | Solo para `SEMANAL` |
| `diasSemana` | Mapa de bits; lunes = bit 0 |
| `intervaloDias` / `intervaloMeses` | Solo para las cadencias periódicas |
| `ancla` | Día desde el que se cuenta el ciclo. Nulo = el día en que se creó |
| `modoCiclo` | Si el siguiente ciclo cuenta desde la fecha fija o desde el último cumplimiento. Solo para las periódicas |
| `minutosSugeridos` | Duración que propone la pantalla de hoy |
| `horaRecordatorio` | Hora local a la que avisar los días que toca. Nulo = no avisa |
| `activo`, `orden`, `notas`, `creadoEn` | |

La entidad resuelve por sí misma `tocaHoy()`, `ocurrencia()` y `cadencia()` (el texto legible). La cadencia vive en la entidad porque la lista de hábitos y la exportación a Excel la escriben igual, y dos copias acabarían contradiciéndose.

### `actividad`

El registro. Es la única tabla que crece con el uso diario. Índices en `dia`, `inicio`, `estado`, `categoriaId` y `habitoId`.

| Columna | Notas |
|---|---|
| `titulo` | La acción concreta |
| `categoriaId`, `habitoId` | FK con `SET NULL` |
| `estado` | `PENDIENTE`, `EN_CURSO`, `COMPLETADO` |
| `inicio` | Instante UTC |
| `fin` | Nulo mientras corre o si solo está agendada |
| `dia` | Día **local** de `inicio`. Derivado, nunca se captura |
| `duracionMinutos` | Minutos de esfuerzo, ya calculados |
| `cantidad` + `unidad` | Medida opcional que el reloj no ve (km, repeticiones, páginas…) |
| `notas`, `creadoEn`, `actualizadoEn` | |

## Las marcas de tiempo

La regla de la casa, en [`Tiempo`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/model/Tiempo.kt): el instante se guarda en UTC porque es un punto en la línea del tiempo y no debe moverse al viajar; el día se guarda aparte como día epoch local porque "cuánto trabajé el martes" es una pregunta del calendario de quien la hace.

`inicio`, `dia` y `duracionMinutos` se guardan los tres aunque uno se derive de los otros: agrupar por día local en SQL exigiría aplicar el huso en cada consulta, y recalcular la duración en cada suma impide usar un índice. Quien escribe paga una vez lo que quien lee pagaría siempre.

### Invariantes que impone el repositorio

Toda escritura pasa por `ActividadesRepositorio.guarda()`, que normaliza antes de tocar la base:

- El `dia` siempre se deriva de `inicio`.
- `EN_CURSO` → sin `fin` y sin `duracionMinutos`.
- `COMPLETADO` con `fin` → manda el reloj y la duración se recalcula.
- `COMPLETADO` sin `fin` → se deduce el `fin` a partir de la duración capturada.
- Nunca se escribe una actividad completada sin duración, porque toda la analítica suma esa columna.

Los minutos entre dos instantes se redondean al más cercano, no se truncan: el redondeo reparte el error en vez de sesgarlo hacia abajo.

Solo puede haber una actividad `EN_CURSO`. Arrancar el cronómetro cierra la anterior con la hora de ese momento; dos cronómetros a la vez producirían más minutos de los que tiene el día.

## Proyecciones

[`Proyecciones.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/db/Proyecciones.kt) declara lo que devuelven las consultas con join o agregación. Son de solo lectura: nadie las inserta ni las modifica.

`ActividadDetallada` (actividad + nombre, color y ámbito de su categoría + nombre de su hábito), `TotalCategoria`, `TotalDia`, `TotalAmbito`, `ConteoEstado`, `CumplimientoDia` y `CumplimientoHabito`.

## Convertidores

[`Convertidores.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/db/Convertidores.kt) traduce `Instant` (milisegundos epoch), `LocalDate` (día epoch), `LocalTime` (segundo del día) y los enums (por nombre) a columnas de SQLite.

`horaRecordatorio` es una hora suelta y no un instante a propósito: «a las ocho» son las ocho de donde estés. Guardar el instante ataría el recordatorio al huso en que se creó y sonaría a las tres de la madrugada después de un vuelo.

## Versiones y migraciones

La versión y el camino para llegar a ella viven juntos, en [`Migraciones.kt`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/db/Migraciones.kt):

```kotlin
object Migraciones {
    const val VERSION = 3
    val TODAS: Array<Migration> = arrayOf(SIN_CAMBIOS_1_2, MODO_DE_CICLO_2_3)
}
```

`OllinDatabase` declara `version = Migraciones.VERSION` y registra `addMigrations(*Migraciones.TODAS)`. Están en el mismo archivo a propósito: subir un número en un sitio y olvidar el paso que lo acompaña en otro es el error que deja la app sin abrir en el teléfono de alguien, y aquí las dos cosas están a tres líneas de distancia.

### Un número de versión usado está quemado

Room guarda la versión **dentro del archivo** de la base. En cuanto un APK con la versión N se instala en un teléfono —el de quien desarrolla incluido—, ese teléfono tiene una base marcada con N para siempre. Bajar la versión después no la libera: convierte cada una de esas instalaciones en un *downgrade*, y Room se niega a abrir una base más nueva que la app.

| Versión | Quién la usó |
|---|---|
| 1 | El primer esquema, y el de las publicadas 1.0.0 y 1.0.1 |
| 2 | `habito.horaRecordatorio` en compilaciones de desarrollo; luego, **la misma forma** que la 1 |
| 3 | `habito.modoCiclo`: desde dónde recuenta una cadencia periódica |

La 2 nació agregando `horaRecordatorio` sobre una 1 que no la tenía. Al preparar la primera publicación se replegó todo a la versión 1 —con la columna ya dentro del `CREATE TABLE` inicial— porque no había nada publicado que migrar. El razonamiento tenía un agujero: no había nada *publicado*, pero sí teléfonos de desarrollo con una base marcada como 2, y para esos la 1.0.0 era un downgrade. Se cerraban al abrirse, sin diálogo.

De ahí que la versión vuelva a ser 2 y que `SIN_CAMBIOS_1_2` no haga nada. Las dos versiones describen exactamente el mismo esquema —mismo `identityHash`, `cc3cd97f…`— y lo único que hacía falta es que el número avanzara en vez de retroceder. Una migración vacía es rara, y por eso está explicada donde vive: no arregla el esquema, reconcilia dos numeraciones que se cruzaron.

Hay una segunda forma del mismo error, y no hace falta bajar la versión para caer en ella: basta con **cambiarle la forma a una versión que ya salió**. Si la 2 ganara una columna, el teléfono que tiene una base marcada como 2 no correría ninguna migración —ya está en la versión que la app pide— y Room validaría contra un esquema que no corresponde. La tentación aparece sola: «esa versión todavía no se ha subido, la aprovecho». Subir no es lo que la quema; instalarla sí, y en desarrollo se instala mucho antes de subir nada.

Lo cubren dos pruebas de `EsquemaTest`: `la version del esquema nunca retrocede`, contra una constante escrita a mano, y `las versiones que ya salieron no cambian de forma`, contra el `identityHash` congelado de cada una. Ninguna de las dos se deriva de los archivos de `app/schemas/`, a propósito: borrar uno —que es justo lo que se hizo aquella vez— bajaría el listón junto con la versión y las pruebas pasarían tan campantes.

**No hay `fallbackToDestructiveMigration` en ningún lado, y no debe haberlo** —ni su variante `OnDowngrade`—. La base va cifrada con una llave del Keystore que no se respalda, así que borrarla y empezar de cero no es un inconveniente: es perder la bitácora entera y no hay de dónde recuperarla. Si falta un paso, Room se niega a abrir. Un arranque que falla se arregla con una actualización; una bitácora borrada, no.

### Cuando aun así falla

Room negándose a abrir mata el proceso si nadie lo atrapa, y eso es lo que hacía: la excepción salía del `launch` de `OllinApp`, llegaba al manejador por defecto del hilo y la app desaparecía sin decir nada. Ahora el arranque va dentro de un `try`, deja el fallo en `OllinApp.arranqueFallido` y [`MainActivity`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/MainActivity.kt) enseña una pantalla que lo explica, con el tipo y el mensaje de la excepción.

Esa pantalla es la excepción a la regla de [ocultar lo interno](seguridad.md#manejo-de-errores): la app no va a funcionar, no hay otra pantalla desde la que enterarse de nada, y sin una línea que copiar no hay forma de distinguir «se me corrompió la base» de «esta versión no abre en mi teléfono». Va antes que el candado, porque si la base no abrió no hay bitácora que proteger.

Dos columnas admiten nulos, y en las dos nulo es una respuesta y no un dato que falta: `horaRecordatorio` nula significa que el hábito no avisa, y `ancla` nula significa «cuenta desde el día en que di de alta el hábito», que es lo que resuelve `Habito.anclaEfectiva()` sin inventarle una fecha.

### Agregar una versión

1. Cambiar las entidades.
2. Subir `Migraciones.VERSION`.
3. Escribir la `Migration` y sumarla a `Migraciones.TODAS`, en orden.
4. Compilar para que KSP escriba `app/schemas/N.json`, y **versionarlo**.
5. Agregar a `MigracionesTest` una prueba propia que escriba un renglón antes de migrar y lo vuelva a leer después.

### Qué vigila cada prueba

| Prueba | Dónde corre | Qué caza |
|---|---|---|
| [`EsquemaTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/EsquemaTest.kt) | JVM, en cada compilación | Una versión sin migración, una migración que salta versiones, un `N.json` sin versionar |
| [`MigracionesTest`](../app/src/androidTest/java/com/carlosalbertoxw/ollin/actividades/db/MigracionesTest.kt) | Emulador, al publicar | Que el SQL de cada paso deje la base exactamente como Room la espera |

Son cosas distintas y las dos hacen falta. Una migración puede existir, estar bien encadenada y aun así dejar una columna con el tipo equivocado o sin su índice; `EsquemaTest` no lo vería, porque no ejecuta SQL. Y al revés: `MigracionesTest` necesita un emulador y diez minutos, así que no puede ser lo único que vigile un descuido de contabilidad.

`MigracionesTest` compara contra los `N.json` que KSP exporta de las entidades, así que valida el resultado y no la intención. Corre sin cifrar —el helper abre con el SQLite del sistema, no con SQLCipher— y da igual: el cifrado envuelve el archivo entero y las migraciones ven el mismo esquema con llave o sin ella.

`EsquemaTest` corre en la JVM en milisegundos, así que entra en cada pull request. `MigracionesTest` bloquea la publicación de una versión, ver [publicación](publicacion.md).

Los esquemas exportados viven en [`app/schemas/`](../app/schemas/) y **se versionan en git**: son la referencia contra la que se prueba cada migración. También viajan como assets de la suite instrumentada, por la línea `sourceSets.getByName("androidTest").assets.srcDir(...)` del build; sin ella `MigrationTestHelper` no encuentra ninguno y las pruebas de migración pasarían sin comprobar nada.
