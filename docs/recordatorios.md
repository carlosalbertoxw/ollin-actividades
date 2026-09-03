# Recordatorios

Ollin avisa de cuatro cosas, y no todas se gobiernan igual:

| Aviso | Cuándo | Interruptor |
|---|---|---|
| **Hábito** | Los días que toca, a `horaRecordatorio` | `Recordatorios` |
| **Tarea** | A su hora de inicio | `Recordatorios` |
| **Respaldo** | Cada semana sin exportar | `Recordarme respaldar` |
| **Versión nueva** | Al encontrarla, una vez por versión | `Avisarme de versiones nuevas` |

**Los cuatro nacen encendidos.** Un recordatorio que hay que ir a activar a Ajustes lo activa quien ya se acordaba solo, que es justo quien menos lo necesita: la función se pagaba a sí misma únicamente para los convencidos.

Encendido no quiere decir que suene sin permiso. Desde Android 13 hace falta `POST_NOTIFICATIONS`, y hasta que se conceda no llega nada; Ajustes enseña el aviso de que falta, con el atajo para darlo. Ver [permisos](#permisos).

**Los dos últimos tienen interruptor propio, y es deliberado.** Apagar los avisos de hábitos es decir «no me persigas con lo que me propuse». El del respaldo es otra cosa: es lo único que se interpone entre un teléfono perdido y una bitácora que no se recupera de ningún lado, porque la llave vive en el Keystore y no viaja. Quien apaga los hábitos no está pidiendo quedarse sin red de seguridad, así que tiene su propio interruptor.

## Qué avisa y qué no

| | Cuándo avisa | Cuándo no |
|---|---|---|
| **Hábito** | Los días que toca según su cadencia, a `horaRecordatorio` | Sin hora puesta · pausado · ya cumplido ese día |
| **Tarea** | A su `inicio` | Si ya está completada o en curso |

Los hábitos periódicos avisan **solo el día que toca**, aunque queden vencidos y sigan a la vista en Hoy. Ver [rachas](rachas.md#lo-vencido-se-queda-a-la-vista).

Un hábito con meta diaria de tres sigue avisando hasta la tercera: el planificador cuenta los cumplimientos del día, no se conforma con que haya alguno.

La hora del hábito es una **hora local suelta**, no un instante. «A las ocho» son las ocho de donde estés: guardar el instante ataría el recordatorio al huso en que se creó y sonaría a las tres de la madrugada después de un vuelo.

## El recordatorio de respaldar

El texto lleva la cuenta de días: *«Tu último respaldo es de hace 21 días»*, no *«acuérdate de respaldar»*. La segunda deja de leerse a la tercera semana porque no dice nada que quien la ve no sepa ya; la primera pone delante el número que uno no tenía en la cabeza. Va en el título y no en el detalle porque es lo único que se ve sin desplegar la notificación.

Quien no ha respaldado nunca no puede leer «hace N días» de algo que no existe, así que ese caso tiene su propia frase. Por eso `ultimoRespaldo` vive aparte de `respaldoDesde`: el ancla del plazo también la mueven el primer arranque y encender el interruptor, pero solo un `.xlsx` escrito mueve la fecha del último respaldo.


Cada **7 días** sin exportar. El plazo cuenta desde el último respaldo, o desde el último aviso si nadie le hizo caso, o desde el primer arranque si no hay ninguna de las dos cosas.

Tres reglas que lo separan de una molestia:

- **No avisa si no hay nada que perder.** Con la bitácora vacía no se dice nada: recordarle un respaldo a quien no ha registrado nada es la primera notificación inútil, la que enseña que las de esta app se pueden ignorar.
- **No avisa el día que se instala.** El primer arranque *estrena* el plazo en vez de disparar: abrir la app y recibir a los dos minutos «respalda tu bitácora» no tiene ningún sentido. Encender el interruptor hace lo mismo.
- **Un aviso desatendido no se repite hasta la semana siguiente.** La marca se pone al avisar y no al respaldar; sin eso se repetiría en cada replanificación, que ocurre varias veces al día.

Exportar desde `Ajustes → Archivo` reinicia el plazo. Se marca al terminar bien y no al empezar: un libro que no llegó a escribirse no es un respaldo.

## El aviso de versión nueva

Cuando la [comprobación diaria](actualizaciones.md) encuentra algo más reciente, se notifica **una vez por versión**. Hasta ahora había que entrar a *Acerca de* a mirarlo, así que enterarse dependía de ir a buscarlo.

El texto habla de respaldar y no solo de actualizar, porque ese es el momento en que más importa: instalar un APK encima es justo cuando algo puede salir mal con los datos, y es el único aviso que llega **antes** de que sea tarde.

Vive en `CoordinadorRecordatorios` y no en el comprobador: el comprobador pregunta, compara y devuelve; no sabe de notificaciones.

## Cómo funciona por dentro

Tres piezas, en `data/recordatorios/`:

| Pieza | Qué hace |
|---|---|
| [`PlanificadorRecordatorios`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/recordatorios/Recordatorio.kt) | Dice qué hay que recordar en una ventana de tiempo |
| [`AlarmaRecordatorios`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/recordatorios/AlarmaRecordatorios.kt) | Programa el despertador del sistema |
| [`CoordinadorRecordatorios`](../app/src/main/java/com/carlosalbertoxw/ollin/actividades/data/recordatorios/CoordinadorRecordatorios.kt) | Une las dos: notifica lo vencido y arma la siguiente alarma |

### Se calcula al vuelo, no se guarda

No hay tabla de avisos programados. Lo que toca depende del calendario del hábito y de lo que ya se cumplió hoy, y las dos cosas cambian sin avisar —al marcar, al editar la cadencia, al cruzar la medianoche—. Una tabla habría que invalidarla en todos esos momentos, y el primero que se olvidara dejaría avisos fantasma.

### Una sola alarma viva

Se programa **una** alarma: la del recordatorio más próximo. Al sonar se vuelve a planificar y se arma la siguiente.

La alternativa —una alarma por recordatorio— obliga a llevar la cuenta de cuáles están puestas para poder cancelarlas cuando un hábito cambia de hora o se cumple, y esa contabilidad es justo donde aparecen los avisos duplicados y los fantasma.

### Cuándo se replanifica

`CoordinadorRecordatorios.despacha()` es el único punto por el que se replanifica, y se le llama desde todos los sitios donde el plan puede haber quedado viejo:

- Al abrir la app.
- Cuando cambia cualquier hábito o actividad (`vigila()` observa la base, con un respiro de 700 ms para no recalcular con cada fila de una importación).
- Al encender o apagar el interruptor.
- Al sonar la alarma.
- Al arrancar el teléfono (`BOOT_COMPLETED`) y tras actualizar la app (`MY_PACKAGE_REPLACED`): las dos cosas borran las alarmas puestas.

### Lo vencido se rescata

Al despachar se miran también las **6 horas hacia atrás**. El teléfono apagado, una alarma diferida en doze o una actualización dejan huecos, y un hábito de esta mañana todavía se puede cumplir. Más atrás no: sería ruido por algo que ya no se puede hacer.

Hacia adelante el horizonte es de **120 días**, porque un hábito «cada tres meses» puede no tocar en mucho tiempo y sin margen su alarma no se programaría nunca.

## Permisos

| Permiso | Desde | Si falta |
|---|---|---|
| `POST_NOTIFICATIONS` | Android 13 | No se ve ningún aviso. Se pide al encender el interruptor |
| `SCHEDULE_EXACT_ALARM` | Android 12 | Los avisos llegan con minutos de retraso, no dejan de llegar |
| `RECEIVE_BOOT_COMPLETED` | — | Tras reiniciar, Ollin queda muda hasta que alguien la abra |

El permiso de notificaciones y el interruptor son la misma intención, así que van en el mismo gesto: si el sistema lo niega, el interruptor no se enciende, porque quedaría prometiendo algo que no puede cumplir.

**La alarma exacta no se da por concedida.** Desde Android 12 hay que autorizarla a mano, y una app de bitácora no puede asumirla: si no está, el aviso sale aproximado (`setAndAllowWhileIdle`) en vez de no salir. Un aviso con unos minutos de retraso sigue sirviendo; uno que no llega, no. No se declara `USE_EXACT_ALARM`, que la tienda reserva a despertadores y calendarios.

Ajustes enseña una advertencia **solo cuando alguno de los dos falta**, con el atajo para concederlo. Un texto fijo sobre algo que casi siempre está bien se deja de leer a la tercera vez.

## Privacidad

Con candado configurado, los avisos van con `VISIBILITY_PRIVATE`: en la pantalla de bloqueo se ve que hay una notificación de Ollin, no de qué.

Sería incoherente marcar la ventana con `FLAG_SECURE` para que la bitácora no salga ni en las apps recientes y a la vez anunciar «Terapia, te toca hoy» a quien mire el teléfono encima de la mesa. Ver [seguridad](seguridad.md).

## Límites conocidos

- **Forzar la detención** de la app desde los ajustes del sistema cancela las alarmas y Android no las restaura hasta que alguien vuelve a abrir Ollin. Es comportamiento del sistema, no hay forma de sortearlo.
- Los fabricantes con gestión agresiva de batería (Xiaomi, Huawei, Samsung en modo estricto) pueden retrasar o suprimir las alarmas. Si los avisos no llegan, el sitio donde mirar es la lista de apps con restricción de batería del teléfono.
- La hora del recordatorio **viaja en el `.xlsx`**, en la columna `Recordatorio` de la pestaña *Habitos*, para que restaurar un respaldo no la pierda. Ver [Excel](excel.md).
