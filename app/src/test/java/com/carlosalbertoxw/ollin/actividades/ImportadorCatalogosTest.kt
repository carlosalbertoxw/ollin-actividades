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
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Las pestañas de catalogo del libro —Categorias, Habitos y Diccionarios— se
 * importan igual que Registros. Aqui se comprueba que lo que sale por la
 * exportacion vuelve a entrar sin perder el ambito, el color, el orden ni la
 * cadencia, y que lo que ya existia se actualiza en vez de duplicarse.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportadorCatalogosTest {

    private lateinit var db: OllinDatabase
    private lateinit var importador: ImportadorExcel

    private val hoy = LocalDate.of(2026, 8, 10)

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
        categorias: List<Categoria> = emptyList(),
        habitos: List<Habito> = emptyList(),
        actividades: List<Actividad> = emptyList()
    ): ByteArray {
        val salida = ByteArrayOutputStream()
        ExportadorExcel(
            DatosExportacion(categorias, habitos, actividades, hoy),
            EsquemaExportacion.EXTENDIDO,
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

    private fun texto(vararg valores: String): List<Celda> = valores.map { Celda.Texto(it) }

    // ---------------------------------------------------------- Categorias

    @Test
    fun `la hoja de categorias devuelve ambito, color, orden y archivada`() = runTest {
        val resultado = importador.importa(
            libroDe(
                categorias = listOf(
                    Categoria(
                        id = 1, nombre = "Trabajo profundo", ambito = Ambito.TRABAJO,
                        colorHex = "#FF8844", archivada = false, orden = 3
                    ),
                    Categoria(
                        id = 2, nombre = "Descanso", ambito = Ambito.PERSONAL,
                        archivada = true, orden = 7
                    )
                )
            ).inputStream()
        )

        assertEquals(listOf("Trabajo profundo", "Descanso"), resultado.categoriasCreadas)

        val guardadas = db.categoriaDao().todas().associateBy { it.nombre }
        val trabajo = guardadas.getValue("Trabajo profundo")
        assertEquals(Ambito.TRABAJO, trabajo.ambito)
        assertEquals("#FF8844", trabajo.colorHex)
        assertEquals(3, trabajo.orden)
        assertFalse(trabajo.archivada)

        val descanso = guardadas.getValue("Descanso")
        assertEquals(Ambito.PERSONAL, descanso.ambito)
        assertEquals(7, descanso.orden)
        assertTrue(descanso.archivada)
        // La celda de color salio vacia y una celda vacia no dice "sin color",
        // dice "no lo mencione": no hay nada que escribir.
        assertNull(descanso.colorHex)
    }

    @Test
    fun `una categoria que ya existe se actualiza y conserva su nombre`() = runTest {
        db.categoriaDao().inserta(
            Categoria(nombre = "Reunión", ambito = Ambito.TRABAJO, orden = 0)
        )

        val resultado = importador.importa(
            libroDe(
                categorias = listOf(
                    Categoria(
                        id = 1, nombre = "reunion", ambito = Ambito.PERSONAL,
                        colorHex = "#112233", orden = 5
                    )
                )
            ).inputStream()
        )

        val guardadas = db.categoriaDao().todas()
        assertEquals(1, guardadas.size)
        assertEquals(1, resultado.categoriasActualizadas)
        assertTrue(resultado.categoriasCreadas.isEmpty())

        val categoria = guardadas.first()
        // El nombre es la llave con la que se emparejan las dos listas y lleva
        // indice unico: se respeta el que ya estaba.
        assertEquals("Reunión", categoria.nombre)
        assertEquals(Ambito.PERSONAL, categoria.ambito)
        assertEquals("#112233", categoria.colorHex)
        assertEquals(5, categoria.orden)
    }

    // ------------------------------------------------------------- Habitos

    @Test
    fun `la hoja de habitos devuelve meta, minutos sugeridos, activo y notas`() = runTest {
        importador.importa(
            libroDe(
                categorias = listOf(Categoria(id = 4, nombre = "Salud", ambito = Ambito.HABITO)),
                habitos = listOf(
                    Habito(
                        id = 1, nombre = "Leer", categoriaId = 4, metaDiaria = 2,
                        minutosSugeridos = 20, activo = false, notas = "Antes de dormir"
                    )
                )
            ).inputStream()
        )

        val leer = db.habitoDao().todos().single()
        assertEquals("Leer", leer.nombre)
        assertEquals(2, leer.metaDiaria)
        assertEquals(20, leer.minutosSugeridos)
        assertFalse(leer.activo)
        assertEquals("Antes de dormir", leer.notas)
        // La categoria se resolvio por nombre contra la hoja de Categorias, que
        // se lee antes justamente para esto.
        assertEquals(db.categoriaDao().todas().single().id, leer.categoriaId)
    }

    @Test
    fun `cada cadencia vuelve con su frecuencia y su intervalo`() = runTest {
        val lunesMiercolesViernes = DiasSemana.TODOS and 0b0010101

        importador.importa(
            libroDe(
                habitos = listOf(
                    Habito(id = 1, nombre = "Diario", frecuencia = Frecuencia.DIARIA),
                    Habito(
                        id = 2, nombre = "Impares", frecuencia = Frecuencia.DIAS_ELEGIDOS,
                        diasSemana = lunesMiercolesViernes
                    ),
                    Habito(id = 3, nombre = "Cuatro", frecuencia = Frecuencia.SEMANAL, metaSemanal = 4),
                    Habito(id = 4, nombre = "Quincenal", frecuencia = Frecuencia.CADA_DIAS, intervaloDias = 15),
                    Habito(id = 5, nombre = "Cada tres", frecuencia = Frecuencia.CADA_DIAS, intervaloDias = 3),
                    Habito(id = 6, nombre = "Trimestral", frecuencia = Frecuencia.CADA_MESES, intervaloMeses = 3),
                    Habito(id = 7, nombre = "Anual", frecuencia = Frecuencia.CADA_MESES, intervaloMeses = 12)
                )
            ).inputStream()
        )

        val guardados = db.habitoDao().todos().associateBy { it.nombre }

        assertEquals(Frecuencia.DIARIA, guardados.getValue("Diario").frecuencia)

        val impares = guardados.getValue("Impares")
        assertEquals(Frecuencia.DIAS_ELEGIDOS, impares.frecuencia)
        assertEquals(lunesMiercolesViernes, impares.diasSemana)

        val cuatro = guardados.getValue("Cuatro")
        assertEquals(Frecuencia.SEMANAL, cuatro.frecuencia)
        assertEquals(4, cuatro.metaSemanal)

        val quincenal = guardados.getValue("Quincenal")
        assertEquals(Frecuencia.CADA_DIAS, quincenal.frecuencia)
        assertEquals(15, quincenal.intervaloDias)

        val cadaTres = guardados.getValue("Cada tres")
        assertEquals(Frecuencia.CADA_DIAS, cadaTres.frecuencia)
        assertEquals(3, cadaTres.intervaloDias)

        val trimestral = guardados.getValue("Trimestral")
        assertEquals(Frecuencia.CADA_MESES, trimestral.frecuencia)
        assertEquals(3, trimestral.intervaloMeses)

        val anual = guardados.getValue("Anual")
        assertEquals(Frecuencia.CADA_MESES, anual.frecuencia)
        assertEquals(12, anual.intervaloMeses)
    }

    @Test
    fun `la racha del archivo no se importa, se recalcula desde los registros`() = runTest {
        db.habitoDao().inserta(Habito(nombre = "Correr", metaDiaria = 1))

        // La hoja que exporta Ollin trae Cumplimientos, Racha actual y Mejor
        // racha. Son resultados, no plantilla: aceptarlos dejaria en pantalla
        // una racha que ningun dia sostiene.
        val libro = libroCrudo(
            Hoja(
                nombre = "Habitos",
                filas = listOf(
                    listOf(Celda.Texto("Habitos")),
                    listOf(Celda.Texto("La racha se calcula como en la app.")),
                    listOf(Celda.Vacia),
                    texto(
                        "Habito", "Categoria", "Cadencia", "Meta diaria", "Minutos sugeridos",
                        "Activo", "Cumplimientos", "Racha actual", "Mejor racha",
                        "Unidad de racha", "Notas"
                    ),
                    listOf(
                        Celda.Texto("Correr"), Celda.Texto(""), Celda.Texto("Cada semana"),
                        Celda.Numero(1.0), Celda.Vacia, Celda.Booleano(true),
                        Celda.Numero(40.0), Celda.Numero(12.0), Celda.Numero(30.0),
                        Celda.Texto("dias"), Celda.Texto("")
                    )
                )
            )
        )

        importador.importa(libro.inputStream())

        val correr = db.habitoDao().todos().single()
        // La cadencia si entra: es plantilla.
        assertEquals(Frecuencia.CADA_DIAS, correr.frecuencia)
        assertEquals(7, correr.intervaloDias)
        // Y no hay ni un solo cumplimiento, porque no hubo registros.
        assertTrue(db.actividadDao().todas().isEmpty())
    }

    // -------------------------------------------------------- Diccionarios

    @Test
    fun `diccionarios da de alta los nombres que ninguna otra hoja trajo`() = runTest {
        val libro = libroCrudo(
            Hoja(
                nombre = "Diccionarios",
                filas = listOf(
                    texto("Categorias", "Ambitos", "Estados", "Unidades", "Habitos"),
                    listOf(
                        Celda.Texto("Lectura"), Celda.Texto("Personal"),
                        Celda.Texto("Completado"), Celda.Texto("Sin medida"),
                        Celda.Texto("Meditar")
                    )
                )
            )
        )

        val resultado = importador.importa(libro.inputStream())

        assertEquals(listOf("Lectura"), resultado.categoriasCreadas)
        assertEquals(listOf("Meditar"), resultado.habitosCreados)
        // Sin ambito propio, una categoria salida de aqui cae en el cajon menos
        // comprometido.
        assertEquals(Ambito.PERSONAL, db.categoriaDao().todas().single().ambito)
    }

    @Test
    fun `diccionarios no pisa lo que la hoja de categorias ya definio`() = runTest {
        importador.importa(
            libroDe(
                categorias = listOf(
                    Categoria(id = 1, nombre = "Salud", ambito = Ambito.FISICO, colorHex = "#00AA55")
                ),
                habitos = listOf(Habito(id = 1, nombre = "Nadar", categoriaId = 1))
            ).inputStream()
        )

        // El libro exportado nombra "Salud" y "Nadar" en las tres pestañas.
        assertEquals(1, db.categoriaDao().todas().size)
        assertEquals(1, db.habitoDao().todos().size)
        val salud = db.categoriaDao().todas().single()
        assertEquals(Ambito.FISICO, salud.ambito)
        assertEquals("#00AA55", salud.colorHex)
    }

    // --------------------------------------------------------- el conjunto

    @Test
    fun `un libro sin hoja de registros no toca la bitacora aunque diga reemplazar`() = runTest {
        val inicio = Tiempo.instante(hoy.atTime(9, 0))
        db.actividadDao().inserta(
            Actividad(
                titulo = "Lo que ya estaba", estado = EstadoActividad.COMPLETADO,
                inicio = inicio, fin = inicio.plusSeconds(600), dia = hoy, duracionMinutos = 10
            )
        )

        val libro = libroCrudo(
            Hoja(
                nombre = "Categorias",
                filas = listOf(
                    texto("Categoria", "Ambito"),
                    texto("Estudio", "Personal")
                )
            )
        )

        val resultado = importador.importa(
            libro.inputStream(),
            OpcionesImportacion(reemplazarTodo = true)
        )

        assertTrue(resultado.soloCatalogos)
        assertEquals(listOf("Categorias"), resultado.hojasLeidas)
        assertEquals(listOf("Estudio"), resultado.categoriasCreadas)
        // Lo importante: no habia nada con que reemplazar, asi que no se borro.
        assertEquals(1, db.actividadDao().todas().size)
        assertEquals("Lo que ya estaba", db.actividadDao().todas().single().titulo)
    }

    @Test
    fun `sin crear faltantes los catalogos del archivo no dan de alta nada`() = runTest {
        val resultado = importador.importa(
            libroDe(
                categorias = listOf(Categoria(id = 1, nombre = "Inventada", ambito = Ambito.TRABAJO)),
                habitos = listOf(Habito(id = 1, nombre = "Inventado"))
            ).inputStream(),
            OpcionesImportacion(creaFaltantes = false)
        )

        assertTrue(resultado.categoriasCreadas.isEmpty())
        assertTrue(resultado.habitosCreados.isEmpty())
        assertTrue(db.categoriaDao().todas().isEmpty())
        assertTrue(db.habitoDao().todos().isEmpty())
    }

    @Test
    fun `el registro se enlaza a la categoria que trajo la hoja de catalogo`() = runTest {
        val categoria = Categoria(
            id = 1, nombre = "Gimnasio", ambito = Ambito.FISICO, colorHex = "#334455", orden = 2
        )
        val inicio = Tiempo.instante(hoy.atTime(18, 0))
        val actividad = Actividad(
            id = 1, titulo = "Pesas", categoriaId = 1, estado = EstadoActividad.COMPLETADO,
            inicio = inicio, fin = inicio.plusSeconds(45 * 60L), dia = hoy, duracionMinutos = 45
        )

        val resultado = importador.importa(
            libroDe(categorias = listOf(categoria), actividades = listOf(actividad)).inputStream()
        )

        assertEquals(1, resultado.importadas)
        assertEquals(0, resultado.sinCategoria)
        assertTrue(resultado.hojasLeidas.contains("Registros"))

        // La categoria se creo una sola vez, desde la hoja de catalogo, con su
        // color y su orden: no la invento el renglon de la bitacora.
        val guardada = db.categoriaDao().todas().single()
        assertEquals("#334455", guardada.colorHex)
        assertEquals(2, guardada.orden)
        assertEquals(guardada.id, db.actividadDao().todas().single().categoriaId)
    }
}
