package mx.ollin.actividades.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mx.ollin.actividades.data.db.Actividad
import mx.ollin.actividades.data.excel.HojaExportable
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.ui.components.Tutorial
import mx.ollin.actividades.ui.screens.AjustesPantalla
import mx.ollin.actividades.ui.screens.ArchivoPantalla
import mx.ollin.actividades.ui.theme.TemaOllin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Ajustes y Archivo son las dos pantallas cuyo efecto no se ve donde se toca:
 * escriben en preferencias y las leen otras pantallas. Lo que se comprueba aqui
 * es justo esa parte invisible.
 */
@RunWith(AndroidJUnit4::class)
class AjustesYArchivoTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun ajustesGuardados() = runBlocking { banco.contenedor.ajustes.ajustes.first() }

    private fun montaAjustes() {
        compose.setContent {
            TemaOllin(oscuro = true) {
                AjustesPantalla(
                    contenedor = banco.contenedor,
                    alAbrirCategorias = {},
                    alAbrirArchivo = {},
                    alAbrirAcercaDe = {},
                    alCerrar = {}
                )
            }
        }
        compose.esperaTexto("Apariencia")
    }

    private fun montaArchivo() {
        compose.setContent {
            TemaOllin(oscuro = true) { ArchivoPantalla(banco.contenedor) {} }
        }
        compose.esperaTexto("Importar")
    }

    @Test
    fun `elegir_tema_claro_queda_escrito_en_las_preferencias`() {
        montaAjustes()
        compose.esperaTexto("Apariencia")

        compose.onNodeWithText("Claro").performClick()

        compose.espera("se guarda el tema claro") { ajustesGuardados().temaOscuro == false }
        assertEquals(false, ajustesGuardados().temaOscuro)
    }

    @Test
    fun `la_meta_de_trabajo_se_guarda_con_lo_que_se_escriba`() {
        montaAjustes()
        compose.esperaTexto("Metas del dia")

        // 300 minutos es la jornada por omision.
        compose.onNode(campo("Trabajo")).performTextReplacement("420")

        compose.espera("se guarda la meta") { ajustesGuardados().metaTrabajoMinutos == 420 }
        assertEquals(420, ajustesGuardados().metaTrabajoMinutos)
    }

    /**
     * El boton de restaurar tutoriales solo aparece cuando hay algo que
     * restaurar: uno que no hiciera nada ocuparia el mismo sitio y obligaria a
     * preguntarse para que sirve.
     */
    @Test
    fun `restaurar_tutoriales_aparece_solo_cuando_hay_alguno_descartado`() {
        montaAjustes()
        compose.esperaTexto("Mostrar tutoriales")
        assertFalse(compose.hayTexto("Volver a mostrar todos los tutoriales"))

        runBlocking { banco.contenedor.ajustes.ocultaTutorial(Tutorial.HOY.clave) }

        compose.esperaTexto("Volver a mostrar todos los tutoriales")
        compose.onNodeWithText("Volver a mostrar todos los tutoriales").performClick()

        compose.espera("se restauran los tutoriales") { ajustesGuardados().tutorialesOcultos.isEmpty() }
        assertEquals(true, ajustesGuardados().muestraTutoriales)
        assertFalse(compose.hayTexto("Volver a mostrar todos los tutoriales"))
    }

    @Test
    fun `sin_actividades_no_se_puede_exportar`() {
        montaArchivo()
        compose.esperaTexto("Exportar", subcadena = true)

        compose.onNodeWithText("Todavia no hay actividades que exportar.").assertExists()
        compose.onNodeWithText("  Exportar 6 pestañas").assertIsNotEnabled()
    }

    @Test
    fun `con_actividades_el_boton_de_exportar_se_habilita_y_dice_cuantas_pestanas_van`() {
        val hoy = Tiempo.hoy()
        banco.siembra {
            guarda(
                Actividad(
                    titulo = "Algo",
                    estado = EstadoActividad.COMPLETADO,
                    inicio = Tiempo.instante(hoy.atTime(9, 0)),
                    dia = hoy,
                    duracionMinutos = 20
                )
            )
        }
        montaArchivo()

        compose.esperaTexto("  Exportar 6 pestañas")
        compose.onNodeWithText("  Exportar 6 pestañas").assertIsEnabled()
    }

    /** "Solo datos" deja el libro con lo reimportable: cuatro pestañas. */
    @Test
    fun `el_preset_de_solo_datos_recorta_las_pestanas_y_se_guarda`() {
        montaArchivo()
        compose.esperaTexto("Solo datos")

        compose.onNodeWithText("Solo datos").performScrollTo().performClick()

        compose.espera("se guarda la seleccion minima") { ajustesGuardados().hojas == HojaExportable.MINIMA }
        assertEquals(HojaExportable.MINIMA, ajustesGuardados().hojas)
        compose.esperaTexto("  Exportar 4 pestañas")
    }
}
