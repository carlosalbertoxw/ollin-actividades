package com.carlosalbertoxw.ollin.actividades.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.theme.EstiloTiempo
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.actividades.ui.theme.colorDeCategoria
import java.time.LocalDate
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import java.time.ZoneOffset
import java.time.Instant
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import java.time.LocalTime
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker

/** Cada ambito tiene su icono. Se repite en filtros, chips y renglones. */
fun iconoDe(ambito: Ambito?): ImageVector = when (ambito) {
    Ambito.TRABAJO -> Icons.Filled.Work
    Ambito.FISICO -> Icons.AutoMirrored.Filled.DirectionsRun
    Ambito.HABITO -> Icons.Filled.Repeat
    Ambito.PERSONAL, null -> Icons.Filled.SelfImprovement
}

/** Duracion con ancho tabular, para que las columnas de la lista alineen. */
@Composable
fun TextoDuracion(
    minutos: Int,
    modifier: Modifier = Modifier,
    enfasis: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = Tiempo.duracion(minutos),
        modifier = modifier,
        style = EstiloTiempo.copy(
            fontWeight = if (enfasis) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (enfasis) 20.sp else 15.sp
        ),
        color = color,
        maxLines = 1
    )
}

/** Tarjeta de indicador: una cifra grande con su etiqueta y una nota opcional. */
@Composable
fun TarjetaValor(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    nota: String? = null,
    acento: Color? = null,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (acento != null) {
                    Punto(acento, 8)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalColoresOllin.current.textoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(valor, style = MaterialTheme.typography.headlineSmall, color = color)
            if (nota != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    nota,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalColoresOllin.current.textoTenue
                )
            }
        }
    }
}

