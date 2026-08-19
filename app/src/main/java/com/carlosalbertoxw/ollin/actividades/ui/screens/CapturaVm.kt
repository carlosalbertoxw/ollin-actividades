package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

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

class CapturaVm(
    private val repo: ActividadesRepositorio,
    private val actividadId: Long?,
    /** Cuando llega, el formulario nace rellenado con la plantilla del habito. */
    private val habitoId: Long? = null,
    /** El dia que se estaba viendo al marcar. Nulo es hoy. */
    private val dia: LocalDate? = null
) : ViewModel() {

    private val _form = MutableStateFlow(FormularioActividad())
    val form: StateFlow<FormularioActividad> = _form

    val categorias: StateFlow<List<Categoria>> = repo.observaCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Los dos caminos se excluyen: o se abre una actividad que ya existe, o
        // se estrena una desde un habito.
        if (actividadId == null && habitoId != null) {
            viewModelScope.launch { rellenaDesde(habitoId, dia ?: Tiempo.hoy()) }
        }
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

    /**
     * Deja el formulario como quedaria el registro directo del habito, para que
     * pulsar Guardar sin tocar nada de exactamente lo mismo que antes hacia la
     * paloma: los minutos sugeridos y una actividad que acaba de terminar.
     *
     * La hora se calcula sobre el instante y no sobre el LocalTime porque un
     * habito largo marcado de madrugada empieza el dia anterior, y restar sobre
     * la hora suelta daria la vuelta al reloj y lo dejaria esta noche.
     */
    private suspend fun rellenaDesde(id: Long, dia: LocalDate) {
        val habito = repo.habito(id) ?: return
        val minutos = (habito.minutosSugeridos ?: 0).coerceAtLeast(0)
        val fin = if (dia == Tiempo.hoy()) {
            Tiempo.ahora()
        } else {
            // El mediodia es la hora que menos miente cuando ya no se sabe.
            Tiempo.instante(dia.atTime(12, 0))
        }
        val inicio = Tiempo.local(fin.minusSeconds(minutos * 60L))
        _form.value = FormularioActividad(
            titulo = habito.nombre,
            categoriaId = habito.categoriaId,
            estado = EstadoActividad.COMPLETADO,
            fecha = inicio.toLocalDate(),
            hora = inicio.toLocalTime().withSecond(0).withNano(0),
            duracionTexto = minutos.toString(),
            habitoId = habito.id
        )
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
