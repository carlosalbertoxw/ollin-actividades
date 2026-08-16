package com.carlosalbertoxw.ollin.actividades.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.ui.screens.HabitosPantalla
import com.carlosalbertoxw.ollin.actividades.ui.theme.TemaOllin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** La lista de habitos: alta con su cadencia, pausa, marcado y borrado. */
@RunWith(AndroidJUnit4::class)
class HabitosPantallaTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private fun monta() {
        compose.setContent { TemaOllin(oscuro = true) { HabitosPantalla(banco.contenedor) } }
        compose.esperaDescripcion("Nuevo habito")
    }

    @Test
    fun `sin_habitos_explica_que_es_un_habito`() {
        monta()
        compose.esperaTexto("Sin habitos todavia")
    }

    @Test
    fun `el_dialogo_da_de_alta_un_habito_con_su_cadencia`() {
        monta()
        compose.esperaTexto("Sin habitos todavia")

        compose.onNodeWithContentDescription("Nuevo habito").performClick()
        compose.esperaTexto("Cada cuando")

        compose.onNode(campo("Nombre")).performTextInput("Regar las plantas")
        compose.onNodeWithText("Cada tantos dias").performClick()
        compose.onNodeWithText("Guardar").performClick()

        compose.esperaTexto("Cada quincena", subcadena = true)

        val guardado = runBlocking { banco.db.habitoDao().todos() }.single()
        assertEquals("Regar las plantas", guardado.nombre)
        assertEquals(Frecuencia.CADA_DIAS, guardado.frecuencia)
        // Quince dias es el valor por omision de la cadencia periodica.
        assertEquals(15, guardado.intervaloDias)
    }

    /** Los dias de la semana solo tienen sentido en la frecuencia que los usa. */
    @Test
    fun `el_selector_de_dias_solo_aparece_en_dias_elegidos`() {
        monta()

        compose.onNodeWithContentDescription("Nuevo habito").performClick()
        compose.esperaTexto("Cada cuando")
        assertFalse(compose.hayTexto("X"))

        compose.onNodeWithText("Dias elegidos").performClick()
        compose.esperaTexto("X")
    }

    @Test
    fun `eliminar_un_habito_lo_saca_de_la_lista`() {
        banco.siembra { guardaHabito(Habito(nombre = "Sobra")) }
        monta()

        compose.esperaTexto("Sobra")
        compose.onNodeWithText("Sobra").performClick()
        compose.esperaTexto("Editar habito")

        compose.onNodeWithContentDescription("Eliminar").performClick()

        compose.esperaTexto("Sin habitos todavia")
        assertTrue(runBlocking { banco.db.habitoDao().todos() }.isEmpty())
    }

    /**
     * Pausar no puede equivaler a perder: el habito baja a "En pausa" y desde
     * ahi se reanuda. Si desapareciera de la lista no habria forma de volver.
     */
    @Test
    fun `un_habito_pausado_sigue_en_la_lista_y_se_puede_reanudar`() {
        banco.siembra { guardaHabito(Habito(nombre = "Correr", activo = false)) }
        monta()

        compose.esperaTexto("En pausa")
        compose.esperaTexto("Correr")

        compose.onNodeWithContentDescription("Reanudar habito").performClick()

        compose.esperaSinTexto("En pausa")
        assertTrue(runBlocking { banco.db.habitoDao().todos() }.single().activo)
    }

    @Test
    fun `marcar_un_habito_desde_su_tarjeta_deja_el_cumplimiento_y_lo_puede_deshacer`() {
        banco.siembra { guardaHabito(Habito(nombre = "Meditar", minutosSugeridos = 10)) }
        monta()

        compose.esperaTexto("Meditar")
        compose.onNodeWithContentDescription("Marcar hoy").performClick()

        compose.esperaTexto("hecho hoy", subcadena = true)
        assertEquals(1, runBlocking { banco.db.actividadDao().cuenta() })

        compose.onNodeWithContentDescription("Deshacer hoy").performClick()
        compose.esperaTexto("pendiente hoy", subcadena = true)
        assertEquals(0, runBlocking { banco.db.actividadDao().cuenta() })
    }
}
