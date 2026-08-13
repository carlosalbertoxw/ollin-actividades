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
    /** Las que ya existian y el archivo cambio: ambito, color, orden, cadencia. */
    val categoriasActualizadas: Int = 0,
    val habitosActualizados: Int = 0,
    /** Que pestañas del libro se aprovecharon, en el orden en que se leyeron. */
    val hojasLeidas: List<String> = emptyList(),
    val sinCategoria: Int = 0,
    val diagnosticos: List<Diagnostico> = emptyList()
) {
    val huboProblemas: Boolean get() = diagnosticos.any { it.severidad != Severidad.INFO }

    /** Cierto si el libro solo traia catalogos: no hay renglones que contar. */
    val soloCatalogos: Boolean get() = filasLeidas == 0 && hojasLeidas.isNotEmpty()
}

/**
 * Lee un libro de Excel y lo vuelca en la bitacora de Ollin.
 *
 * Aprovecha las cuatro pestañas que sabe reconocer, en este orden y solo si
 * vienen en el archivo:
 *
 * 1. **Categorias** — el catalogo con su ambito, color, orden y si esta archivada.
 * 2. **Habitos** — la plantilla de cada habito con su cadencia y su meta.
 * 3. **Diccionarios** — solo para rellenar los nombres que las anteriores no trajeron.
 * 4. **Registros** — la bitacora.
 *
 * El orden importa: Registros nombra sus categorias y habitos por texto, asi
 * que solo puede enlazarlos con los que ya existen. Leyendo antes los catalogos,
 * un registro cae en la categoria con su color y su ambito de verdad en vez de
 * en una recien inventada.
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

        val REQUERIDAS = listOf("fecha", "titulo")

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
        val catalogos = ImportadorCatalogos(categoriaDao, habitoDao).aplica(libro, opciones)

        val datos = eligeHojaDeDatos(libro)
        if (datos == null) {
            // Un libro de puros catalogos es legitimo: sirve para reordenar las
            // categorias o retocar los habitos sin tocar la bitacora. Lo que no
            // se hace en ese caso es vaciarla, aunque venga marcado reemplazar:
            // no hay nada con que reemplazarla.
            if (catalogos.hojas.isNotEmpty()) {
                return catalogos.aResultado(
                    Diagnostico(
                        Severidad.INFO,
                        "El libro no traia hoja de Registros. Solo se actualizaron los " +
                            "catalogos; la bitacora quedo intacta."
                    )
                )
            }
            return ResultadoImportacion(
                diagnosticos = listOf(
                    Diagnostico(
                        Severidad.ERROR,
                        "No encontre ninguna hoja con columnas de Fecha y Titulo."
                    )
                )
            )
        }
        return procesa(datos.first, datos.second, opciones, catalogos)
    }

    /** La hoja buena es la que trae, al menos, Fecha + Titulo. */
    private fun eligeHojaDeDatos(libro: LibroLeido): Pair<HojaLeida, MapaColumnas>? {
        libro.hojaLlamada("Registros")
            ?.let { hoja -> hoja.reconoce(SINONIMOS, REQUERIDAS)?.let { return hoja to it } }
        return libro.hojas.firstNotNullOfOrNull { hoja ->
            hoja.reconoce(SINONIMOS, REQUERIDAS)?.let { hoja to it }
        }
    }

    private suspend fun procesa(
        hoja: HojaLeida,
        mapa: MapaColumnas,
        opciones: OpcionesImportacion,
        catalogos: ResumenCatalogos
    ): ResultadoImportacion {
        val diagnosticos = catalogos.diagnosticos.toMutableList()
        val crudas = mutableListOf<FilaCruda>()
        var omitidas = 0

        hoja.renglones(mapa).forEach { renglon ->
            val dia = renglon.celda("fecha")?.let(::leeFecha)
            val titulo = renglon.texto("titulo")

            if (dia == null || titulo == null) {
                omitidas++
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "Renglon incompleto (falta la fecha o el titulo). Se omitio.",
                    renglon.numero
                )
                return@forEach
            }

            val minutos = renglon.entero("minutos")
            crudas += FilaCruda(
                numeroFila = renglon.numero,
                dia = dia,
                titulo = titulo,
                categoria = renglon.texto("categoria"),
                ambito = renglon.texto("ambito")?.let(::leeAmbito),
                estado = renglon.texto("estado")?.let(::leeEstado) ?: infiereEstado(dia, minutos),
                inicio = renglon.celda("inicio")?.let(::leeHora),
                fin = renglon.celda("fin")?.let(::leeHora),
                minutos = minutos,
                cantidad = renglon.decimal("cantidad"),
                unidad = renglon.texto("unidad")?.let(::leeUnidad) ?: Unidad.NINGUNA,
                habito = renglon.texto("habito"),
                notas = renglon.texto("notas")
            )
        }

        val filasLeidas = hoja.altoDeDatos(mapa)
        val hojas = catalogos.hojas + hoja.nombre

        if (crudas.isEmpty()) {
            // Con catalogos leidos esto no es un fracaso: un libro exportado con
            // la bitacora vacia sirve igual para acomodar categorias y habitos.
            val aviso = if (catalogos.hojas.isEmpty()) {
                Diagnostico(Severidad.ERROR, "No hubo ningun renglon aprovechable.")
            } else {
                Diagnostico(
                    Severidad.AVISO,
                    "La hoja de Registros no traia renglones aprovechables. Los catalogos " +
                        "si se actualizaron y la bitacora quedo intacta."
                )
            }
            return catalogos.aResultado().copy(
                filasLeidas = filasLeidas,
                omitidas = omitidas,
                hojasLeidas = hojas,
                diagnosticos = diagnosticos + aviso
            )
        }

        // ---- 1. Resolver categorias -----------------------------------------
        // El indice se relee de la base y no del resumen: la fase de catalogos
        // ya dio de alta las suyas, y aqui solo quedan las que unicamente
        // aparecen mencionadas en un renglon de la bitacora.
        val categoriasCreadas = catalogos.categoriasCreadas.toMutableList()
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
        val habitosCreados = catalogos.habitosCreados.toMutableList()
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
            filasLeidas = filasLeidas,
            importadas = aInsertar.size,
            omitidas = omitidas,
            categoriasCreadas = categoriasCreadas,
            habitosCreados = habitosCreados,
            categoriasActualizadas = catalogos.categoriasActualizadas,
            habitosActualizados = catalogos.habitosActualizados,
            hojasLeidas = hojas,
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

/** El resumen de catalogos visto como resultado, para un libro sin Registros. */
private fun ResumenCatalogos.aResultado(vararg extra: Diagnostico) = ResultadoImportacion(
    categoriasCreadas = categoriasCreadas,
    habitosCreados = habitosCreados,
    categoriasActualizadas = categoriasActualizadas,
    habitosActualizados = habitosActualizados,
    hojasLeidas = hojas,
    diagnosticos = diagnosticos + extra
)
