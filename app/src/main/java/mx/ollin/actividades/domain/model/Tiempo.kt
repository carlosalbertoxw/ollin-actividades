package mx.ollin.actividades.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Todo lo que tiene que ver con relojes y calendarios vive aqui.
 *
 * Regla de la casa: el instante se guarda en UTC (milisegundos epoch) porque es
 * un punto en la linea del tiempo y no debe moverse al viajar; el dia se guarda
 * aparte como dia epoch local porque "cuanto trabaje el martes" es una pregunta
 * del calendario de quien la hace, no del meridiano de Greenwich.
 */
object Tiempo {

    private val ES: Locale = Locale.forLanguageTag("es-MX")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm", ES)
    private val FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM", ES)
    private val FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", ES)
    private val FECHA_HORA = DateTimeFormatter.ofPattern("d MMM, HH:mm", ES)

    fun zona(): ZoneId = ZoneId.systemDefault()

    fun ahora(): Instant = Instant.now()

    fun hoy(): LocalDate = LocalDate.now(zona())

    fun local(instante: Instant): LocalDateTime = LocalDateTime.ofInstant(instante, zona())

    fun dia(instante: Instant): LocalDate = local(instante).toLocalDate()

    fun instante(fechaHora: LocalDateTime): Instant = fechaHora.atZone(zona()).toInstant()

    fun hora(instante: Instant): String = HORA.format(local(instante))

    fun fechaCorta(dia: LocalDate): String = FECHA_CORTA.format(dia)

    fun fechaLarga(dia: LocalDate): String =
        FECHA_LARGA.format(dia).replaceFirstChar { it.titlecase(ES) }

    fun fechaHora(instante: Instant): String = FECHA_HORA.format(local(instante))

    /** "Hoy" y "Ayer" se leen mejor que la fecha cuando son lo mas frecuente. */
    fun diaRelativo(dia: LocalDate): String = when (dia) {
        hoy() -> "Hoy"
        hoy().minusDays(1) -> "Ayer"
        hoy().plusDays(1) -> "Manana"
        else -> fechaLarga(dia)
    }

    fun inicialDia(dia: DayOfWeek): String = when (dia) {
        DayOfWeek.MONDAY -> "L"
        DayOfWeek.TUESDAY -> "M"
        DayOfWeek.WEDNESDAY -> "X"
        DayOfWeek.THURSDAY -> "J"
        DayOfWeek.FRIDAY -> "V"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "D"
    }

    /**
     * Duracion en formato corto: "45 min", "2 h 05". Se evita el decimal de
     * horas porque "1.75 h" obliga a hacer una cuenta mental para nada.
     */
    fun duracion(minutos: Int): String {
        if (minutos <= 0) return "0 min"
        val horas = minutos / 60
        val resto = minutos % 60
        return when {
            horas == 0 -> "$resto min"
            resto == 0 -> "$horas h"
            else -> String.format(ES, "%d h %02d", horas, resto)
        }
    }

    /** Version larga, para totales donde el "h" solo se puede confundir. */
    fun duracionLarga(minutos: Int): String {
        if (minutos <= 0) return "sin tiempo"
        val horas = minutos / 60
        val resto = minutos % 60
        return when {
            horas == 0 -> "$resto minutos"
            resto == 0 -> if (horas == 1) "1 hora" else "$horas horas"
            else -> "${if (horas == 1) "1 hora" else "$horas horas"} $resto min"
        }
    }

    /** Cronometro vivo: mm:ss debajo de una hora, h:mm:ss arriba. */
    fun cronometro(segundos: Long): String {
        val s = segundos.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val seg = s % 60
        return if (h > 0) String.format(ES, "%d:%02d:%02d", h, m, seg)
        else String.format(ES, "%02d:%02d", m, seg)
    }
}

/**
 * Los dias de la semana de un habito se guardan como mapa de bits: el lunes es
 * el bit 0 y el domingo el 6. Un entero cabe en una columna y se compara sin
 * abrir una tabla aparte.
 */
object DiasSemana {
    const val TODOS = 0b1111111

    fun contiene(mascara: Int, dia: DayOfWeek): Boolean =
        mascara and (1 shl (dia.value - 1)) != 0

    fun alterna(mascara: Int, dia: DayOfWeek): Int =
        mascara xor (1 shl (dia.value - 1))

    fun etiqueta(mascara: Int): String = when {
        mascara and TODOS == TODOS -> "Todos los dias"
        mascara == 0 -> "Ningun dia"
        mascara and 0b0011111 == 0b0011111 && mascara and 0b1100000 == 0 -> "Entre semana"
        mascara and 0b1100000 == 0b1100000 && mascara and 0b0011111 == 0 -> "Fin de semana"
        else -> DayOfWeek.entries
            .filter { contiene(mascara, it) }
            .joinToString(" ") { Tiempo.inicialDia(it) }
    }
}
