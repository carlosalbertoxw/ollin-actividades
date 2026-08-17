package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Los rangos que de verdad se consultan. Un selector de fechas libre casi nunca se usa. */
enum class Rango(val etiqueta: String, val dias: Long?) {
    HOY("Hoy", 0),
    SEMANA("7 días", 6),
    MES("30 días", 29),
    TODO("Todo", null);

    fun desde(hoy: LocalDate): LocalDate? = dias?.let { hoy.minusDays(it) }
}

data class FiltroActividades(
    val texto: String = "",
    val estado: EstadoActividad? = null,
    val ambito: Ambito? = null,
    val rango: Rango = Rango.SEMANA
)

class ActividadesVm(private val repo: ActividadesRepositorio) : ViewModel() {

    private val _filtro = MutableStateFlow(FiltroActividades())
    val filtro: StateFlow<FiltroActividades> = _filtro

    @OptIn(ExperimentalCoroutinesApi::class)
    val actividades: StateFlow<List<ActividadDetallada>> = _filtro
        .flatMapLatest { f ->
            val hoy = Tiempo.hoy()
            repo.observaActividades(
                texto = f.texto,
                estado = f.estado,
                ambito = f.ambito,
                desde = f.rango.desde(hoy),
                hasta = if (f.rango == Rango.TODO) null else hoy
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun actualiza(bloque: (FiltroActividades) -> FiltroActividades) {
        _filtro.value = bloque(_filtro.value)
    }
}
