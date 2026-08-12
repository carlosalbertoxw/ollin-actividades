package mx.ollin.actividades.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import mx.ollin.actividades.data.db.ConteoEstado
import mx.ollin.actividades.data.db.CumplimientoDia
import mx.ollin.actividades.data.db.TotalAmbito
import mx.ollin.actividades.data.db.TotalCategoria
import mx.ollin.actividades.data.db.TotalDia
import mx.ollin.actividades.di.Contenedor
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.domain.model.Unidad
import mx.ollin.actividades.ui.components.AyudaDePantalla
import mx.ollin.actividades.ui.components.BarraAvance
import mx.ollin.actividades.ui.components.BarrasDias
import mx.ollin.actividades.ui.components.EstadoVacio
import mx.ollin.actividades.ui.components.Punto
import mx.ollin.actividades.ui.components.SeccionTitulo
import mx.ollin.actividades.ui.components.TarjetaValor
import mx.ollin.actividades.ui.components.Tutorial
import mx.ollin.actividades.ui.recuerdaVm
import mx.ollin.actividades.ui.theme.LocalColoresOllin
import mx.ollin.actividades.ui.theme.colorDeCategoria
import java.time.LocalDate

/** Ventanas de analisis. Mas de tres opciones vuelven la pantalla un panel de control. */
enum class Ventana(val etiqueta: String, val dias: Long) {
    SEMANA("7 dias", 7),
    MES("30 dias", 30),
    TRIMESTRE("90 dias", 90)
}

class AnaliticaVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    private val _ventana = MutableStateFlow(Ventana.SEMANA)
    val ventana: StateFlow<Ventana> = _ventana

    private val rango = _ventana

    @OptIn(ExperimentalCoroutinesApi::class)
    val porDia: StateFlow<List<TotalDia>> = rango
        .flatMapLatest { v -> repo.observaTotalPorDia(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val porCategoria: StateFlow<List<TotalCategoria>> = rango
        .flatMapLatest { v -> repo.observaTotalPorCategoria(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val porAmbito: StateFlow<List<TotalAmbito>> = rango
        .flatMapLatest { v -> repo.observaTotalPorAmbito(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val porEstado: StateFlow<List<ConteoEstado>> = rango
        .flatMapLatest { v -> repo.observaConteoPorEstado(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val habitos: StateFlow<List<CumplimientoDia>> = rango
        .flatMapLatest { v -> repo.observaCumplimientoGlobal(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val kilometros: StateFlow<Double> = rango
        .flatMapLatest { v -> repo.observaSumaDeUnidad(Unidad.KILOMETROS, desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun eligeVentana(v: Ventana) {
        _ventana.value = v
    }

    private fun desde(v: Ventana): LocalDate = Tiempo.hoy().minusDays(v.dias - 1)
}

@Composable
fun AnaliticaPantalla(contenedor: Contenedor) {
    val vm = recuerdaVm("analitica") { AnaliticaVm(contenedor) }
    val ventana by vm.ventana.collectAsStateWithLifecycle()
    val porDia by vm.porDia.collectAsStateWithLifecycle()
    val porCategoria by vm.porCategoria.collectAsStateWithLifecycle()
    val porAmbito by vm.porAmbito.collectAsStateWithLifecycle()
    val porEstado by vm.porEstado.collectAsStateWithLifecycle()
    val habitos by vm.habitos.collectAsStateWithLifecycle()
    val kilometros by vm.kilometros.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val hoy = Tiempo.hoy()
    val dias = (0 until ventana.dias).map { hoy.minusDays(ventana.dias - 1 - it) }
    val minutosPorDia = porDia.associate { it.dia to it.minutos }
    val serie = dias.map { minutosPorDia[it] ?: 0 }

    val totalMinutos = serie.sum()
    val diasConRegistro = serie.count { it > 0 }
    val completadas = porEstado.firstOrNull { it.estado == EstadoActividad.COMPLETADO }?.conteo ?: 0
    val pendientes = porEstado.firstOrNull { it.estado == EstadoActividad.PENDIENTE }?.conteo ?: 0
    val diasConHabito = habitos.count { it.veces > 0 }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { AyudaDePantalla(contenedor, Tutorial.ANALITICA) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ventana.entries.forEach { v ->
                    FilterChip(
                        selected = ventana == v,
                        onClick = { vm.eligeVentana(v) },
                        label = { Text(v.etiqueta) }
                    )
                }
            }
        }

        if (totalMinutos == 0 && completadas == 0) {
            item {
                EstadoVacio(
                    icono = Icons.Filled.Insights,
                    titulo = "Todavia no hay que graficar",
                    detalle = "En cuanto completes actividades, aqui aparece en que se te va el tiempo.",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaValor(
                    etiqueta = "Tiempo registrado",
                    valor = Tiempo.duracion(totalMinutos),
                    nota = "en ${ventana.etiqueta}",
                    modifier = Modifier.weight(1f)
                )
                TarjetaValor(
                    etiqueta = "Promedio por dia",
                    valor = Tiempo.duracion(
                        // Se divide entre los dias con registro y no entre todos:
                        // promediar contra dias vacios mide la constancia, no el
                        // esfuerzo, y para constancia ya esta la barra de abajo.
                        if (diasConRegistro > 0) totalMinutos / diasConRegistro else 0
                    ),
                    nota = "$diasConRegistro de ${ventana.dias} dias con registro",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    SeccionTitulo("Minutos por dia")
                    Spacer(Modifier.height(12.dp))
                    BarrasDias(
                        valores = serie,
                        etiquetas = etiquetasDe(dias),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (porAmbito.isNotEmpty()) {
            item { SeccionTitulo("En que se va el tiempo") }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val mayor = porAmbito.maxOf { it.minutos }.coerceAtLeast(1)
                        porAmbito.forEach { total ->
                            RenglonProporcion(
                                nombre = total.ambito?.etiqueta ?: "Sin categoria",
                                minutos = total.minutos,
                                proporcion = total.minutos.toDouble() / mayor,
                                color = colores.de(total.ambito),
                                nota = "${total.conteo} registros"
                            )
                        }
                    }
                }
            }
        }

        if (porCategoria.isNotEmpty()) {
            item { SeccionTitulo("Por categoria") }
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val mayor = porCategoria.maxOf { it.minutos }.coerceAtLeast(1)
                        porCategoria.take(8).forEach { total ->
                            RenglonProporcion(
                                nombre = total.nombre ?: "Sin categoria",
                                minutos = total.minutos,
                                proporcion = total.minutos.toDouble() / mayor,
                                color = colorDeCategoria(total.colorHex, colores.de(total.ambito)),
                                nota = "${total.conteo} registros"
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TarjetaValor(
                    etiqueta = "Completadas",
                    valor = completadas.toString(),
                    nota = if (pendientes > 0) "$pendientes pendientes" else "sin pendientes",
                    acento = colores.completado,
                    modifier = Modifier.weight(1f)
                )
                TarjetaValor(
                    etiqueta = "Dias con habitos",
                    valor = "$diasConHabito",
                    nota = "de ${ventana.dias} dias",
                    acento = colores.habito,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (kilometros > 0.0) {
            item {
                TarjetaValor(
                    etiqueta = "Distancia acumulada",
                    valor = Unidad.KILOMETROS.formatea(kilometros),
                    nota = "en ${ventana.etiqueta}",
                    acento = colores.fisico,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RenglonProporcion(
    nombre: String,
    minutos: Int,
    proporcion: Double,
    color: Color,
    nota: String
) {
    val colores = LocalColoresOllin.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Punto(color, 8)
            Spacer(Modifier.width(8.dp))
            Text(
                nombre,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                Tiempo.duracion(minutos),
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
        }
        Spacer(Modifier.height(6.dp))
        BarraAvance(proporcion, Modifier.fillMaxWidth(), color)
        Spacer(Modifier.height(2.dp))
        Text(nota, style = MaterialTheme.typography.labelSmall, color = colores.textoTenue)
    }
}

/**
 * Etiquetas del eje. Con 30 o 90 dias no caben todas, asi que se marcan solo
 * los extremos y el centro: una reticula ilegible no informa mas que tres
 * fechas bien puestas.
 */
private fun etiquetasDe(dias: List<LocalDate>): List<String> = when {
    dias.size <= 7 -> dias.map { Tiempo.inicialDia(it.dayOfWeek) }
    else -> listOf(
        Tiempo.fechaCorta(dias.first()),
        Tiempo.fechaCorta(dias[dias.size / 2]),
        Tiempo.fechaCorta(dias.last())
    )
}
