package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.repo.HabitoConAvance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitosVm(private val repo: ActividadesRepositorio) : ViewModel() {

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

    /**
     * Borra el ultimo cumplimiento de hoy. Marcar abre el formulario de captura
     * en vez de registrar a ciegas; deshacer no tiene nada que revisar.
     */
    fun deshace(avance: HabitoConAvance) {
        viewModelScope.launch { repo.deshaceHabito(avance.habito.id) }
    }

    fun reanuda(habito: Habito) {
        viewModelScope.launch { repo.guardaHabito(habito.copy(activo = true)) }
    }
}
