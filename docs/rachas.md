# Hábitos y rachas

Un hábito es una plantilla; sus cumplimientos son actividades completadas que apuntan a él. Marcar un hábito deja un registro normal en la bitácora: aparece en el historial, suma en la analítica y se puede editar después.

El cálculo vive en [`Rachas`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/usecase/Rachas.kt), sin dependencias de Android, y se prueba en [`RachasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/RachasTest.kt).

## Cadencias

| Frecuencia | Qué significa | Campos que usa |
|---|---|---|
| `DIARIA` | Todos los días | — |
| `DIAS_ELEGIDOS` | Ciertos días de la semana | `diasSemana` (mapa de bits) |
| `SEMANAL` | Cierto número de veces por semana | `metaSemanal` |
| `CADA_DIAS` | Cada N días desde un ancla | `intervaloDias`, `ancla` |
| `CADA_MESES` | Cada N meses desde un ancla | `intervaloMeses`, `ancla` |

Las tres primeras se apoyan en el calendario semanal. Las dos últimas cuentan desde una fecha ancla, y son las que permiten lo que no cabe en una semana: cada quince días, cada mes, cada trimestre.

Las ocurrencias periódicas se calculan **siempre desde el ancla**, no encadenando saltos, porque `plusMonths` recorta al último día del mes: un hábito anclado al 31 cae en el 28 de febrero, pero el de marzo debe volver al 31 y no quedarse en el 28 para siempre.

## Las dos reglas de la racha

1. **El día de hoy no rompe la racha mientras no termine.** Un hábito sin marcar a las nueve de la mañana está pendiente, no fallado. Castigarlo desde temprano es la forma más rápida de que alguien deje de abrir la app.
2. **Los días que el hábito no toca se saltan sin penalizar y sin sumar.** Un hábito de lunes a viernes conserva su racha durante el fin de semana.

Un día cuenta como cumplido cuando el número de registros de ese día alcanza `metaDiaria`.

## En qué se mide

No todos los hábitos cuentan días: decir "3 días" de algo que toca cada dos meses no significaría nada.

| Frecuencia | Unidad de la racha | Cómo se cuenta |
|---|---|---|
| `SEMANAL` | semanas | Semanas consecutivas en las que se alcanzó `metaSemanal` |
| `CADA_DIAS`, `CADA_MESES` | veces | Repeticiones consecutivas cumplidas |
| Resto | días | Días consecutivos aplicables cumplidos |

En las cadencias periódicas, un ciclo se da por cumplido si hay algún registro entre su fecha y la de la siguiente repetición. Sin esa holgura, marcar un día tarde algo que toca cada quince rompería la racha, que es lo contrario de lo que la racha debería premiar.

`ResumenRacha` devuelve la racha actual, la mejor histórica y la unidad.

## Límites

Para no recorrer el calendario entero: 1500 días hacia atrás en las rachas por día y 600 repeticiones en las periódicas. El repositorio, además, solo lee 400 días de historia (`VENTANA_RACHA`) al pintar la lista de hábitos con su avance.

## Activos y pausados

`observaHabitosConAvance(soloActivos = ...)` separa dos usos: la pantalla de Hoy solo quiere lo que toca hacer; la de Hábitos los administra y necesita ver también los pausados. Si no, pausar uno equivaldría a perderlo: la app no volvería a enseñarlo por ningún lado y nadie podría reactivarlo.

## Registro de un cumplimiento

`registraHabito()` inserta una actividad completada con el nombre del hábito, su categoría y los minutos indicados (o los sugeridos por la plantilla). Un hábito marcado para un día pasado se ancla al mediodía: es la hora que menos miente cuando ya no se sabe a qué hora fue.

`deshaceHabito()` borra el último registro de ese hábito en ese día.
