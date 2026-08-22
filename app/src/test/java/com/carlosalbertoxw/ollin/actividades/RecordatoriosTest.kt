package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.PlanificadorRecordatorios
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Recordatorio
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Que avisa Ollin y cuando.
 *
 * El planificador es la unica parte de los recordatorios que se puede probar en
 * la JVM: lo demas —AlarmManager, el canal de notificaciones, el arranque del
 * telefono— es sistema operativo. Por eso la logica de "que toca" vive alli y
 * no repartida entre el receptor y el coordinador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RecordatoriosTest {

    private lateinit var db: OllinDatabase
    private lateinit var planificador: PlanificadorRecordatorios

    private val hoy: LocalDate = Tiempo.hoy()

    @Before
    fun abre() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OllinDatabase::class.java
        ).allowMainThreadQueries().build()
        planificador = PlanificadorRecordatorios(db.habitoDao(), db.actividadDao())
    }

    @After
    fun cierra() = db.close()

    /** La ventana del dia de hoy entero, que es donde caen casi todas las pruebas. */
    private suspend fun avisosDeHoy(): List<Recordatorio> = planificador.entre(
        Tiempo.instante(hoy.atStartOfDay()),
        Tiempo.instante(hoy.plusDays(1).atStartOfDay())
    )

    private suspend fun cumple(habitoId: Long, dia: LocalDate = hoy) {
        val inicio = Tiempo.instante(dia.atTime(9, 0))
        db.actividadDao().inserta(
            Actividad(
                titulo = "hecho",
                habitoId = habitoId,
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                fin = inicio,
                dia = dia,
                duracionMinutos = 10
            )
        )
    }

    @Test
    fun `un habito sin hora no avisa`() = runTest {
        db.habitoDao().inserta(Habito(nombre = "Leer"))

        assertTrue(avisosDeHoy().isEmpty())
    }

    @Test
    fun `un habito diario avisa a su hora`() = runTest {
        db.habitoDao().inserta(
            Habito(nombre = "Leer", horaRecordatorio = LocalTime.of(20, 30))
        )

        val avisos = avisosDeHoy()

        assertEquals(1, avisos.size)
        assertEquals("Leer", avisos.first().titulo)
        assertEquals(Tiempo.instante(hoy.atTime(20, 30)), avisos.first().cuando)
    }

    /** Avisar de lo ya hecho es la forma mas rapida de que alguien apague todo. */
    @Test
    fun `un habito ya cumplido hoy no avisa`() = runTest {
        val id = db.habitoDao().inserta(
            Habito(nombre = "Leer", horaRecordatorio = LocalTime.of(20, 30))
        )
        cumple(id)

        assertTrue(avisosDeHoy().isEmpty())
    }

    /** Con meta de tres, el primer cumplimiento no lo da por hecho. */
    @Test
    fun `un habito con meta diaria sigue avisando hasta completarla`() = runTest {
        val id = db.habitoDao().inserta(
            Habito(nombre = "Tomar agua", metaDiaria = 3, horaRecordatorio = LocalTime.of(11, 0))
        )
        cumple(id)
        assertEquals(1, avisosDeHoy().size)

        cumple(id)
        cumple(id)
        assertTrue(avisosDeHoy().isEmpty())
    }

    @Test
    fun `un habito pausado no avisa`() = runTest {
        db.habitoDao().inserta(
            Habito(nombre = "Correr", activo = false, horaRecordatorio = LocalTime.of(7, 0))
        )

        assertTrue(avisosDeHoy().isEmpty())
    }

    /** El calendario manda: si el habito no toca hoy, hoy no hay aviso. */
    @Test
    fun `un habito de dias elegidos solo avisa los dias que toca`() = runTest {
        val soloHoy = 1 shl (hoy.dayOfWeek.value - 1)
        db.habitoDao().inserta(
            Habito(
                nombre = "Gimnasio",
                frecuencia = Frecuencia.DIAS_ELEGIDOS,
                diasSemana = soloHoy,
                horaRecordatorio = LocalTime.of(7, 0)
            )
        )
        db.habitoDao().inserta(
            Habito(
                nombre = "Nunca",
                frecuencia = Frecuencia.DIAS_ELEGIDOS,
                diasSemana = DiasSemana.TODOS xor soloHoy,
                horaRecordatorio = LocalTime.of(7, 0)
            )
        )

        assertEquals(listOf("Gimnasio"), avisosDeHoy().map { it.titulo })
    }

    /** Cada quince dias anclado a hoy: hoy si, manana no, en quince si. */
    @Test
    fun `un habito periodico avisa siguiendo su ancla`() = runTest {
        db.habitoDao().inserta(
            Habito(
                nombre = "Regar",
                frecuencia = Frecuencia.CADA_DIAS,
                intervaloDias = 15,
                ancla = hoy,
                horaRecordatorio = LocalTime.of(9, 0)
            )
        )

        val quincena = planificador.entre(
            Tiempo.instante(hoy.atStartOfDay()),
            Tiempo.instante(hoy.plusDays(20).atStartOfDay())
        )

        assertEquals(
            listOf(
                Tiempo.instante(hoy.atTime(9, 0)),
                Tiempo.instante(hoy.plusDays(15).atTime(9, 0))
            ),
            quincena.map { it.cuando }
        )
    }

    @Test
    fun `una tarea pendiente avisa a su hora de inicio`() = runTest {
        val inicio = Tiempo.instante(hoy.atTime(16, 45))
        db.actividadDao().inserta(
            Actividad(
                titulo = "Llamar al dentista",
                estado = EstadoActividad.PENDIENTE,
                inicio = inicio,
                dia = hoy
            )
        )

        val avisos = avisosDeHoy()

        assertEquals(1, avisos.size)
        assertEquals(Recordatorio.Clase.TAREA, avisos.first().clase)
        assertEquals(inicio, avisos.first().cuando)
    }

    @Test
    fun `una tarea ya completada no avisa`() = runTest {
        val inicio = Tiempo.instante(hoy.atTime(16, 45))
        db.actividadDao().inserta(
            Actividad(
                titulo = "Llamar al dentista",
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                fin = inicio,
                dia = hoy,
                duracionMinutos = 5
            )
        )

        assertTrue(avisosDeHoy().isEmpty())
    }

    @Test
    fun `los avisos salen ordenados por hora`() = runTest {
        db.habitoDao().inserta(Habito(nombre = "Tarde", horaRecordatorio = LocalTime.of(21, 0)))
        db.habitoDao().inserta(Habito(nombre = "Temprano", horaRecordatorio = LocalTime.of(6, 0)))
        db.actividadDao().inserta(
            Actividad(
                titulo = "Media tarde",
                estado = EstadoActividad.PENDIENTE,
                inicio = Tiempo.instante(hoy.atTime(15, 0)),
                dia = hoy
            )
        )

        assertEquals(
            listOf("Temprano", "Media tarde", "Tarde"),
            avisosDeHoy().map { it.titulo }
        )
    }

    /** Un habito y una tarea con el mismo id son cosas distintas. */
    @Test
    fun `el id de notificacion no choca entre un habito y una tarea`() {
        assertNotEquals(
            Recordatorio(Recordatorio.Clase.HABITO, 3, "a", "", Tiempo.ahora()).idNotificacion,
            Recordatorio(Recordatorio.Clase.TAREA, 3, "a", "", Tiempo.ahora()).idNotificacion
        )
    }

    /** Fuera de la ventana no se devuelve nada, aunque el habito toque. */
    @Test
    fun `la ventana acota lo que se devuelve`() = runTest {
        db.habitoDao().inserta(Habito(nombre = "Leer", horaRecordatorio = LocalTime.of(20, 0)))

        val temprano = Tiempo.instante(hoy.atTime(6, 0))
        val vacio = planificador.entre(temprano, temprano.plus(Duration.ofHours(2)))

        assertTrue(vacio.isEmpty())
    }
}
