package com.carlosalbertoxw.ollin.actividades.domain.usecase

import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * En que se mide una racha. No todos los habitos cuentan dias: el semanal
 * cuenta semanas y el periodico cuenta repeticiones, porque decir "3 dias" de
 * algo que toca cada dos meses no significaria nada.
 */
enum class UnidadRacha(val etiqueta: String) {
    DIAS("dias"),
    SEMANAS("semanas"),
    VECES("veces")
}

data class ResumenRacha(
    val actual: Int,
    val mejor: Int,
    val unidadRacha: UnidadRacha
) {
    val unidad: String get() = unidadRacha.etiqueta
}

/**
 * La racha de un habito.
 *
 * Dos decisiones que no son obvias:
 *
 * 1. El dia de hoy no rompe la racha mientras no termine. Un habito sin marcar
 *    a las nueve de la manana esta pendiente, no fallado; castigarlo desde
 *    temprano es la forma mas rapida de que alguien deje de abrir la app.
 * 2. Los dias que el habito no toca se saltan sin penalizar y sin sumar. Un
 *    habito de lunes a viernes conserva su racha durante el fin de semana.
 */
object Rachas {

    /** Tope de dias hacia atras. Evita recorrer la historia entera del calendario. */
    private const val LIMITE_DIAS = 1_500

    /** Tope de repeticiones que se recorren en una frecuencia periodica. */
    private const val LIMITE_OCURRENCIAS = 600

    fun calcula(
        habito: Habito,
        cumplidosPorDia: Map<LocalDate, Int>,
        hoy: LocalDate
    ): ResumenRacha {
        val meta = habito.metaDiaria.coerceAtLeast(1)
        val dias = cumplidosPorDia.filterValues { it >= meta }.keys
        if (dias.isEmpty()) return ResumenRacha(0, 0, unidadDe(habito))

        return when {
            habito.frecuencia == Frecuencia.SEMANAL -> porSemanas(habito, dias, hoy)
            habito.frecuencia.esPeriodica -> porCiclos(habito, dias, hoy)
            else -> porDias(habito, dias, hoy)
        }
    }

    private fun unidadDe(habito: Habito): UnidadRacha = when {
        habito.frecuencia == Frecuencia.SEMANAL -> UnidadRacha.SEMANAS
        habito.frecuencia.esPeriodica -> UnidadRacha.VECES
        else -> UnidadRacha.DIAS
    }

    /**
     * La racha de un habito periodico: cuantas repeticiones seguidas se
     * cumplieron.
     *
     * No se cuenta dia por dia sino por ciclos, y un ciclo se da por cumplido
     * si hay algun registro entre su fecha y la de la siguiente repeticion. Sin
     * esa holgura, marcar un dia tarde algo que toca cada quince romperia la
     * racha, que es exactamente lo contrario de lo que la racha deberia premiar.
     */
    private fun porCiclos(habito: Habito, dias: Set<LocalDate>, hoy: LocalDate): ResumenRacha {
        val ocurrencias = ocurrenciasHasta(habito, hoy)
        if (ocurrencias.isEmpty()) return ResumenRacha(0, 0, UnidadRacha.VECES)

        val cumplidos = ocurrencias.mapIndexed { i, inicio ->
            val siguiente = ocurrencias.getOrNull(i + 1)
            dias.any { !it.isBefore(inicio) && (siguiente == null || it.isBefore(siguiente)) }
        }

        var mejor = 0
        var corrida = 0
        cumplidos.forEach {
            if (it) {
                corrida++
                if (corrida > mejor) mejor = corrida
            } else {
                corrida = 0
            }
        }

        // Hacia atras desde la ultima repeticion. La vigente es de cortesia: aun
        // esta corriendo, todavia no se falla.
        var actual = 0
        for (i in cumplidos.indices.reversed()) {
            if (cumplidos[i]) actual++
            else if (i != cumplidos.lastIndex) break
        }

        return ResumenRacha(actual, maxOf(mejor, actual), UnidadRacha.VECES)
    }

    /** Las fechas en que tocaba, desde el ancla y hasta hoy. */
    private fun ocurrenciasHasta(habito: Habito, hoy: LocalDate): List<LocalDate> {
        val ancla = habito.anclaEfectiva()
        if (ancla.isAfter(hoy)) return emptyList()

        val lista = mutableListOf<LocalDate>()
        var indice = 0L
        while (lista.size < LIMITE_OCURRENCIAS) {
            val fecha = habito.ocurrencia(ancla, indice)
            if (fecha.isAfter(hoy)) break
            lista += fecha
            indice++
        }
        return lista
    }

    private fun porDias(habito: Habito, dias: Set<LocalDate>, hoy: LocalDate): ResumenRacha {
        val aplica = { dia: LocalDate ->
            habito.frecuencia != Frecuencia.DIAS_ELEGIDOS ||
                DiasSemana.contiene(habito.diasSemana, dia.dayOfWeek)
        }

        val primero = dias.min()

        // Racha actual: hacia atras desde hoy, con el dia de hoy de cortesia.
        var actual = 0
        var cursor = hoy
        while (!cursor.isBefore(primero)) {
            if (aplica(cursor)) {
                if (cursor in dias) actual++
                else if (cursor != hoy) break
            }
            cursor = cursor.minusDays(1)
        }

        // Mejor racha: un barrido desde el primer cumplimiento hasta hoy.
        var mejor = 0
        var corrida = 0
        var dia = maxOf(primero, hoy.minusDays(LIMITE_DIAS.toLong()))
        while (!dia.isAfter(hoy)) {
            if (aplica(dia)) {
                if (dia in dias) {
                    corrida++
                    if (corrida > mejor) mejor = corrida
                } else if (dia != hoy) {
                    corrida = 0
                }
            }
            dia = dia.plusDays(1)
        }

        return ResumenRacha(actual, maxOf(mejor, actual), UnidadRacha.DIAS)
    }

    private fun porSemanas(habito: Habito, dias: Set<LocalDate>, hoy: LocalDate): ResumenRacha {
        val campos = WeekFields.of(Locale.forLanguageTag("es-MX"))
        val semanaDe = { dia: LocalDate -> dia.with(campos.dayOfWeek(), 1L) }
        val meta = habito.metaSemanal.coerceAtLeast(1)

        val porSemana = dias.groupingBy(semanaDe).eachCount()
        val cumplidas = porSemana.filterValues { it >= meta }.keys
        if (cumplidas.isEmpty()) return ResumenRacha(0, 0, UnidadRacha.SEMANAS)

        val semanaActual = semanaDe(hoy)
        var actual = 0
        var cursor = semanaActual
        while (true) {
            if (cursor in cumplidas) actual++
            else if (cursor != semanaActual) break
            cursor = cursor.minusWeeks(1)
            if (cursor.isBefore(cumplidas.min())) break
        }

        var mejor = 0
        var corrida = 0
        var semana = cumplidas.min()
        while (!semana.isAfter(semanaActual)) {
            if (semana in cumplidas) {
                corrida++
                if (corrida > mejor) mejor = corrida
            } else if (semana != semanaActual) {
                corrida = 0
            }
            semana = semana.plusWeeks(1)
        }

        return ResumenRacha(actual, maxOf(mejor, actual), UnidadRacha.SEMANAS)
    }
}
