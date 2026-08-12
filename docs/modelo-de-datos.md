# Modelo de datos

Room sobre SQLite cifrado. Tres tablas, versión de esquema **2**, esquemas exportados en `app/schemas/`.

Las entidades de Room son también el modelo de dominio: [`Entidades.kt`](../app/src/main/java/mx/ollin/actividades/data/db/Entidades.kt).

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

Una instalación nueva arranca con 16 categorías de ejemplo ([`Semilla.kt`](../app/src/main/java/mx/ollin/actividades/data/db/Semilla.kt)), renombrables y borrables sin consecuencias. Una app de registro que abre vacía obliga a inventar la taxonomía antes de poder anotar nada.

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
| `minutosSugeridos` | Duración que propone la pantalla de hoy |
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

La regla de la casa, en [`Tiempo`](../app/src/main/java/mx/ollin/actividades/domain/model/Tiempo.kt): el instante se guarda en UTC porque es un punto en la línea del tiempo y no debe moverse al viajar; el día se guarda aparte como día epoch local porque "cuánto trabajé el martes" es una pregunta del calendario de quien la hace.

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

[`Proyecciones.kt`](../app/src/main/java/mx/ollin/actividades/data/db/Proyecciones.kt) declara lo que devuelven las consultas con join o agregación. Son de solo lectura: nadie las inserta ni las modifica.

`ActividadDetallada` (actividad + nombre, color y ámbito de su categoría + nombre de su hábito), `TotalCategoria`, `TotalDia`, `TotalAmbito`, `ConteoEstado`, `CumplimientoDia` y `CumplimientoHabito`.

## Convertidores

[`Convertidores.kt`](../app/src/main/java/mx/ollin/actividades/data/db/Convertidores.kt) traduce `Instant` (milisegundos epoch), `LocalDate` (día epoch) y los enums (por nombre) a columnas de SQLite.

## Migraciones

| Versión | Cambio |
|---|---|
| 1 | Esquema inicial |
| 2 | Cadencias periódicas: `intervaloDias`, `intervaloMeses` y `ancla` en `habito` |

`ancla` admite nulos a propósito: para los hábitos que ya existían significa "cuenta desde el día en que te di de alta", que es lo que resuelve `Habito.anclaEfectiva()` sin inventarles una fecha.

La migración se prueba contra el esquema real de la versión 1 en [`MigracionTest`](../app/src/test/java/mx/ollin/actividades/MigracionTest.kt): si se equivoca, Room no abre y la bitácora queda inaccesible.

Al agregar una versión: modifica las entidades, sube `version` en `OllinDatabase`, escribe la `Migration`, regístrala en `addMigrations(...)`, versiona el nuevo `app/schemas/N.json` que genera KSP y agrega su prueba.
