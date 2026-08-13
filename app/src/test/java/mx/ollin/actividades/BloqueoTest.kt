package mx.ollin.actividades

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mx.ollin.actividades.data.prefs.AjustesRepositorio
import mx.ollin.actividades.data.prefs.ModoBloqueo
import mx.ollin.actividades.data.seguridad.ClavePin
import mx.ollin.actividades.data.seguridad.ControlBloqueo
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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * El candado de la app: quien decide si Ollin esta cerrada y con que.
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

    // ------------------------------------------------------------- control

    /**
     * Arranca bloqueada a proposito: todavia no se sabe si hay candado puesto y
     * equivocarse hacia el lado abierto ensena la bitacora a quien no debia.
     */
    @Test
    fun `el control nace bloqueado`() {
        assertTrue(ControlBloqueo(ajustes).bloqueado.value)
    }

    @Test
    fun `sin candado configurado se abre solo`() {
        val control = ControlBloqueo(ajustes)

        asienta()

        assertFalse(control.bloqueado.value)
    }

    @Test
    fun `volver antes del minuto de gracia no vuelve a pedir la llave`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.alIrAlFondo()
        pasaElTiempo(30_000)
        control.alVolverAlFrente()

        assertFalse(control.bloqueado.value)
    }

    /**
     * El minuto de gracia existe para que elegir un .xlsx en el selector del
     * sistema no te expulse de la app. Pasado ese minuto, el candado vuelve.
     */
    @Test
    fun `volver despues del minuto de gracia vuelve a bloquear`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.alIrAlFondo()
        pasaElTiempo(ControlBloqueo.GRACIA_MILLIS + 1)
        control.alVolverAlFrente()

        assertTrue(control.bloqueado.value)
    }

    @Test
    fun `sin candado configurado ni el tiempo lo vuelve a cerrar`() {
        val control = ControlBloqueo(ajustes)
        asienta()

        control.alIrAlFondo()
        pasaElTiempo(3 * 60 * 60 * 1000L)
        control.alVolverAlFrente()

        assertFalse(control.bloqueado.value)
    }

    /** Un control que nunca fue al fondo no puede bloquearse por volver. */
    @Test
    fun `volver al frente sin haber salido no hace nada`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        pasaElTiempo(60 * 60 * 1000L)
        control.alVolverAlFrente()

        assertFalse(control.bloqueado.value)
    }

    private fun conCandadoAbierto(): ControlBloqueo {
        val control = ControlBloqueo(ajustes)
        // El control lee las preferencias en su propio alcance sobre el hilo
        // principal. Hay que dejarlo llegar antes de desbloquear: con el modo
        // todavia en NINGUNO, la cuenta de la gracia ni siquiera correria.
        asienta()
        control.desbloquea()
        return control
    }

    /** Deja que el colector de preferencias del control corra en el hilo principal. */
    private fun asienta() {
        repeat(ASENTADAS) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(2)
        }
        ShadowLooper.idleMainLooper()
    }

    /** Adelanta el reloj monotono que mide la gracia, sin tocar la hora del sistema. */
    private fun pasaElTiempo(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val ASENTADAS = 60
    }
}
