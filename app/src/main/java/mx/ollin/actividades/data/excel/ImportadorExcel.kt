package mx.ollin.actividades.data.excel

import mx.ollin.actividades.data.db.Actividad
import mx.ollin.actividades.data.db.ActividadDao
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.CategoriaDao
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.data.db.HabitoDao
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.domain.model.Unidad
import mx.ollin.actividades.domain.model.normalizaClave
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Severidad { INFO, AVISO, ERROR }

data class Diagnostico(
    val severidad: Severidad,
    val mensaje: String,
    val fila: Int? = null
)

data class OpcionesImportacion(
    /** Da de alta las categorias y habitos que el archivo mencione y no existan. */
    val creaFaltantes: Boolean = true,
    /** Vacia la bitacora antes de importar. Si es falso, agrega. */
    val reemplazarTodo: Boolean = true
)

data class ResultadoImportacion(
    val filasLeidas: Int = 0,
    val importadas: Int = 0,
    val omitidas: Int = 0,
    val categoriasCreadas: List<String> = emptyList(),
    val habitosCreados: List<String> = emptyList(),
    val sinCategoria: Int = 0,
    val diagnosticos: List<Diagnostico> = emptyList()
) {
    val huboProblemas: Boolean get() = diagnosticos.any { it.severidad != Severidad.INFO }
}

/**
 * Lee un libro de Excel y lo vuelca en la bitacora de Ollin.
 *
 * Reconoce los encabezados sin importar acentos ni mayusculas, y admite hojas
 * que no salieron de aqui: con una columna de fecha y otra de titulo ya hay
 * suficiente para registrar. Lo que no venga se completa con una regla explicita
 * en vez de rechazarse, porque una importacion que falla por una columna
 * ausente obliga a editar el archivo a mano justo cuando menos se quiere.
 */
