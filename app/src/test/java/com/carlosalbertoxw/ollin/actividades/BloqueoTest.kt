package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las preferencias del candado: con que se cierra Ollin y que queda guardado.
 *
 * Las reglas del control --arrancar cerrado, la gracia, el freno-- estan en
 * [ControlBloqueoTest], sin DataStore.
 *
 * Se prueba contra el DataStore de verdad porque el error que importa es
 * precisamente el de estado a medias —modo PIN sin PIN guardado—, y ese solo
 * aparece si las dos escrituras van juntas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BloqueoTest {

    private lateinit var ajustes: AjustesRepositorio

    @Before
    fun abre() {
        ajustes = AjustesRepositorio(ApplicationProvider.getApplicationContext<Application>())
        // El DataStore se cachea entre pruebas de la misma clase: sin volver a
        // fabrica, cada una heredaria el candado que dejo puesto la anterior.
        ajustes.restauraDeFabrica()
    }

    // -------------------------------------------------------- preferencias

    @Test
    fun `sin candado configurado no queda ningun secreto`() = runTest {
        val actuales = ajustes.ajustes.first()

        assertEquals(ModoBloqueo.NINGUNO, actuales.modoBloqueo)
        assertNull(actuales.pinHash)
        assertNull(actuales.pinSal)
    }

    /**
     * Modo y secreto se escriben de golpe. Si fueran dos escrituras podria
     * quedar un "modo PIN" sin PIN, y eso deja la app cerrada sin llave.
     */
    @Test
    fun `activar el PIN deja modo, huella y sal juntos`() = runTest {
        val sal = ClavePin.nuevaSal()
        ajustes.activaBloqueoPin(hash = ClavePin.deriva("4321", sal), sal = sal)

        val actuales = ajustes.ajustes.first()
        assertEquals(ModoBloqueo.PIN, actuales.modoBloqueo)
        assertNotNull(actuales.pinHash)
        assertNotNull(actuales.pinSal)
        assertTrue(ClavePin.coincide("4321", actuales.pinHash, actuales.pinSal))
    }

    /** Pasar al bloqueo del telefono tiene que llevarse el PIN viejo. */
    @Test
    fun `pasar al bloqueo del sistema borra el PIN anterior`() = runTest {
        val sal = ClavePin.nuevaSal()
        ajustes.activaBloqueoPin(hash = ClavePin.deriva("4321", sal), sal = sal)

        ajustes.activaBloqueoSistema()

        val actuales = ajustes.ajustes.first()
        assertEquals(ModoBloqueo.SISTEMA, actuales.modoBloqueo)
        assertNull(actuales.pinHash)
        assertNull(actuales.pinSal)
    }

    @Test
    fun `quitar el bloqueo no deja rastro del PIN`() = runTest {
        val sal = ClavePin.nuevaSal()
        ajustes.activaBloqueoPin(hash = ClavePin.deriva("4321", sal), sal = sal)

        ajustes.quitaBloqueo()

        val actuales = ajustes.ajustes.first()
        assertEquals(ModoBloqueo.NINGUNO, actuales.modoBloqueo)
        assertNull(actuales.pinHash)
        assertNull(actuales.pinSal)
    }
}
