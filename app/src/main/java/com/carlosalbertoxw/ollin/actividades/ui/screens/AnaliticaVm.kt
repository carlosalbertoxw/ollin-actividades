package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.ConteoEstado
import com.carlosalbertoxw.ollin.actividades.data.db.CumplimientoDia
import com.carlosalbertoxw.ollin.actividades.data.db.TotalAmbito
import com.carlosalbertoxw.ollin.actividades.data.db.TotalCategoria
import com.carlosalbertoxw.ollin.actividades.data.db.TotalDia
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Ventanas de analisis. Mas de tres opciones vuelven la pantalla un panel de control. */
enum class Ventana(val etiqueta: String, val dias: Long) {
    SEMANA("7 días", 7),
    MES("30 días", 30),
    TRIMESTRE("90 días", 90)
}

@OptIn(ExperimentalCoroutinesApi::class)
class AnaliticaVm(private val repo: ActividadesRepositorio) : ViewModel() {

    private val _ventana = MutableStateFlow(Ventana.SEMANA)
    val ventana: StateFlow<Ventana> = _ventana

    private val rango = _ventana

    val porDia: StateFlow<List<TotalDia>> = rango
        .flatMapLatest { v -> repo.observaTotalPorDia(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val porCategoria: StateFlow<List<TotalCategoria>> = rango
        .flatMapLatest { v -> repo.observaTotalPorCategoria(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val porAmbito: StateFlow<List<TotalAmbito>> = rango
        .flatMapLatest { v -> repo.observaTotalPorAmbito(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val porEstado: StateFlow<List<ConteoEstado>> = rango
        .flatMapLatest { v -> repo.observaConteoPorEstado(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habitos: StateFlow<List<CumplimientoDia>> = rango
        .flatMapLatest { v -> repo.observaCumplimientoGlobal(desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val kilometros: StateFlow<Double> = rango
        .flatMapLatest { v -> repo.observaSumaDeUnidad(Unidad.KILOMETROS, desde(v), Tiempo.hoy()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun eligeVentana(v: Ventana) {
        _ventana.value = v
    }

    private fun desde(v: Ventana): LocalDate = Tiempo.hoy().minusDays(v.dias - 1)
}
