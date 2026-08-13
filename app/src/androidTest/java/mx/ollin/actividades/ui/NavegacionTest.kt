package mx.ollin.actividades.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import mx.ollin.actividades.ui.theme.TemaOllin
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Que todo lo que la app ofrece se pueda alcanzar con el dedo.
 *
 * Es la prueba que protege contra la peor clase de regresion: una pantalla que
 * sigue compilando y sigue existiendo, pero a la que ya no lleva ningun camino.
 */
@RunWith(AndroidJUnit4::class)
class NavegacionTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun montaRaiz() {
        compose.setContent { TemaOllin(oscuro = true) { OllinRaiz(banco.contenedor) } }
        compose.esperaTexto("Que estas haciendo")
    }

    @Test
    fun `las_cuatro_pestanas_estan_y_se_puede_entrar_a_cada_una`() {
        montaRaiz()

        listOf("Hoy", "Registro", "Habitos", "Analitica").forEach { nombre ->
            compose.onNode(pestana(nombre)).assertIsDisplayed()
        }

        compose.onNode(pestana("Registro")).performClick()
        compose.esperaTexto("Buscar por titulo o nota")

        compose.onNode(pestana("Analitica")).performClick()
        compose.esperaTexto("Todavia no hay que graficar")

        compose.onNode(pestana("Habitos")).performClick()
        compose.esperaTexto("Sin habitos todavia")

        compose.onNode(pestana("Hoy")).performClick()
        compose.esperaTexto("Que estas haciendo")
    }

    @Test
    fun `el_boton_de_registrar_abre_la_captura_y_se_puede_cerrar`() {
        montaRaiz()

        compose.onNodeWithContentDescription("Registrar").performClick()
        compose.esperaTexto("Nueva actividad")

        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.esperaTexto("Que estas haciendo")
    }

    /**
     * En Habitos manda su propio boton flotante. Dos botones encimados no se
     * entienden, asi que el de Registrar tiene que retirarse.
     */
    @Test
    fun `en_habitos_se_retira_el_boton_de_registrar`() {
        montaRaiz()

        compose.onNode(pestana("Habitos")).performClick()
        compose.esperaDescripcion("Nuevo habito")
        compose.espera("se retira el boton de registrar") { !compose.hayDescripcion("Registrar") }
    }

    @Test
    fun `desde_hoy_se_llega_a_ajustes_y_de_ahi_a_categorias_archivo_y_acerca_de`() {
        montaRaiz()

        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.esperaTexto("Metas del dia")

        compose.onNodeWithText("Categorias").performScrollTo().performClick()
        compose.esperaDescripcion("Nueva categoria")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.esperaTexto("Metas del dia")

        compose.onNodeWithText("Archivo").performScrollTo().performClick()
        compose.esperaTexto("Importar")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.esperaTexto("Metas del dia")

        compose.onNodeWithText("Acerca de Ollin").performScrollTo().performClick()
        compose.esperaTexto("Que quiere decir Ollin")
    }
}
