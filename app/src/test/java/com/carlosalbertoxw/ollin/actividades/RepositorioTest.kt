package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * La version de SDK y el modo de SQLite estan en test/resources/robolectric.properties.
 *
 * Se arranca con una Application pelona en vez de con [OllinApp]: la de verdad
 * siembra el catalogo contra la base cifrada, y SQLCipher es una biblioteca
 * nativa de Android que en la JVM no existe. Aqui se prueban las reglas del
 * repositorio sobre una base en memoria, no el arranque de la app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepositorioTest {

    private lateinit var db: OllinDatabase
    private lateinit var repo: ActividadesRepositorio

    @Before
    fun abre() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OllinDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = ActividadesRepositorio(
            db,
            ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        )
    }

    @After
    fun cierra() {
        db.close()
    }

    @Test
    fun `una actividad completada guarda su duracion y su hora de fin`() = runTest {
        val inicio = Tiempo.instante(LocalDate.of(2026, 8, 3).atTime(9, 0))
        val id = repo.guarda(
            Actividad(
                titulo = "Reunion de diseno",
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                dia = LocalDate.of(2026, 1, 1), // dia equivocado a proposito
                duracionMinutos = 90
            )
        )

        val guardada = repo.actividad(id)!!
        assertEquals(90, guardada.duracionMinutos)
        // El dia se deriva del inicio, no de lo que traiga el objeto.
        assertEquals(LocalDate.of(2026, 8, 3), guardada.dia)
        assertEquals(inicio.plusSeconds(90 * 60L), guardada.fin)
    }

    @Test
    fun `la hora de fin manda sobre la duracion capturada`() = runTest {
        val inicio = Tiempo.instante(LocalDate.of(2026, 8, 3).atTime(9, 0))
        val id = repo.guarda(
            Actividad(
                titulo = "Correr",
                estado = EstadoActividad.COMPLETADO,
                inicio = inicio,
                fin = inicio.plusSeconds(40 * 60L),
                dia = LocalDate.of(2026, 8, 3),
                duracionMinutos = 999
            )
        )

        assertEquals(40, repo.actividad(id)!!.duracionMinutos)
    }

    @Test
    fun `solo puede correr una actividad a la vez`() = runTest {
        repo.iniciaAhora("Enfoque profundo")
        repo.iniciaAhora("Llamada")

        val corriendo = db.actividadDao().enCurso()
        assertEquals(1, corriendo.size)
        assertEquals("Llamada", corriendo.first().titulo)

        // La primera quedo cerrada, no perdida.
        val todas = repo.observaActividades(limite = 10).first()
        assertEquals(2, todas.size)
        val cerrada = todas.first { it.actividad.titulo == "Enfoque profundo" }
        assertEquals(EstadoActividad.COMPLETADO, cerrada.actividad.estado)
        assertNotNull(cerrada.actividad.duracionMinutos)
    }

    @Test
    fun `detener escribe la duracion y deja de correr`() = runTest {
        val id = repo.iniciaAhora("Caminata")
        assertNull(repo.actividad(id)!!.duracionMinutos)

        repo.detiene(id)

        val cerrada = repo.actividad(id)!!
        assertEquals(EstadoActividad.COMPLETADO, cerrada.estado)
        assertNotNull(cerrada.fin)
        assertTrue(db.actividadDao().enCurso().isEmpty())
    }

    @Test
    fun `marcar un habito deja un registro completado que se puede deshacer`() = runTest {
        val categoriaId = repo.guardaCategoria(
            Categoria(nombre = "Lectura", ambito = Ambito.HABITO)
        )
        val habitoId = repo.guardaHabito(
            Habito(nombre = "Leer", categoriaId = categoriaId, minutosSugeridos = 20)
        )
        val habito = repo.habito(habitoId)!!

        repo.registraHabito(habito)

        val conAvance = repo.observaHabitosConAvance().first().first()
        assertEquals(1, conAvance.vecesHoy)
        assertEquals(20, conAvance.minutosHoy)
        assertEquals(1, conAvance.racha.actual)
        assertTrue(conAvance.cumplidoHoy)

        repo.deshaceHabito(habitoId)

        val despues = repo.observaHabitosConAvance().first().first()
        assertEquals(0, despues.vecesHoy)
        assertEquals(0, despues.racha.actual)
    }

    /**
     * Pausar un habito no puede equivaler a perderlo. La pantalla de habitos
     * pide la lista completa justo para poder reactivarlo; si volviera a
     * filtrar por activos, el habito quedaria en la base sin una sola forma de
     * llegar a el desde la app.
     */
    @Test
    fun `un habito pausado desaparece de hoy pero sigue en la lista de habitos`() = runTest {
        val habitoId = repo.guardaHabito(Habito(nombre = "Correr"))
        val habito = repo.habito(habitoId)!!

        repo.guardaHabito(habito.copy(activo = false))

        val enHoy = repo.observaHabitosConAvance().first()
        assertTrue(enHoy.isEmpty())

        val enPantallaHabitos = repo.observaHabitosConAvance(soloActivos = false).first()
        assertEquals(1, enPantallaHabitos.size)
        assertEquals("Correr", enPantallaHabitos.first().habito.nombre)
        assertEquals(false, enPantallaHabitos.first().habito.activo)
    }

    @Test
    fun `reanudar un habito pausado le devuelve su historial y su racha`() = runTest {
        val habitoId = repo.guardaHabito(Habito(nombre = "Meditar", minutosSugeridos = 10))
        val habito = repo.habito(habitoId)!!
        repo.registraHabito(habito)

        repo.guardaHabito(habito.copy(activo = false))
        // El cumplimiento sobrevive a la pausa: es una actividad, no un campo
        // del habito.
        assertEquals(1, repo.observaHabitosConAvance(soloActivos = false).first().first().vecesHoy)

        repo.guardaHabito(repo.habito(habitoId)!!.copy(activo = true))

        val reanudado = repo.observaHabitosConAvance().first().first()
        assertEquals(1, reanudado.vecesHoy)
        assertEquals(1, reanudado.racha.actual)
        assertTrue(reanudado.cumplidoHoy)
    }

    /**
     * Borrar un habito no puede borrar lo que ya se hizo. La clave foranea es
     * SET_NULL a proposito: los cumplimientos siguen siendo actividades
     * completadas, cuentan en la analitica y conservan sus minutos; lo unico que
     * pierden es el vinculo con una plantilla que ya no existe.
     */
    @Test
    fun `eliminar un habito conserva los registros que dejo`() = runTest {
        val habitoId = repo.guardaHabito(Habito(nombre = "Nadar", minutosSugeridos = 45))
        val habito = repo.habito(habitoId)!!
        repo.registraHabito(habito)

        repo.eliminaHabito(habito)

        assertNull(repo.habito(habitoId))
        val registros = db.actividadDao().todas()
        assertEquals(1, registros.size)
        assertEquals("Nadar", registros.first().titulo)
        assertEquals(45, registros.first().duracionMinutos)
        assertNull(registros.first().habitoId)
    }

    /**
     * Lo mismo con las categorias: archivar es lo habitual, pero si alguien
     * borra una, sus actividades se quedan sin categoria en vez de irse con ella.
     */
    @Test
    fun `eliminar una categoria no se lleva sus actividades`() = runTest {
        val categoriaId = repo.guardaCategoria(Categoria(nombre = "Ocio", ambito = Ambito.PERSONAL))
        val hoy = Tiempo.hoy()
        repo.guarda(
            Actividad(
                titulo = "Leer novela",
                categoriaId = categoriaId,
                estado = EstadoActividad.COMPLETADO,
                inicio = Tiempo.instante(hoy.atTime(20, 0)),
                dia = hoy,
                duracionMinutos = 60
            )
        )

        repo.eliminaCategoria(repo.categoria(categoriaId)!!)

        val registros = db.actividadDao().todas()
        assertEquals(1, registros.size)
        assertNull(registros.first().categoriaId)
        assertEquals(60, registros.first().duracionMinutos)
    }

    @Test
    fun `la analitica solo suma lo completado`() = runTest {
        val hoy = Tiempo.hoy()
        val categoriaId = repo.guardaCategoria(
            Categoria(nombre = "Enfoque", ambito = Ambito.TRABAJO)
        )
        repo.guarda(
            Actividad(
                titulo = "Redactar",
                categoriaId = categoriaId,
                estado = EstadoActividad.COMPLETADO,
                inicio = Tiempo.instante(hoy.atTime(9, 0)),
                dia = hoy,
                duracionMinutos = 50
            )
        )
        repo.guarda(
            Actividad(
                titulo = "Pendiente de la tarde",
                categoriaId = categoriaId,
                estado = EstadoActividad.PENDIENTE,
                inicio = Tiempo.instante(hoy.atTime(17, 0)),
                dia = hoy,
                duracionMinutos = 120
            )
        )

        val porAmbito = repo.observaTotalPorAmbito(hoy, hoy).first()
        assertEquals(1, porAmbito.size)
        assertEquals(Ambito.TRABAJO, porAmbito.first().ambito)
        assertEquals(50, porAmbito.first().minutos)
    }
}
