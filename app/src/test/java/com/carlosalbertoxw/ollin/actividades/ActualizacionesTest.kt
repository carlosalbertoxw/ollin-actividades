package com.carlosalbertoxw.ollin.actividades

import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El aviso de actualizaciones, sin red.
 *
 * La descarga entra por parametro, asi que todo lo que decide algo —comparar
 * versiones, interpretar el JSON, saber si toca preguntar— se puede probar con
 * un texto escrito a mano. Lo unico que queda sin cubrir es el `HttpURLConnection`
 * en si, que no toma ninguna decision.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ActualizacionesTest {

    private lateinit var ajustes: AjustesRepositorio

    @Before
    fun preparar() {
        ajustes = AjustesRepositorio(ApplicationProvider.getApplicationContext())
        ajustes.restauraDeFabrica()
    }

    private fun comprobador(
        instalada: String? = "1.0.0",
        respuesta: String = JSON_1_2_0
    ) = ComprobadorActualizaciones(
        ajustes = ajustes,
        instalada = Version.de(instalada),
        url = "https://ejemplo.invalido/version.json",
        descarga = { respuesta }
    )

    // ------------------------------------------------------------- versiones

    @Test
    fun `una version mayor es posterior aunque el texto ordene al reves`() {
        val diez = Version.de("1.10.0")!!
        val nueve = Version.de("1.9.0")!!

        assertTrue("1.10.0 tiene que ser posterior a 1.9.0", diez > nueve)
        assertTrue("y el orden alfabetico dice lo contrario", "1.10.0" < "1.9.0")
    }

    @Test
    fun `la v del tag y el sufijo de depuracion no cambian la version`() {
        val esperada = Version(1, 2, 3)

        assertEquals(esperada, Version.de("1.2.3"))
        assertEquals(esperada, Version.de("v1.2.3"))
        assertEquals(esperada, Version.de("1.2.3-debug"))
        assertEquals(esperada, Version.de(" v1.2.3 "))
    }

    @Test
    fun `una version incompleta rellena con ceros y una invalida es nula`() {
        assertEquals(Version(2, 0, 0), Version.de("2"))
        assertEquals(Version(2, 1, 0), Version.de("2.1"))

        assertNull(Version.de(null))
        assertNull(Version.de(""))
        assertNull(Version.de("ultima"))
        assertNull(Version.de("1.x.0"))
        assertNull(Version.de("1.2.3.4"))
    }

    // ----------------------------------------------------------------- JSON

    @Test
    fun `se lee la version, el enlace y las notas del sitio`() {
        val publicada = ComprobadorActualizaciones.lee(JSON_1_2_0)!!

        assertEquals(Version(1, 2, 0), publicada.version)
        assertEquals("https://ejemplo.invalido/ollin-1.2.0.apk", publicada.url)
        assertEquals("Arregla el cronómetro.", publicada.notas)
        assertEquals("2026-09-15", publicada.publicadaEn)
    }

    /**
     * El enlace acaba abriendose en el navegador de alguien, y viene de fuera.
     * Uno en claro deja la descarga a merced de quien este en medio de la red.
     */
    @Test
    fun `un enlace que no es https se rechaza`() {
        val enClaro = """{"version":"1.2.0","apk":"http://ejemplo.invalido/ollin.apk"}"""
        assertNull(ComprobadorActualizaciones.lee(enClaro))
    }

    @Test
    fun `sin version legible no se entiende nada del archivo`() {
        assertNull(ComprobadorActualizaciones.lee("""{"apk":"https://a.invalido/x.apk"}"""))
        assertNull(ComprobadorActualizaciones.lee("""{"version":"proxima"}"""))
        assertNull(ComprobadorActualizaciones.lee("esto no es json"))
    }

    @Test
    fun `si no hay apk sirve el sitio, que es a donde se manda al usuario`() {
        val soloSitio = """{"version":"1.2.0","sitio":"https://ejemplo.invalido/"}"""
        assertEquals("https://ejemplo.invalido/", ComprobadorActualizaciones.lee(soloSitio)!!.url)
    }

    // --------------------------------------------------------- comparaciones

    @Test
    fun `una version publicada mas nueva se anuncia`() = runTest {
        val resultado = comprobador(instalada = "1.0.0").compruebaAhora()

        assertTrue(resultado is Resultado.HayVersionNueva)
        assertEquals(
            Version(1, 2, 0),
            (resultado as Resultado.HayVersionNueva).publicada.version
        )
    }

    @Test
    fun `la misma version no se anuncia`() = runTest {
        assertEquals(Resultado.AlDia, comprobador(instalada = "1.2.0").compruebaAhora())
    }

    /**
     * Puede pasar de verdad: quien compila desde el codigo va por delante de lo
     * publicado. Anunciarle una "actualizacion" a una version anterior seria
     * invitarlo a retroceder.
     */
    @Test
    fun `una version instalada posterior a la publicada no anuncia nada`() = runTest {
        assertEquals(Resultado.AlDia, comprobador(instalada = "2.0.0").compruebaAhora())
    }

    // ------------------------------------------------------ una vez al dia

    @Test
    fun `la primera vez siempre toca`() = runTest {
        assertTrue(comprobador().compruebaSiToca() is Resultado.HayVersionNueva)
    }

    @Test
    fun `no se vuelve a preguntar el mismo dia`() = runTest {
        val ahora = 1_800_000_000_000L
        comprobador().compruebaSiToca(ahora)

        val segunda = comprobador().compruebaSiToca(ahora + 60_000L)
        assertEquals(Resultado.NoTocaba, segunda)
    }

    @Test
    fun `pasado un dia se vuelve a preguntar`() = runTest {
        val ahora = 1_800_000_000_000L
        comprobador().compruebaSiToca(ahora)

        val siguiente = comprobador()
            .compruebaSiToca(ahora + ComprobadorActualizaciones.UN_DIA_MS + 1)
        assertTrue(siguiente is Resultado.HayVersionNueva)
    }

    /**
     * Sin el valor absoluto, atrasar el reloj del telefono dejaria la
     * comprobacion congelada hasta que la fecha volviera a alcanzar la marca
     * guardada, que puede ser dentro de años.
     */
    @Test
    fun `mover el reloj hacia atras no congela la comprobacion`() = runTest {
        val ahora = 1_800_000_000_000L
        comprobador().compruebaSiToca(ahora)

        val enElPasado = comprobador()
            .compruebaSiToca(ahora - ComprobadorActualizaciones.UN_DIA_MS - 1)
        assertTrue(enElPasado is Resultado.HayVersionNueva)
    }

    @Test
    fun `con el interruptor apagado no se pregunta nada`() = runTest {
        ajustes.guardaBuscarActualizaciones(false)
        assertEquals(Resultado.NoTocaba, comprobador().compruebaSiToca())
    }

    // ------------------------------------------------------------- memoria

    @Test
    fun `lo que dijo el sitio queda guardado para la pantalla de Acerca de`() = runTest {
        comprobador().compruebaAhora(cuando())

        val guardado = ajustes.ajustes.first()
        assertEquals("1.2.0", guardado.versionDisponible)
        assertEquals("https://ejemplo.invalido/ollin-1.2.0.apk", guardado.urlDeDescarga)
        assertEquals("Arregla el cronómetro.", guardado.notasDeVersion)
        assertEquals(cuando(), guardado.ultimaComprobacion)
    }

    /**
     * Un fallo no gasta el dia: si se guardara la marca, quedarse sin señal una
     * vez dejaria a Ollin sin volver a preguntar hasta el dia siguiente.
     */
    @Test
    fun `un fallo no adelanta el reloj de la siguiente comprobacion`() = runTest {
        val roto = ComprobadorActualizaciones(
            ajustes = ajustes,
            instalada = Version(1, 0, 0),
            url = "https://ejemplo.invalido/version.json",
            descarga = { error("sin red") }
        )

        assertTrue(roto.compruebaSiToca() is Resultado.Fallo)
        assertEquals(0L, ajustes.ajustes.first().ultimaComprobacion)
    }

    @Test
    fun `apagar el interruptor olvida lo que se supo`() = runTest {
        comprobador().compruebaAhora()
        ajustes.guardaBuscarActualizaciones(false)

        val guardado = ajustes.ajustes.first()
        assertNull(guardado.versionDisponible)
        assertNull(guardado.urlDeDescarga)
        assertEquals(0L, guardado.ultimaComprobacion)
    }

    private fun cuando() = 1_800_000_000_000L

    private companion object {
        val JSON_1_2_0 = """
            {
              "version": "1.2.0",
              "publicada": "2026-09-15",
              "apk": "https://ejemplo.invalido/ollin-1.2.0.apk",
              "sitio": "https://ejemplo.invalido/",
              "notas": "Arregla el cronómetro."
            }
        """.trimIndent()
    }
}
