package com.carlosalbertoxw.ollin.actividades.data.repo

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.carlosalbertoxw.ollin.actividades.data.excel.DatosExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.ExportadorExcel
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.excel.ImportadorExcel
import com.carlosalbertoxw.ollin.actividades.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.ResultadoImportacion
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.ConteoEstado
import com.carlosalbertoxw.ollin.actividades.data.db.CumplimientoDia
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.db.TotalAmbito
import com.carlosalbertoxw.ollin.actividades.data.db.TotalCategoria
import com.carlosalbertoxw.ollin.actividades.data.db.TotalDia
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import com.carlosalbertoxw.ollin.actividades.domain.usecase.Rachas
import com.carlosalbertoxw.ollin.actividades.domain.usecase.ResumenRacha
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/** Un habito con lo que hace falta para pintarlo: avance de hoy y racha. */
data class HabitoConAvance(
    val habito: Habito,
    val vecesHoy: Int,
    val minutosHoy: Int,
    val racha: ResumenRacha,
    val tocaHoy: Boolean
) {
    val cumplidoHoy: Boolean get() = vecesHoy >= habito.metaDiaria.coerceAtLeast(1)
}

/**
 * Toda la escritura pasa por aqui. Las pantallas no tocan los DAO, y asi las
 * reglas que mantienen coherentes las marcas de tiempo viven en un solo lugar.
 */
