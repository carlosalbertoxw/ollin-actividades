package com.carlosalbertoxw.ollin.actividades.data.excel

import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.usecase.Rachas
import com.carlosalbertoxw.ollin.actividades.domain.usecase.ResumenRacha
import java.time.LocalDate

/** Fotografia completa de los datos en el momento de exportar. */
data class DatosExportacion(
    val categorias: List<Categoria>,
    val habitos: List<Habito>,
    val actividades: List<Actividad>,
    /** El "hoy" con el que se calculan las rachas. Explicito para poder probarlo. */
    val hoy: LocalDate = Tiempo.hoy()
) {
    private val categoriaPorId: Map<Long, Categoria> = categorias.associateBy { it.id }
    private val habitoPorId: Map<Long, Habito> = habitos.associateBy { it.id }

    fun nombreCategoria(id: Long?): String = id?.let { categoriaPorId[it]?.nombre } ?: ""

    fun nombreHabito(id: Long?): String = id?.let { habitoPorId[it]?.nombre } ?: ""

    /** El ambito sale de la categoria: la actividad no lo guarda por su cuenta. */
    fun etiquetaAmbito(categoriaId: Long?): String =
        categoriaId?.let { categoriaPorId[it]?.ambito?.etiqueta } ?: ""

    val actividadesOrdenadas: List<Actividad> by lazy {
        actividades.sortedWith(compareBy({ it.dia }, { it.inicio }, { it.id }))
    }

    /** Solo lo cerrado suma tiempo: lo pendiente todavia no ocurrio. */
    val completadas: List<Actividad> by lazy {
        actividades.filter { it.estado == EstadoActividad.COMPLETADO }
    }

    /** Dias con algo completado, en orden ascendente. */
    val diasConRegistro: List<LocalDate> by lazy {
        completadas.map { it.dia }.distinct().sorted()
    }

    /** Numero de la ultima fila con datos en Registros (fila 1 = encabezado). */
    val ultimaFilaRegistros: Int get() = actividades.size + 1

    val minutosTotales: Int by lazy { completadas.sumOf { it.duracionMinutos ?: 0 } }

    fun minutosDeCategoria(id: Long?): Int =
        completadas.filter { it.categoriaId == id }.sumOf { it.duracionMinutos ?: 0 }

    fun sesionesDeCategoria(id: Long?): Int = completadas.count { it.categoriaId == id }

    fun minutosDelDia(dia: LocalDate): Int =
        completadas.filter { it.dia == dia }.sumOf { it.duracionMinutos ?: 0 }

    fun sesionesDelDia(dia: LocalDate): Int = completadas.count { it.dia == dia }

    /**
     * La racha de un habito, calculada con la misma regla que la pantalla.
     * Va como valor fijo y no como formula: saltarse los dias que el habito no
     * toca es logica de calendario que una hoja de calculo no reproduce.
     */
    fun racha(habito: Habito): ResumenRacha {
        val porDia = completadas
            .filter { it.habitoId == habito.id }
            .groupingBy { it.dia }
            .eachCount()
        return Rachas.calcula(habito, porDia, hoy)
    }

    fun cumplimientos(habito: Habito): Int = completadas.count { it.habitoId == habito.id }
}
