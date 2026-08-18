package com.carlosalbertoxw.ollin.actividades.data.excel

import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.CategoriaDao
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.HabitoDao
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.normalizaClave
import java.time.DayOfWeek

/** Lo que la fase de catalogos dejo hecho, para sumarlo al resultado final. */
internal data class ResumenCatalogos(
    val categoriasCreadas: List<String> = emptyList(),
    val categoriasActualizadas: Int = 0,
    val habitosCreados: List<String> = emptyList(),
    val habitosActualizados: Int = 0,
    /** Los nombres de las pestañas que si venian en el libro. */
    val hojas: List<String> = emptyList(),
    val diagnosticos: List<Diagnostico> = emptyList()
)

/**
 * Lee las pestañas de catalogo de un libro —Categorias, Habitos y
 * Diccionarios— y las vuelca en la base antes de tocar los registros.
 *
 * Por que antes: la hoja de Registros nombra sus categorias y habitos por
 * texto, y solo puede enlazarlos si ya existen. Importando primero los
 * catalogos, un registro cae en la categoria con su ambito y su color de
 * verdad, en vez de en una recien inventada como PERSONAL.
 *
 * **El nombre no se toca nunca.** Es la llave con la que se emparejan el
 * archivo y la base, y ademas lleva indice unico: renombrar por la ortografia
 * del archivo podria chocar contra otra fila y tumbar la importacion entera.
 * Lo que si se actualiza de una categoria que ya existe es su ambito, su color,
 * su orden y si esta archivada, que es justamente para lo que sirve poder
 * reordenar el catalogo fuera del telefono.
 */
