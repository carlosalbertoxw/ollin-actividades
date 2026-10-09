package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo
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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

    /**
     * El minuto de gracia existe para que elegir un .xlsx en el selector del
     * sistema no te expulse de la app, y se pide expresamente antes de abrirlo.
     */
    @Test
    fun `volver del selector antes del minuto no vuelve a pedir la llave`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.esperaVueltaDelSistema()
        control.alIrAlFondo()
        pasaElTiempo(30_000)
        control.alVolverAlFrente()

        assertFalse(control.bloqueado.value)
    }

    @Test
    fun `volver del selector despues del minuto vuelve a bloquear`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.esperaVueltaDelSistema()
        control.alIrAlFondo()
        pasaElTiempo(ControlBloqueo.GRACIA_MILLIS + 1)
        control.alVolverAlFrente()

        assertTrue(control.bloqueado.value)
    }

    /**
     * Salir sin avisar es el caso que el candado existe para cubrir: pulsar
     * Inicio y pasarle el telefono a alguien. Ahi no hay gracia que valga.
     */
    @Test
    fun `salir al fondo sin avisar cierra de inmediato`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.alIrAlFondo()
        control.alVolverAlFrente()

        assertTrue(control.bloqueado.value)
    }

    /** La gracia se gasta al usarla: el siguiente viaje tiene que volver a pedirla. */
    @Test
    fun `la gracia no se hereda al siguiente viaje`() = runTest {
        ajustes.activaBloqueoSistema()
        val control = conCandadoAbierto()

        control.esperaVueltaDelSistema()
        control.alIrAlFondo()
        control.alVolverAlFrente()

        control.alIrAlFondo()
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

    // ----------------------------------------------------- intentos de PIN

    /** Pone un PIN sellado, como lo deja hoy Ajustes. */
    private suspend fun conPin(pin: String): ControlBloqueo {
        val control = ControlBloqueo(ajustes, SELLO)
        val (hash, sal) = control.huellaNueva(pin)
        ajustes.activaBloqueoPin(hash = hash, sal = sal)
        asienta()
        return control
    }

    @Test
    fun `un PIN correcto contra su huella sellada abre`() = runTest {
        val control = conPin("2468")

        assertTrue(ajustes.ajustes.first().pinHash!!.startsWith(ClavePin.PREFIJO_SELLADA))
        assertEquals(ControlBloqueo.IntentoDePin.Correcto, control.intentaPin("2468"))
        assertFalse(control.bloqueado.value)
    }

    /**
     * La huella de la 1.2.1 y anteriores es PBKDF2 a secas. Tiene que seguir
     * abriendo, y en cuanto abre se cambia por la sellada con la misma sal.
     */
    @Test
    fun `acertar contra una huella vieja la guarda sellada`() = runTest {
        val sal = ClavePin.nuevaSal()
        ajustes.activaBloqueoPin(hash = ClavePin.deriva("2468", sal), sal = sal)
        val control = ControlBloqueo(ajustes, SELLO)
        asienta()

        assertEquals(ControlBloqueo.IntentoDePin.Correcto, control.intentaPin("2468"))

        val actuales = ajustes.ajustes.first()
        assertEquals(sal, actuales.pinSal)
        assertEquals(ClavePin.huella("2468", sal, SELLO), actuales.pinHash)
        assertEquals(ControlBloqueo.IntentoDePin.Correcto, control.intentaPin("2468"))
    }

    @Test
    fun `fallar contra una huella vieja no la migra`() = runTest {
        val sal = ClavePin.nuevaSal()
        val vieja = ClavePin.deriva("2468", sal)
        ajustes.activaBloqueoPin(hash = vieja, sal = sal)
        val control = ControlBloqueo(ajustes, SELLO)
        asienta()

        assertEquals(ControlBloqueo.IntentoDePin.Incorrecto, control.intentaPin("1111"))

        val actuales = ajustes.ajustes.first()
        assertEquals(vieja, actuales.pinHash)
        assertEquals(1, actuales.pinFallos)
    }

    /**
     * El freno es el mismo para todos los que piden el PIN, Ajustes incluido:
     * en espera ni siquiera se comprueba, aunque el PIN sea el correcto.
     */
    @Test
    fun `pasada la gracia hay que esperar aunque el PIN sea el bueno`() = runTest {
        val control = conPin("2468")

        repeat(ClavePin.FALLOS_DE_GRACIA + 1) {
            assertEquals(ControlBloqueo.IntentoDePin.Incorrecto, control.intentaPin("0000"))
        }

        val enEspera = control.intentaPin("2468")
        assertTrue("Salio $enEspera", enEspera is ControlBloqueo.IntentoDePin.EnEspera)
        assertEquals(
            "Esperar no cuenta como otro fallo",
            ClavePin.FALLOS_DE_GRACIA + 1,
            ajustes.ajustes.first().pinFallos
        )

        pasaElTiempo(ClavePin.esperaSegundos(ClavePin.FALLOS_DE_GRACIA + 1) * 1_000L)
        assertEquals(ControlBloqueo.IntentoDePin.Correcto, control.intentaPin("2468"))
        assertEquals("Acertar limpia la cuenta", 0, ajustes.ajustes.first().pinFallos)
        assertEquals(0, control.segundosDeEspera())
    }

    /**
     * Cerrar la app despues de cada fallo no debe regalar un intento: el
     * control nuevo arranca cobrando la espera que tocan los fallos guardados.
     */
    @Test
    fun `reabrir la app con fallos guardados arranca con la espera puesta`() = runTest {
        conPin("2468")
        repeat(ClavePin.FALLOS_DE_GRACIA + 2) { ajustes.sumaFalloPin() }

        val control = ControlBloqueo(ajustes, SELLO)
        asienta()

        assertEquals(
            ClavePin.esperaSegundos(ClavePin.FALLOS_DE_GRACIA + 2),
            control.segundosDeEspera()
        )
        assertTrue(control.intentaPin("2468") is ControlBloqueo.IntentoDePin.EnEspera)
    }

    @Test
    fun `los primeros fallos no cuestan espera`() = runTest {
        val control = conPin("2468")

        repeat(ClavePin.FALLOS_DE_GRACIA) {
            assertEquals(ControlBloqueo.IntentoDePin.Incorrecto, control.intentaPin("0000"))
            assertEquals(0, control.segundosDeEspera())
        }
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

        /** El Keystore no existe en la JVM: un HMAC con llave fija hace sus veces. */
        val SELLO = ClavePin.Sello { huella ->
            Mac.getInstance("HmacSHA256")
                .apply { init(SecretKeySpec(ByteArray(32) { 1 }, "HmacSHA256")) }
                .doFinal(huella)
        }
    }
}
