package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.repo.HabitoConAvance
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HoyVm(
    private val repo: ActividadesRepositorio,
    preferencias: AjustesRepositorio
) : ViewModel() {

    /**
     * El dia que la pantalla esta enseñando.
     *
     * No puede ser una constante del ViewModel. La app se queda abierta y cruza
     * la medianoche, y con el dia congelado seguiria enseñando lo de ayer bajo
     * el titulo "Hoy" —y, peor, ahi escribiria los habitos que marcaras—.
     *
     * Se reprograma sola para despertar justo en el cambio de dia: entre una
     * medianoche y la siguiente no gasta nada, y solo emite cuando de verdad
     * cambio la fecha, asi que tampoco provoca recomposiciones de mas.
     */
    val dia: StateFlow<LocalDate> = flow {
        while (true) {
            val actual = Tiempo.hoy()
            emit(actual)
            delay(milisHastaElCambioDeDia(actual))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tiempo.hoy())

    val enCurso: StateFlow<ActividadDetallada?> = repo.observaEnCurso()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * El latido del cronometro.
     *
     * Late solo mientras hay algo corriendo y mientras alguien mira la pantalla.
     * Sin actividad en curso no hay ningun numero que avance, asi que un tick
     * por segundo solo serviria para gastar bateria sin enseñar nada distinto.
     */
    val ahora: StateFlow<Instant> = enCurso
        .flatMapLatest { corriendo ->
            if (corriendo == null) flowOf(Tiempo.ahora())
            else flow {
                while (true) {
                    emit(Tiempo.ahora())
                    delay(1_000)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), Tiempo.ahora())

    val delDia: StateFlow<List<ActividadDetallada>> = dia
        .flatMapLatest(repo::observaDelDia)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendientes: StateFlow<List<ActividadDetallada>> = dia
        .flatMapLatest { repo.observaPendientes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habitos: StateFlow<List<HabitoConAvance>> = dia
        .flatMapLatest { repo.observaHabitosConAvance(it) }
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

    val ajustes: StateFlow<Ajustes> = preferencias.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    /** Minutos completados hoy por ambito. Es el resumen de la jornada. */
    val minutosPorAmbito: StateFlow<Map<Ambito?, Int>> = dia
        .flatMapLatest { repo.observaTotalPorAmbito(it, it) }
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

    /**
     * Borra el ultimo cumplimiento del habito en el dia que se esta viendo.
     *
     * Marcar ya no vive aqui: abre el formulario de captura rellenado con la
     * plantilla del habito, para poder revisar los minutos y la hora antes de
     * guardar. Deshacer si sigue siendo inmediato, porque no hay nada que
     * revisar y el registro que quita se puede volver a crear.
     */
    fun deshace(avance: HabitoConAvance) {
        val elDia = dia.value
        viewModelScope.launch { repo.deshaceHabito(avance.habito.id, elDia) }
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

    private companion object {
        /** Lo que falta para la medianoche, con un piso para no girar en vacio. */
        fun milisHastaElCambioDeDia(desde: LocalDate): Long {
            val manana = desde.plusDays(1).atStartOfDay(Tiempo.zona()).toInstant()
            return (manana.toEpochMilli() - Tiempo.ahora().toEpochMilli())
                .coerceAtLeast(1_000L)
        }
    }
}
