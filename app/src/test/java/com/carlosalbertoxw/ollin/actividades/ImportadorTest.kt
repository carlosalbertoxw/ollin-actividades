package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import com.carlosalbertoxw.ollin.actividades.data.excel.Celda
import com.carlosalbertoxw.ollin.actividades.data.excel.DatosExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.ExportadorExcel
import com.carlosalbertoxw.ollin.actividades.data.excel.Hoja
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.excel.ImportadorExcel
import com.carlosalbertoxw.ollin.actividades.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.XlsxEscritor
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
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
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lo que sale por la exportacion tiene que volver a entrar por la importacion
 * y quedar igual. Se usa una base en memoria y una Application pelona: SQLCipher
 * es nativo de Android y aqui no hace falta para nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportadorTest {

    private lateinit var db: OllinDatabase
    private lateinit var importador: ImportadorExcel

    private val hoy = LocalDate.of(2026, 8, 10)

    /**
     * Serial de Excel que da una fecha valida como LocalDate pero que desborda
     * al reducirla a milisegundos. Simula un archivo corrupto que no revienta
     * hasta que la escritura ya empezo.
     */
    private val SERIAL_IMPOSIBLE = 360_000_000_000.0

    @Before
    fun abre() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OllinDatabase::class.java
        ).allowMainThreadQueries().build()
        importador = ImportadorExcel(db)
    }

    @After
    fun cierra() = db.close()

    private fun libroDe(
        categorias: List<Categoria>,
        habitos: List<Habito>,
        actividades: List<Actividad>,
        esquema: EsquemaExportacion = EsquemaExportacion.EXTENDIDO
    ): ByteArray {
        val salida = ByteArrayOutputStream()
        ExportadorExcel(
            DatosExportacion(categorias, habitos, actividades, hoy),
            esquema,
            HojaExportable.PREDETERMINADAS
        ).escribeEn(salida)
        return salida.toByteArray()
    }

    /** Un libro armado a mano, para los casos que la exportacion no produce. */
    private fun libroCrudo(vararg hojas: Hoja): ByteArray {
        val salida = ByteArrayOutputStream()
        XlsxEscritor(hojas.toList()).escribeEn(salida)
        return salida.toByteArray()
    }

    private fun completada(
        id: Long,
        titulo: String,
        dia: LocalDate,
        hora: LocalTime,
        minutos: Int,
        categoriaId: Long? = 1,
        habitoId: Long? = null,
        cantidad: Double? = null,
        unidad: Unidad = Unidad.NINGUNA
    ): Actividad {
        val inicio = Tiempo.instante(dia.atTime(hora))
        return Actividad(
            id = id,
            titulo = titulo,
            categoriaId = categoriaId,
            estado = EstadoActividad.COMPLETADO,
            inicio = inicio,
            fin = inicio.plusSeconds(minutos * 60L),
            dia = dia,
            duracionMinutos = minutos,
            cantidad = cantidad,
            unidad = unidad,
            habitoId = habitoId
        )
    }

    @Test
    fun `la ida y vuelta conserva titulo, dia, minutos, hora y medida`() = runTest {
        val categorias = listOf(
            Categoria(id = 1, nombre = "Correr", ambito = Ambito.FISICO)
        )
        val original = completada(
            1, "Correr 5 km", hoy, LocalTime.of(6, 45), 32,
            cantidad = 5.0, unidad = Unidad.KILOMETROS
        )

        val resultado = importador.importa(
            libroDe(categorias, emptyList(), listOf(original)).inputStream()
        )

        assertEquals(1, resultado.importadas)
        assertEquals(0, resultado.omitidas)

        val guardadas = db.actividadDao().todas()
        assertEquals(1, guardadas.size)
        val a = guardadas.first()
        assertEquals("Correr 5 km", a.titulo)
        assertEquals(hoy, a.dia)
        assertEquals(32, a.duracionMinutos)
        assertEquals(EstadoActividad.COMPLETADO, a.estado)
        assertEquals(LocalTime.of(6, 45), Tiempo.local(a.inicio).toLocalTime())
        assertEquals(5.0, a.cantidad!!, 0.001)
        assertEquals(Unidad.KILOMETROS, a.unidad)
        // La categoria del archivo se dio de alta y quedo enlazada.
        assertNotNull(a.categoriaId)
        assertEquals("Correr", db.categoriaDao().porId(a.categoriaId!!)!!.nombre)
    }

    @Test
    fun `la categoria nueva hereda el ambito que venia en el archivo`() = runTest {
        val categorias = listOf(Categoria(id = 1, nombre = "Gimnasio", ambito = Ambito.FISICO))
        importador.importa(
            libroDe(
                categorias,
                emptyList(),
                listOf(completada(1, "Pesas", hoy, LocalTime.of(18, 0), 45))
            ).inputStream()
        )

        val creada = db.categoriaDao().todas().first { it.nombre == "Gimnasio" }
        assertEquals(Ambito.FISICO, creada.ambito)
    }

    @Test
    fun `no se duplica una categoria que ya existe aunque cambien acentos y mayusculas`() = runTest {
        db.categoriaDao().inserta(Categoria(nombre = "Reunión", ambito = Ambito.TRABAJO))

        importador.importa(
            libroDe(
                listOf(Categoria(id = 1, nombre = "reunion", ambito = Ambito.TRABAJO)),
                emptyList(),
                listOf(completada(1, "Junta semanal", hoy, LocalTime.of(10, 0), 60))
            ).inputStream()
        )

        assertEquals(1, db.categoriaDao().todas().size)
        assertEquals(1, db.actividadDao().todas().size)
    }

    @Test
    fun `el habito del archivo se crea y queda enlazado al registro`() = runTest {
        val categorias = listOf(Categoria(id = 1, nombre = "Lectura", ambito = Ambito.HABITO))
        val habitos = listOf(Habito(id = 7, nombre = "Leer 20 minutos", categoriaId = 1))

        importador.importa(
            libroDe(
                categorias,
                habitos,
                listOf(completada(1, "Leer 20 minutos", hoy, LocalTime.of(7, 0), 20, habitoId = 7))
            ).inputStream()
        )

        val habito = db.habitoDao().todos().first { it.nombre == "Leer 20 minutos" }
        assertEquals(habito.id, db.actividadDao().todas().first().habitoId)
    }

    @Test
    fun `una pendiente no trae duracion, para que no sume en la analitica`() = runTest {
        val manana = hoy.plusDays(1)
        val inicio = Tiempo.instante(manana.atTime(11, 0))
        val pendiente = Actividad(
            id = 1,
            titulo = "Junta por venir",
            categoriaId = null,
            estado = EstadoActividad.PENDIENTE,
            inicio = inicio,
            fin = null,
            dia = manana,
            duracionMinutos = null
        )

        importador.importa(libroDe(emptyList(), emptyList(), listOf(pendiente)).inputStream())

        val a = db.actividadDao().todas().first()
        assertEquals(EstadoActividad.PENDIENTE, a.estado)
        assertNull(a.duracionMinutos)
        assertNull(a.fin)
    }

    @Test
    fun `reemplazar vacia lo que habia antes`() = runTest {
        db.actividadDao().inserta(completada(0, "Lo de antes", hoy, LocalTime.of(8, 0), 15, null))

        importador.importa(
            libroDe(
                emptyList(), emptyList(),
                listOf(completada(1, "Lo del archivo", hoy, LocalTime.of(9, 0), 30, null))
            ).inputStream(),
            OpcionesImportacion(reemplazarTodo = true)
        )

        val guardadas = db.actividadDao().todas()
        assertEquals(1, guardadas.size)
        assertEquals("Lo del archivo", guardadas.first().titulo)
    }

    /**
     * La prueba que sostiene la transaccion de la importacion.
     *
     * "Reemplazar todo" borra la bitacora antes de escribir la nueva. Si algo
     * revienta entre las dos cosas y no hay transaccion, no queda ni lo viejo
     * ni lo nuevo, y no hay de donde recuperarlo: la base va cifrada con una
     * llave del Keystore que no se respalda, asi que el unico respaldo es el
     * .xlsx que se estaba importando.
     *
     * El fallo se provoca con una fecha absurda, que es lo que trae un archivo
     * corrupto de verdad. El serial pasa el parseo sin protestar —LocalDate
     * llega hasta el ano mil millones— y solo revienta al escribir, cuando el
     * convertidor de Room reduce el instante a milisegundos y el producto se
     * sale de un Long. Es decir: despues del borrado y a media insercion, que
     * es exactamente el hueco que la transaccion tiene que tapar.
     */
    @Test
    fun `si la importacion falla despues de borrar, la bitacora anterior sigue ahi`() = runTest {
        db.actividadDao().inserta(completada(0, "Lo de antes", hoy, LocalTime.of(8, 0), 15, null))
        db.actividadDao().inserta(completada(0, "Y esto tambien", hoy, LocalTime.of(9, 0), 20, null))

        val libro = libroCrudo(
            Hoja(
                nombre = "Registros",
                filas = listOf(
                    listOf(Celda.Texto("Fecha"), Celda.Texto("Titulo"), Celda.Texto("Minutos")),
                    listOf(Celda.Numero(SERIAL_IMPOSIBLE), Celda.Texto("Fila envenenada"), Celda.Numero(30.0))
                )
            )
        )

        val fallo = runCatching {
            importador.importa(libro.inputStream(), OpcionesImportacion(reemplazarTodo = true))
        }

        assertTrue("La importacion tenia que fallar", fallo.isFailure)
        val quedaron = db.actividadDao().todas()
        assertEquals(2, quedaron.size)
        assertEquals(listOf("Lo de antes", "Y esto tambien"), quedaron.map { it.titulo })
    }

    @Test
    fun `sin reemplazar, lo del archivo se suma a lo que ya habia`() = runTest {
        db.actividadDao().inserta(completada(0, "Lo de antes", hoy, LocalTime.of(8, 0), 15, null))

        importador.importa(
            libroDe(
                emptyList(), emptyList(),
                listOf(completada(1, "Lo del archivo", hoy, LocalTime.of(9, 0), 30, null))
            ).inputStream(),
            OpcionesImportacion(reemplazarTodo = false)
        )

        assertEquals(2, db.actividadDao().todas().size)
    }

    @Test
    fun `el esquema compacto tambien se puede reimportar`() = runTest {
        val categorias = listOf(Categoria(id = 1, nombre = "Enfoque", ambito = Ambito.TRABAJO))
        importador.importa(
            libroDe(
                categorias,
                emptyList(),
                listOf(completada(1, "Escribir", hoy, LocalTime.of(9, 0), 75)),
                esquema = EsquemaExportacion.COMPACTO
            ).inputStream()
        )

        val a = db.actividadDao().todas().first()
        assertEquals("Escribir", a.titulo)
        assertEquals(75, a.duracionMinutos)
        assertEquals(hoy, a.dia)
        // Sin columna de hora, el registro se ancla al mediodia.
        assertEquals(LocalTime.NOON, Tiempo.local(a.inicio).toLocalTime())
    }

    @Test
    fun `sin crear faltantes la actividad entra pero se queda sin categoria`() = runTest {
        val resultado = importador.importa(
            libroDe(
                listOf(Categoria(id = 1, nombre = "Inventada", ambito = Ambito.PERSONAL)),
                emptyList(),
                listOf(completada(1, "Algo", hoy, LocalTime.of(9, 0), 10))
            ).inputStream(),
            OpcionesImportacion(creaFaltantes = false)
        )

        assertEquals(1, resultado.importadas)
        assertEquals(1, resultado.sinCategoria)
        assertTrue(db.categoriaDao().todas().isEmpty())
        assertNull(db.actividadDao().todas().first().categoriaId)
    }
}
