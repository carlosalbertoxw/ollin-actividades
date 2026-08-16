package com.carlosalbertoxw.ollin.actividades.domain.model

import java.util.Locale

/**
 * El ambito es el lente con el que se mira un registro. Es lo que permite que
 * una sola tabla de actividades sirva de bitacora de trabajo, de monitor de
 * ejercicio y de tracker de habitos sin tres modelos paralelos.
 */
enum class Ambito(val etiqueta: String) {
    TRABAJO("Trabajo"),
    FISICO("Actividad fisica"),
    HABITO("Habito"),
    PERSONAL("Personal")
}

/** Las tres situaciones en las que puede estar un registro. */
enum class EstadoActividad(val etiqueta: String) {
    PENDIENTE("Pendiente"),
    EN_CURSO("En curso"),
    COMPLETADO("Completado")
}

/**
 * Unidad de la medida opcional que acompana a una actividad. El tiempo ya lo
 * cubren las marcas de inicio y fin; esto es para el esfuerzo que el reloj no
 * ve: los 5 km de una carrera o las 30 repeticiones de una serie.
 */
enum class Unidad(val etiqueta: String, val abreviatura: String, val decimales: Int) {
    NINGUNA("Sin medida", "", 0),
    KILOMETROS("Kilometros", "km", 2),
    METROS("Metros", "m", 0),
    PASOS("Pasos", "pasos", 0),
    REPETICIONES("Repeticiones", "reps", 0),
    SERIES("Series", "series", 0),
    CALORIAS("Calorias", "kcal", 0),
    PAGINAS("Paginas", "pags", 0),
    VASOS("Vasos", "vasos", 0),
    TAREAS("Tareas", "tareas", 0);

    fun formatea(cantidad: Double): String {
        // Locale explicito: con el del sistema, "5.25 km" se volveria "5,25 km"
        // en un telefono en aleman y las pruebas dependerian de donde corren.
        val texto = if (decimales == 0) cantidad.toLong().toString()
        else String.format(Locale.forLanguageTag("es-MX"), "%.${decimales}f", cantidad)
            .trimEnd('0')
            .trimEnd('.', ',')
        return if (abreviatura.isEmpty()) texto else "$texto $abreviatura"
    }
}

/**
 * Cada cuando se espera cumplir un habito.
 *
 * Las tres primeras se apoyan en el calendario semanal. Las dos ultimas cuentan
 * desde una fecha ancla, y son las que permiten lo que no cabe en una semana:
 * cada quince dias, cada mes, cada dos meses o cada cuando se te ocurra.
 */
enum class Frecuencia(val etiqueta: String) {
    DIARIA("Todos los dias"),
    DIAS_ELEGIDOS("Dias elegidos"),
    SEMANAL("Cierto numero por semana"),
    CADA_DIAS("Cada tantos dias"),
    CADA_MESES("Cada tantos meses");

    /** Cierto si la cadencia se cuenta desde una fecha ancla y no por semana. */
    val esPeriodica: Boolean get() = this == CADA_DIAS || this == CADA_MESES
}

/**
 * Quita acentos y mayusculas para comparar nombres escritos de dos maneras.
 * "Reunion" y "reunión" deben caer en la misma categoria.
 */
fun String.normalizaClave(): String = trim()
    .lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
    .replace('ñ', 'n')
    .replace(Regex("\\s+"), " ")