class ImportadorExcel(
    private val categoriaDao: CategoriaDao,
    private val habitoDao: HabitoDao,
    private val actividadDao: ActividadDao
) {

    private companion object {
        val SINONIMOS: Map<String, List<String>> = mapOf(
            "fecha" to listOf("fecha", "dia", "date", "day"),
            "titulo" to listOf("titulo", "actividad", "tarea", "nombre", "descripcion", "concepto", "title"),
            "categoria" to listOf("categoria", "category", "rubro"),
            "ambito" to listOf("ambito", "area", "tipo"),
            "estado" to listOf("estado", "status"),
            "inicio" to listOf("inicio", "hora inicio", "hora de inicio", "comienzo", "start"),
            "fin" to listOf("fin", "hora fin", "hora de fin", "termino", "end"),
            "minutos" to listOf("minutos", "duracion", "duracion minutos", "tiempo", "min"),
            "cantidad" to listOf("cantidad", "medida", "valor"),
            "unidad" to listOf("unidad", "unit"),
            "habito" to listOf("habito", "habit"),
            "notas" to listOf("notas", "nota", "comentario", "comentarios", "observaciones")
        )

        val FORMATOS_FECHA = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
        )

        /**
         * Hora a la que se ancla lo que no trae hora. El mediodia es la que
         * menos miente cuando ya nadie recuerda a que hora fue, y evita que un
         * registro se corra de dia al cambiar de huso.
         */
        val HORA_NEUTRA: LocalTime = LocalTime.NOON
    }

    /** Fila ya interpretada pero todavia sin ids de base. */
    private data class FilaCruda(
        val numeroFila: Int,
        val dia: LocalDate,
        val titulo: String,
        val categoria: String?,
        val ambito: Ambito?,
        val estado: EstadoActividad,
        val inicio: LocalTime?,
        val fin: LocalTime?,
        val minutos: Int?,
        val cantidad: Double?,
        val unidad: Unidad,
        val habito: String?,
        val notas: String?
    )

    suspend fun importa(
        entrada: InputStream,
        opciones: OpcionesImportacion = OpcionesImportacion()
    ): ResultadoImportacion {
        val libro = XlsxLector.lee(entrada)
        val hoja = eligeHojaDeDatos(libro)
            ?: return ResultadoImportacion(
                diagnosticos = listOf(
                    Diagnostico(
                        Severidad.ERROR,
                        "No encontre ninguna hoja con columnas de Fecha y Titulo."
                    )
                )
            )
        return procesa(hoja, opciones)
    }

    /** La hoja buena es la que trae, al menos, Fecha + Titulo. */
    private fun eligeHojaDeDatos(libro: LibroLeido): HojaLeida? {
        val preferida = libro.hoja("Registros")
        if (preferida != null && mapaColumnas(preferida).esUtilizable()) return preferida
        return libro.hojas.firstOrNull { mapaColumnas(it).esUtilizable() }
    }

    private class MapaColumnas(private val indices: Map<String, Int>) {
        operator fun get(clave: String): Int? = indices[clave]
        fun esUtilizable(): Boolean =
            indices.containsKey("fecha") && indices.containsKey("titulo")
    }

    private fun mapaColumnas(hoja: HojaLeida): MapaColumnas {
        val encabezado = hoja.filas.firstOrNull() ?: return MapaColumnas(emptyMap())
        val indices = mutableMapOf<String, Int>()
        encabezado.forEachIndexed { i, celda ->
            val texto = celda.comoTexto()?.normalizaClave() ?: return@forEachIndexed
            SINONIMOS.forEach { (clave, alias) ->
                if (!indices.containsKey(clave) && alias.any { it == texto }) indices[clave] = i
            }
        }
        return MapaColumnas(indices)
    }

    private suspend fun procesa(
        hoja: HojaLeida,
        opciones: OpcionesImportacion
    ): ResultadoImportacion {
        val columnas = mapaColumnas(hoja)
        val diagnosticos = mutableListOf<Diagnostico>()
        val crudas = mutableListOf<FilaCruda>()
        var omitidas = 0

        hoja.filas.drop(1).forEachIndexed { indice, fila ->
            val numeroFila = indice + 2
            if (fila.all { it.estaVacia }) return@forEachIndexed

            fun celda(clave: String) = columnas[clave]?.let { fila.getOrNull(it) }

            val dia = celda("fecha")?.let(::leeFecha)
            val titulo = celda("titulo")?.comoTexto()?.trim()

            if (dia == null || titulo.isNullOrBlank()) {
                omitidas++
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "Renglon incompleto (falta la fecha o el titulo). Se omitio.",
                    numeroFila
                )
                return@forEachIndexed
            }

            val minutos = celda("minutos")?.let(::leeEntero)
            crudas += FilaCruda(
                numeroFila = numeroFila,
                dia = dia,
                titulo = titulo,
                categoria = celda("categoria")?.comoTexto()?.trim()?.ifBlank { null },
                ambito = celda("ambito")?.comoTexto()?.let(::leeAmbito),
                estado = celda("estado")?.comoTexto()?.let(::leeEstado)
                    ?: infiereEstado(dia, minutos),
                inicio = celda("inicio")?.let(::leeHora),
                fin = celda("fin")?.let(::leeHora),
                minutos = minutos,
                cantidad = celda("cantidad")?.numero,
                unidad = celda("unidad")?.comoTexto()?.let(::leeUnidad) ?: Unidad.NINGUNA,
                habito = celda("habito")?.comoTexto()?.trim()?.ifBlank { null },
                notas = celda("notas")?.comoTexto()?.trim()?.ifBlank { null }
            )
        }

        if (crudas.isEmpty()) {
            return ResultadoImportacion(
                filasLeidas = (hoja.filas.size - 1).coerceAtLeast(0),
                omitidas = omitidas,
                diagnosticos = diagnosticos +
                    Diagnostico(Severidad.ERROR, "No hubo ningun renglon aprovechable.")
            )
        }

        // ---- 1. Resolver categorias -----------------------------------------
        val categoriasCreadas = mutableListOf<String>()
        val indiceCategorias = categoriaDao.todas()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()
        var sinCategoria = 0

        for (nombre in crudas.mapNotNull { it.categoria }.distinct()) {
            val clave = nombre.normalizaClave()
            if (indiceCategorias.containsKey(clave)) continue
            if (!opciones.creaFaltantes) {
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "La categoria \"$nombre\" no existe en Ollin y no se creo."
                )
                continue
            }
            // El ambito sale del primer renglon que la use; si el archivo no lo
            // trae, personal es el cajon menos comprometido.
            val ambito = crudas.firstOrNull {
                it.categoria?.normalizaClave() == clave && it.ambito != null
            }?.ambito ?: Ambito.PERSONAL
            val id = categoriaDao.inserta(
                Categoria(nombre = nombre, ambito = ambito, orden = indiceCategorias.size)
            )
            indiceCategorias[clave] = Categoria(id = id, nombre = nombre, ambito = ambito)
            categoriasCreadas += nombre
        }

        // ---- 2. Resolver habitos --------------------------------------------
        val habitosCreados = mutableListOf<String>()
        val indiceHabitos = habitoDao.todos()
            .associateBy { it.nombre.normalizaClave() }
            .toMutableMap()

        for (nombre in crudas.mapNotNull { it.habito }.distinct()) {
            val clave = nombre.normalizaClave()
            if (indiceHabitos.containsKey(clave)) continue
            if (!opciones.creaFaltantes) continue
            val categoriaId = crudas.firstOrNull { it.habito?.normalizaClave() == clave }
                ?.categoria
                ?.let { indiceCategorias[it.normalizaClave()]?.id }
            val id = habitoDao.inserta(
                Habito(nombre = nombre, categoriaId = categoriaId, orden = indiceHabitos.size)
            )
            indiceHabitos[clave] = Habito(id = id, nombre = nombre, categoriaId = categoriaId)
            habitosCreados += nombre
        }

        // ---- 3. Insertar ------------------------------------------------------
        if (opciones.reemplazarTodo) actividadDao.eliminaTodas()

        val aInsertar = crudas.map { cruda ->
            val categoriaId = cruda.categoria?.let { indiceCategorias[it.normalizaClave()]?.id }
            if (categoriaId == null) sinCategoria++
            construye(cruda, categoriaId, cruda.habito?.let { indiceHabitos[it.normalizaClave()]?.id })
        }
        actividadDao.insertaTodas(aInsertar)

        if (sinCategoria > 0) {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "$sinCategoria actividades quedaron sin categoria. Puedes asignarlas " +
                    "abriendolas desde el registro."
            )
        }

        return ResultadoImportacion(
            filasLeidas = (hoja.filas.size - 1).coerceAtLeast(0),
            importadas = aInsertar.size,
            omitidas = omitidas,
            categoriasCreadas = categoriasCreadas,
            habitosCreados = habitosCreados,
            sinCategoria = sinCategoria,
            diagnosticos = diagnosticos
        )
    }

    /**
     * Arma la actividad dejando coherentes inicio, fin y duracion, que es la
     * misma regla que aplica el repositorio al guardar desde la pantalla: una
     * actividad completada nunca se queda sin minutos, porque toda la analitica
     * suma esa columna.
     */
    private fun construye(cruda: FilaCruda, categoriaId: Long?, habitoId: Long?): Actividad {
        val horaInicio = cruda.inicio ?: HORA_NEUTRA
        val inicio = Tiempo.instante(cruda.dia.atTime(horaInicio))

        // Los minutos capturados mandan sobre el reloj: si el archivo trae los
        // dos y no cuadran, la duracion es el dato que alguien escribio a
        // proposito. Solo si falta se deduce de la hora de fin.
        val minutos = cruda.minutos
            ?: cruda.fin?.let { fin ->
                val bruto = fin.toSecondOfDay() - horaInicio.toSecondOfDay()
                // Una actividad que termina "antes" de empezar cruzo la medianoche.
                val segundos = if (bruto < 0) bruto + 86_400 else bruto
                segundos / 60
            }
            ?: 0

        return when (cruda.estado) {
            EstadoActividad.COMPLETADO -> Actividad(
                titulo = cruda.titulo,
                categoriaId = categoriaId,
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                fin = inicio.plusSeconds(minutos * 60L),
                dia = cruda.dia,
                duracionMinutos = minutos.coerceAtLeast(0),
                cantidad = cruda.cantidad,
                unidad = cruda.unidad,
                habitoId = habitoId,
                notas = cruda.notas
            )

            // Ni pendiente ni en curso tienen duracion cerrada: dejarles minutos
            // los haria sumar en la analitica sin haber ocurrido.
            EstadoActividad.PENDIENTE, EstadoActividad.EN_CURSO -> Actividad(
                titulo = cruda.titulo,
                categoriaId = categoriaId,
                estado = cruda.estado,
                inicio = inicio,
                fin = null,
                dia = cruda.dia,
                duracionMinutos = null,
                cantidad = cruda.cantidad,
                unidad = cruda.unidad,
                habitoId = habitoId,
                notas = cruda.notas
            )
        }
    }

    // ------------------------------------------------------------- lectura

    private fun leeFecha(celda: CeldaLeida): LocalDate? {
        celda.numero?.let { return Ooxml.desdeSerial(it) }
        val texto = celda.texto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        FORMATOS_FECHA.forEach { formato ->
            runCatching { return LocalDate.parse(texto, formato) }
        }
        return null
    }

    /** Una hora puede venir como fraccion de dia (Excel) o como "08:30". */
    private fun leeHora(celda: CeldaLeida): LocalTime? {
        celda.numero?.let { return Ooxml.horaDesdeSerial(it) }
        val texto = celda.texto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        return runCatching { LocalTime.parse(texto) }.getOrNull()
            ?: runCatching { LocalTime.parse(texto, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
    }

    private fun leeEntero(celda: CeldaLeida): Int? {
        celda.numero?.let { return Math.round(it).toInt() }
        return celda.texto?.trim()?.filter { it.isDigit() || it == '-' }?.toIntOrNull()
    }

    private fun leeEstado(texto: String): EstadoActividad? {
        val clave = texto.normalizaClave()
        return EstadoActividad.entries.firstOrNull {
            it.name.normalizaClave() == clave || it.etiqueta.normalizaClave() == clave
        } ?: when {
            clave.startsWith("hecho") || clave.startsWith("listo") || clave == "si" ->
                EstadoActividad.COMPLETADO
            clave.startsWith("corriendo") || clave.startsWith("activo") ->
                EstadoActividad.EN_CURSO
            else -> null
        }
    }

    private fun leeAmbito(texto: String): Ambito? {
        val clave = texto.normalizaClave()
        return Ambito.entries.firstOrNull {
            it.name.normalizaClave() == clave || it.etiqueta.normalizaClave() == clave
        }
    }

    private fun leeUnidad(texto: String): Unidad? {
        val clave = texto.normalizaClave()
        if (clave.isEmpty()) return Unidad.NINGUNA
        return Unidad.entries.firstOrNull {
            it.name.normalizaClave() == clave ||
                it.etiqueta.normalizaClave() == clave ||
                (it.abreviatura.isNotEmpty() && it.abreviatura.normalizaClave() == clave)
        }
    }

    /**
     * Sin columna de estado hay que decidir. Lo del pasado se da por hecho —es
     * una bitacora, no una agenda— y lo del futuro por pendiente.
     */
    private fun infiereEstado(dia: LocalDate, minutos: Int?): EstadoActividad = when {
        dia.isAfter(Tiempo.hoy()) && (minutos == null || minutos <= 0) -> EstadoActividad.PENDIENTE
        else -> EstadoActividad.COMPLETADO
    }
}