class ActividadesRepositorio(
    private val db: OllinDatabase,
    private val resolver: ContentResolver
) {

    private val categorias = db.categoriaDao()
    private val habitos = db.habitoDao()
    private val actividades = db.actividadDao()

    // ---------------- Consultas ----------------

    fun observaCategorias(): Flow<List<Categoria>> = categorias.observaActivas()

    fun observaTodasLasCategorias(): Flow<List<Categoria>> = categorias.observaTodas()

    fun observaActividades(
        texto: String = "",
        estado: EstadoActividad? = null,
        ambito: Ambito? = null,
        categoriaId: Long? = null,
        desde: LocalDate? = null,
        hasta: LocalDate? = null,
        limite: Int = 500
    ): Flow<List<ActividadDetallada>> = actividades.observaDetalladas(
        texto = texto.trim(),
        estado = estado,
        ambito = ambito,
        categoriaId = categoriaId,
        desde = desde,
        hasta = hasta,
        limite = limite
    )

    fun observaDelDia(dia: LocalDate): Flow<List<ActividadDetallada>> =
        actividades.observaDelDia(dia)

    fun observaEnCurso(): Flow<ActividadDetallada?> = actividades.observaEnCurso()

    fun observaPendientes(hasta: LocalDate = Tiempo.hoy()): Flow<List<ActividadDetallada>> =
        actividades.observaPendientes(hasta)

    fun observaTotalPorCategoria(desde: LocalDate, hasta: LocalDate): Flow<List<TotalCategoria>> =
        actividades.observaTotalPorCategoria(desde, hasta)

    fun observaTotalPorDia(desde: LocalDate, hasta: LocalDate): Flow<List<TotalDia>> =
        actividades.observaTotalPorDia(desde, hasta)

    fun observaTotalPorAmbito(desde: LocalDate, hasta: LocalDate): Flow<List<TotalAmbito>> =
        actividades.observaTotalPorAmbito(desde, hasta)

    fun observaConteoPorEstado(desde: LocalDate, hasta: LocalDate): Flow<List<ConteoEstado>> =
        actividades.observaConteoPorEstado(desde, hasta)

    fun observaSumaDeUnidad(unidad: Unidad, desde: LocalDate, hasta: LocalDate): Flow<Double> =
        actividades.observaSumaDeUnidad(unidad.name, desde, hasta)

    fun observaCumplimientoGlobal(desde: LocalDate, hasta: LocalDate): Flow<List<CumplimientoDia>> =
        actividades.observaCumplimientoGlobal(desde, hasta)

    suspend fun actividad(id: Long): Actividad? = actividades.porId(id)

    suspend fun habito(id: Long): Habito? = habitos.porId(id)

    suspend fun categoria(id: Long): Categoria? = categorias.porId(id)

    /**
     * Los habitos con su avance del dia y su racha. La racha se calcula en
     * Kotlin y no en SQL porque depende de que dias toca el habito, y eso vive
     * en la plantilla; SQLite tendria que reconstruir el calendario.
     *
     * [soloActivos] separa dos usos distintos: la pantalla de hoy solo quiere
     * lo que toca hacer, pero la de habitos los administra, y ahi hay que ver
     * tambien los pausados. Si no, pausar uno equivaldria a perderlo: la app no
     * volveria a ensenarlo por ningun lado y nadie podria reactivarlo.
     */
    fun observaHabitosConAvance(
        dia: LocalDate = Tiempo.hoy(),
        soloActivos: Boolean = true
    ): Flow<List<HabitoConAvance>> =
        combine(
            if (soloActivos) habitos.observaActivos() else habitos.observaTodos(),
            actividades.observaCumplimientosDesde(dia.minusDays(VENTANA_RACHA))
        ) { lista, cumplimientos ->
            val porHabito = cumplimientos.groupBy { it.habitoId }
            lista.map { habito ->
                val historia = porHabito[habito.id].orEmpty()
                val hoy = historia.firstOrNull { it.dia == dia }
                HabitoConAvance(
                    habito = habito,
                    vecesHoy = hoy?.veces ?: 0,
                    minutosHoy = hoy?.minutos ?: 0,
                    racha = Rachas.calcula(habito, historia.associate { it.dia to it.veces }, dia),
                    tocaHoy = habito.tocaHoy(dia)
                )
            }
        }

    // ---------------- Escritura de actividades ----------------

    /**
     * Guarda una actividad dejando coherentes inicio, fin, dia y duracion.
     *
     * Las tres reglas: el dia siempre se deriva del inicio; si hay hora de fin
     * manda el reloj y la duracion se recalcula; si solo hay duracion capturada
     * a mano, se deduce la hora de fin. Nunca se escribe una actividad
     * completada sin duracion, porque toda la analitica suma esa columna.
     */
    suspend fun guarda(actividad: Actividad): Long {
        val normalizada = normaliza(actividad)
        return if (normalizada.id == 0L) {
            actividades.inserta(normalizada)
        } else {
            actividades.actualiza(normalizada)
            normalizada.id
        }
    }

    private fun normaliza(a: Actividad): Actividad {
        val dia = Tiempo.dia(a.inicio)
        return when (a.estado) {
            EstadoActividad.EN_CURSO -> a.copy(
                dia = dia,
                fin = null,
                duracionMinutos = null,
                actualizadoEn = System.currentTimeMillis()
            )

            EstadoActividad.PENDIENTE -> a.copy(
                dia = dia,
                fin = null,
                actualizadoEn = System.currentTimeMillis()
            )

            EstadoActividad.COMPLETADO -> {
                val fin = a.fin
                if (fin != null) {
                    a.copy(
                        dia = dia,
                        duracionMinutos = minutosEntre(a.inicio, fin),
                        actualizadoEn = System.currentTimeMillis()
                    )
                } else {
                    val minutos = a.duracionMinutos ?: 0
                    a.copy(
                        dia = dia,
                        fin = a.inicio.plusSeconds(minutos * 60L),
                        duracionMinutos = minutos,
                        actualizadoEn = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    /**
     * Arranca el cronometro. Solo puede haber una actividad corriendo: si ya
     * habia otra, se cierra con la hora de este momento. Dos cronometros a la
     * vez producirian mas minutos de los que tiene el dia.
     */
    suspend fun iniciaAhora(
        titulo: String,
        categoriaId: Long? = null,
        habitoId: Long? = null,
        notas: String? = null,
        unidad: Unidad = Unidad.NINGUNA
    ): Long {
        val ahora = Tiempo.ahora()
        cierraLoQueCorra(ahora)
        return actividades.inserta(
            Actividad(
                titulo = titulo.trim(),
                categoriaId = categoriaId,
                habitoId = habitoId,
                estado = EstadoActividad.EN_CURSO,
                inicio = ahora,
                dia = Tiempo.dia(ahora),
                unidad = unidad,
                notas = notas?.takeIf { it.isNotBlank() }
            )
        )
    }

    /** Convierte una pendiente en la actividad que corre ahora. */
    suspend fun arranca(id: Long) {
        val actividad = actividades.porId(id) ?: return
        val ahora = Tiempo.ahora()
        cierraLoQueCorra(ahora)
        actividades.actualiza(
            actividad.copy(
                estado = EstadoActividad.EN_CURSO,
                inicio = ahora,
                dia = Tiempo.dia(ahora),
                fin = null,
                duracionMinutos = null,
                actualizadoEn = System.currentTimeMillis()
            )
        )
    }

    /** Cierra la actividad y deja escrita su duracion. */
    suspend fun detiene(id: Long, cantidad: Double? = null): Int {
        val actividad = actividades.porId(id) ?: return 0
        val fin = Tiempo.ahora()
        val minutos = minutosEntre(actividad.inicio, fin)
        actividades.actualiza(
            actividad.copy(
                estado = EstadoActividad.COMPLETADO,
                fin = fin,
                duracionMinutos = minutos,
                cantidad = cantidad ?: actividad.cantidad,
                actualizadoEn = System.currentTimeMillis()
            )
        )
        return minutos
    }

    /** Marca como completada sin haberla cronometrado. */
    suspend fun completa(id: Long, minutos: Int) {
        val actividad = actividades.porId(id) ?: return
        guarda(
            actividad.copy(
                estado = EstadoActividad.COMPLETADO,
                fin = null,
                duracionMinutos = minutos.coerceAtLeast(0)
            )
        )
    }

    suspend fun elimina(id: Long) {
        actividades.porId(id)?.let { actividades.elimina(it) }
    }

    private suspend fun cierraLoQueCorra(momento: Instant) {
        actividades.enCurso().forEach { previa ->
            actividades.actualiza(
                previa.copy(
                    estado = EstadoActividad.COMPLETADO,
                    fin = momento,
                    duracionMinutos = minutosEntre(previa.inicio, momento),
                    actualizadoEn = System.currentTimeMillis()
                )
            )
        }
    }

    // ---------------- Habitos ----------------

    /**
     * Marca un habito como hecho hoy. Deja un registro completado igual que
     * cualquier otro: asi aparece en la bitacora, suma en la analitica y se
     * puede editar despues.
     */
    suspend fun registraHabito(habito: Habito, minutos: Int? = null, dia: LocalDate = Tiempo.hoy()) {
        val duracion = (minutos ?: habito.minutosSugeridos ?: 0).coerceAtLeast(0)
        // Un habito de ayer se ancla al mediodia: es la hora que menos miente
        // cuando ya no se sabe a que hora fue.
        val inicio = if (dia == Tiempo.hoy()) {
            Tiempo.ahora().minusSeconds(duracion * 60L)
        } else {
            Tiempo.instante(dia.atTime(12, 0)).minusSeconds(duracion * 60L)
        }
        actividades.inserta(
            Actividad(
                titulo = habito.nombre,
                categoriaId = habito.categoriaId,
                habitoId = habito.id,
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                fin = inicio.plusSeconds(duracion * 60L),
                dia = dia,
                duracionMinutos = duracion
            )
        )
    }

    /** Deshace el ultimo registro de un habito en un dia. */
    suspend fun deshaceHabito(habitoId: Long, dia: LocalDate = Tiempo.hoy()) {
        actividades.ultimoCumplimiento(habitoId, dia)?.let { actividades.elimina(it) }
    }

    suspend fun guardaHabito(habito: Habito): Long =
        if (habito.id == 0L) habitos.inserta(habito) else {
            habitos.actualiza(habito); habito.id
        }

    suspend fun eliminaHabito(habito: Habito) = habitos.elimina(habito)

    // ---------------- Categorias ----------------

    suspend fun guardaCategoria(categoria: Categoria): Long =
        if (categoria.id == 0L) categorias.inserta(categoria) else {
            categorias.actualiza(categoria); categoria.id
        }

    suspend fun eliminaCategoria(categoria: Categoria) = categorias.elimina(categoria)

    // ---------------- Archivo ----------------

    fun observaConteoActividades(): Flow<Int> = actividades.observaConteo()

    suspend fun importa(uri: Uri, opciones: OpcionesImportacion): ResultadoImportacion =
        withContext(Dispatchers.IO) {
            val importador = ImportadorExcel(categorias, habitos, actividades)
            resolver.openInputStream(uri)?.use { importador.importa(it, opciones) }
                ?: error("No se pudo abrir el archivo seleccionado")
        }

    suspend fun exporta(
        uri: Uri,
        esquema: EsquemaExportacion,
        hojas: Set<HojaExportable>
    ) = withContext(Dispatchers.IO) {
        val datos = DatosExportacion(
            categorias = categorias.todas(),
            habitos = habitos.todos(),
            actividades = actividades.todas()
        )
        abreParaEscribir(uri).use { salida ->
            ExportadorExcel(datos, esquema, hojas).escribeEn(salida)
        }
    }

    /**
     * Abre el destino elegido en el selector del sistema.
     *
     * Se pide "wt" (escribir truncando) porque al sobrescribir un archivo mas
     * grande, sin truncar quedaria la cola del viejo pegada al final y el .xlsx
     * saldria corrupto. Pero no todos los proveedores de documentos soportan
     * ese modo: varios gestores de archivos y los de nube revientan con "wt" y
     * solo aceptan "w". Un archivo recien creado por el selector nace vacio,
     * asi que "w" basta y es preferible a no poder exportar.
     */
    private fun abreParaEscribir(uri: Uri): OutputStream {
        runCatching { resolver.openOutputStream(uri, "wt") }
            .getOrNull()
            ?.let { return it }
        return resolver.openOutputStream(uri, "w")
            ?: error("No se pudo escribir en el archivo seleccionado")
    }

    companion object {
        /** Dias de historia que se leen para calcular rachas. Poco mas de un ano. */
        private const val VENTANA_RACHA = 400L

        /**
         * Minutos entre dos instantes, redondeando al mas cercano. Una sesion
         * de 89 segundos vale mas como "1 min" que como "1 min" truncado de
         * 119: el redondeo reparte el error en vez de sesgarlo hacia abajo.
         */
        fun minutosEntre(inicio: Instant, fin: Instant): Int {
            val millis = (fin.toEpochMilli() - inicio.toEpochMilli()).coerceAtLeast(0L)
            return ((millis + 30_000L) / 60_000L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
    }
}
