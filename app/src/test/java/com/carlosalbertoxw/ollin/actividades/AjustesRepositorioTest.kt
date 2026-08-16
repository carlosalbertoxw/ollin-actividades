package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AjustesRepositorioTest {

    private lateinit var repo: AjustesRepositorio

    @Before
    fun abre() {
        repo = AjustesRepositorio(ApplicationProvider.getApplicationContext<Application>())
        // El DataStore se cachea entre pruebas de la misma clase: sin volver a
        // fabrica, cada una heredaria lo que escribio la anterior.
        repo.restauraDeFabrica()
    }

    private suspend fun actuales() = repo.ajustes.first()

    /**
     * Los valores de partida se afirman sobre [Ajustes], que es donde estan
     * declarados. Contra el almacen no significarian nada: cualquier prueba de
     * esta clase pudo escribir antes.
     */
    @Test
    fun `los valores de fabrica son los que documenta la pantalla de ajustes`() {
        val a = Ajustes()

        assertNull(a.temaOscuro)        // sigue al sistema
        assertEquals(false, a.colorDinamico)
        assertEquals(300, a.metaTrabajoMinutos)
        assertEquals(30, a.metaFisicoMinutos)
        assertEquals(25, a.duracionRapidaMinutos)
        assertEquals(true, a.muestraCompletadasEnHoy)
        assertEquals(true, a.muestraTutoriales)
        assertEquals(EsquemaExportacion.EXTENDIDO, a.esquema)
        assertEquals(HojaExportable.PREDETERMINADAS, a.hojas)
        assertEquals(true, a.reemplazarAlImportar)
        assertEquals(true, a.creaFaltantesAlImportar)
    }

    @Test
    fun `el tema recorre sus tres estados`() = runTest {
        repo.guardaTema(true)
        assertEquals(true, actuales().temaOscuro)

        repo.guardaTema(false)
        assertEquals(false, actuales().temaOscuro)

        repo.guardaTema(null)
        assertNull(actuales().temaOscuro)
    }

    /**
     * Las metas se acotan al escribir: un dia no tiene mas de 1440 minutos, y
     * una meta imposible dejaria la barra de hoy pegada al cero para siempre.
     */
    @Test
    fun `las metas no salen del dia`() = runTest {
        repo.guardaMetaTrabajo(99_999)
        assertEquals(24 * 60, actuales().metaTrabajoMinutos)

        repo.guardaMetaFisico(-10)
        assertEquals(0, actuales().metaFisicoMinutos)

        repo.guardaDuracionRapida(0)
        assertEquals(1, actuales().duracionRapidaMinutos)
    }

    /** Registros es la fuente de las demas hojas: no se puede quedar fuera. */
    @Test
    fun `guardar hojas siempre conserva Registros`() = runTest {
        repo.guardaHojas(setOf(HojaExportable.POR_DIA))

        val hojas = actuales().hojas
        assertTrue(HojaExportable.REGISTROS in hojas)
        assertTrue(HojaExportable.POR_DIA in hojas)
    }

    /**
     * Una seleccion vacia caeria en un libro sin pestañas. Si en el disco quedo
     * algo asi, se vuelve a la seleccion completa en vez de exportar nada.
     */
    @Test
    fun `una seleccion vacia de hojas cae en las predeterminadas`() = runTest {
        repo.guardaHojas(emptySet())

        assertEquals(HojaExportable.normaliza(emptySet()), actuales().hojas)
        assertTrue(HojaExportable.REGISTROS in actuales().hojas)
    }

    @Test
    fun `los tutoriales se ocultan de uno en uno y se restauran todos juntos`() = runTest {
        repo.ocultaTutorial("hoy")
        repo.ocultaTutorial("habitos")
        repo.guardaMuestraTutoriales(false)

        assertEquals(setOf("hoy", "habitos"), actuales().tutorialesOcultos)

        repo.reiniciaTutoriales()

        assertEquals(emptySet<String>(), actuales().tutorialesOcultos)
        assertEquals(true, actuales().muestraTutoriales)
    }

    @Test
    fun `las opciones de importacion se recuerdan`() = runTest {
        repo.guardaReemplazar(false)
        repo.guardaCreaFaltantes(false)
        repo.guardaEsquema(EsquemaExportacion.COMPACTO)

        val a = actuales()
        assertEquals(false, a.reemplazarAlImportar)
        assertEquals(false, a.creaFaltantesAlImportar)
        assertEquals(EsquemaExportacion.COMPACTO, a.esquema)
    }
}
