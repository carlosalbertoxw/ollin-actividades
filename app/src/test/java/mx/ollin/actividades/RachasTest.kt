package mx.ollin.actividades

import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.domain.model.DiasSemana
import mx.ollin.actividades.domain.model.Frecuencia
import mx.ollin.actividades.domain.usecase.Rachas
import mx.ollin.actividades.domain.usecase.UnidadRacha
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RachasTest {

    private val lunes = LocalDate.of(2026, 8, 3)   // lunes
    private val diario = Habito(id = 1, nombre = "Leer")

    private fun dias(vararg fechas: LocalDate) = fechas.associateWith { 1 }

    @Test
    fun `sin cumplimientos la racha es cero`() {
        val resumen = Rachas.calcula(diario, emptyMap(), lunes)
        assertEquals(0, resumen.actual)
        assertEquals(0, resumen.mejor)
    }

    @Test
    fun `tres dias seguidos cuentan tres`() {
        val hoy = lunes.plusDays(2)
        val resumen = Rachas.calcula(
            diario,
            dias(lunes, lunes.plusDays(1), hoy),
            hoy
        )
        assertEquals(3, resumen.actual)
        assertEquals(3, resumen.mejor)
    }

    @Test
    fun `el dia de hoy sin marcar no rompe la racha`() {
        val hoy = lunes.plusDays(3)
        val resumen = Rachas.calcula(
            diario,
            dias(lunes, lunes.plusDays(1), lunes.plusDays(2)),
            hoy
        )
        assertEquals(3, resumen.actual)
    }

    @Test
    fun `un dia saltado si la rompe`() {
        val hoy = lunes.plusDays(4)
        val resumen = Rachas.calcula(
            diario,
            // Falta el dia +2, asi que solo cuenta lo posterior al hueco.
            dias(lunes, lunes.plusDays(1), lunes.plusDays(3), hoy),
            hoy
        )
        assertEquals(2, resumen.actual)
        assertEquals(2, resumen.mejor)
    }

    @Test
    fun `la mejor racha sobrevive a la caida de la actual`() {
        val hoy = lunes.plusDays(6)
        val resumen = Rachas.calcula(
            diario,
            dias(lunes, lunes.plusDays(1), lunes.plusDays(2), lunes.plusDays(3), hoy),
            hoy
        )
        assertEquals(1, resumen.actual)
        assertEquals(4, resumen.mejor)
    }

    @Test
    fun `el fin de semana no rompe un habito de lunes a viernes`() {
        val entreSemana = Habito(
            id = 2,
            nombre = "Enfoque",
            frecuencia = Frecuencia.DIAS_ELEGIDOS,
            diasSemana = DayOfWeek.entries
                .filter { it.value <= 5 }
                .fold(0) { acc, dia -> DiasSemana.alterna(acc, dia) }
        )
        val viernes = lunes.plusDays(4)
        val siguienteLunes = lunes.plusDays(7)

        val resumen = Rachas.calcula(
            entreSemana,
            dias(lunes, lunes.plusDays(1), lunes.plusDays(2), lunes.plusDays(3), viernes),
            siguienteLunes
        )
        // Sabado y domingo no tocaban: la racha de cinco sigue viva el lunes.
        assertEquals(5, resumen.actual)
    }

    @Test
    fun `una meta de dos veces al dia no se cumple con una`() {
        val doble = diario.copy(metaDiaria = 2)
        val resumen = Rachas.calcula(doble, mapOf(lunes to 1), lunes)
        assertEquals(0, resumen.actual)

        val cumplido = Rachas.calcula(doble, mapOf(lunes to 2), lunes)
        assertEquals(1, cumplido.actual)
    }

    // ------------------------------------------------- frecuencias periodicas

    private fun cadaDias(n: Int, ancla: LocalDate) = Habito(
        id = 10,
        nombre = "Regar las plantas",
        frecuencia = Frecuencia.CADA_DIAS,
        intervaloDias = n,
        ancla = ancla
    )

    private fun cadaMeses(n: Int, ancla: LocalDate) = Habito(
        id = 11,
        nombre = "Revision del coche",
        frecuencia = Frecuencia.CADA_MESES,
        intervaloMeses = n,
        ancla = ancla
    )

    @Test
    fun `un habito quincenal solo toca cada quince dias desde su ancla`() {
        val habito = cadaDias(15, lunes)
        assertEquals(true, habito.tocaHoy(lunes))
        assertEquals(false, habito.tocaHoy(lunes.plusDays(1)))
        assertEquals(false, habito.tocaHoy(lunes.plusDays(14)))
        assertEquals(true, habito.tocaHoy(lunes.plusDays(15)))
        assertEquals(true, habito.tocaHoy(lunes.plusDays(30)))
        // Antes del ancla no toca: el habito todavia no existia.
        assertEquals(false, habito.tocaHoy(lunes.minusDays(15)))
    }

    @Test
    fun `la racha quincenal cuenta repeticiones, no dias`() {
        val habito = cadaDias(15, lunes)
        val resumen = Rachas.calcula(
            habito,
            dias(lunes, lunes.plusDays(15), lunes.plusDays(30)),
            lunes.plusDays(30)
        )
        assertEquals(3, resumen.actual)
        assertEquals("veces", resumen.unidad)
    }

    @Test
    fun `marcar un habito quincenal con retraso no rompe la racha`() {
        val habito = cadaDias(15, lunes)
        // La segunda vez se marco dos dias tarde, dentro del mismo ciclo.
        val resumen = Rachas.calcula(
            habito,
            dias(lunes, lunes.plusDays(17)),
            lunes.plusDays(20)
        )
        assertEquals(2, resumen.actual)
    }

    @Test
    fun `saltarse un ciclo entero si rompe la racha quincenal`() {
        val habito = cadaDias(15, lunes)
        // Se cumplio el primero y el tercero; el ciclo de en medio quedo vacio.
        val resumen = Rachas.calcula(
            habito,
            dias(lunes, lunes.plusDays(30)),
            lunes.plusDays(30)
        )
        assertEquals(1, resumen.actual)
        assertEquals(1, resumen.mejor)
    }

    @Test
    fun `el ciclo en curso es de cortesia y no rompe la racha`() {
        val habito = cadaDias(15, lunes)
        // Toco el dia 15 y aun no se marca, pero el ciclo sigue abierto.
        val resumen = Rachas.calcula(habito, dias(lunes), lunes.plusDays(16))
        assertEquals(1, resumen.actual)
    }

    @Test
    fun `un habito mensual toca el mismo dia de cada mes`() {
        val diez = LocalDate.of(2026, 1, 10)
        val habito = cadaMeses(1, diez)
        assertEquals(true, habito.tocaHoy(diez))
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 2, 10)))
        assertEquals(false, habito.tocaHoy(LocalDate.of(2026, 2, 11)))
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 3, 10)))
    }

    @Test
    fun `un habito bimestral se salta el mes de en medio`() {
        val enero = LocalDate.of(2026, 1, 5)
        val habito = cadaMeses(2, enero)
        assertEquals(true, habito.tocaHoy(enero))
        assertEquals(false, habito.tocaHoy(LocalDate.of(2026, 2, 5)))
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 3, 5)))
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 5, 5)))
    }

    /**
     * Un habito anclado al 31 no puede tocar el 31 de febrero. Se recorre al
     * ultimo dia del mes corto, pero el mes siguiente vuelve al 31: si se
     * encadenaran los saltos se quedaria pegado en el 28 para siempre.
     */
    @Test
    fun `un habito anclado a fin de mes se recorre sin perder su dia`() {
        val treintaYUno = LocalDate.of(2026, 1, 31)
        val habito = cadaMeses(1, treintaYUno)
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 2, 28)))
        assertEquals(false, habito.tocaHoy(LocalDate.of(2026, 3, 28)))
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun `la racha mensual cuenta meses cumplidos seguidos`() {
        val diez = LocalDate.of(2026, 1, 10)
        val habito = cadaMeses(1, diez)
        val resumen = Rachas.calcula(
            habito,
            dias(diez, LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 12)),
            LocalDate.of(2026, 3, 20)
        )
        // El de marzo se marco dos dias tarde y sigue contando: es su ciclo.
        assertEquals(3, resumen.actual)
        assertEquals("veces", resumen.unidad)
    }

    @Test
    fun `sin ancla explicita se cuenta desde el dia en que se creo el habito`() {
        val creado = LocalDate.of(2026, 8, 3).atStartOfDay(java.time.ZoneId.systemDefault())
        val habito = Habito(
            id = 12,
            nombre = "Cambiar el filtro",
            frecuencia = Frecuencia.CADA_DIAS,
            intervaloDias = 30,
            ancla = null,
            creadoEn = creado.toInstant().toEpochMilli()
        )
        assertEquals(LocalDate.of(2026, 8, 3), habito.anclaEfectiva())
        assertEquals(true, habito.tocaHoy(LocalDate.of(2026, 9, 2)))
    }

    @Test
    fun `el habito semanal cuenta semanas`() {
        val semanal = Habito(
            id = 3,
            nombre = "Gimnasio",
            frecuencia = Frecuencia.SEMANAL,
            metaSemanal = 3
        )
        val cumplidos = buildMap {
            // Tres dias en la primera semana y tres en la segunda.
            listOf(0L, 2L, 4L, 7L, 9L, 11L).forEach { put(lunes.plusDays(it), 1) }
        }
        val resumen = Rachas.calcula(semanal, cumplidos, lunes.plusDays(11))
        assertEquals(UnidadRacha.SEMANAS, resumen.unidadRacha)
        assertEquals(2, resumen.actual)
    }
}
