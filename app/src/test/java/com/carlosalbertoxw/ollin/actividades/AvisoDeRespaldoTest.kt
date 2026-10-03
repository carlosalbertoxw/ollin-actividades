package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.AvisoDeRespaldo
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.CoordinadorRecordatorios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * El aviso de Hoy: cuando sale, cuando se va y cuanto dura quitarlo.
 * Sin Android: el reloj se inyecta y se empuja a mano.
 */
class AvisoDeRespaldoTest {

    private val desde = Instant.parse("2026-09-01T12:00:00Z")
    private val plazo = Duration.ofDays(CoordinadorRecordatorios.PLAZO_RESPALDO_DIAS)

    private fun ajustes(
        avisa: Boolean = true,
        respaldoDesde: Long = desde.toEpochMilli(),
        ultimo: Long = 0L,
        ultimoAviso: Long = 0L
    ) = Ajustes(
        avisaRespaldo = avisa,
        respaldoDesde = respaldoDesde,
        ultimoRespaldo = ultimo,
        ultimoAvisoRespaldo = ultimoAviso
    )

    // ------------------------------------------------------ si toca enseñarlo

    @Test
    fun `sale a la semana sin respaldo, con el titulo de la notificacion`() {
        val ahora = desde.plus(plazo)

        val texto = AvisoDeRespaldo.texto(ajustes(), hayBitacora = true, descartado = false, ahora = ahora)

        assertEquals(CoordinadorRecordatorios.tituloDelRespaldo(0L, ahora), texto)
    }

    @Test
    fun `no sale antes de la semana`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(), true, false, desde.plus(Duration.ofDays(6))))
    }

    @Test
    fun `exportar lo quita sin tocar nada mas`() {
        val ahora = desde.plus(Duration.ofDays(30))
        assertNotNull(AvisoDeRespaldo.texto(ajustes(), true, false, ahora))

        // Lo que deja escrito marcaRespaldo: el ancla y el ultimo respaldo, ahora.
        val respaldado = ajustes(respaldoDesde = ahora.toEpochMilli(), ultimo = ahora.toEpochMilli())

        assertNull("Con un respaldo de hoy ya no toca", AvisoDeRespaldo.texto(respaldado, true, false, ahora))
    }

    /**
     * La notificacion de hace un rato no lo apaga: esa marca existe para no
     * repetir la notificacion cada dia, y quien la descarto sigue sin respaldo.
     */
    @Test
    fun `la notificacion ya enviada no lo apaga`() {
        val ahora = desde.plus(Duration.ofDays(10))

        val texto = AvisoDeRespaldo.texto(
            ajustes(ultimoAviso = ahora.minusSeconds(60).toEpochMilli()), true, false, ahora
        )

        assertNotNull(texto)
    }

    @Test
    fun `apagar el recordatorio en Ajustes tambien lo apaga aqui`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(avisa = false), true, false, desde.plus(Duration.ofDays(30))))
    }

    /** Una instalacion sin nada registrado no tiene nada que perder. */
    @Test
    fun `sin bitacora no sale`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(), hayBitacora = false, descartado = false, ahora = desde.plus(plazo)))
    }

    /** Sin ancla todavia: la estrena la notificacion, no este aviso. */
    @Test
    fun `antes del primer plazo no sale`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(respaldoDesde = 0L), true, false, desde.plus(plazo)))
    }

    @Test
    fun `quitado no sale aunque toque`() {
        assertNull(AvisoDeRespaldo.texto(ajustes(), true, descartado = true, ahora = desde.plus(plazo)))
    }

    // ------------------------------------------------- cuanto dura quitarlo

    private var reloj = 5_000_000L
    private fun aviso() = AvisoDeRespaldo(reloj = { reloj })

    @Test
    fun `quitarlo dura mientras la app sigue abierta`() {
        val aviso = aviso()

        aviso.descarta()

        assertTrue(aviso.descartado.value)
    }

    /** El selector de archivos manda la app al fondo: volver de ahi no es abrirla. */
    @Test
    fun `salir un momento y volver no lo trae de regreso`() {
        val aviso = aviso()
        aviso.descarta()

        aviso.alIrAlFondo()
        reloj += AvisoDeRespaldo.AUSENCIA_MILLIS - 1
        aviso.alVolverAlFrente()

        assertTrue(aviso.descartado.value)
    }

    @Test
    fun `abrir la app otra vez lo vuelve a enseñar`() {
        val aviso = aviso()
        aviso.descarta()

        aviso.alIrAlFondo()
        reloj += AvisoDeRespaldo.AUSENCIA_MILLIS
        aviso.alVolverAlFrente()

        assertFalse("Ahora no no es nunca", aviso.descartado.value)
    }

    @Test
    fun `una app recien arrancada lo enseña`() {
        assertFalse(aviso().descartado.value)
    }
}