/** Barra de avance contra una meta, con semaforo al reves: aqui llegar es bueno. */
@Composable
fun BarraAvance(
    avance: Double,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val colores = LocalColoresOllin.current
    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colores.trazoSuave)
    ) {
        Box(
            Modifier
                .fillMaxWidth(avance.coerceIn(0.0, 1.0).toFloat())
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/**
 * Barras por dia. Es la grafica de toda la analitica: una serie de enteros
 * (minutos) contra la linea base, sin ejes ni reticula que estorben.
 */
@Composable
fun BarrasDias(
    valores: List<Int>,
    etiquetas: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    alto: Int = 132
) {
    val colores = LocalColoresOllin.current
    val maximo = (valores.maxOrNull() ?: 0).coerceAtLeast(1)

    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(alto.dp)
        ) {
            if (valores.isEmpty()) return@Canvas
            val anchoRanura = size.width / valores.size
            val anchoBarra = (anchoRanura * 0.55f).coerceAtMost(22f.dp.toPx())
            val radio = androidx.compose.ui.geometry.CornerRadius(4f, 4f)

            valores.forEachIndexed { i, valor ->
                val centro = anchoRanura * i + anchoRanura / 2f
                val altoBarra = (valor.toFloat() / maximo) * size.height
                // La ranura vacia se insinua para que se note el dia sin registro.
                drawRoundRect(
                    color = colores.trazoSuave,
                    topLeft = androidx.compose.ui.geometry.Offset(centro - anchoBarra / 2f, size.height - 3f.dp.toPx()),
                    size = Size(anchoBarra, 3f.dp.toPx()),
                    cornerRadius = radio
                )
                if (valor > 0) {
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(centro - anchoBarra / 2f, size.height - altoBarra),
                        size = Size(anchoBarra, altoBarra),
                        cornerRadius = radio
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            etiquetas.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoTenue,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    detalle: String,
    modifier: Modifier = Modifier,
    accion: @Composable (() -> Unit)? = null
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icono,
            contentDescription = null,
            tint = LocalColoresOllin.current.textoTenue,
            modifier = Modifier.size(44.dp)
        )
        Text(titulo, style = MaterialTheme.typography.titleMedium)
        Text(
            detalle,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalColoresOllin.current.textoTenue
        )
        accion?.invoke()
    }
}

/** Punto de color de una categoria o un ambito. */
@Composable
fun Punto(color: Color, tamano: Int = 10) {
    Box(
        Modifier
            .size(tamano.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SeccionTitulo(
    texto: String,
    modifier: Modifier = Modifier,
    accion: @Composable (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
        accion?.invoke()
    }
}

/** Contenedor con borde suave, para agrupar sin recurrir a otra tarjeta. */
@Composable
fun Marco(
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, LocalColoresOllin.current.trazoSuave, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) { contenido() }
}

/**
 * El renglon de una actividad. Lo comparten la pantalla de hoy y el registro:
 * si la lista se lee distinto en cada una, el mismo dato parece dos cosas.
 */
@Composable
fun RenglonActividad(
    detalle: ActividadDetallada,
    modifier: Modifier = Modifier,
    alPulsar: () -> Unit = {},
    accion: @Composable (() -> Unit)? = null
) {
    val colores = LocalColoresOllin.current
    val actividad = detalle.actividad
    val colorCategoria = colorDeCategoria(
        detalle.categoriaColor,
        colores.de(detalle.categoriaAmbito)
    )
    // No recibe reloj: en las listas solo hay pendientes y completadas, y
    // ninguna de las dos tiene un tiempo que avance mientras se mira.
    val minutos = actividad.minutosVividos()

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = alPulsar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Punto(colorCategoria, 10)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                actividad.titulo,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitulo(detalle),
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            TextoDuracion(
                minutos = minutos,
                color = if (actividad.estado == EstadoActividad.EN_CURSO) colores.enCurso
                else MaterialTheme.colorScheme.onSurface
            )
            val medida = actividad.cantidad
            if (medida != null && medida > 0.0) {
                Text(
                    actividad.unidad.formatea(medida),
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoTenue
                )
            }
        }

        if (accion != null) {
            Spacer(Modifier.width(4.dp))
            accion()
        }
    }
}

private fun subtitulo(detalle: ActividadDetallada): String {
    val actividad = detalle.actividad
    val partes = mutableListOf<String>()
    partes += when (actividad.estado) {
        EstadoActividad.EN_CURSO -> "En curso desde ${Tiempo.hora(actividad.inicio)}"
        EstadoActividad.PENDIENTE -> "Pendiente ${Tiempo.hora(actividad.inicio)}"
        EstadoActividad.COMPLETADO -> Tiempo.hora(actividad.inicio)
    }
    detalle.categoriaNombre?.let { partes += it }
    if (detalle.habitoNombre != null) partes += "Habito"
    return partes.joinToString(" · ")
}

/**
 * Confirma que se borre el ultimo cumplimiento de un habito.
 *
 * Deshacer no abre el formulario —no hay nada que ajustar, solo se quita— pero
 * si pregunta: la paloma y el deshacer son el mismo control y ocupan el mismo
 * pixel, asi que el pulgar que iba a marcar cae sobre el deshacer en cuanto el
 * habito ya esta hecho, y sin confirmacion eso borra un registro sin que nadie
 * se entere de que existia.
 *
 * Vive aqui porque lo usan la pantalla de Hoy y la de Habitos con el mismo
 * texto, y dos copias de esta frase acabarian contradiciendose.
 */
@Composable
fun DialogoDeshacerHabito(
    nombre: String,
    /** El dia que se esta viendo; Hoy sabe mirar otras fechas. */
    dia: LocalDate,
    alConfirmar: () -> Unit,
    alCerrar: () -> Unit
) {
    val cuando = if (dia == Tiempo.hoy()) "de hoy" else "del ${Tiempo.fechaCorta(dia)}"
    AlertDialog(
        onDismissRequest = alCerrar,
        confirmButton = {
            TextButton(onClick = {
                alCerrar()
                alConfirmar()
            }) { Text("Deshacer") }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        },
        title = { Text("Deshacer «$nombre»") },
        text = {
            Text(
                "Se borra el último registro $cuando y la racha vuelve a como " +
                    "estaba. Puedes volver a marcarlo cuando quieras."
            )
        }
    )
}

/**
 * El selector de fecha del sistema, devolviendo un [LocalDate].
 *
 * El estado del DatePicker de Material habla en milisegundos epoch **en UTC a
 * medianoche**, no en la zona del telefono. Convertirlo con el huso local
 * pierde un dia cada vez que se cruza la frontera de la fecha —al este de
 * Greenwich por la manana, al oeste por la tarde— asi que la ida y la vuelta
 * usan las dos UTC y la fecha elegida es exactamente la que se toco.
 *
 * Vive aqui porque lo usan la captura de una actividad y el ancla de un habito
 * periodico, y esa conversion escrita dos veces es un error esperando su turno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(
    inicial: LocalDate,
    alElegir: (LocalDate) -> Unit,
    alCerrar: () -> Unit
) {
    val estado = rememberDatePickerState(
        initialSelectedDateMillis = inicial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = alCerrar,
        confirmButton = {
            TextButton(onClick = {
                estado.selectedDateMillis?.let { millis ->
                    alElegir(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                alCerrar()
            }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        }
    ) {
        DatePicker(state = estado)
    }
}

/**
 * El selector de hora del sistema, devolviendo un [LocalTime].
 *
 * Va en reloj de 24 horas siempre: la app esta en espanol de Mexico y toda la
 * bitacora —el cronometro, la hora de inicio, la exportacion— ya se escribe
 * asi. Mezclar am/pm aqui obligaria a leer dos formatos en la misma pantalla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(
    inicial: LocalTime,
    titulo: String,
    alElegir: (LocalTime) -> Unit,
    alCerrar: () -> Unit
) {
    val estado = rememberTimePickerState(
        initialHour = inicial.hour,
        initialMinute = inicial.minute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = alCerrar,
        confirmButton = {
            TextButton(onClick = {
                alElegir(LocalTime.of(estado.hour, estado.minute))
                alCerrar()
            }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = alCerrar) { Text("Cancelar") }
        },
        title = { Text(titulo) },
        text = { TimePicker(state = estado) }
    )
}
