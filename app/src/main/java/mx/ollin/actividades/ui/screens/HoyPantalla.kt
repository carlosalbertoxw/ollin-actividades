package mx.ollin.actividades.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import mx.ollin.actividades.data.db.ActividadDetallada
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.data.prefs.Ajustes
import mx.ollin.actividades.data.repo.HabitoConAvance
import mx.ollin.actividades.di.Contenedor
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.ui.components.AyudaDePantalla
import mx.ollin.actividades.ui.components.BarraAvance
import mx.ollin.actividades.ui.components.Punto
import mx.ollin.actividades.ui.components.RenglonActividad
import mx.ollin.actividades.ui.components.SeccionTitulo
import mx.ollin.actividades.ui.components.Tutorial
import mx.ollin.actividades.ui.components.iconoDe
import mx.ollin.actividades.ui.recuerdaVm
import mx.ollin.actividades.ui.theme.LocalColoresOllin
import mx.ollin.actividades.ui.theme.colorDeCategoria
import java.time.Instant

class HoyVm(private val contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio
    private val hoy = Tiempo.hoy()

    /**
     * El latido del cronometro. Emite mientras alguien mira la pantalla y se
     * apaga solo al salir: un tick por segundo en segundo plano gastaria
     * bateria para redibujar algo que nadie ve.
     */
    val ahora: StateFlow<Instant> = flow {
        while (true) {
            emit(Tiempo.ahora())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), Tiempo.ahora())

    val enCurso: StateFlow<ActividadDetallada?> = repo.observaEnCurso()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val delDia: StateFlow<List<ActividadDetallada>> = repo.observaDelDia(hoy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendientes: StateFlow<List<ActividadDetallada>> = repo.observaPendientes(hoy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habitos: StateFlow<List<HabitoConAvance>> = repo.observaHabitosConAvance(hoy)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Para resolver la categoria de cada habito. Va sobre todas y no solo las
     * activas: un habito puede apuntar a una archivada, y esconderla la haria
     * parecer un habito suelto.
     */
    val indiceCategorias: StateFlow<Map<Long, Categoria>> = repo.observaTodasLasCategorias()
        .map { lista -> lista.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val ajustes: StateFlow<Ajustes> = contenedor.ajustes.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    /** Minutos completados hoy por ambito. Es el resumen de la jornada. */
    val minutosPorAmbito: StateFlow<Map<Ambito?, Int>> = repo.observaTotalPorAmbito(hoy, hoy)
        .map { totales -> totales.associate { it.ambito to it.minutos } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _categoriaRapida = MutableStateFlow<Long?>(null)
    val categoriaRapida: StateFlow<Long?> = _categoriaRapida

    fun eligeCategoriaRapida(id: Long?) {
        _categoriaRapida.value = if (_categoriaRapida.value == id) null else id
    }

    fun inicia(titulo: String) {
        val limpio = titulo.trim()
        if (limpio.isEmpty()) return
        viewModelScope.launch {
            repo.iniciaAhora(titulo = limpio, categoriaId = _categoriaRapida.value)
        }
    }

    fun detiene() {
        val id = enCurso.value?.actividad?.id ?: return
        viewModelScope.launch { repo.detiene(id) }
    }

    fun arranca(id: Long) {
        viewModelScope.launch { repo.arranca(id) }
    }

    fun completaSinCronometro(id: Long) {
        viewModelScope.launch {
            repo.completa(id, ajustes.value.duracionRapidaMinutos)
        }
    }

    fun alterna(habito: HabitoConAvance) {
        viewModelScope.launch {
            if (habito.cumplidoHoy) repo.deshaceHabito(habito.habito.id, hoy)
            else repo.registraHabito(habito.habito, dia = hoy)
        }
    }

    fun cronometraHabito(habito: Habito) {
        viewModelScope.launch {
            repo.iniciaAhora(
                titulo = habito.nombre,
                categoriaId = habito.categoriaId,
                habitoId = habito.id
            )
        }
    }
}

@Composable
fun HoyPantalla(
    contenedor: Contenedor,
    alAbrirActividad: (Long) -> Unit,
    alAbrirAjustes: () -> Unit
) {
    val vm = recuerdaVm("hoy") { HoyVm(contenedor) }
    val enCurso by vm.enCurso.collectAsStateWithLifecycle()
    val ahora by vm.ahora.collectAsStateWithLifecycle()
    val delDia by vm.delDia.collectAsStateWithLifecycle()
    val pendientes by vm.pendientes.collectAsStateWithLifecycle()
    val habitos by vm.habitos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val indiceCategorias by vm.indiceCategorias.collectAsStateWithLifecycle()
    val categoriaRapida by vm.categoriaRapida.collectAsStateWithLifecycle()
    val minutos by vm.minutosPorAmbito.collectAsStateWithLifecycle()
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val completadas = delDia.filter { it.actividad.estado == EstadoActividad.COMPLETADO }
    val totalHoy = completadas.sumOf { it.actividad.duracionMinutos ?: 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(Tiempo.diaRelativo(Tiempo.hoy()), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (totalHoy > 0) "${Tiempo.duracionLarga(totalHoy)} registradas"
                        else "Sin tiempo registrado todavia",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                IconButton(onClick = alAbrirAjustes) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        }

        item {
            AyudaDePantalla(
                contenedor,
                Tutorial.HOY,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }

        item {
            TarjetaCronometro(
                enCurso = enCurso,
                ahora = ahora,
                categorias = categorias,
                categoriaRapida = categoriaRapida,
                alElegirCategoria = vm::eligeCategoriaRapida,
                alIniciar = vm::inicia,
                alDetener = vm::detiene,
                alAbrir = { enCurso?.actividad?.id?.let(alAbrirActividad) },
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
            )
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetaDelDia(
                    etiqueta = "Trabajo",
                    minutos = minutos[Ambito.TRABAJO] ?: 0,
                    meta = ajustes.metaTrabajoMinutos,
                    color = colores.trabajo,
                    modifier = Modifier.weight(1f)
                )
                MetaDelDia(
                    etiqueta = "Movimiento",
                    minutos = minutos[Ambito.FISICO] ?: 0,
                    meta = ajustes.metaFisicoMinutos,
                    color = colores.fisico,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (habitos.isNotEmpty()) {
            item {
                SeccionTitulo(
                    "Habitos de hoy",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val cumplidos = habitos.count { it.cumplidoHoy }
                    Text(
                        "$cumplidos de ${habitos.count { it.tocaHoy }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
            items(habitos.filter { it.tocaHoy }, key = { it.habito.id }) { avance ->
                RenglonHabitoHoy(
                    avance = avance,
                    categoria = avance.habito.categoriaId?.let(indiceCategorias::get),
                    alAlternar = { vm.alterna(avance) },
                    alCronometrar = { vm.cronometraHabito(avance.habito) }
                )
            }
        }

        if (pendientes.isNotEmpty()) {
            item {
                SeccionTitulo(
                    "Pendientes",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(pendientes, key = { it.actividad.id }) { detalle ->
                RenglonActividad(
                    detalle = detalle,
                    ahora = ahora,
                    alPulsar = { alAbrirActividad(detalle.actividad.id) },
                    accion = {
                        Row {
                            IconButton(onClick = { vm.arranca(detalle.actividad.id) }) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Iniciar",
                                    tint = colores.enCurso
                                )
                            }
                            IconButton(onClick = { vm.completaSinCronometro(detalle.actividad.id) }) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Marcar como hecha",
                                    tint = colores.completado
                                )
                            }
                        }
                    }
                )
            }
        }

        if (ajustes.muestraCompletadasEnHoy && completadas.isNotEmpty()) {
            item {
                SeccionTitulo(
                    "Registro de hoy",
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        Tiempo.duracion(totalHoy),
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }
            items(completadas, key = { it.actividad.id }) { detalle ->
                RenglonActividad(
                    detalle = detalle,
                    ahora = ahora,
                    alPulsar = { alAbrirActividad(detalle.actividad.id) }
                )
                HorizontalDivider(color = colores.trazoSuave)
            }
        }
    }
}

/**
 * El corazon de la pantalla: o esta corriendo algo, y entonces manda el
 * cronometro, o no hay nada, y entonces manda el campo para arrancar. Nunca
 * las dos cosas: dos llamados a la accion se anulan entre si.
 */
@Composable
private fun TarjetaCronometro(
    enCurso: ActividadDetallada?,
    ahora: Instant,
    categorias: List<Categoria>,
    categoriaRapida: Long?,
    alElegirCategoria: (Long) -> Unit,
    alIniciar: (String) -> Unit,
    alDetener: () -> Unit,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    var titulo by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            if (enCurso != null) {
                val actividad = enCurso.actividad
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punto(
                        colorDeCategoria(enCurso.categoriaColor, colores.de(enCurso.categoriaAmbito)),
                        10
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        enCurso.categoriaNombre ?: "Sin categoria",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.textoTenue
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(actividad.titulo, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    Tiempo.cronometro(actividad.segundosVividos(ahora)),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    color = colores.enCurso
                )
                Text(
                    "Desde las ${Tiempo.hora(actividad.inicio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = alDetener) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Detener")
                    }
                    TextButton(onClick = alAbrir) { Text("Editar") }
                }
            } else {
                Text("Que estas haciendo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Reunion de diseno, correr 5 km...") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categorias, key = { it.id }) { categoria ->
                        FilterChip(
                            selected = categoria.id == categoriaRapida,
                            onClick = { alElegirCategoria(categoria.id) },
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
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = {
                        alIniciar(titulo)
                        titulo = ""
                    },
                    enabled = titulo.isNotBlank()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar")
                }
            }
        }
    }
}

@Composable
private fun MetaDelDia(
    etiqueta: String,
    minutos: Int,
    meta: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(color, 8)
                Spacer(Modifier.width(8.dp))
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoTenue
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(Tiempo.duracion(minutos), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            BarraAvance(
                avance = if (meta <= 0) 0.0 else minutos.toDouble() / meta,
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (meta > 0) "Meta ${Tiempo.duracion(meta)}" else "Sin meta",
                style = MaterialTheme.typography.labelSmall,
                color = colores.textoTenue
            )
        }
    }
}

@Composable
private fun RenglonHabitoHoy(
    avance: HabitoConAvance,
    categoria: Categoria?,
    alAlternar: () -> Unit,
    alCronometrar: () -> Unit
) {
    val colores = LocalColoresOllin.current
    val cumplido = avance.cumplidoHoy
    val colorCategoria = colorDeCategoria(
        categoria?.colorHex,
        colores.de(categoria?.ambito ?: Ambito.HABITO)
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (cumplido) colores.completado else colores.trazoSuave),
            contentAlignment = Alignment.Center
        ) {
            if (cumplido) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(avance.habito.nombre, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            // La categoria abre el renglon: es lo que dice a que area de la vida
            // pertenece el habito, y su punto de color repite el codigo que la
            // analitica usa para agrupar.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(colorCategoria, 7)
                Spacer(Modifier.width(6.dp))
                Text(
                    buildString {
                        append(categoria?.nombre ?: "Sin categoria")
                        append(" · ")
                        if (avance.racha.actual > 0) {
                            append("Racha de ${avance.racha.actual} ${avance.racha.unidad}")
                        } else {
                            append("Sin racha activa")
                        }
                        val meta = avance.habito.metaDiaria
                        if (meta > 1) append(" · ${avance.vecesHoy}/$meta hoy")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = alCronometrar) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Cronometrar", tint = colores.textoTenue)
        }
        IconButton(onClick = alAlternar) {
            Icon(
                if (cumplido) Icons.AutoMirrored.Filled.Undo else Icons.Filled.Check,
                contentDescription = if (cumplido) "Deshacer" else "Marcar",
                tint = if (cumplido) colores.textoTenue else colores.completado
            )
        }
    }
}
