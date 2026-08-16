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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import com.carlosalbertoxw.ollin.actividades.ui.components.iconoDe
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** El formulario completo, con los cinco datos del registro y las medidas opcionales. */
data class FormularioActividad(
    val id: Long = 0,
    val titulo: String = "",
    val categoriaId: Long? = null,
    val estado: EstadoActividad = EstadoActividad.COMPLETADO,
    val fecha: LocalDate = Tiempo.hoy(),
    val hora: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val duracionTexto: String = "30",
    val cantidadTexto: String = "",
    val unidad: Unidad = Unidad.NINGUNA,
    val notas: String = "",
    val habitoId: Long? = null,
    val creadoEn: Long = System.currentTimeMillis()
) {
    val esNueva: Boolean get() = id == 0L
    val duracion: Int get() = duracionTexto.toIntOrNull()?.coerceAtLeast(0) ?: 0
}

class CapturaVm(contenedor: Contenedor, private val actividadId: Long?) : ViewModel() {

    private val repo = contenedor.repositorio

    private val _form = MutableStateFlow(FormularioActividad())
    val form: StateFlow<FormularioActividad> = _form

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (actividadId != null) {
            viewModelScope.launch {
                repo.actividad(actividadId)?.let { a ->
                    val local = Tiempo.local(a.inicio)
                    _form.value = FormularioActividad(
                        id = a.id,
                        titulo = a.titulo,
                        categoriaId = a.categoriaId,
                        estado = a.estado,
                        fecha = local.toLocalDate(),
                        hora = local.toLocalTime(),
                        duracionTexto = (a.duracionMinutos ?: a.minutosVividos()).toString(),
                        cantidadTexto = a.cantidad?.let { c ->
                            if (c % 1.0 == 0.0) c.toLong().toString() else c.toString()
                        } ?: "",
                        unidad = a.unidad,
                        notas = a.notas.orEmpty(),
                        habitoId = a.habitoId,
                        creadoEn = a.creadoEn
                    )
                }
            }
        }
    }

    fun actualiza(bloque: (FormularioActividad) -> FormularioActividad) {
        _form.value = bloque(_form.value)
    }

    /** Devuelve falso si falta el titulo, que es el unico campo sin valor por omision. */
    fun guarda(alTerminar: () -> Unit): Boolean {
        val f = _form.value
        if (f.titulo.isBlank()) return false
        val inicio = Tiempo.instante(f.fecha.atTime(f.hora))
        viewModelScope.launch {
            repo.guarda(
                Actividad(
                    id = f.id,
                    titulo = f.titulo.trim(),
                    categoriaId = f.categoriaId,
                    estado = f.estado,
                    inicio = inicio,
                    fin = null,
                    dia = f.fecha,
                    duracionMinutos = if (f.estado == EstadoActividad.EN_CURSO) null else f.duracion,
                    cantidad = f.cantidadTexto.replace(',', '.').toDoubleOrNull(),
                    unidad = f.unidad,
                    habitoId = f.habitoId,
                    notas = f.notas.takeIf { it.isNotBlank() },
                    creadoEn = f.creadoEn
                )
            )
            alTerminar()
        }
        return true
    }

    fun elimina(alTerminar: () -> Unit) {
        val id = _form.value.id
        if (id == 0L) return alTerminar()
        viewModelScope.launch {
            repo.elimina(id)
            alTerminar()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturaPantalla(
    contenedor: Contenedor,
    actividadId: Long?,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("captura-$actividadId") { CapturaVm(contenedor, actividadId) }
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
                label = { Text("Que hiciste") },
                placeholder = { Text("Reunion de diseno") },
                isError = faltaTitulo,
                supportingText = if (faltaTitulo) {
                    { Text("Ponle un titulo para poder guardarlo") }
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
            Text("Categoria", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
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
                    label = { Text("Duracion en minutos") },
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
                    "Mientras corre, la duracion la lleva el cronometro.",
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
        // El selector trabaja en UTC a medianoche; se convierte con el mismo
        // huso para no perder un dia al cruzar la frontera de la fecha.
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = form.fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pidiendoFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { millis ->
                        val dia = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.actualiza { it.copy(fecha = dia) }
                    }
                    pidiendoFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { pidiendoFecha = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estado)
        }
    }

    if (pidiendoHora) {
        val estado = rememberTimePickerState(
            initialHour = form.hora.hour,
            initialMinute = form.hora.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { pidiendoHora = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.actualiza { it.copy(hora = LocalTime.of(estado.hour, estado.minute)) }
                    pidiendoHora = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { pidiendoHora = false }) { Text("Cancelar") }
            },
            title = { Text("Hora de inicio") },
            text = { TimePicker(state = estado) }
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
            text = { Text("Se borra el registro y deja de contar en la analitica.") }
        )
    }
}
