package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.recordatorios.CoordinadorRecordatorios
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Lo que despierta el receptor corre solo, tras cada reinicio y cada alarma: lo
 * que falle ahi no puede cerrar la app ni dejar sin liberar el `goAsync()`.
 */
class ReceptorRecordatoriosTest {

    @Test
    fun `un fallo se entrega y no sale de la corrutina`() = runTest {
        val error = IllegalStateException("La base no abrio")
        var entregado: Throwable? = null
        var terminados = 0

        CoordinadorRecordatorios.sinCaerse(
            bloque = { throw error },
            alFallar = { entregado = it },
            alTerminar = { terminados++ }
        )

        assertSame(error, entregado)
        assertEquals("El goAsync() se libera aunque falle", 1, terminados)
    }

    /** Lo que llega si SQLCipher no carga no es una Exception. */
    @Test
    fun `tambien un Error se entrega`() = runTest {
        var entregado: Throwable? = null

        CoordinadorRecordatorios.sinCaerse(
            bloque = { throw UnsatisfiedLinkError("sqlcipher") },
            alFallar = { entregado = it },
            alTerminar = {}
        )

        assertTrue(entregado is UnsatisfiedLinkError)
    }

    @Test
    fun `sin fallo se termina una vez y no se reporta nada`() = runTest {
        var reportes = 0
        var terminados = 0

        CoordinadorRecordatorios.sinCaerse(
            bloque = {},
            alFallar = { reportes++ },
            alTerminar = { terminados++ }
        )

        assertEquals(0, reportes)
        assertEquals(1, terminados)
    }

    /** Atrapar la cancelacion rompe la de las corrutinas: se deja pasar. */
    @Test
    fun `la cancelacion se deja pasar y aun asi se termina`() = runTest {
        var reportes = 0
        var terminados = 0

        try {
            CoordinadorRecordatorios.sinCaerse(
                bloque = { throw CancellationException("cerrado") },
                alFallar = { reportes++ },
                alTerminar = { terminados++ }
            )
            fail("La cancelacion tenia que salir")
        } catch (esperada: CancellationException) {
            assertEquals("cerrado", esperada.message)
        }

        assertEquals(0, reportes)
        assertEquals(1, terminados)
    }
}
