package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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

    // ---------------------------------------------------------------- sello

    @Test
    fun `la huella sellada lleva su marca y abre con el mismo sello`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.huella("2468", sal, SELLO)

        assertTrue(huella.startsWith(ClavePin.PREFIJO_SELLADA))
        assertEquals(
            ClavePin.Verificacion.CORRECTO,
            ClavePin.verifica("2468", huella, sal, SELLO)
        )
        assertEquals(
            ClavePin.Verificacion.INCORRECTO,
            ClavePin.verifica("2469", huella, sal, SELLO)
        )
    }

    /**
     * Lo que compra el sello: la huella copiada a otro aparato no se puede
     * comprobar alli, porque la llave del sello no salio de este.
     */
    @Test
    fun `una huella sellada no coincide con otro sello ni sin sello`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.huella("2468", sal, SELLO)

        assertFalse(ClavePin.coincide("2468", huella, sal, OTRO_SELLO))
        assertFalse(ClavePin.coincide("2468", huella, sal))
    }

    /** Las huellas de antes del sello siguen abriendo, y avisan que hay que sellarlas. */
    @Test
    fun `una huella sin sellar acierta pidiendo que se selle`() = runTest {
        val sal = ClavePin.nuevaSal()
        val vieja = ClavePin.deriva("2468", sal)

        assertEquals(
            ClavePin.Verificacion.CORRECTO_SIN_SELLAR,
            ClavePin.verifica("2468", vieja, sal, SELLO)
        )
    }

    /** Un sello que falla es un PIN incorrecto, no una pantalla de bloqueo caida. */
    @Test
    fun `un sello que lanza no tumba la verificacion`() = runTest {
        val sal = ClavePin.nuevaSal()
        val huella = ClavePin.huella("2468", sal, SELLO)
        val roto = ClavePin.Sello { error("sin Keystore") }

        assertEquals(
            ClavePin.Verificacion.INCORRECTO,
            ClavePin.verifica("2468", huella, sal, roto)
        )
    }

    private companion object {
        /** El Keystore no existe en la JVM: un HMAC con llave fija hace sus veces. */
        val SELLO = selloCon(1)
        val OTRO_SELLO = selloCon(2)

        fun selloCon(semilla: Byte) = ClavePin.Sello { huella ->
            Mac.getInstance("HmacSHA256")
                .apply { init(SecretKeySpec(ByteArray(32) { semilla }, "HmacSHA256")) }
                .doFinal(huella)
        }
    }
}
