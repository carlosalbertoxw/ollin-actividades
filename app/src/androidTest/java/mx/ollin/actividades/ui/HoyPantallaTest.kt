package mx.ollin.actividades.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import mx.ollin.actividades.data.db.Actividad
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.ui.screens.HoyPantalla
import mx.ollin.actividades.ui.theme.TemaOllin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * La pantalla del dia es por donde entra casi todo lo que se registra, asi que
 * lo que se prueba aqui es el recorrido completo: lo que se toca en la interfaz
 * termina escrito en la base, y lo que hay en la base se ve en la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class HoyPantallaTest {

    @get:Rule(order = 0)
    val banco = BancoDePruebas()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var abierta: Long? = null
    private var ajustesAbiertos = false

    private fun monta() {
        compose.setContent {
            TemaOllin(oscuro = true) {
                HoyPantalla(
                    contenedor = banco.contenedor,
                    alAbrirActividad = { abierta = it },
                    alAbrirAjustes = { ajustesAbiertos = true }
                )
            }
        }
    }

    @Test
    fun `sin_nada_registrado_invita_a_escribir_y_no_deja_iniciar_en_blanco`() {
        monta()

        compose.esperaTexto("Que estas haciendo")
        compose.esperaTexto("Sin tiempo registrado todavia")
        compose.onNodeWithText("Iniciar").assertIsNotEnabled()
    }

    @Test
    fun `escribir_un_titulo_e_iniciar_deja_el_cronometro_corriendo`() {
        monta()
        compose.esperaTexto("Que estas haciendo")

        compose.onNodeWithText("Reunion de diseno, correr 5 km...").performTextInput("Enfoque profundo")
        compose.onNodeWithText("Iniciar").performClick()

        compose.esperaTexto("Detener")
        compose.esperaTexto("Enfoque profundo")

        val corriendo = runBlocking { banco.db.actividadDao().enCurso() }
        assertEquals(1, corriendo.size)
        assertEquals("Enfoque profundo", corriendo.first().titulo)
        assertNull(corriendo.first().duracionMinutos)
    }

    @Test
    fun `detener_cierra_la_actividad_y_le_escribe_su_duracion`() {
        banco.siembra { iniciaAhora("Caminata") }
        monta()

        compose.esperaTexto("Detener")
        compose.onNodeWithText("Detener").performClick()

        compose.esperaTexto("Que estas haciendo")

        val todas = runBlocking { banco.db.actividadDao().todas() }
        assertEquals(1, todas.size)
        assertEquals(EstadoActividad.COMPLETADO, todas.first().estado)
        assertNotNull(todas.first().fin)
        assertNotNull(todas.first().duracionMinutos)
    }

    @Test
    fun `marcar_un_habito_lo_deja_hecho_y_el_boton_pasa_a_deshacer`() {
        banco.siembra {
            val categoria = guardaCategoria(Categoria(nombre = "Lectura", ambito = Ambito.HABITO))
            guardaHabito(Habito(nombre = "Leer", categoriaId = categoria, minutosSugeridos = 20))
        }
        monta()

        compose.esperaTexto("Habitos de hoy")
        compose.esperaTexto("Sin racha activa", subcadena = true)

        compose.onNodeWithContentDescription("Marcar").performClick()

        compose.esperaDescripcion("Deshacer")
        compose.esperaTexto("Racha de 1 dias", subcadena = true)

        val cumplimientos = runBlocking { banco.db.actividadDao().todas() }
        assertEquals(1, cumplimientos.size)
        assertEquals(20, cumplimientos.first().duracionMinutos)

        // Y se puede desandar: marcar de mas no debe ser irreversible.
        compose.onNodeWithContentDescription("Deshacer").performClick()
        compose.esperaTexto("Sin racha activa", subcadena = true)
        assertTrue(runBlocking { banco.db.actividadDao().todas() }.isEmpty())
    }

    /**
     * Una pendiente ofrece dos caminos: arrancar el cronometro o darla por hecha
     * con la duracion rapida de los ajustes. Los dos tienen que estar a la vista
     * sin abrir la actividad.
     */
    @Test
    fun `una_pendiente_se_puede_marcar_como_hecha_sin_cronometrarla`() {
        val hoy = Tiempo.hoy()
        banco.siembra {
            guarda(
                Actividad(
                    titulo = "Llamar al banco",
                    estado = EstadoActividad.PENDIENTE,
                    inicio = Tiempo.instante(hoy.atTime(9, 0)),
                    dia = hoy
                )
            )
        }
        monta()

        compose.esperaTexto("Pendientes")
        compose.onNodeWithContentDescription("Marcar como hecha").performClick()

        compose.esperaTexto("Registro de hoy")

        val guardada = runBlocking { banco.db.actividadDao().todas() }.first()
        assertEquals(EstadoActividad.COMPLETADO, guardada.estado)
        // La duracion rapida por omision son 25 minutos.
        assertEquals(25, guardada.duracionMinutos)
    }

    @Test
    fun `pulsar_un_renglon_abre_esa_actividad_y_el_engrane_abre_ajustes`() {
        val hoy = Tiempo.hoy()
        val id = banco.siembra {
            guarda(
                Actividad(
                    titulo = "Redactar informe",
                    estado = EstadoActividad.COMPLETADO,
                    inicio = Tiempo.instante(hoy.atTime(10, 0)),
                    dia = hoy,
                    duracionMinutos = 40
                )
            )
        }
        monta()

        compose.esperaTexto("Redactar informe")
        compose.onNodeWithText("Redactar informe").performClick()
        assertEquals(id, abierta)

        compose.onNodeWithContentDescription("Ajustes").performClick()
        assertTrue(ajustesAbiertos)
    }
}
