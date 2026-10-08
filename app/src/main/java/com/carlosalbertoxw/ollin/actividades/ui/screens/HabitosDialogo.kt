/*
 * El dialogo para crear o editar un habito, con su editor de cadencia y la
 * hora del recordatorio. Aparte de HabitosPantalla.kt por tamano.
 */
package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.ModoCiclo
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.components.DialogoFecha
import com.carlosalbertoxw.ollin.actividades.ui.components.DialogoHora
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
private fun SelectorHoraRecordatorio(
    actual: LocalTime?,
    alElegir: (LocalTime) -> Unit,
    alCerrar: () -> Unit
) {
    DialogoHora(
        // Las ocho de la manana es la hora por omision de un recordatorio que
        // todavia no tiene ninguna: temprano, pero no de madrugada.
        inicial = actual ?: LocalTime.of(8, 0),
        titulo = "Hora del recordatorio",
        alElegir = alElegir,
        alCerrar = alCerrar
    )
}

/**
 * El cada-cuanto de una frecuencia periodica: el numero y desde cuando se
 * cuenta.
 *
 * Los atajos cubren lo que casi todo el mundo quiere —quincenal, mensual,
 * bimestral— y el campo de al lado deja poner cualquier otro numero, que es lo
 * que hace personalizable a la cadencia sin inventar una opcion aparte.
 */
