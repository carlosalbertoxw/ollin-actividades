package mx.ollin.actividades

import kotlinx.coroutines.test.runTest
import mx.ollin.actividades.data.seguridad.ClavePin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * El PIN de Ollin no se guarda: se guarda un derivado del que no se puede
 * volver atras. Si esto se rompiera, el archivo de preferencias contendria la
 * llave de la bitacora en claro.
 */
class ClavePinTest {

    @Test
    fun `la sal es distinta cada vez y trae los bytes que dice`() {
        val primera = ClavePin.nuevaSal()
        val segunda = ClavePin.nuevaSal()

        assertNotEquals(primera, segunda)
        assertEquals(16, Base64.getDecoder().decode(primera).size)
    }

    @Test
    fun `el mismo PIN con la misma sal da siempre la misma huella`() = runTest {
        val sal = ClavePin.nuevaSal()
        assertEquals(ClavePin.deriva("1234", sal), ClavePin.deriva("1234", sal))
    }

    /**
     * La sal por telefono es lo que impide precalcular una tabla con las diez
     * mil combinaciones de un PIN de cuatro digitos y usarla contra todos.
     */
    @Test
    fun `el mismo PIN con sal distinta da huellas distintas`() = runTest {
        val huella = ClavePin.deriva("1234", ClavePin.nuevaSal())
        val otra = ClavePin.deriva("1234", ClavePin.nuevaSal())

        assertNotEquals(huella, otra)
    }

    @Test
    fun `la huella no contiene el PIN`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("482913", sal)

        assertFalse(huella.contains("482913"))
        assertEquals(32, Base64.getDecoder().decode(huella).size)
    }

    @Test
    fun `coincide acepta el PIN correcto y rechaza el equivocado`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("2468", sal)

        assertTrue(ClavePin.coincide("2468", huella, sal))
        assertFalse(ClavePin.coincide("2469", huella, sal))
        assertFalse(ClavePin.coincide("", huella, sal))
    }

    /**
     * Sin huella guardada no hay con que comparar, y devolver cierto ahi
     * abriria la app a cualquiera en cuanto las preferencias se corrompieran.
     */
    @Test
    fun `sin huella o sin sal nunca coincide`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.deriva("1111", sal)

        assertFalse(ClavePin.coincide("1111", null, sal))
        assertFalse(ClavePin.coincide("1111", huella, null))
        assertFalse(ClavePin.coincide("1111", "", ""))
        // Una huella ilegible tampoco puede abrir: se trata como no coincidencia.
        assertFalse(ClavePin.coincide("1111", "no-es-base64-%%%", sal))
    }

    @Test
    fun `los limites de largo son los que la interfaz respeta`() {
        assertEquals(4, ClavePin.LARGO_MINIMO)
        assertEquals(12, ClavePin.LARGO_MAXIMO)
    }
}
