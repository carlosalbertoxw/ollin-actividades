package com.carlosalbertoxw.ollin.actividades.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.ollin.actividades.MainActivity
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.LlaveDelPin
import com.carlosalbertoxw.ollin.actividades.ui.screens.BloqueoPantalla
import com.carlosalbertoxw.ollin.actividades.ui.screens.DialogoPinActual
import com.carlosalbertoxw.ollin.actividades.ui.theme.TemaOllin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El candado por PIN en pantalla, con el Keystore de verdad.
 *
 * Las reglas --cuantos fallos, cuanta espera-- ya se prueban en la JVM
 * (ControlBloqueoTest, ClavePinTest). Aqui se prueba lo que la JVM no puede: el
 * sello de [LlaveDelPin] y que las dos pantallas que piden el PIN respeten el
 * mismo freno. Es la misma prueba que en Ollin Finanzas.
 *
 * Cada prueba arma su propio [ControlBloqueo] y no usa el del contenedor: ese
 * vive lo que vive el proceso, y lo que una prueba desbloqueara o castigara se
 * le pasaria a la siguiente. Por eso tampoco hace falta [BancoDePruebas]: las
 * preferencias y lo que se guarda van en memoria.
 *
 * El reloj es de mentira y no avanza solo: con uno de verdad, la espera se
 * acabaria a media asercion.
 *
 * La actividad es [MainActivity] solo porque es la `FragmentActivity` que hay;
 * su contenido se reemplaza por la pantalla que se prueba.
 */
@RunWith(AndroidJUnit4::class)
class CandadoPinTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    // Los tres se cruzan entre el hilo de la prueba y el principal.
    @Volatile
    private var ahora = 1_000_000L

    @Volatile
    private var fallosGuardados = 0

    @Volatile
    private var confirmado = false

    /**
     * Las preferencias nacen ya con el candado y los fallos puestos: el control
     * fija la espera en su primera lectura, como al reabrir la app, y una
     * primera lectura sin candado lo dejaria abierto.
     */
    private fun preparaControl(fallos: Int = 0): Pair<ControlBloqueo, Ajustes> {
        val sal = ClavePin.nuevaSal()
        val ajustes = Ajustes(
            modoBloqueo = ModoBloqueo.PIN,
            pinHash = runBlocking { ClavePin.huella(PIN, sal, LlaveDelPin) },
            pinSal = sal,
            pinFallos = fallos
        )
        val control = ControlBloqueo(
            preferencias = MutableStateFlow(ajustes),
            guardaFallos = { fallosGuardados = it },
            sello = LlaveDelPin,
            guardaHuella = { _, _ -> },
            reloj = { ahora }
        )
        // La primera lectura corre en el hilo principal; con fallos de mas, se
        // espera a que haya fijado su espera antes de dibujar nada.
        compose.espera("el control fija su espera") {
            ControlBloqueo.esperaMillis(fallos) == 0L || control.segundosDeEspera() > 0
        }
        return control to ajustes
    }

    private fun muestra(contenido: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { actividad ->
            actividad.setContent { TemaOllin(oscuro = true) { contenido() } }
        }
        compose.waitForIdle()
    }

    private fun teclea(pin: String) {
        compose.onNode(campo("PIN")).performTextInput(pin)
    }

    private fun esperaPinIncorrecto() {
        compose.espera("aparece \"PIN incorrecto\"", ESPERA_COMPROBACION_MS) {
            compose.hayTexto("PIN incorrecto")
        }
    }

    private fun esperaDesbloqueo(control: ControlBloqueo) {
        compose.espera("el PIN correcto abre", ESPERA_COMPROBACION_MS) {
            !control.bloqueado.value
        }
    }

    /** Cumple la espera en el reloj de mentira y aguarda a que la pantalla se entere. */
    private fun cumpleLaEspera() {
        ahora += ControlBloqueo.ESPERA_MAXIMA_MILLIS
        compose.esperaSinTexto("Demasiados intentos", subcadena = true)
    }

    @Test
    fun el_pin_correcto_abre() {
        val (control, ajustes) = preparaControl()
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        teclea(PIN)
        compose.onNodeWithText("Entrar").assertIsEnabled().performClick()

        esperaDesbloqueo(control)
    }

    @Test
    fun un_pin_incorrecto_avisa_y_cuenta_el_fallo() {
        val (control, ajustes) = preparaControl()
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        teclea("0000")
        compose.onNodeWithText("Entrar").assertIsEnabled().performClick()

        esperaPinIncorrecto()
        assertEquals(1, fallosGuardados)
        assertTrue("Un PIN incorrecto no abre", control.bloqueado.value)
    }

    /**
     * Con seis fallos guardados, al abrir ya hay espera: ni el PIN correcto
     * entra hasta que se cumple. Cumplida, el mismo PIN abre.
     */
    @Test
    fun con_fallos_guardados_hay_que_esperar_aun_con_el_pin_correcto() {
        val (control, ajustes) = preparaControl(fallos = 6)
        muestra { BloqueoPantalla(compose.activity, ajustes, control) }

        // Mientras corre la espera el campo esta apagado y el boton cuenta atras.
        compose.esperaTexto("Demasiados intentos", subcadena = true)
        compose.onNode(hasText("Espera", substring = true)).assertIsNotEnabled()

        cumpleLaEspera()
        teclea(PIN)
        compose.onNodeWithText("Entrar").assertIsEnabled().performClick()

        esperaDesbloqueo(control)
    }

    /**
     * El dialogo de Ajustes se abre con la app ya desbloqueada: pasa por el mismo
     * control y la misma espera que la pantalla de bloqueo, o seria el atajo para
     * adivinar el PIN.
     */
    @Test
    fun el_dialogo_de_ajustes_respeta_la_misma_espera() {
        val (control, _) = preparaControl(fallos = 6)
        muestra {
            DialogoPinActual(
                bloqueo = control,
                alConfirmar = { confirmado = true },
                alCancelar = {}
            )
        }

        compose.esperaTexto("Demasiados intentos", subcadena = true)
        compose.onNode(hasText("Espera", substring = true)).assertIsNotEnabled()

        cumpleLaEspera()
        teclea(PIN)
        compose.onNodeWithText("Confirmar").assertIsEnabled().performClick()

        compose.espera("el dialogo confirma el PIN", ESPERA_COMPROBACION_MS) { confirmado }
    }

    @Test
    fun el_dialogo_de_ajustes_cuenta_los_fallos() {
        val (control, _) = preparaControl()
        muestra {
            DialogoPinActual(
                bloqueo = control,
                alConfirmar = {},
                alCancelar = {}
            )
        }

        teclea("0000")
        compose.onNodeWithText("Confirmar").assertIsEnabled().performClick()

        esperaPinIncorrecto()
        assertEquals(1, fallosGuardados)
    }
}

private const val PIN = "2468"

/**
 * Lo que se le da a la comprobacion del PIN, que pasa por PBKDF2 y el Keystore
 * de verdad. Un clic sobre un boton apagado no hace nada y no avisa, por eso cada
 * prueba comprueba antes que este encendido: asi, si esta espera se agota, el
 * boton si se pulso. En Ollin Finanzas, 10 s se agotaron una vez en API 34 y la
 * misma prueba paso al reejecutarla sin cambios.
 */
private const val ESPERA_COMPROBACION_MS = 30_000L
