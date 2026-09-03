package com.carlosalbertoxw.ollin.actividades.ui

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import org.junit.rules.ExternalResource

/**
 * El andamio comun de las pruebas de interfaz.
 *
 * Levanta un [Contenedor] de verdad —mismos ViewModel, mismo repositorio, mismas
 * preferencias— pero con la base en memoria: la de produccion va cifrada y
 * compartirla entre pruebas dejaria a cada una heredando los datos de la
 * anterior. Todo lo demas que se ejercita es el codigo que se va a publicar.
 */
class BancoDePruebas : ExternalResource() {

    lateinit var db: OllinDatabase
        private set

    lateinit var contenedor: Contenedor
        private set

    val repo: ActividadesRepositorio get() = contenedor.repositorio

    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, OllinDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        contenedor = Contenedor(app) { db }
        contenedor.ajustes.restauraDeFabrica()
    }

    override fun after() {
        db.close()
    }

    /** Prepara datos antes de componer. La pantalla lee del mismo repositorio. */
    fun <T> siembra(bloque: suspend ActividadesRepositorio.() -> T): T = runBlocking { repo.bloque() }
}

/**
 * Devuelve las preferencias a su estado de fabrica.
 *
 * El DataStore de la app es uno solo y sobrevive de una prueba a la siguiente,
 * asi que sin esto lo que una guarda condicionaria a la que corra despues y el
 * resultado de la suite dependeria del orden. Los valores salen de [Ajustes],
 * que es donde estan declarados: si alguno cambia, esto lo sigue solo.
 */
fun AjustesRepositorio.restauraDeFabrica() = runBlocking {
    val omision = Ajustes()
    guardaTema(omision.temaOscuro)
    guardaColorDinamico(omision.colorDinamico)
    guardaMetaTrabajo(omision.metaTrabajoMinutos)
    guardaMetaFisico(omision.metaFisicoMinutos)
    guardaDuracionRapida(omision.duracionRapidaMinutos)
    guardaMuestraCompletadas(omision.muestraCompletadasEnHoy)
    guardaRecordatorios(omision.recordatorios)
    guardaBuscarActualizaciones(omision.buscarActualizaciones)
    guardaAvisaRespaldo(omision.avisaRespaldo)
    reiniciaTutoriales()
    guardaEsquema(EsquemaExportacion.EXTENDIDO)
    guardaHojas(HojaExportable.PREDETERMINADAS)
    guardaReemplazar(omision.reemplazarAlImportar)
    guardaCreaFaltantes(omision.creaFaltantesAlImportar)
    quitaBloqueo()
}

/**
 * Espera a que se cumpla algo y deja la pantalla asentada antes de devolver el
 * control.
 *
 * Se sondea en tiempo real en vez de usar `waitUntil` porque la condicion no
 * siempre es lo que hay en pantalla: a veces es lo que quedo escrito en la base
 * o en las preferencias, y eso ocurre en hilos que el reloj de Compose no mueve.
 *
 * El `waitForIdle` del final no sobra: que un texto ya se vea no significa que
 * la entrada haya terminado de animarse, y pulsar sobre algo que todavia se
 * esta acomodando es la receta de una prueba que falla una de cada tres veces.
 */
fun ComposeContentTestRule.espera(que: String, condicion: () -> Boolean) {
    val limite = System.currentTimeMillis() + TIEMPO_LIMITE_MS
    while (true) {
        if (condicion()) return waitForIdle()
        if (System.currentTimeMillis() > limite) break
        Thread.sleep(PAUSA_MS)
    }
    throw AssertionError("No se cumplio en $TIEMPO_LIMITE_MS ms: $que")
}

fun ComposeContentTestRule.esperaTexto(texto: String, subcadena: Boolean = false) =
    espera("aparece \"$texto\"") { hayTexto(texto, subcadena) }

fun ComposeContentTestRule.esperaSinTexto(texto: String, subcadena: Boolean = false) =
    espera("desaparece \"$texto\"") { !hayTexto(texto, subcadena) }

fun ComposeContentTestRule.esperaDescripcion(texto: String) =
    espera("aparece el control \"$texto\"") { hayDescripcion(texto) }

fun ComposeContentTestRule.hayTexto(texto: String, subcadena: Boolean = false): Boolean =
    onAllNodesWithText(texto, substring = subcadena, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

/** Lo mismo para los controles que solo se anuncian a lectores de pantalla. */
fun ComposeContentTestRule.hayDescripcion(texto: String): Boolean =
    onAllNodesWithContentDescription(texto, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .isNotEmpty()

/**
 * Una pestaña de la barra de abajo. Hace falta distinguirla del texto suelto:
 * "Hoy" es a la vez el nombre de la pestaña y el titulo de la pantalla.
 */
fun pestana(nombre: String) =
    hasText(nombre) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

/**
 * El campo de un formulario, buscado por su etiqueta y no por su valor: la
 * etiqueta no cambia mientras se escribe, y el texto de apoyo la acompaña en el
 * mismo nodo, asi que la comparacion tiene que ser por subcadena.
 */
fun campo(etiqueta: String) = hasText(etiqueta, substring = true) and hasSetTextAction()

private const val TIEMPO_LIMITE_MS = 10_000L
private const val PAUSA_MS = 50L
