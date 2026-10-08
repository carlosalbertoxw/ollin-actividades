package com.carlosalbertoxw.ollin.actividades

import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.ComprobadorActualizaciones
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Resultado
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.Version
import com.carlosalbertoxw.ollin.actividades.data.actualizaciones.siguienteSalto
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

    private fun comprobador(instalada: String? = "1.0.0", respuesta: String = JSON_1_2_0) =
        ComprobadorActualizaciones(
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
        assertEquals(APK_1_2_0, publicada.url)
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

    /**
     * El sitio vive en el dominio propio, y el dominio propio es justo lo que se
     * puede perder. Sin una descarga oficial no hay nada que ofrecer.
     */
    @Test
    fun `sin apk no basta el sitio`() {
        val soloSitio =
            """{"version":"1.2.0","sitio":"https://carlosalbertoxw.com/ollin-actividades/"}"""
        assertNull(ComprobadorActualizaciones.lee(soloSitio))
    }

    /**
     * Quien se quedara con el dominio podria anunciar a todas las instalaciones
     * el enlace que quisiera. Https no basta: tiene que ser una release de aqui.
     */
    @Test
    fun `solo se acepta una descarga de las releases de este repositorio`() {
        val ajenos = listOf(
            "https://ejemplo.invalido/ollin-1.2.0.apk",
            "https://github.com/otra-cuenta/ollin-actividades/releases/download/v1/x.apk",
            "https://github.com/carlosalbertoxw/ollin-actividades-falsa/releases/download/v1/x.apk",
            "https://github.com.ejemplo.invalido/carlosalbertoxw/ollin-actividades/releases/download/x",
            "https://github.com@ejemplo.invalido/carlosalbertoxw/ollin-actividades/releases/download/x",
            "https://github.com:8443/carlosalbertoxw/ollin-actividades/releases/download/v1/x.apk",
            "${APK_BASE}../../../../otra-cuenta/repo/releases/download/v1/x.apk",
            "${APK_BASE}%2E%2E/%2e%2e/%2e%2e/%2e%2e/otra-cuenta/repo/x.apk",
            "http://github.com/carlosalbertoxw/ollin-actividades/releases/download/v1/x.apk"
        )

        ajenos.forEach { apk ->
            assertNull(
                "No debe aceptarse $apk",
                ComprobadorActualizaciones.lee("""{"version":"1.2.0","apk":"$apk"}""")
            )
        }
    }

    @Test
    fun `una nota mas larga que el tope se recorta`() {
        val larga = "a".repeat(ComprobadorActualizaciones.TOPE_NOTAS * 3)
        val publicada = ComprobadorActualizaciones.lee(
            """{"version":"1.2.0","apk":"$APK_1_2_0","notas":"$larga"}"""
        )!!

        assertEquals(ComprobadorActualizaciones.TOPE_NOTAS, publicada.notas!!.length)
        assertTrue(publicada.notas!!.endsWith("…"))
    }

    // ------------------------------------------------------- redirecciones

    /**
     * El caso real que lo hizo falta: poner un dominio propio delante de GitHub
     * Pages deja el `.github.io` devolviendo un 301 para siempre. La direccion
     * va compilada dentro de cada APK y no se puede corregir en los que ya
     * estan instalados, asi que tiene que sobrevivir a la mudanza.
     */
    @Test
    fun `un 301 a https se sigue`() {
        assertEquals(
            "https://carlosalbertoxw.com/ollin-actividades/version.json",
            siguienteSalto(301, "https://carlosalbertoxw.com/ollin-actividades/version.json")
        )
    }

    @Test
    fun `todos los codigos de redireccion valen, no solo el 301`() {
        listOf(301, 302, 303, 307, 308).forEach { codigo ->
            assertEquals(
                "El $codigo tambien es una mudanza",
                "https://carlosalbertoxw.com/ollin-actividades/x.json",
                siguienteSalto(codigo, "https://carlosalbertoxw.com/ollin-actividades/x.json")
            )
        }
    }

    /**
     * Es la unica razon por la que las redirecciones se siguen a mano en vez de
     * dejarselas a HttpURLConnection: un salto que acabe en claro deja la
     * respuesta a merced de quien este en medio de la red, y con ella el enlace
     * de descarga que la app va a ofrecer.
     */
    @Test
    fun `un salto que sale de https no se sigue`() {
        assertNull(siguienteSalto(301, "http://carlosalbertoxw.com/ollin-actividades/version.json"))
        assertNull(siguienteSalto(302, "ftp://carlosalbertoxw.com/ollin-actividades/version.json"))
        assertNull(siguienteSalto(301, "/version.json"))
        assertNull(siguienteSalto(301, null))
        assertNull(siguienteSalto(301, ""))
    }

    /** La mudanza es de github.io al dominio propio; cualquier otro destino no lo es. */
    @Test
    fun `un salto a otro dominio no se sigue`() {
        assertEquals(
            "https://carlosalbertoxw.github.io/ollin-actividades/version.json",
            siguienteSalto(301, "https://carlosalbertoxw.github.io/ollin-actividades/version.json")
        )
        assertNull(siguienteSalto(301, "https://ejemplo.invalido/version.json"))
        assertNull(siguienteSalto(301, "https://carlosalbertoxw.com.ejemplo.invalido/version.json"))
        assertNull(siguienteSalto(301, "https://carlosalbertoxw.com@ejemplo.invalido/version.json"))
        assertNull(siguienteSalto(301, "https://carlosalbertoxw.com:8443/version.json"))
    }

    @Test
    fun `una respuesta que no es 3xx no es una mudanza`() {
        assertNull(siguienteSalto(200, "https://carlosalbertoxw.com/ollin-actividades/x.json"))
        assertNull(siguienteSalto(404, "https://carlosalbertoxw.com/ollin-actividades/x.json"))
        assertNull(siguienteSalto(500, null))
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
        assertEquals(APK_1_2_0, guardado.urlDeDescarga)
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
        const val APK_BASE = ComprobadorActualizaciones.DESCARGAS_OFICIALES
        const val APK_1_2_0 = "${APK_BASE}v1.2.0/ollin-actividades-1.2.0.apk"

        val JSON_1_2_0 = """
            {
              "version": "1.2.0",
              "publicada": "2026-09-15",
              "apk": "$APK_1_2_0",
              "sitio": "https://ejemplo.invalido/",
              "notas": "Arregla el cronómetro."
            }
        """.trimIndent()
    }
}
