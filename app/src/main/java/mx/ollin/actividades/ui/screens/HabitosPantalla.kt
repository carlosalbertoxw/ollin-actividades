package mx.ollin.actividades.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.data.repo.HabitoConAvance
import mx.ollin.actividades.di.Contenedor
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.DiasSemana
import mx.ollin.actividades.domain.model.Frecuencia
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.ui.components.AyudaDePantalla
import mx.ollin.actividades.ui.components.EstadoVacio
import mx.ollin.actividades.ui.components.Punto
import mx.ollin.actividades.ui.components.SeccionTitulo
import mx.ollin.actividades.ui.components.Tutorial
import mx.ollin.actividades.ui.components.iconoDe
import mx.ollin.actividades.ui.recuerdaVm
import mx.ollin.actividades.ui.theme.LocalColoresOllin
import mx.ollin.actividades.ui.theme.colorDeCategoria
import java.time.DayOfWeek
import java.time.LocalDate

class HabitosVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    /** Con los pausados incluidos: esta es la pantalla donde se administran. */
    val habitos: StateFlow<List<HabitoConAvance>> = repo.observaHabitosConAvance(soloActivos = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Para resolver la categoria de cada habito, no para elegirla. Va sobre
     * todas y no solo las activas: un habito viejo puede apuntar a una
     * categoria archivada, y ahi mostrar "sin categoria" seria mentir.
     */
    val indiceCategorias: StateFlow<Map<Long, Categoria>> = repo.observaTodasLasCategorias()
        .map { lista -> lista.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun guarda(habito: Habito) {
        viewModelScope.launch { repo.guardaHabito(habito) }
    }

    fun elimina(habito: Habito) {
        viewModelScope.launch { repo.eliminaHabito(habito) }
    }

    fun alterna(avance: HabitoConAvance) {
        viewModelScope.launch {
            if (avance.cumplidoHoy) repo.deshaceHabito(avance.habito.id)
            else repo.registraHabito(avance.habito)
        }
    }

    fun reanuda(habito: Habito) {
        viewModelScope.launch { repo.guardaHabito(habito.copy(activo = true)) }
    }
}

@Composable
fun HabitosPantalla(contenedor: Contenedor) {
    val vm = recuerdaVm("habitos") { HabitosVm(contenedor) }
    val habitos by vm.habitos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val indiceCategorias by vm.indiceCategorias.collectAsStateWithLifecycle()

    var editando by remember { mutableStateOf<Habito?>(null) }
    val colores = LocalColoresOllin.current

    val (activos, pausados) = habitos.partition { it.habito.activo }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editando = Habito(nombre = "") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuevo habito") }
            )
        }
    ) { relleno ->
        if (habitos.isEmpty()) {
            EstadoVacio(
                icono = Icons.Filled.Repeat,
                titulo = "Sin habitos todavia",
                detalle = "Un habito es algo que quieres repetir: leer, caminar, tomar agua. " +
                    "Al marcarlo se guarda como una actividad mas.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(relleno)
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(relleno),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "ayuda") { AyudaDePantalla(contenedor, Tutorial.HABITOS) }

                items(activos, key = { it.habito.id }) { avance ->
                    TarjetaHabito(
                        avance = avance,
                        categoria = avance.habito.categoriaId?.let(indiceCategorias::get),
                        alAlternar = { vm.alterna(avance) },
                        alEditar = { editando = avance.habito }
                    )
                }

                if (pausados.isNotEmpty()) {
                    item(key = "encabezado-pausados") {
                        Column(Modifier.padding(top = 6.dp)) {
                            SeccionTitulo("En pausa")
                            Text(
                                "No aparecen en Hoy y su racha no avanza, pero conservan " +
                                    "todo su historial. Pulsa reanudar para volver a contarlos.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colores.textoTenue
                            )
                        }
                    }

                    items(pausados, key = { it.habito.id }) { avance ->
                        TarjetaHabito(
                            avance = avance,
                            categoria = avance.habito.categoriaId?.let(indiceCategorias::get),
                            alAlternar = { vm.reanuda(avance.habito) },
                            alEditar = { editando = avance.habito }
                        )
                    }
                }
            }
        }
    }

    editando?.let { habito ->
        DialogoHabito(
            inicial = habito,
            categorias = categorias,
            alGuardar = {
                vm.guarda(it)
                editando = null
            },
            alEliminar = if (habito.id == 0L) null else {
                {
                    vm.elimina(habito)
                    editando = null
                }
            },
            alCerrar = { editando = null }
        )
    }
}

