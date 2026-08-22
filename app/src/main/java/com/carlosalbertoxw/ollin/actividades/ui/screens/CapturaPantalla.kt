package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import com.carlosalbertoxw.ollin.actividades.ui.components.iconoDe
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.LocalDate
import java.time.LocalTime
import com.carlosalbertoxw.ollin.actividades.ui.components.DialogoFecha
import com.carlosalbertoxw.ollin.actividades.ui.components.DialogoHora

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaPantalla(
    contenedor: Contenedor,
    actividadId: Long?,
    habitoId: Long? = null,
    dia: LocalDate? = null,
    alCerrar: () -> Unit
) {
    // La clave lleva las tres cosas: marcar dos habitos distintos, o el mismo
    // en dos dias, tiene que estrenar formulario y no reusar el anterior.
    val vm = recuerdaVm("captura-$actividadId-$habitoId-$dia") {
        CapturaVm(contenedor.repositorio, actividadId, habitoId, dia)
    }
    val form by vm.form.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var faltaTitulo by remember { mutableStateOf(false) }
    var pidiendoFecha by remember { mutableStateOf(false) }
    var pidiendoHora by remember { mutableStateOf(false) }
    var confirmandoBorrado by remember { mutableStateOf(false) }

    LaunchedEffect(form.titulo) { if (form.titulo.isNotBlank()) faltaTitulo = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.esNueva) "Nueva actividad" else "Editar actividad") },
                navigationIcon = {
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                },
                actions = {
                    if (!form.esNueva) {
                        IconButton(onClick = { confirmandoBorrado = true }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = form.titulo,
                onValueChange = { texto -> vm.actualiza { it.copy(titulo = texto) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Qué hiciste") },
                placeholder = { Text("Reunión de diseño") },
                isError = faltaTitulo,
                supportingText = if (faltaTitulo) {
                    { Text("Ponle un título para poder guardarlo") }
                } else null,
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))
            Text("Estado", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstadoActividad.entries.forEach { estado ->
                    FilterChip(
                        selected = form.estado == estado,
                        onClick = { vm.actualiza { it.copy(estado = estado) } },
                        label = { Text(estado.etiqueta) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Categoría", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categorias, key = { it.id }) { categoria ->
                    FilterChip(
                        selected = form.categoriaId == categoria.id,
                        onClick = {
                            vm.actualiza {
                                it.copy(categoriaId = if (it.categoriaId == categoria.id) null else categoria.id)
                            }
                        },
                        label = { Text(categoria.nombre) },
                        leadingIcon = {
                            Icon(
                                iconoDe(categoria.ambito),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Inicio",
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoTenue
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pidiendoFecha = true },
                    label = { Text(Tiempo.fechaLarga(form.fecha)) }
                )
                AssistChip(
                    onClick = { pidiendoHora = true },
                    label = { Text(form.hora.toString().take(5)) }
                )
            }

            if (form.estado != EstadoActividad.EN_CURSO) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = form.duracionTexto,
                    onValueChange = { texto ->
                        vm.actualiza { it.copy(duracionTexto = texto.filter(Char::isDigit).take(4)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duración en minutos") },
                    supportingText = { Text(Tiempo.duracionLarga(form.duracion)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(15, 25, 30, 45, 60, 90)) { minutos ->
                        AssistChip(
                            onClick = { vm.actualiza { it.copy(duracionTexto = minutos.toString()) } },
                            label = { Text(Tiempo.duracion(minutos)) }
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Mientras corre, la duración la lleva el cronómetro.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Medida (opcional)",
                style = MaterialTheme.typography.labelLarge,
                color = colores.textoTenue
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = form.cantidadTexto,
                    onValueChange = { texto ->
                        vm.actualiza {
                            it.copy(cantidadTexto = texto.filter { c -> c.isDigit() || c == '.' || c == ',' })
                        }
                    },
                    modifier = Modifier.width(140.dp),
                    label = { Text("Cantidad") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(Modifier.width(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Unidad.entries.toList()) { unidad ->
                        FilterChip(
                            selected = form.unidad == unidad,
                            onClick = { vm.actualiza { it.copy(unidad = unidad) } },
                            label = { Text(unidad.etiqueta) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = form.notas,
                onValueChange = { texto -> vm.actualiza { it.copy(notas = texto) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notas") },
                minLines = 2
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (!vm.guarda(alCerrar)) faltaTitulo = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guardar")
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pidiendoFecha) {
        DialogoFecha(
            inicial = form.fecha,
            alElegir = { dia -> vm.actualiza { it.copy(fecha = dia) } },
            alCerrar = { pidiendoFecha = false }
        )
    }

    if (pidiendoHora) {
        DialogoHora(
            inicial = form.hora,
            titulo = "Hora de inicio",
            alElegir = { hora -> vm.actualiza { it.copy(hora = hora) } },
            alCerrar = { pidiendoHora = false }
        )
    }

    if (confirmandoBorrado) {
        AlertDialog(
            onDismissRequest = { confirmandoBorrado = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoBorrado = false
                    vm.elimina(alCerrar)
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmandoBorrado = false }) { Text("Cancelar") }
            },
            title = { Text("Eliminar la actividad") },
            text = { Text("Se borra el registro y deja de contar en la analítica.") }
        )
    }
}
