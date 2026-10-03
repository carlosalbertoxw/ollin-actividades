package com.carlosalbertoxw.ollin.actividades.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.CoordinadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.ui.screens.HoyPantalla
import com.carlosalbertoxw.ollin.actividades.ui.theme.TemaOllin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El aviso de respaldo arriba de Hoy, sobre un dispositivo.
 *
 * Las reglas --cuando toca, cuanto dura quitarlo-- ya se prueban en la JVM
 * (AvisoDeRespaldoTest). Aqui se prueba lo que solo existe en pantalla: que Hoy
 * escuche DataStore y la bitacora, y que la tarjeta aparezca, lleve a Archivo,
 * se quite con la cruz y se vaya sola en cuanto hay un respaldo.
 */
@RunWith(AndroidJUnit4::class)
class AvisoDeRespaldoEnHoyTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var archivoAbierto = false

    /** Una bitacora con algo dentro y un respaldo de hace ocho dias. */
    private fun montaConRespaldoVencido() {
        banco.siembra { iniciaAhora("Caminata") }
        runBlocking {
            banco.contenedor.ajustes.marcaRespaldo(System.currentTimeMillis() - OCHO_DIAS_MS)
        }
        compose.setContent {
            TemaOllin(oscuro = true) {
                HoyPantalla(
                    contenedor = banco.contenedor,
                    alAbrirActividad = {},
                    alRegistrarHabito = { _, _ -> },
                    alAbrirAjustes = {},
                    alAbrirArchivo = { archivoAbierto = true }
                )
            }
        }
    }

    @Test
    fun el_aviso_sale_arriba_con_los_dias_y_lleva_a_archivo() {
        montaConRespaldoVencido()

        compose.esperaTexto("Tu último respaldo es de hace 8 días")
        compose.onNodeWithText("Exportar ahora").performClick()

        compose.espera("se abre Archivo") { archivoAbierto }
    }

    @Test
    fun la_cruz_lo_quita() {
        montaConRespaldoVencido()
        compose.esperaTexto(TITULO, subcadena = true)

        compose.onNodeWithContentDescription("Quitar el aviso").performClick()

        compose.esperaSinTexto(TITULO, subcadena = true)
    }

    /** Exportar escribe la fecha del respaldo, y con eso basta para que se vaya. */
    @Test
    fun un_respaldo_hecho_lo_quita_solo() {
        montaConRespaldoVencido()
        compose.esperaTexto(TITULO, subcadena = true)

        runBlocking { banco.contenedor.ajustes.marcaRespaldo(System.currentTimeMillis()) }

        compose.esperaSinTexto(TITULO, subcadena = true)
    }

    /** Con el recordatorio apagado en Ajustes no sale ni la notificacion ni esto. */
    @Test
    fun apagado_en_ajustes_no_sale() {
        montaConRespaldoVencido()
        compose.esperaTexto(TITULO, subcadena = true)

        runBlocking { banco.contenedor.ajustes.guardaAvisaRespaldo(false) }

        compose.esperaSinTexto(TITULO, subcadena = true)
        assertTrue(!compose.hayTexto("Exportar ahora"))
    }

    private companion object {
        const val TITULO = "Tu último respaldo es de"
        val OCHO_DIAS_MS = (CoordinadorRecordatorios.PLAZO_RESPALDO_DIAS + 1) * 24L * 60 * 60 * 1000
    }
}
