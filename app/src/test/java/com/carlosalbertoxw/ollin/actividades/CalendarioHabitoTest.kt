package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.ModoCiclo
import com.carlosalbertoxw.ollin.actividades.domain.usecase.CalendarioHabito
import com.carlosalbertoxw.ollin.actividades.domain.usecase.Rachas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Cuando toca un habito periodico, en los dos modos.
 *
 * El ejemplo de todas estas pruebas es el mismo: **cada quince dias, anclado al
 * 1 de agosto**. Con fechas fijas toca el 1, el 16 y el 31; contando desde el
 * ultimo cumplimiento, el 16 se convierte en lo que decida quien lo haga.
 */
class CalendarioHabitoTest {

    private val agosto1 = LocalDate.of(2026, 8, 1)

    private fun cadaQuince(modo: ModoCiclo) = Habito(
        id = 1,
        nombre = "Cambiar el filtro",
        frecuencia = Frecuencia.CADA_DIAS,
        intervaloDias = 15,
        ancla = agosto1,
        modoCiclo = modo
    )

    private fun dias(vararg dia: Int): Set<LocalDate> =
        dia.map { LocalDate.of(2026, 8, it) }.toSet()

    // ------------------------------------------------------- fechas fijas

    @Test
    fun `con fechas fijas el calendario no se mueve aunque se haga tarde`() {
        val habito = cadaQuince(ModoCiclo.CALENDARIO)
        // Tocaba el 16 y se hizo el 20, con cuatro dias de retraso.
        val cumplidos = dias(1, 20)

        val ocurrencias = CalendarioHabito.ocurrencias(habito, cumplidos, LocalDate.of(2026, 9, 20))

        assertEquals(
            "El siguiente sigue siendo el 31, no quince dias despues del 20",
            listOf(
                agosto1,
                LocalDate.of(2026, 8, 16),
                LocalDate.of(2026, 8, 31),
                LocalDate.of(2026, 9, 15)
            ),
            ocurrencias
        )
    }

    // --------------------------------------------- desde el ultimo cumplido

    /** Lo que se pidio: hacerlo tarde recorre el calendario desde ese dia. */
    @Test
    fun `contando desde el ultimo, hacerlo tarde recorre las siguientes`() {
        val habito = cadaQuince(ModoCiclo.DESDE_ULTIMO)
        val cumplidos = dias(1, 20)

        val ocurrencias = CalendarioHabito.ocurrencias(habito, cumplidos, LocalDate.of(2026, 9, 10))

        assertEquals(
            "Tras cumplirlo el 20, el siguiente cae quince dias despues: el 4 de septiembre",
            listOf(agosto1, LocalDate.of(2026, 8, 16), LocalDate.of(2026, 9, 4)),
            ocurrencias
        )
    }

    @Test
    fun `contando desde el ultimo, sin cumplir no hay siguiente`() {
        val habito = cadaQuince(ModoCiclo.DESDE_ULTIMO)
        // Se cumplio el 1 y nada mas: el ciclo del 16 sigue abierto.
        val cumplidos = dias(1)

        val ocurrencias = CalendarioHabito.ocurrencias(habito, cumplidos, LocalDate.of(2026, 10, 30))

        assertEquals(
            "Dos meses sin hacerlo son una ocurrencia vencida, no cuatro",
            listOf(agosto1, LocalDate.of(2026, 8, 16)),
            ocurrencias
        )
    }

    // ------------------------------------------------------------ vencido

    @Test
    fun `lo que toco y no se hizo sigue pendiente los dias siguientes`() {
        val habito = cadaQuince(ModoCiclo.CALENDARIO)
        val cumplidos = dias(1)

        assertTrue("El 16 toca", CalendarioHabito.pendienteEl(habito, cumplidos, agosto1.plusDays(15)))
        assertTrue(
            "El 20 sigue pendiente, no desaparece hasta el 31",
            CalendarioHabito.pendienteEl(habito, cumplidos, LocalDate.of(2026, 8, 20))
        )
    }

    @Test
    fun `cumplirlo tarde lo quita de pendiente`() {
        val habito = cadaQuince(ModoCiclo.CALENDARIO)
        val cumplidos = dias(1, 20)

        assertFalse(
            CalendarioHabito.pendienteEl(habito, cumplidos, LocalDate.of(2026, 8, 21))
        )
        assertTrue(
            "Y el 31 vuelve a tocar",
            CalendarioHabito.pendienteEl(habito, cumplidos, LocalDate.of(2026, 8, 31))
        )
    }

    @Test
    fun `un habito diario no arrastra lo de ayer`() {
        val diario = Habito(id = 2, nombre = "Leer", frecuencia = Frecuencia.DIARIA)

        // Toca todos los dias, pero no por lo que se dejo de hacer ayer: se
        // pregunta a la entidad, que no sabe de deudas.
        assertTrue(CalendarioHabito.pendienteEl(diario, emptySet(), agosto1))
    }

    // -------------------------------------------------------------- racha

    @Test
    fun `hacerlo con unos dias de retraso no rompe la racha`() {
        val habito = cadaQuince(ModoCiclo.DESDE_ULTIMO)
        // 1, y luego el 20 en vez del 16: cuatro dias tarde.
        val cumplidos = mapOf(agosto1 to 1, LocalDate.of(2026, 8, 20) to 1)

        val racha = Rachas.calcula(habito, cumplidos, LocalDate.of(2026, 8, 21))

        assertEquals("Dos ciclos seguidos", 2, racha.actual)
    }

    /**
     * La regla que hace que la racha pueda romperse cuando el calendario lo
     * decide quien cumple: se rompe si en el hueco cupo otro ciclo entero.
     */
    @Test
    fun `dejar pasar un ciclo entero si rompe la racha`() {
        val habito = cadaQuince(ModoCiclo.DESDE_ULTIMO)
        // 1, y luego el 5 de septiembre: veinte dias despues del 16 que tocaba,
        // o sea que en el hueco cupo otro ciclo de quince.
        val cumplidos = mapOf(agosto1 to 1, LocalDate.of(2026, 9, 5) to 1)

        val racha = Rachas.calcula(habito, cumplidos, LocalDate.of(2026, 9, 6))

        assertEquals("El ciclo saltado la corta", 1, racha.actual)
        assertEquals(1, racha.mejor)
    }

    @Test
    fun `el modo de fabrica es el de siempre`() {
        assertEquals(
            "Un habito que ya existia no puede cambiar de comportamiento al actualizar",
            ModoCiclo.CALENDARIO,
            Habito(nombre = "x").modoCiclo
        )
    }
}