internal class ImportadorCatalogos(
    private val categoriaDao: CategoriaDao,
    private val habitoDao: HabitoDao
) {

    private companion object {
        val COLUMNAS_CATEGORIA: Map<String, List<String>> = mapOf(
            "nombre" to listOf("categoria", "categorias", "nombre", "nombre de la categoria", "category"),
            "ambito" to listOf("ambito", "area", "tipo", "scope"),
            "color" to listOf("color", "colorhex", "color hex", "hex"),
            "archivada" to listOf("archivada", "archivado", "oculta", "archived"),
            "orden" to listOf("orden", "posicion", "order")
        )

        val COLUMNAS_HABITO: Map<String, List<String>> = mapOf(
            "nombre" to listOf("habito", "habitos", "nombre", "habit"),
            "categoria" to listOf("categoria", "category", "rubro"),
            "cadencia" to listOf("cadencia", "frecuencia", "periodicidad"),
            "ancla" to listOf(
                "cuenta desde", "ancla", "desde", "fecha de inicio", "inicio del ciclo",
                "fecha ancla", "anchor"
            ),
            "meta" to listOf("meta diaria", "meta", "veces al dia"),
            "minutos" to listOf("minutos sugeridos", "minutos", "duracion sugerida"),
            "activo" to listOf("activo", "activa", "vigente"),
            "orden" to listOf("orden", "posicion", "order"),
            "notas" to listOf("notas", "nota", "comentario", "comentarios", "observaciones")
        )

        /**
         * De Diccionarios solo se aprovechan las dos listas que el usuario
         * escribe. Ambitos, estados y unidades son enumeraciones fijas de la
         * app: leerlas de un archivo no agregaria nada y si permitiria
         * inventarse valores que ninguna pantalla sabe enseñar.
         */
        val COLUMNAS_DICCIONARIO: Map<String, List<String>> = mapOf(
            "categorias" to listOf("categorias", "categoria"),
            "habitos" to listOf("habitos", "habito")
        )

        /** Cadencias con nombre propio que [Habito.cadencia] sabe escribir. */
        val DIAS_FIJOS: Map<String, Int> = mapOf(
            "entre semana" to 0b0011111,
            "fin de semana" to 0b1100000,
            "ningun dia" to 0
        )

        val CADA_TANTOS_DIAS: Map<String, Int> = mapOf(
            "cada semana" to 7,
            "cada quincena" to 15
        )

        val CADA_TANTOS_MESES: Map<String, Int> = mapOf(
            "cada mes" to 1,
            "cada trimestre" to 3,
            "cada semestre" to 6,
            "cada ano" to 12
        )

        val POR_SEMANA = Regex("^(\\d+) dias? por semana$")
        val CADA_DIAS = Regex("^cada (\\d+) dias?$")
        val CADA_MESES = Regex("^cada (\\d+) (?:mes|meses)$")
    }

    /** Lo que una cadencia escrita en el libro dice de la plantilla del habito. */
    private data class Cadencia(
        val frecuencia: Frecuencia,
        val metaSemanal: Int? = null,
        val diasSemana: Int? = null,
        val intervaloDias: Int? = null,
        val intervaloMeses: Int? = null
    )

    /** Indice de lo que ya existe, por nombre normalizado. */
    private lateinit var categorias: MutableMap<String, Categoria>
    private lateinit var habitos: MutableMap<String, Habito>

    private val diagnosticos = mutableListOf<Diagnostico>()
    private val categoriasCreadas = mutableListOf<String>()
    private val habitosCreados = mutableListOf<String>()
    private val hojas = mutableListOf<String>()
    private var categoriasActualizadas = 0
    private var habitosActualizados = 0

    suspend fun aplica(libro: LibroLeido, opciones: OpcionesImportacion): ResumenCatalogos {
        categorias = categoriaDao.todas().associateByTo(mutableMapOf()) { it.nombre.normalizaClave() }
        habitos = habitoDao.todos().associateByTo(mutableMapOf()) { it.nombre.normalizaClave() }

        // Categorias primero: un habito puede apuntar a una de ellas.
        leeCategorias(libro, opciones)
        leeHabitos(libro, opciones)
        // Diccionarios al final y solo para rellenar: sus columnas son nombres
        // sueltos, sin ambito ni cadencia, asi que cualquier otra hoja sabe mas.
        leeDiccionarios(libro, opciones)

        return ResumenCatalogos(
            categoriasCreadas = categoriasCreadas.toList(),
            categoriasActualizadas = categoriasActualizadas,
            habitosCreados = habitosCreados.toList(),
            habitosActualizados = habitosActualizados,
            hojas = hojas.toList(),
            diagnosticos = diagnosticos.toList()
        )
    }

    // ---------------------------------------------------------- Categorias

    private suspend fun leeCategorias(libro: LibroLeido, opciones: OpcionesImportacion) {
        val hoja = libro.hojaLlamada("Categorias") ?: return
        val mapa = hoja.reconoce(COLUMNAS_CATEGORIA, requeridas = listOf("nombre")) ?: run {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "La hoja Categorías no trae una columna con el nombre de la categoría. Se ignoró."
            )
            return
        }
        hojas += hoja.nombre

        hoja.renglones(mapa).forEach { renglon ->
            val nombre = renglon.texto("nombre") ?: return@forEach
            val ambito = renglon.texto("ambito")?.let(::leeAmbito)
            val color = renglon.texto("color")?.takeIf { it.startsWith("#") }
            val archivada = renglon.booleano("archivada")
            val orden = renglon.entero("orden")

            val existente = categorias[nombre.normalizaClave()]
            if (existente != null) {
                val actualizada = existente.copy(
                    ambito = ambito ?: existente.ambito,
                    colorHex = color ?: existente.colorHex,
                    archivada = archivada ?: existente.archivada,
                    orden = orden ?: existente.orden
                )
                if (actualizada != existente) {
                    categoriaDao.actualiza(actualizada)
                    categorias[nombre.normalizaClave()] = actualizada
                    categoriasActualizadas++
                }
                return@forEach
            }

            if (!opciones.creaFaltantes) {
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "La categoría \"$nombre\" no existe en Ollin y no se creó.",
                    renglon.numero
                )
                return@forEach
            }
            crea(nombre, ambito ?: Ambito.PERSONAL, color, archivada ?: false, orden)
        }
    }

    private suspend fun crea(
        nombre: String,
        ambito: Ambito,
        color: String?,
        archivada: Boolean,
        orden: Int?
    ) {
        val nueva = Categoria(
            nombre = nombre,
            ambito = ambito,
            colorHex = color,
            archivada = archivada,
            orden = orden ?: categorias.size
        )
        val id = categoriaDao.inserta(nueva)
        categorias[nombre.normalizaClave()] = nueva.copy(id = id)
        categoriasCreadas += nombre
    }

    // ------------------------------------------------------------- Habitos

    private suspend fun leeHabitos(libro: LibroLeido, opciones: OpcionesImportacion) {
        val hoja = libro.hojaLlamada("Habitos") ?: return
        val mapa = hoja.reconoce(COLUMNAS_HABITO, requeridas = listOf("nombre")) ?: run {
            diagnosticos += Diagnostico(
                Severidad.AVISO,
                "La hoja Hábitos no trae una columna con el nombre del hábito. Se ignoró."
            )
            return
        }
        hojas += hoja.nombre

        // Cumplimientos, racha actual y mejor racha no se leen a proposito: son
        // resultados que la app recalcula desde los registros, y aceptarlos del
        // archivo dejaria en pantalla una racha que ningun dia sostiene.
        hoja.renglones(mapa).forEachIndexed { posicion, renglon ->
            val nombre = renglon.texto("nombre") ?: return@forEachIndexed
            val categoriaId = renglon.texto("categoria")
                ?.let { categorias[it.normalizaClave()]?.id }
            val meta = renglon.entero("meta")?.takeIf { it > 0 }
            val minutos = renglon.entero("minutos")?.takeIf { it > 0 }
            val activo = renglon.booleano("activo")
            val orden = renglon.entero("orden")
            val notas = renglon.texto("notas")
            val ancla = renglon.fecha("ancla")

            val textoCadencia = renglon.texto("cadencia")
            val cadencia = textoCadencia?.let(::leeCadencia)
            if (textoCadencia != null && cadencia == null) {
                diagnosticos += Diagnostico(
                    Severidad.AVISO,
                    "No entendí la cadencia \"$textoCadencia\" de \"$nombre\". " +
                        "Se quedo con la que tenia.",
                    renglon.numero
                )
            }

            val existente = habitos[nombre.normalizaClave()]
            if (existente != null) {
                val actualizado = existente.copy(
                    categoriaId = categoriaId ?: existente.categoriaId,
                    metaDiaria = meta ?: existente.metaDiaria,
                    minutosSugeridos = minutos ?: existente.minutosSugeridos,
                    activo = activo ?: existente.activo,
                    orden = orden ?: existente.orden,
                    notas = notas ?: existente.notas,
                    ancla = ancla ?: existente.ancla
                ).con(cadencia)
                if (actualizado != existente) {
                    habitoDao.actualiza(actualizado)
                    habitos[nombre.normalizaClave()] = actualizado
                    habitosActualizados++
                }
                return@forEachIndexed
            }

            if (!opciones.creaFaltantes) return@forEachIndexed

            val nuevo = Habito(
                nombre = nombre,
                categoriaId = categoriaId,
                metaDiaria = meta ?: 1,
                minutosSugeridos = minutos,
                activo = activo ?: true,
                // Sin esta fecha el habito nace hoy y su ciclo se recorre: al
                // restaurar un respaldo, lo que tocaba el dia 3 pasaria a tocar
                // el dia de la importacion. Ver "Cuenta desde" en docs/excel.md.
                ancla = ancla,
                // Sin columna de orden manda el lugar que ocupa en la hoja: es
                // el orden que alguien acomodo al editarla.
                orden = orden ?: posicion,
                notas = notas
            ).con(cadencia)
            val id = habitoDao.inserta(nuevo)
            habitos[nombre.normalizaClave()] = nuevo.copy(id = id)
            habitosCreados += nombre
        }
    }

    /** Deja en el habito solo lo que la cadencia leida sabe; el resto no se toca. */
    private fun Habito.con(cadencia: Cadencia?): Habito = when (cadencia) {
        null -> this
        else -> copy(
            frecuencia = cadencia.frecuencia,
            metaSemanal = cadencia.metaSemanal ?: metaSemanal,
            diasSemana = cadencia.diasSemana ?: diasSemana,
            intervaloDias = cadencia.intervaloDias ?: intervaloDias,
            intervaloMeses = cadencia.intervaloMeses ?: intervaloMeses
        )
    }

    // -------------------------------------------------------- Diccionarios

    /**
     * De aqui solo salen nombres, sin ambito ni cadencia. Por eso unicamente
     * crea lo que falta y jamas actualiza: si la categoria ya existe, lo que la
     * base tiene siempre es mas rico que una celda con su nombre.
     */
    private suspend fun leeDiccionarios(libro: LibroLeido, opciones: OpcionesImportacion) {
        val hoja = libro.hojaLlamada("Diccionarios") ?: return
        val mapa = hoja.reconoce(COLUMNAS_DICCIONARIO) ?: return
        if (!mapa.tiene("categorias") && !mapa.tiene("habitos")) return
        hojas += hoja.nombre
        if (!opciones.creaFaltantes) return

        val renglones = hoja.renglones(mapa)

        // Las categorias van completas antes que los habitos por la misma razon
        // que en el resto del importador: un habito puede colgar de una de ellas.
        renglones.forEach { renglon ->
            val nombre = renglon.texto("categorias") ?: return@forEach
            if (categorias.containsKey(nombre.normalizaClave())) return@forEach
            crea(nombre, Ambito.PERSONAL, color = null, archivada = false, orden = null)
        }

        renglones.forEach { renglon ->
            val nombre = renglon.texto("habitos") ?: return@forEach
            if (habitos.containsKey(nombre.normalizaClave())) return@forEach
            val nuevo = Habito(nombre = nombre, orden = habitos.size)
            val id = habitoDao.inserta(nuevo)
            habitos[nombre.normalizaClave()] = nuevo.copy(id = id)
            habitosCreados += nombre
        }
    }

    // ------------------------------------------------------------ cadencia

    /**
     * El camino de vuelta de [Habito.cadencia]. Se acepta tambien el nombre
     * crudo de la [Frecuencia] y su etiqueta, para un archivo escrito a mano.
     *
     * "Todos los dias" resuelve a DIARIA aunque tambien sea lo que escribe un
     * habito de dias elegidos con los siete marcados: se comportan igual, y
     * DIARIA es la que la pantalla enseña mas limpia.
     */
    private fun leeCadencia(texto: String): Cadencia? {
        val clave = texto.normalizaClave()
        if (clave.isEmpty()) return null

        Frecuencia.entries.firstOrNull {
            it.name.normalizaClave() == clave || it.etiqueta.normalizaClave() == clave
        }?.let { return Cadencia(it) }

        DIAS_FIJOS[clave]?.let { return Cadencia(Frecuencia.DIAS_ELEGIDOS, diasSemana = it) }
        CADA_TANTOS_DIAS[clave]?.let { return Cadencia(Frecuencia.CADA_DIAS, intervaloDias = it) }
        CADA_TANTOS_MESES[clave]?.let { return Cadencia(Frecuencia.CADA_MESES, intervaloMeses = it) }

        POR_SEMANA.find(clave)?.let {
            return Cadencia(Frecuencia.SEMANAL, metaSemanal = it.groupValues[1].toInt())
        }
        CADA_DIAS.find(clave)?.let {
            return Cadencia(Frecuencia.CADA_DIAS, intervaloDias = it.groupValues[1].toInt())
        }
        CADA_MESES.find(clave)?.let {
            return Cadencia(Frecuencia.CADA_MESES, intervaloMeses = it.groupValues[1].toInt())
        }

        return mascaraDeIniciales(clave)
            ?.let { Cadencia(Frecuencia.DIAS_ELEGIDOS, diasSemana = it) }
    }

    /** "L M X J V" de vuelta al mapa de bits que guarda [DiasSemana]. */
    private fun mascaraDeIniciales(clave: String): Int? {
        val partes = clave.split(" ").filter { it.isNotEmpty() }
        if (partes.isEmpty()) return null
        var mascara = 0
        partes.forEach { parte ->
            val dia = DayOfWeek.entries.firstOrNull {
                Tiempo.inicialDia(it).normalizaClave() == parte
            } ?: return null
            mascara = mascara or (1 shl (dia.value - 1))
        }
        return mascara
    }

    private fun leeAmbito(texto: String): Ambito? {
        val clave = texto.normalizaClave()
        return Ambito.entries.firstOrNull {
            it.name.normalizaClave() == clave || it.etiqueta.normalizaClave() == clave
        }
    }
}
