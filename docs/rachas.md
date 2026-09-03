# Hábitos y rachas

Un hábito es una plantilla; sus cumplimientos son actividades completadas que apuntan a él. Marcar un hábito deja un registro normal en la bitácora: aparece en el historial, suma en la analítica y se puede editar después.

El cálculo vive en [`Rachas`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/usecase/Rachas.kt), sin dependencias de Android, y se prueba en [`RachasTest`](../app/src/test/java/com/carlosalbertoxw/ollin/actividades/RachasTest.kt).

## Cadencias

| Frecuencia | Qué significa | Campos que usa |
|---|---|---|
| `DIARIA` | Todos los días | — |
| `DIAS_ELEGIDOS` | Ciertos días de la semana | `diasSemana` (mapa de bits) |
| `SEMANAL` | Cierto número de veces por semana | `metaSemanal` |
| `CADA_DIAS` | Cada N días desde un ancla | `intervaloDias`, `ancla`, `modoCiclo` |
| `CADA_MESES` | Cada N meses desde un ancla | `intervaloMeses`, `ancla`, `modoCiclo` |

Las tres primeras se apoyan en el calendario semanal. Las dos últimas cuentan desde una fecha ancla, y son las que permiten lo que no cabe en una semana: cada quince días, cada mes, cada trimestre.

Las ocurrencias periódicas se calculan **siempre desde el ancla**, no encadenando saltos, porque `plusMonths` recorta al último día del mes: un hábito anclado al 31 cae en el 28 de febrero, pero el de marzo debe volver al 31 y no quedarse en el 28 para siempre.

