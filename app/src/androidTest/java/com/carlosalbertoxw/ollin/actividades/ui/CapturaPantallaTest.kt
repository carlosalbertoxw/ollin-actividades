package com.carlosalbertoxw.ollin.actividades.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.screens.CapturaPantalla
import com.carlosalbertoxw.ollin.actividades.ui.theme.TemaOllin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapturaPantallaTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var cerrada = false

    private fun monta(actividadId: Long? = null) {
        compose.setContent {
            TemaOllin(oscuro = true) {
                CapturaPantalla(
                    contenedor = banco.contenedor,
                    actividadId = actividadId,
                    alCerrar = { cerrada = true }
                )
            }
        }
        compose.esperaTexto("Guardar")
    }

    /** El titulo es el unico campo sin valor por omision; sin el no hay registro. */
    @Test
    fun `guardar_sin_titulo_avisa_y_no_escribe_nada`() {
        monta()

        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        compose.esperaTexto("Ponle un titulo para poder guardarlo", subcadena = true)
        assertFalse(cerrada)
        assertTrue(runBlocking { banco.db.actividadDao().todas() }.isEmpty())
    }

    @Test
    fun `una_actividad_nueva_se_guarda_con_su_duracion_y_cierra_la_pantalla`() {
        monta()

        compose.onNode(campo("Que hiciste")).performTextInput("Correr en el parque")
        compose.onNodeWithText("45 min").performScrollTo().performClick()
        compose.onNodeWithText("Guardar").performScrollTo().performClick()

        compose.espera("se cierra la pantalla") { cerrada }

        val guardada = runBlocking { banco.db.actividadDao().todas() }.single()
        assertEquals("Correr en el parque", guardada.titulo)
        assertEquals(45, guardada.duracionMinutos)
        assertEquals(EstadoActividad.COMPLETADO, guardada.estado)
    }

    /** Mientras corre manda el cronometro: capturar minutos a mano ahi no aplica. */
    @Test
    fun `en_curso_se_retira_el_campo_de_duracion`() {
        monta()
        compose.esperaTexto("Duracion en minutos", subcadena = true)

        compose.onNodeWithText("En curso").performScrollTo().performClick()

        compose.esperaTexto("Mientras corre, la duracion la lleva el cronometro.")
        assertFalse(compose.hayTexto("Duracion en minutos", subcadena = true))
    }

    @Test
    fun `editar_una_actividad_trae_sus_datos_y_guarda_encima`() {
        val hoy = Tiempo.hoy()
        val id = banco.siembra {
            guarda(
                Actividad(
                    titulo = "Redactar",
                    estado = EstadoActividad.COMPLETADO,
                    inicio = Tiempo.instante(hoy.atTime(9, 0)),
                    dia = hoy,
                    duracionMinutos = 30
                )
            )
        }
        monta(id)

        compose.esperaTexto("Editar actividad")
        compose.espera("carga el titulo guardado") { compose.hayTexto("Redactar", subcadena = true) }

        compose.onNode(campo("Que hiciste")).performTextReplacement("Redactar el informe")
        compose.onNodeWithText("Guardar").performScrollTo().performClick()
        compose.espera("se cierra la pantalla") { cerrada }

        val guardada = runBlocking { banco.db.actividadDao().todas() }.single()
        assertEquals("Redactar el informe", guardada.titulo)
        // Sigue siendo la misma fila: editar no puede duplicar el registro.
        assertEquals(id, guardada.id)
    }

    @Test
    fun `eliminar_pide_confirmacion_y_borra_la_actividad`() {
        val hoy = Tiempo.hoy()
        val id = banco.siembra {
            guarda(
                Actividad(
                    titulo = "Sobra",
                    estado = EstadoActividad.COMPLETADO,
                    inicio = Tiempo.instante(hoy.atTime(9, 0)),
                    dia = hoy,
                    duracionMinutos = 10
                )
            )
        }
        monta(id)
        compose.esperaTexto("Editar actividad")

        compose.onNodeWithContentDescription("Eliminar").performClick()
        compose.esperaTexto("Eliminar la actividad")
        compose.onNodeWithText("Eliminar").performClick()

        compose.espera("se cierra la pantalla") { cerrada }
        assertNull(runBlocking { banco.db.actividadDao().porId(id) })
    }

    /** El boton de borrar no existe en una actividad que todavia no se guarda. */
    @Test
    fun `una_actividad_nueva_no_ofrece_eliminar`() {
        monta()
        compose.esperaTexto("Nueva actividad")

        assertFalse(compose.hayDescripcion("Eliminar"))
    }
}
