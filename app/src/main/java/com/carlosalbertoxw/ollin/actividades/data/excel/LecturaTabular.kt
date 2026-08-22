package com.carlosalbertoxw.ollin.actividades.data.excel

import com.carlosalbertoxw.ollin.actividades.domain.model.normalizaClave
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Reconocimiento de encabezados, compartido por todo lo que se importa.
 *
 * No basta con leer la fila 1. Las hojas que Ollin genera para analisis llevan
 * titulo, una linea de explicacion y un renglon en blanco antes de la tabla, asi
 * que el encabezado de Habitos vive en la fila 4. Y un libro ajeno puede traer
 * cualquier cosa arriba.
 */
internal class MapaColumnas(
    /** Indice 0-based del renglon de encabezados. Los datos empiezan despues. */
    val filaEncabezado: Int,
    private val indices: Map<String, Int>
) {
    operator fun get(clave: String): Int? = indices[clave]

    fun tiene(vararg claves: String): Boolean = claves.all { indices.containsKey(it) }

    /** Cuantas columnas conocidas reconocio. Sirve para elegir entre candidatas. */
    val reconocidas: Int get() = indices.size
}

/**
 * Busca entre las primeras filas cual es el encabezado.
 *
 * Se queda con la que reconoce mas columnas, no con la primera que sirva: en la
 * hoja de Habitos el titulo de la fila 1 dice justamente "Habitos", que es
 * tambien el nombre de la columna, y quedarse con la primera coincidencia daria
 * por encabezado un renglon de una sola celda.
 */
internal fun HojaLeida.reconoce(
    sinonimos: Map<String, List<String>>,
    requeridas: List<String> = emptyList(),
    filasCandidatas: Int = 8
): MapaColumnas? = filas.take(filasCandidatas)
    .mapIndexedNotNull { fila, celdas ->
        val indices = mutableMapOf<String, Int>()
        celdas.forEachIndexed { columna, celda ->
            val texto = celda.comoTexto()?.normalizaClave().orEmpty()
            if (texto.isEmpty()) return@forEachIndexed
            sinonimos.forEach { (clave, alias) ->
                if (!indices.containsKey(clave) && alias.any { it == texto }) {
                    indices[clave] = columna
                }
            }
        }
        MapaColumnas(fila, indices).takeIf { mapa ->
            indices.isNotEmpty() && requeridas.all { indices.containsKey(it) }
        }
    }
    .maxByOrNull { it.reconocidas }

/** Un renglon de datos ya emparejado con el encabezado de su hoja. */
internal class Renglon(
    /** Numero de fila tal como lo enseña Excel, para poder reportarlo. */
    val numero: Int,
    private val celdas: List<CeldaLeida>,
    private val mapa: MapaColumnas
) {
    val estaVacio: Boolean get() = celdas.all { it.estaVacia }

    fun celda(clave: String): CeldaLeida? = mapa[clave]?.let { celdas.getOrNull(it) }

    fun texto(clave: String): String? = celda(clave)?.comoTexto()?.trim()?.ifBlank { null }

    fun entero(clave: String): Int? = celda(clave)?.let(::leeEntero)

    fun decimal(clave: String): Double? = celda(clave)?.numero

    fun booleano(clave: String): Boolean? = texto(clave)?.let(::leeBooleano)

    /**
     * Una fecha puede venir como serial de Excel o escrita a mano en cualquiera
     * de los formatos habituales.
     *
     * Vive aqui y no en el importador de registros porque tiene dos clientes:
     * la hoja de Registros y la columna "Cuenta desde" de Habitos. Dos lectores
     * de fecha acabarian aceptando formatos distintos, y el usuario no tiene
     * por que saber cual pestana admite cual.
     */
    fun fecha(clave: String): LocalDate? {
        val celda = celda(clave) ?: return null
        celda.numero?.let { return Ooxml.desdeSerial(it) }
        val texto = celda.texto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        FORMATOS_FECHA.forEach { formato ->
            runCatching { return LocalDate.parse(texto, formato) }
        }
        return null
    }

    /**
     * Una hora puede venir como fraccion de dia (lo que escribe Excel al dar
     * formato de hora) o escrita a mano, que es lo habitual en esta columna.
     */
    fun hora(clave: String): LocalTime? {
        val celda = celda(clave) ?: return null
        celda.numero?.let { return Ooxml.horaDesdeSerial(it) }
        val texto = celda.texto?.trim().orEmpty()
        if (texto.isEmpty()) return null
        FORMATOS_HORA.forEach { formato ->
            runCatching { return LocalTime.parse(texto, formato) }
        }
        return null
    }

    private companion object {
        val FORMATOS_HORA = listOf(
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ISO_LOCAL_TIME
        )

        val FORMATOS_FECHA = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
        )
    }
}

/** Los renglones que siguen al encabezado, ya sin los que vienen en blanco. */
internal fun HojaLeida.renglones(mapa: MapaColumnas): List<Renglon> =
    filas.drop(mapa.filaEncabezado + 1)
        .mapIndexed { i, celdas -> Renglon(mapa.filaEncabezado + i + 2, celdas, mapa) }
        .filterNot { it.estaVacio }

/** Cuantos renglones hay debajo del encabezado, en blanco incluidos. */
internal fun HojaLeida.altoDeDatos(mapa: MapaColumnas): Int =
    (filas.size - mapa.filaEncabezado - 1).coerceAtLeast(0)

/**
 * La hoja que se llame como alguno de estos nombres. Se compara sin acentos ni
 * mayusculas para que "Categorías" y "categorias" sean la misma pestaña.
 */
internal fun LibroLeido.hojaLlamada(vararg nombres: String): HojaLeida? {
    val buscadas = nombres.map { it.normalizaClave() }
    return hojas.firstOrNull { it.nombre.normalizaClave() in buscadas }
}

internal fun leeEntero(celda: CeldaLeida): Int? {
    celda.numero?.let { return Math.round(it).toInt() }
    return celda.texto?.trim()?.filter { it.isDigit() || it == '-' }?.toIntOrNull()
}

/**
 * El lector convierte las celdas booleanas de Excel en VERDADERO/FALSO, pero un
 * archivo escrito a mano trae lo que sea. Lo que no se reconoce devuelve nulo y
 * quien llama decide: no es lo mismo "no lo dijo" que "dijo que no".
 */
internal fun leeBooleano(texto: String): Boolean? = when (texto.normalizaClave()) {
    "verdadero", "true", "si", "s", "x", "1", "activo", "activa", "vigente" -> true
    "falso", "false", "no", "n", "0", "inactivo", "inactiva", "archivado", "archivada" -> false
    else -> null
}