El ancla es opcional: `anclaEfectiva()` devuelve la fijada a mano o, si no hay, el día en que se dio de alta el hábito. En el diálogo del hábito se fija con **Seleccionar fecha**, que abre el calendario del sistema sobre el ancla vigente —así se corrige desde donde está, no desde hoy— y **Quitar la fecha** la suelta para volver al día de alta. El selector es [`DialogoFecha`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/components/Comunes.kt), compartido con la captura de una actividad: su estado habla en UTC a medianoche, y convertir eso con el huso local pierde un día cada vez que se cruza la frontera de la fecha. Por eso viaja en el `.xlsx` como la columna **Cuenta desde** de la pestaña *Habitos* —ver [Excel](excel.md#cuenta-desde-el-ancla-de-las-cadencias-periódicas)—: sin esa columna, restaurar un respaldo haría nacer el hábito el día de la importación y le correría el calendario.

## Si se hace tarde: las dos maneras de contar

Solo aplica a las cadencias periódicas, y solo importa cuando algo se hace con retraso — pero entonces importa mucho. Es `modoCiclo`, y se elige en el diálogo del hábito.

| Modo | Qué hace | Para qué sirve |
|---|---|---|
| **Fechas fijas** (`CALENDARIO`) | `ancla + n × intervalo`. Hacerlo tarde no mueve nada | La renta, el pago del día 1: si se paga el 3, el siguiente sigue siendo el 1 |
| **Desde que lo hice** (`DESDE_ULTIMO`) | El intervalo vuelve a empezar en cada cumplimiento | Cambiar el filtro cada quince días: si se cambió con cinco de retraso, los quince siguientes empiezan ese día |

Con el ejemplo de siempre —cada quince días, anclado al 1 de agosto— tocaba el 16 y se hizo el 20:

- **Fechas fijas:** el siguiente sigue siendo el 31. Once días después, no quince.
- **Desde que lo hice:** el siguiente pasa a ser el 4 de septiembre.

Nace en **fechas fijas**, que es lo que hacían todos los hábitos antes de que existiera la opción: actualizar la app no le mueve el calendario a nadie.

El cálculo vive en [`CalendarioHabito`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/domain/usecase/CalendarioHabito.kt) y no en la entidad, porque en el segundo modo **el calendario depende de lo cumplido**, y eso es historia que `Habito` no tiene ni debe tener.

Una consecuencia que no es evidente: contando desde el último cumplimiento, mientras una ocurrencia siga pendiente **no hay siguiente**. No se puede contar quince días desde algo que todavía no pasó. Por eso un hábito así que lleva dos meses sin hacerse tiene una sola ocurrencia vencida, no cuatro, y por eso el recordatorio solo puede programar una fecha futura a la vez.

## Lo vencido se queda a la vista

Un hábito periódico que tocó y no se hizo **sigue apareciendo** en Hoy y en la lista, con la fecha en que tocaba, hasta que se haga o llegue la siguiente ocurrencia.

Antes solo era cierto el día exacto: un hábito cada tres meses que se pasaba un día no volvía a asomar en tres meses. No se fallaba, se perdía de vista, que es peor porque ni siquiera se sabe.

**Los recordatorios no siguen esa regla, y es a propósito.** Avisan solo el día que toca. Un hábito vencido está pendiente todos los días hasta que se haga, y avisar cada uno convierte un olvido en una campana diaria: quien se retrasa una semana con algo trimestral recibiría siete avisos idénticos y acabaría apagando los recordatorios enteros. Vencido se **ve**; se **avisa** una vez.

Las cadencias no periódicas no arrastran nada: un hábito diario que no se hizo ayer no está vencido hoy, está fallado, y llevar eso a la pantalla de Hoy la llenaría de deudas que nadie puede pagar.

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

En las cadencias periódicas, un ciclo se da por cumplido si hay algún registro dentro de su ventana, y la ventana acaba en lo que llegue antes: la siguiente ocurrencia, o **un intervalo entero de gracia**. Sin esa holgura, marcar un día tarde algo que toca cada quince rompería la racha, que es lo contrario de lo que la racha debería premiar.

Con fechas fijas las dos cosas coinciden casi siempre —la siguiente *es* ancla + intervalo—, salvo en los meses cortos, donde un ancla al 31 recorta al 28 y manda la siguiente.

Contando desde el último cumplimiento la gracia es lo único que acota, y sin ella la racha no podría romperse nunca: cada ocurrencia nace del cumplimiento anterior, así que todas quedarían cumplidas por construcción. La regla, dicha en corto: **se rompe cuando en el hueco cupo otro ciclo entero.** Cada quince días, hacerlo con cuatro de retraso no la rompe; dejar pasar quince, sí.

`ResumenRacha` devuelve la racha actual, la mejor histórica y la unidad.

## Límites

Para no recorrer el calendario entero: 1500 días hacia atrás en las rachas por día y 600 repeticiones en las periódicas. El repositorio, además, solo lee 400 días de historia (`VENTANA_RACHA`) al pintar la lista de hábitos con su avance.

## Recordatorios

Un hábito puede llevar `horaRecordatorio`. Si la tiene, Ollin avisa **los días que el hábito toca y todavía no se ha cumplido**; si ya se cumplió, no avisa, porque recordar lo hecho es la forma más rápida de que alguien apague los avisos enteros. Un hábito con meta diaria de tres sigue avisando hasta la tercera.

Las tareas —actividades pendientes— avisan a su hora de inicio, que es la que ya tenían: una pendiente es justamente algo agendado para un momento.

El interruptor maestro está en `Ajustes → Recordatorios` y **nace apagado**. Ver [Recordatorios](recordatorios.md) para cómo se programan y qué puede impedir que suenen.

## Activos y pausados

`observaHabitosConAvance(soloActivos = ...)` separa dos usos: la pantalla de Hoy solo quiere lo que toca hacer; la de Hábitos los administra y necesita ver también los pausados. Si no, pausar uno equivaldría a perderlo: la app no volvería a enseñarlo por ningún lado y nadie podría reactivarlo.

## Registro de un cumplimiento

La paloma de Hoy y la de Hábitos **no escriben**: abren el formulario de captura ya relleno con la plantilla del hábito —nombre, categoría, minutos sugeridos y una actividad que acaba de terminar— y el cumplimiento lo deja el botón Guardar. Así se puede corregir la duración real o la hora antes de que entre en la analítica, en vez de registrar a ciegas y tener que editar después. Pulsar Guardar sin tocar nada deja exactamente el mismo registro que dejaría un marcado directo.

La ruta lleva el hábito y el día (`captura?habito=…&dia=…`), porque la pantalla de Hoy sabe mirar otras fechas y marcar ahí tiene que registrar en la que se está viendo. Un hábito marcado para un día pasado se ancla al mediodía: es la hora que menos miente cuando ya no se sabe a qué hora fue.

`registraHabito()` es el camino sin formulario, el que usan las pruebas y cualquier registro directo: inserta una actividad completada con el nombre del hábito, su categoría y los minutos indicados (o los sugeridos por la plantilla).

`deshaceHabito()` borra el último registro de ese hábito en ese día, y **pregunta antes**. No abre el formulario —no hay nada que ajustar, solo se quita— pero sí un diálogo de confirmación: la paloma y el deshacer son el mismo control y ocupan el mismo píxel, así que el pulgar que iba a marcar cae sobre el deshacer en cuanto el hábito ya está hecho, y sin confirmación eso borra un registro sin que nadie se entere de que existía.

El diálogo vive en [`DialogoDeshacerHabito`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/ui/components/Comunes.kt) porque las dos pantallas lo enseñan con el mismo texto.
