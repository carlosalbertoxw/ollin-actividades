package com.carlosalbertoxw.ollin.actividades.data.excel

/**
 * Modo de columnas de la hoja Registros.
 *
 * COMPACTO emite solo las cinco columnas indispensables, para quien quiere la
 * hoja lo mas angosta posible. EXTENDIDO agrega ambito, horas de inicio y fin,
 * la medida con su unidad, el habito de origen y las notas.
 */
enum class EsquemaExportacion(val etiqueta: String, val descripcion: String) {
    EXTENDIDO(
        "Extendido",
        "Agrega Ámbito, Inicio, Fin, Cantidad, Unidad, Hábito y Notas. Es el que conserva todo."
    ),
    COMPACTO(
        "Compacto",
        "Solo las 5 columnas indispensables: fecha, título, categoría, estado y minutos."
    );

    val columnas: List<String>
        get() = when (this) {
            EXTENDIDO -> listOf(
                "Fecha", "Titulo", "Categoria", "Ambito", "Estado", "Inicio", "Fin",
                "Minutos", "Cantidad", "Unidad", "Habito", "Notas"
            )
            COMPACTO -> listOf("Fecha", "Titulo", "Categoria", "Estado", "Minutos")
        }

    /** Indice 1-based de una columna dentro de Registros, o 0 si no existe en este modo. */
    fun indiceDe(nombre: String): Int = columnas.indexOfFirst {
        it.equals(nombre, ignoreCase = true)
    }.let { if (it < 0) 0 else it + 1 }

    val columnaFecha: Int get() = indiceDe("Fecha")
    val columnaCategoria: Int get() = indiceDe("Categoria")
    val columnaEstado: Int get() = indiceDe("Estado")
    val columnaMinutos: Int get() = indiceDe("Minutos")
}

/**
 * Pestañas que Ollin sabe generar. El usuario elige cuales exportar; Registros
 * es obligatoria porque es la unica que contiene la bitacora y de la que
 * dependen las formulas de todas las demas.
 */
enum class HojaExportable(
    val titulo: String,
    val descripcion: String,
    val obligatoria: Boolean = false
) {
    REGISTROS(
        "Registros",
        "Toda la bitácora, una actividad por renglón. Es la fuente de las demás hojas.",
        obligatoria = true
    ),
    POR_DIA(
        "Por dia",
        "Minutos y sesiones de cada día con actividad, para ver la constancia de un vistazo."
    ),
    POR_CATEGORIA(
        "Por categoria",
        "Cuánto tiempo se fue a cada categoría y qué tajada del total representa."
    ),
    HABITOS(
        "Habitos",
        "Los hábitos con su cadencia, su racha actual y la mejor que has tenido."
    ),
    CATEGORIAS(
        "Categorias",
        "El catálogo con su ámbito y su color. Sirve para reordenarlo fuera del teléfono."
    ),
    DICCIONARIOS(
        "Diccionarios",
        "Listas de categorías, ámbitos, estados, unidades y hábitos. Alimenta los desplegables de Registros."
    );

    companion object {
        /** Seleccion inicial: el libro completo. */
        val PREDETERMINADAS: Set<HojaExportable> = entries.toSet()

        /** Solo lo que se puede volver a importar, para quien quiere el respaldo. */
        val MINIMA: Set<HojaExportable> = setOf(REGISTROS, CATEGORIAS, HABITOS, DICCIONARIOS)

        fun normaliza(seleccion: Set<HojaExportable>): Set<HojaExportable> =
            seleccion + entries.filter { it.obligatoria }
    }
}
