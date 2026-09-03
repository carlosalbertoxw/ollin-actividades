package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.CoordinadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.PlanificadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * El recordatorio de respaldar.
 *
 * Existe porque la base va cifrada con una llave del Keystore que no se
 * respalda ni viaja a otro telefono: el `.xlsx` no es *un* respaldo, es el
 * unico. Lo que estas pruebas cuidan es que el aviso llegue cuando sirve y
 * calle cuando no, que es lo que separa un recordatorio util de una molestia
 * que se acaba apagando.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RespaldoTest {

    private lateinit var db: OllinDatabase
    private lateinit var ajustes: AjustesRepositorio
    private lateinit var coordinador: CoordinadorRecordatorios

    /**
     * Truncado a milisegundos a proposito: las preferencias guardan un `Long`
     * de milisegundos epoch, asi que un instante con nanosegundos no vuelve
     * igual del disco y las comparaciones fallarian por 483 microsegundos.
     */
    private val ahora: Instant = Instant.ofEpochMilli(Tiempo.ahora().toEpochMilli())

    @Before
    fun abre() {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(contexto, OllinDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        ajustes = AjustesRepositorio(contexto)
        ajustes.restauraDeFabrica()

        coordinador = CoordinadorRecordatorios(
            contexto = contexto,
            planificador = PlanificadorRecordatorios(db.habitoDao(), db.actividadDao()),
            ajustes = ajustes
        )
    }

    @After
    fun cierra() = db.close()

    /** El horizonte que usa despacha(), para no descartar el aviso por lejano. */
    private val hasta: Instant get() = ahora.plus(Duration.ofDays(120))

    private suspend fun pendiente() =
        coordinador.respaldoPendiente(ajustes.ajustes.first(), ahora, hasta)

    private suspend fun registraAlgo() {
        db.actividadDao().inserta(
            Actividad(
                titulo = "algo",
                estado = EstadoActividad.COMPLETADO,
                inicio = ahora,
                fin = ahora,
                dia = Tiempo.dia(ahora),
                duracionMinutos = 10
            )
        )
    }

    private fun haceDias(dias: Long) = ahora.minus(Duration.ofDays(dias)).toEpochMilli()

    // -------------------------------------------------------- cuando calla

    /**
     * Estrenar la app y recibir a los dos minutos "respalda tu bitacora" no
     * tiene ningun sentido: lo que hace el primer arranque es poner el reloj a
     * cero, no avisar.
     *
     * Se comprueba por el contrato visible —no hay aviso inmediato, hay una
     * cita a una semana— y no fijando el instante exacto: el plazo lo estrena
     * tanto el primer arranque como encender el interruptor, y las dos cosas
     * ocurren en el `@Before`. Atarse a cual de las dos gano seria atarse a un
     * detalle del andamio.
     */
    @Test
    fun `recien instalada no avisa de golpe`() = runTest {
        registraAlgo()

        val aviso = pendiente()
        assertNotNull("Se programa, no se dispara", aviso)
        assertEquals(
            "Y cae a una semana vista",
            7L,
            Duration.between(ahora, aviso!!.cuando).toDays()
        )
    }

    @Test
    fun `sin nada registrado no se recuerda respaldar`() = runTest {
        ajustes.marcaRespaldo(haceDias(30))

        assertNull("Una instalacion recien estrenada no tiene nada que perder", pendiente())
    }

    @Test
    fun `antes de la semana no se dice nada`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(3))

        val aviso = pendiente()
        assertNotNull("Se programa para su fecha", aviso)
        assertEquals(
            "Dentro de cuatro dias, no ahora",
            ahora.plus(Duration.ofDays(4)),
            aviso!!.cuando
        )
    }

    @Test
    fun `apagado no avisa aunque toque`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(30))
        ajustes.guardaAvisaRespaldo(false)

        assertNull(pendiente())
    }

    // -------------------------------------------------------- cuando avisa

    @Test
    fun `pasada la semana sin respaldar, toca`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(8))

        val aviso = pendiente()
        assertNotNull(aviso)
        assertEquals("Vencido se avisa ya", ahora, aviso!!.cuando)
    }

    /**
     * El aviso desatendido no se repite en cada replanificacion —que ocurre
     * varias veces al dia— sino a la semana siguiente.
     */
    @Test
    fun `un aviso desatendido no se repite hasta la semana siguiente`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(30))
        ajustes.marcaAvisoDeRespaldo(haceDias(2))

        val aviso = pendiente()
        assertEquals(
            "Cinco dias despues del ultimo aviso, no otra vez ahora",
            ahora.plus(Duration.ofDays(5)),
            aviso!!.cuando
        )
    }

    @Test
    fun `exportar reinicia el plazo`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(30))
        assertEquals(ahora, pendiente()!!.cuando)

        // Lo que hace ArchivoVm al terminar de exportar.
        ajustes.marcaRespaldo(ahora.toEpochMilli())

        assertEquals(
            "El siguiente cae una semana despues",
            ahora.plus(Duration.ofDays(7)),
            pendiente()!!.cuando
        )
    }

    @Test
    fun `encender el interruptor estrena plazo, no avisa de golpe`() = runTest {
        registraAlgo()
        ajustes.marcaRespaldo(haceDias(30))
        ajustes.guardaAvisaRespaldo(false)
        ajustes.guardaAvisaRespaldo(true)

        val aviso = pendiente()
        assertNotNull(aviso)
        assertEquals(
            "Quien lo enciende hoy quiere el de dentro de una semana",
            7L,
            Duration.between(ahora, aviso!!.cuando).toDays()
        )
    }

    // -------------------------------------------------------- version nueva

    @Test
    fun `de una version solo se avisa una vez`() = runTest {
        coordinador.avisaDeVersionNueva("1.2.0")
        assertEquals("1.2.0", ajustes.ajustes.first().versionAvisada)

        // La segunda no vuelve a notificar: ya se dijo.
        coordinador.avisaDeVersionNueva("1.2.0")
        assertEquals("1.2.0", ajustes.ajustes.first().versionAvisada)

        coordinador.avisaDeVersionNueva("1.3.0")
        assertEquals(
            "Una version distinta si se anuncia",
            "1.3.0",
            ajustes.ajustes.first().versionAvisada
        )
    }
}
