package com.carlosalbertoxw.ollin.actividades

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Actualizar por encima de una version anterior.
 *
 * Esta clase llega de rebote, por una regresion que le costo una version
 * publicada a Ollin Finanzas: guardo un dato como **entero** bajo una clave, en
 * la version siguiente pidio ese mismo nombre como **texto**, y DataStore --que
 * guarda el tipo junto al valor-- lanzo un ClassCastException dentro del Flow
 * que alimenta el arranque. La app se cerraba al abrirse, pero solo en los
 * telefonos que ya tenian la version anterior instalada.
 *
 * Aqui ninguna clave ha cambiado de tipo, asi que no hay nada roto que
 * arreglar. Lo que faltaba era la prueba: ninguna de las otras puede ver este
 * problema, porque todas empiezan con el disco vacio, que es el unico escenario
 * donde no existe.
 *
 * Al agregar una clave nueva, agrega tambien su caso aqui.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class PreferenciasHeredadasTest {

    private val repositorio = AjustesRepositorio(ApplicationProvider.getApplicationContext())

    /** Lo que una version anterior dejo escrito, con los tipos de entonces. */
    private fun comoLasDejoLaVersionAnterior() = mutablePreferencesOf(
        stringPreferencesKey("tema") to "oscuro",
        booleanPreferencesKey("color_dinamico") to true,
        intPreferencesKey("meta_trabajo_minutos") to 420,
        booleanPreferencesKey("recordatorios") to true,
        booleanPreferencesKey("buscar_actualizaciones") to false,
        longPreferencesKey("ultima_comprobacion") to 1_756_000_000_000L,
        stringPreferencesKey("version_disponible") to "1.0.1",
        stringPreferencesKey("modo_bloqueo") to ModoBloqueo.PIN.name,
        stringPreferencesKey("pin_hash") to "unahuella",
        intPreferencesKey("pin_fallos") to 2
    )

    @Test
    fun `lo que dejo escrito una version anterior se sigue leyendo`() {
        val ajustes = repositorio.interpreta(comoLasDejoLaVersionAnterior())

        assertEquals(true, ajustes.temaOscuro)
        assertEquals(420, ajustes.metaTrabajoMinutos)
        assertTrue(ajustes.recordatorios)
        assertEquals("1.0.1", ajustes.versionDisponible)
        assertEquals(ModoBloqueo.PIN, ajustes.modoBloqueo)
        assertEquals("unahuella", ajustes.pinHash)
        assertEquals(2, ajustes.pinFallos)
    }

    /**
     * El corazon del asunto: una clave con un tipo que no corresponde se trata
     * como ausente, no como motivo para cerrar la app. Sin la lectura
     * comprobada, cada una de estas tres lineas cierra Ollin al arrancar.
     */
    @Test
    fun `una clave con el tipo equivocado se ignora y no revienta`() {
        val revueltas = mutablePreferencesOf(
            intPreferencesKey("tema") to 1,
            stringPreferencesKey("meta_trabajo_minutos") to "siete horas",
            longPreferencesKey("recordatorios") to 1L
        )

        val ajustes = repositorio.interpreta(revueltas)

        assertNull("El tema vuelve a seguir al sistema", ajustes.temaOscuro)
        assertEquals(300, ajustes.metaTrabajoMinutos)
        // Contra el valor declarado y no contra uno escrito aqui: lo que se
        // afirma es "vuelve al de fabrica", no "vuelve a false". Si el de
        // fabrica cambia --y cambio--, esto lo sigue solo en vez de romperse.
        assertEquals(
            "Los recordatorios vuelven a su valor de fabrica",
            Ajustes().recordatorios,
            ajustes.recordatorios
        )
    }

    /** Una instalación nueva no lee nada y sale con lo de fábrica. */
    @Test
    fun `sin nada guardado salen los valores de fabrica`() {
        val ajustes = repositorio.interpreta(mutablePreferencesOf())

        assertEquals(300, ajustes.metaTrabajoMinutos)
        assertEquals(ModoBloqueo.NINGUNO, ajustes.modoBloqueo)
        assertTrue(ajustes.buscarActualizaciones)
        assertNull(ajustes.versionDisponible)
    }
}