@Composable
private fun EditorIntervalo(
    enMeses: Boolean,
    valor: String,
    alCambiar: (String) -> Unit,
    ancla: LocalDate,
    anclaFijada: Boolean,
    alElegirAncla: (LocalDate) -> Unit,
    alSoltarAncla: () -> Unit
) {
    var pidiendoFecha by remember { mutableStateOf(false) }
    val colores = LocalColoresOllin.current
    val atajos = if (enMeses) listOf(1, 2, 3, 6) else listOf(7, 15, 30)

    Text(
        if (enMeses) "Cada cuántos meses" else "Cada cuántos días",
        style = MaterialTheme.typography.labelLarge,
        color = colores.textoTenue
    )
    Spacer(Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = valor,
            onValueChange = { alCambiar(it.filter(Char::isDigit).take(3)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(96.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(atajos) { n ->
                FilterChip(
                    selected = valor.toIntOrNull() == n,
                    onClick = { alCambiar(n.toString()) },
                    label = { Text(if (enMeses) "$n" else "$n") }
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(
        "Se cuenta desde el ${Tiempo.fechaCorta(ancla)}" +
            if (anclaFijada) "." else ", el día en que diste de alta el hábito.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { pidiendoFecha = true }) { Text("Seleccionar fecha") }
        if (anclaFijada) {
            TextButton(onClick = alSoltarAncla) { Text("Quitar la fecha") }
        }
    }

    if (pidiendoFecha) {
        DialogoFecha(
            // Se abre sobre el ancla vigente, que si nadie la fijo es el dia en
            // que nacio el habito: se corrige desde ahi en vez de desde hoy.
            inicial = ancla,
            alElegir = alElegirAncla,
            alCerrar = { pidiendoFecha = false }
        )
    }
}

@Composable
internal fun DialogoHabito(
    inicial: Habito,
    categorias: List<Categoria>,
    alGuardar: (Habito) -> Unit,
    alEliminar: (() -> Unit)?,
    alCerrar: () -> Unit
) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var categoriaId by remember { mutableStateOf(inicial.categoriaId) }
    var frecuencia by remember { mutableStateOf(inicial.frecuencia) }
    var dias by remember { mutableStateOf(inicial.diasSemana) }
    var metaDiaria by remember { mutableStateOf(inicial.metaDiaria.toString()) }
    var metaSemanal by remember { mutableStateOf(inicial.metaSemanal.toString()) }
    var intervaloDias by remember { mutableStateOf(inicial.intervaloDias.toString()) }
    var intervaloMeses by remember { mutableStateOf(inicial.intervaloMeses.toString()) }
    var ancla by remember { mutableStateOf(inicial.ancla) }
    var modoCiclo by remember { mutableStateOf(inicial.modoCiclo) }
    var minutos by remember { mutableStateOf(inicial.minutosSugeridos?.toString() ?: "") }
    var activo by remember { mutableStateOf(inicial.activo) }
    var recordatorio by remember { mutableStateOf(inicial.horaRecordatorio) }
    var pidiendoHora by remember { mutableStateOf(false) }
    val colores = LocalColoresOllin.current

    AlertDialog(
        onDismissRequest = alCerrar,
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        inicial.copy(
                            nombre = nombre.trim(),
                            categoriaId = categoriaId,
                            frecuencia = frecuencia,
                            diasSemana = dias,
                            metaDiaria = metaDiaria.toIntOrNull()?.coerceIn(1, 20) ?: 1,
                            metaSemanal = metaSemanal.toIntOrNull()?.coerceIn(1, 7) ?: 5,
                            intervaloDias = intervaloDias.toIntOrNull()?.coerceIn(1, 365) ?: 15,
                            intervaloMeses = intervaloMeses.toIntOrNull()?.coerceIn(1, 24) ?: 1,
                            ancla = ancla,
                            modoCiclo = modoCiclo,
                            minutosSugeridos = minutos.toIntOrNull()?.takeIf { it > 0 },
                            activo = activo,
                            horaRecordatorio = recordatorio
                        )
                    )
                },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (alEliminar != null) {
                    IconButton(onClick = alEliminar) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = alCerrar) { Text("Cancelar") }
            }
        },
        title = { Text(if (inicial.id == 0L) "Nuevo hábito" else "Editar hábito") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    placeholder = { Text("Leer 20 minutos") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "Cada cuando",
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Frecuencia.entries.forEach { opcion ->
                        FilterChip(
                            selected = frecuencia == opcion,
                            onClick = { frecuencia = opcion },
                            label = { Text(opcion.etiqueta) }
                        )
                    }
                }

                if (frecuencia == Frecuencia.DIAS_ELEGIDOS) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { dia ->
                            val puesto = DiasSemana.contiene(dias, dia)
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (puesto) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            colores.trazoSuave
                                        }
                                    )
                                    .clickable { dias = DiasSemana.alterna(dias, dia) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Tiempo.inicialDia(dia),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (puesto) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        colores.textoTenue
                                    }
                                )
                            }
                        }
                    }
                }

                if (frecuencia == Frecuencia.SEMANAL) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = metaSemanal,
                        onValueChange = { metaSemanal = it.filter(Char::isDigit).take(1) },
                        label = { Text("Días por semana") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (frecuencia.esPeriodica) {
                    Spacer(Modifier.height(10.dp))
                    val enMeses = frecuencia == Frecuencia.CADA_MESES
                    EditorIntervalo(
                        enMeses = enMeses,
                        valor = if (enMeses) intervaloMeses else intervaloDias,
                        alCambiar = { if (enMeses) intervaloMeses = it else intervaloDias = it },
                        ancla = ancla ?: inicial.anclaEfectiva(),
                        anclaFijada = ancla != null,
                        alElegirAncla = { ancla = it },
                        alSoltarAncla = { ancla = null }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Si lo haces tarde",
                        style = MaterialTheme.typography.labelLarge,
                        color = colores.textoTenue
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModoCiclo.entries.forEach { opcion ->
                            FilterChip(
                                selected = modoCiclo == opcion,
                                onClick = { modoCiclo = opcion },
                                label = { Text(opcion.etiqueta) }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        modoCiclo.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "Categoria",
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categorias, key = { it.id }) { categoria ->
                        FilterChip(
                            selected = categoriaId == categoria.id,
                            onClick = {
                                categoriaId =
                                    if (categoriaId == categoria.id) null else categoria.id
                            },
                            label = { Text(categoria.nombre) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = metaDiaria,
                        onValueChange = { metaDiaria = it.filter(Char::isDigit).take(2) },
                        label = { Text("Veces al día") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minutos,
                        onValueChange = { minutos = it.filter(Char::isDigit).take(3) },
                        label = { Text("Minutos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Recordatorio",
                    style = MaterialTheme.typography.labelLarge,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (recordatorio != null) {
                        "Avisa a las ${Tiempo.horaLocal(recordatorio!!)} los días que toca, " +
                            "salvo que ya lo hayas cumplido."
                    } else {
                        "Sin aviso. Ponle una hora y Ollin te lo recuerda los días que toca."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { pidiendoHora = true }) {
                        Text(if (recordatorio != null) "Cambiar la hora" else "Poner una hora")
                    }
                    if (recordatorio != null) {
                        TextButton(onClick = { recordatorio = null }) { Text("Quitar el aviso") }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // El texto lleva weight y el Switch se queda con su ancho: sin
                // esto la descripcion se extiende por debajo del control y
                // termina cortada.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (activo) "En seguimiento" else "En pausa",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (activo) {
                                "Aparece en Hoy y su racha avanza."
                            } else {
                                "No aparece en Hoy y su racha no avanza. Conserva su " +
                                    "historial y lo puedes reanudar desde la lista de hábitos."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                    Switch(checked = activo, onCheckedChange = { activo = it })
                }
            }
        }
    )

    if (pidiendoHora) {
        SelectorHoraRecordatorio(
            actual = recordatorio,
            alElegir = { recordatorio = it },
            alCerrar = { pidiendoHora = false }
        )
    }
}
