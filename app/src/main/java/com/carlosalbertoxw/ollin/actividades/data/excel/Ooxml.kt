package com.carlosalbertoxw.ollin.actividades.data.excel

import java.time.LocalDate
import java.time.LocalTime

/** Utilidades de bajo nivel del formato SpreadsheetML. */
object Ooxml {

    /**
     * Excel cuenta los dias desde el 30/12/1899: 2026-01-01 es el serial 46023.
     */
    const val DESPLAZAMIENTO_SERIAL = 25_569L

    private const val SEGUNDOS_POR_DIA = 86_400.0

    fun aSerial(fecha: LocalDate): Long = fecha.toEpochDay() + DESPLAZAMIENTO_SERIAL

    fun desdeSerial(serial: Double): LocalDate =
        LocalDate.ofEpochDay(Math.floor(serial).toLong() - DESPLAZAMIENTO_SERIAL)

    /** La hora del dia es la fraccion decimal del serial. 12:00 es 0.5. */
    fun aFraccion(hora: LocalTime): Double = hora.toSecondOfDay() / SEGUNDOS_POR_DIA

    /**
     * Hora contenida en un serial. Sirve tanto para una celda de solo hora
     * (0.75) como para una de fecha y hora (46023.75).
     */
    fun horaDesdeSerial(serial: Double): LocalTime {
        val fraccion = serial - Math.floor(serial)
        val segundos = Math.round(fraccion * SEGUNDOS_POR_DIA).toInt().coerceIn(0, 86_399)
        return LocalTime.ofSecondOfDay(segundos.toLong())
    }

    /** 1 -> A, 27 -> AA. */
    fun letraColumna(indice: Int): String {
        require(indice >= 1) { "La columna arranca en 1" }
        val sb = StringBuilder()
        var n = indice
        while (n > 0) {
            val resto = (n - 1) % 26
            sb.append(('A' + resto))
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** "A" -> 1, "AA" -> 27. */
    fun indiceColumna(letras: String): Int {
        var n = 0
        for (c in letras.uppercase()) {
            if (c !in 'A'..'Z') continue
            n = n * 26 + (c - 'A' + 1)
        }
        return n
    }

    fun referencia(fila: Int, columna: Int): String = "${letraColumna(columna)}$fila"

    /** Separa "BC12" en ("BC", 12). */
    fun partesReferencia(ref: String): Pair<String, Int> {
        val letras = ref.takeWhile { it.isLetter() }
        val numero = ref.dropWhile { it.isLetter() }.toIntOrNull() ?: 0
        return letras to numero
    }

    fun escapaXml(texto: String): String {
        val sb = StringBuilder(texto.length + 16)
        for (c in texto) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else ->
                    // XML 1.0 no admite estos caracteres de control ni siquiera escapados.
                    if (c.code < 0x20 && c != '\n' && c != '\t' && c != '\r') sb.append(' ')
                    else sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Un nombre de hoja no admite : \ / ? * [ ] y tiene tope de 31 caracteres.
     * Ademas Excel rechaza que empiece o termine con apostrofo.
     */
    fun saneaNombreHoja(nombre: String): String {
        val limpio = nombre.replace(Regex("[\\\\/*\\[\\]:?]"), "-").trim().trim('\'')
        val recortado = if (limpio.length > 31) limpio.take(31) else limpio
        return recortado.ifBlank { "Hoja" }
    }

    /** Referencia a otra hoja: entrecomilla solo si hace falta. */
    fun refHoja(nombreHoja: String, rango: String): String {
        val necesitaComillas = nombreHoja.any { !it.isLetterOrDigit() && it != '_' }
        val nombre = if (necesitaComillas) "'${nombreHoja.replace("'", "''")}'" else nombreHoja
        return "$nombre!$rango"
    }
}