@Composable
private fun TarjetaHabito(
    avance: HabitoConAvance,
    categoria: Categoria?,
    alAlternar: () -> Unit,
    alEditar: () -> Unit
) {
    val colores = LocalColoresOllin.current
    val color = colorDeCategoria(categoria?.colorHex, colores.de(categoria?.ambito ?: Ambito.HABITO))
    val cumplido = avance.cumplidoHoy
    val pausado = !avance.habito.activo
    // La llama solo arde si la racha sigue corriendo. En pausa no avanza, y
    // pintarla encendida prometeria un progreso que no esta pasando.
    val rachaViva = avance.racha.actual > 0 && !pausado

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = alEditar),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punto(color, 10)
                    Spacer(Modifier.width(8.dp))
                    Text(avance.habito.nombre, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                EtiquetaCategoria(categoria, color)
                Spacer(Modifier.height(6.dp))
                Text(
                    resumen(avance),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (rachaViva) colores.enCurso else colores.textoTenue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${avance.racha.actual} ${avance.racha.unidad}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (rachaViva) colores.enCurso else colores.textoTenue
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Mejor: ${avance.racha.mejor}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.textoTenue
                    )
                }
            }

            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            pausado -> MaterialTheme.colorScheme.secondaryContainer
                            cumplido -> colores.completado
                            else -> colores.trazoSuave
                        }
                    )
                    .clickable(onClick = alAlternar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        pausado -> Icons.Filled.PlayArrow
                        cumplido -> Icons.AutoMirrored.Filled.Undo
                        else -> Icons.Filled.Check
                    },
                    contentDescription = when {
                        pausado -> "Reanudar habito"
                        cumplido -> "Deshacer hoy"
                        else -> "Marcar hoy"
                    },
                    tint = when {
                        pausado -> MaterialTheme.colorScheme.onSecondaryContainer
                        cumplido -> MaterialTheme.colorScheme.surface
                        else -> colores.textoTenue
                    }
                )
            }
        }
    }
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
    alAnclarHoy: () -> Unit,
    alSoltarAncla: () -> Unit
) {
    val colores = LocalColoresOllin.current
    val atajos = if (enMeses) listOf(1, 2, 3, 6) else listOf(7, 15, 30)

    Text(
        if (enMeses) "Cada cuantos meses" else "Cada cuantos dias",
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
            if (anclaFijada) "." else ", el dia en que diste de alta el habito.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = alAnclarHoy) { Text("Empezar hoy") }
        if (anclaFijada) {
            TextButton(onClick = alSoltarAncla) { Text("Quitar la fecha") }
        }
    }
}

/**
 * La categoria del habito, con el icono de su ambito.
 *
 * Sin ella la lista dice que hay algo que repetir pero no a que area de la vida
 * pertenece, que es justo por donde la analitica lo va a sumar. Se pinta con el
 * color de la categoria para que el punto del titulo se explique solo.
 */
@Composable
private fun EtiquetaCategoria(categoria: Categoria?, color: Color) {
    val colores = LocalColoresOllin.current
    val tinte = if (categoria != null) color else colores.textoTenue

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            iconoDe(categoria?.ambito),
            contentDescription = null,
            tint = tinte,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            categoria?.nombre ?: "Sin categoria",
            style = MaterialTheme.typography.labelMedium,
            color = tinte,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun resumen(avance: HabitoConAvance): String {
    val habito = avance.habito
    val cadencia = habito.cadencia()
    val hoy = when {
        // Un habito en pausa no esta pendiente: no se espera nada de el hoy.
        !habito.activo -> "en pausa"
        !avance.tocaHoy -> "hoy no toca"
        avance.cumplidoHoy -> "hecho hoy"
        habito.metaDiaria > 1 -> "${avance.vecesHoy}/${habito.metaDiaria} hoy"
        else -> "pendiente hoy"
    }
    val tiempo = if (avance.minutosHoy > 0) " · ${Tiempo.duracion(avance.minutosHoy)}" else ""
    return "$cadencia · $hoy$tiempo"
}

@Composable
private fun DialogoHabito(
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
    var minutos by remember { mutableStateOf(inicial.minutosSugeridos?.toString() ?: "") }
    var activo by remember { mutableStateOf(inicial.activo) }
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
                            minutosSugeridos = minutos.toIntOrNull()?.takeIf { it > 0 },
                            activo = activo
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
        title = { Text(if (inicial.id == 0L) "Nuevo habito" else "Editar habito") },
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
                Text("Cada cuando", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
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
                                        if (puesto) MaterialTheme.colorScheme.primaryContainer
                                        else colores.trazoSuave
                                    )
                                    .clickable { dias = DiasSemana.alterna(dias, dia) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    Tiempo.inicialDia(dia),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (puesto) MaterialTheme.colorScheme.onPrimaryContainer
                                    else colores.textoTenue
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
                        label = { Text("Dias por semana") },
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
                        alAnclarHoy = { ancla = Tiempo.hoy() },
                        alSoltarAncla = { ancla = null }
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("Categoria", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categorias, key = { it.id }) { categoria ->
                        FilterChip(
                            selected = categoriaId == categoria.id,
                            onClick = {
                                categoriaId = if (categoriaId == categoria.id) null else categoria.id
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
                        label = { Text("Veces al dia") },
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
                                    "historial y lo puedes reanudar desde la lista de habitos."
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
}
