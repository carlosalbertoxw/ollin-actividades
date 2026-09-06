package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.repo.HabitoConAvance
import com.carlosalbertoxw.ollin.actividades.domain.usecase.ResumenRacha
import com.carlosalbertoxw.ollin.actividades.domain.usecase.UnidadRacha
import com.carlosalbertoxw.ollin.actividades.ui.screens.ordenDeHabitos
import com.carlosalbertoxw.ollin.actividades.ui.screens.ordenDeHoy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * En que orden se ven las dos listas de habitos.
 *
 * Se prueban aparte de las pantallas porque el orden es una regla y no un
 * detalle de pintado: lo fallado primero, y despues lo que se puede atender
 * antes.
 */
class OrdenDeListasTest {

    private val hoy = LocalDate.of(2026, 9, 6)

    private fun avance(
        id: Long,
        nombre: String,
        tocaHoy: Boolean = true,
        vencidoDesde: LocalDate? = null,
        proxima: LocalDate? = null
    ) = HabitoConAvance(
        habito = Habito(id = id, nombre = nombre),
        vecesHoy = 0,
        minutosHoy = 0,
        racha = ResumenRacha(0, 0, UnidadRacha.DIAS),
        tocaHoy = tocaHoy,
        vencidoDesde = vencidoDesde,
        proxima = proxima
    )

    private fun nombres(lista: List<HabitoConAvance>) = lista.map { it.habito.nombre }

    // ------------------------------------------------------ pantalla de hoy

    @Test
    fun `en hoy lo vencido sube y lo demas se queda donde estaba`() {
        val lista = listOf(
            avance(1, "Leer"),
            avance(2, "Filtro", vencidoDesde = hoy.minusDays(3)),
            avance(3, "Nadar"),
            avance(4, "Plantas", vencidoDesde = hoy.minusDays(1))
        )

        assertEquals(
            listOf("Filtro", "Plantas", "Leer", "Nadar"),
            nombres(ordenDeHoy(lista))
        )
    }

    @Test
    fun `en hoy entre lo vencido va primero lo que lleva mas esperando`() {
        val lista = listOf(
            avance(1, "Ayer", vencidoDesde = hoy.minusDays(1)),
            avance(2, "Hace tres semanas", vencidoDesde = hoy.minusWeeks(3))
        )

        assertEquals(
            "Una deuda de tres semanas no puede quedar debajo de una de ayer",
            listOf("Hace tres semanas", "Ayer"),
            nombres(ordenDeHoy(lista))
        )
    }

    @Test
    fun `en hoy lo que no toca no aparece`() {
        val lista = listOf(
            avance(1, "Leer"),
            avance(2, "Trimestral", tocaHoy = false, proxima = hoy.plusMonths(2))
        )

        assertEquals(listOf("Leer"), nombres(ordenDeHoy(lista)))
    }

    // -------------------------------------------------- lista de habitos

    @Test
    fun `los habitos van de lo mas atrasado a lo mas lejano`() {
        val lista = listOf(
            avance(1, "Dentro de tres meses", tocaHoy = false, proxima = hoy.plusMonths(3)),
            avance(2, "Hoy"),
            avance(3, "Vencido hace una semana", vencidoDesde = hoy.minusWeeks(1)),
            avance(4, "Pasado manana", tocaHoy = false, proxima = hoy.plusDays(2)),
            avance(5, "Vencido ayer", vencidoDesde = hoy.minusDays(1))
        )

        assertEquals(
            listOf(
                "Vencido hace una semana",
                "Vencido ayer",
                "Hoy",
                "Pasado manana",
                "Dentro de tres meses"
            ),
            nombres(ordenDeHabitos(lista, hoy))
        )
    }

    @Test
    fun `lo que no tiene fecha que dar va al final`() {
        val lista = listOf(
            avance(1, "Sin fecha", tocaHoy = false, proxima = null),
            avance(2, "Dentro de un ano", tocaHoy = false, proxima = hoy.plusYears(1))
        )

        assertEquals(
            "No tener fecha es no saberla todavia, no ser lo mas urgente",
            listOf("Dentro de un ano", "Sin fecha"),
            nombres(ordenDeHabitos(lista, hoy))
        )
    }

    @Test
    fun `dos habitos del mismo dia conservan el orden que traian`() {
        val lista = listOf(
            avance(1, "Segundo de la lista"),
            avance(2, "Tercero de la lista"),
            avance(3, "Primero de la lista", vencidoDesde = hoy.minusDays(2))
        )

        assertEquals(
            "Empatados en fecha manda el orden que eligio su dueno",
            listOf("Primero de la lista", "Segundo de la lista", "Tercero de la lista"),
            nombres(ordenDeHabitos(lista, hoy))
        )
    }
}
