package mx.ollin.actividades

import mx.ollin.actividades.data.db.Actividad
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.data.excel.DatosExportacion
import mx.ollin.actividades.data.excel.EsquemaExportacion
import mx.ollin.actividades.data.excel.ExportadorExcel
import mx.ollin.actividades.data.excel.HojaExportable
import mx.ollin.actividades.data.excel.Ooxml
import mx.ollin.actividades.data.excel.XlsxLector
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.domain.model.Unidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime

/**
 * El libro que sale tiene que poder volver a entrar. Se prueba sobre el
 * escritor y el lector de verdad, sin Android de por medio: el .xlsx es solo un
 * zip con XML dentro y ninguna de las dos mitades toca el telefono.
 */
class ExcelRoundTripTest {

    private val hoy = LocalDate.of(2026, 8, 10)

    private val categorias = listOf(
        Categoria(id = 1, nombre = "Enfoque profundo", ambito = Ambito.TRABAJO, colorHex = "#3D6DB5"),
        Categoria(id = 2, nombre = "Correr", ambito = Ambito.FISICO, colorHex = "#2F9E6E")
    )

    private val habitos = listOf(
        Habito(id = 7, nombre = "Leer 20 minutos", categoriaId = 1, minutosSugeridos = 20)
    )

    private fun actividad(
        id: Long,
        titulo: String,
        dia: LocalDate,
        hora: LocalTime,
        minutos: Int,
        categoriaId: Long? = 1,
        estado: EstadoActividad = EstadoActividad.COMPLETADO,
        habitoId: Long? = null,
        cantidad: Double? = null,
        unidad: Unidad = Unidad.NINGUNA
    ): Actividad {
        val inicio = Tiempo.instante(dia.atTime(hora))
        return Actividad(
            id = id,
            titulo = titulo,
            categoriaId = categoriaId,
            estado = estado,
            inicio = inicio,
            fin = if (estado == EstadoActividad.COMPLETADO) inicio.plusSeconds(minutos * 60L) else null,
            dia = dia,
            duracionMinutos = if (estado == EstadoActividad.COMPLETADO) minutos else null,
            cantidad = cantidad,
            unidad = unidad,
            habitoId = habitoId
        )
    }

    private val actividades = listOf(
        actividad(1, "Rediseno de la pantalla de hoy", hoy.minusDays(1), LocalTime.of(9, 0), 90),
        actividad(
            2, "Correr 5 km", hoy.minusDays(1), LocalTime.of(19, 30), 32,
            categoriaId = 2, cantidad = 5.0, unidad = Unidad.KILOMETROS
        ),
        actividad(3, "Leer 20 minutos", hoy, LocalTime.of(7, 15), 20, habitoId = 7),
        actividad(
            4, "Junta pendiente", hoy.plusDays(1), LocalTime.of(11, 0), 0,
            estado = EstadoActividad.PENDIENTE
        )
    )

    private fun exporta(esquema: EsquemaExportacion): ByteArray {
        val datos = DatosExportacion(categorias, habitos, actividades, hoy)
        val salida = ByteArrayOutputStream()
        ExportadorExcel(datos, esquema, HojaExportable.PREDETERMINADAS).escribeEn(salida)
        return salida.toByteArray()
    }

    @Test
    fun `el libro completo trae todas las pestanas elegidas`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())

        HojaExportable.entries.forEach { hoja ->
            assertNotNull("Falta la pestana ${hoja.titulo}", libro.hoja(hoja.titulo))
        }
    }

    @Test
    fun `registros conserva fecha, titulo, categoria y minutos de cada actividad`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val hoja = libro.hoja("Registros")!!

        // Encabezado mas una fila por actividad.
        assertEquals(actividades.size + 1, hoja.filas.size)

        val encabezado = hoja.filas[0].mapNotNull { it.comoTexto() }
        assertEquals(EsquemaExportacion.EXTENDIDO.columnas, encabezado)

        // Las filas salen ordenadas por dia: la primera es la del dia anterior.
        val primera = hoja.filas[1]
        assertEquals(hoy.minusDays(1), Ooxml.desdeSerial(primera[0].numero!!))
        assertEquals("Rediseno de la pantalla de hoy", primera[1].comoTexto())
        assertEquals("Enfoque profundo", primera[2].comoTexto())
        assertEquals("Trabajo", primera[3].comoTexto())
        assertEquals("Completado", primera[4].comoTexto())
        assertEquals(LocalTime.of(9, 0), Ooxml.horaDesdeSerial(primera[5].numero!!))
        assertEquals(90.0, primera[7].numero!!, 0.001)
    }

    @Test
    fun `la medida y su unidad sobreviven al viaje`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val fila = libro.hoja("Registros")!!.filas.first { it[1].comoTexto() == "Correr 5 km" }

        assertEquals(5.0, fila[8].numero!!, 0.001)
        assertEquals("Kilometros", fila[9].comoTexto())
    }

    @Test
    fun `el habito de origen viaja con el registro`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val fila = libro.hoja("Registros")!!.filas.first { it[1].comoTexto() == "Leer 20 minutos" }

        assertEquals("Leer 20 minutos", fila[10].comoTexto())
    }

    @Test
    fun `el esquema compacto emite solo sus cinco columnas`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.COMPACTO).inputStream())
        val hoja = libro.hoja("Registros")!!

        assertEquals(
            EsquemaExportacion.COMPACTO.columnas,
            hoja.filas[0].mapNotNull { it.comoTexto() }
        )
        assertEquals(5, hoja.filas[1].size)
    }

    @Test
    fun `por dia suma solo lo completado y deja el total al final`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val hoja = libro.hoja("Por dia")!!

        // La pendiente de manana no aparece: solo hay dos dias con registro.
        val fechas = hoja.filas.mapNotNull { fila -> fila.getOrNull(0)?.numero }
        assertEquals(2, fechas.size)

        // El cache de las formulas es el que ve un visor que no recalcula.
        val total = hoja.filas.last()
        assertEquals("Total", total[0].comoTexto())
        assertEquals((90 + 32 + 20).toDouble(), total[1].numero!!, 0.001)
        assertEquals(3.0, total[3].numero!!, 0.001)
    }

    @Test
    fun `por categoria reparte los minutos y el porcentaje suma uno`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val hoja = libro.hoja("Por categoria")!!

        val trabajo = hoja.filas.first { it.getOrNull(0)?.comoTexto() == "Enfoque profundo" }
        assertEquals(110.0, trabajo[2].numero!!, 0.001)   // 90 de ayer + 20 de hoy

        val correr = hoja.filas.first { it.getOrNull(0)?.comoTexto() == "Correr" }
        assertEquals(32.0, correr[2].numero!!, 0.001)

        val total = hoja.filas.last()
        assertEquals("Total", total[0].comoTexto())
        assertEquals(1.0, total[5].numero!!, 0.001)
    }

    /**
     * El renglon de lo que no tiene categoria no puede cruzarse contra su propio
     * nombre: en Registros esas celdas vienen vacias. Como el libro sale con
     * `fullCalcOnLoad`, una formula mal cruzada se veria en cero desde el primer
     * momento, sin que el cache alcanzara a salvarla.
     */
    @Test
    fun `lo que no tiene categoria se cruza contra el vacio, no contra su nombre`() {
        val sueltas = actividades.map { it.copy(categoriaId = null) }
        val salida = ByteArrayOutputStream()
        ExportadorExcel(
            DatosExportacion(categorias, habitos, sueltas, hoy),
            EsquemaExportacion.EXTENDIDO,
            HojaExportable.PREDETERMINADAS
        ).escribeEn(salida)

        val hoja = XlsxLector.lee(salida.toByteArray().inputStream()).hoja("Por categoria")!!
        val fila = hoja.filas.first { it.getOrNull(0)?.comoTexto() == "(sin categoria)" }

        // El lector devuelve el cache; la formula viva se comprueba en el XML.
        assertEquals((90 + 32 + 20).toDouble(), fila[2].numero!!, 0.001)

        // Por categoria es la segunda pestana del libro. El lector solo devuelve
        // el valor cacheado, asi que la formula viva hay que mirarla en el XML.
        val xml = parteDelZip(salida.toByteArray(), "xl/worksheets/sheet2.xml")
        assertTrue(
            "La formula debe preguntar por la celda vacia",
            xml.contains("&quot;&quot;")
        )
        assertTrue(
            "La formula no debe cruzarse contra el nombre del renglon",
            xml.contains("SUMIFS")
        )
    }

    @Test
    fun `la hoja de habitos trae la racha calculada`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val hoja = libro.hoja("Habitos")!!
        val fila = hoja.filas.first { it.getOrNull(0)?.comoTexto() == "Leer 20 minutos" }

        assertEquals("Enfoque profundo", fila[1].comoTexto())
        assertEquals("Todos los dias", fila[2].comoTexto())
        assertEquals(1.0, fila[6].numero!!, 0.001)   // un cumplimiento
        assertEquals(1.0, fila[7].numero!!, 0.001)   // racha de un dia
        assertEquals("dias", fila[9].comoTexto())
    }

    @Test
    fun `los diccionarios llevan los catalogos que alimentan los desplegables`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        val hoja = libro.hoja("Diccionarios")!!

        assertEquals(
            listOf("Categorias", "Ambitos", "Estados", "Unidades", "Habitos"),
            hoja.filas[0].mapNotNull { it.comoTexto() }
        )
        val primeraColumna = hoja.filas.drop(1).mapNotNull { it.getOrNull(0)?.comoTexto() }
        assertEquals(categorias.map { it.nombre }, primeraColumna)
    }

    @Test
    fun `un libro sin actividades sigue siendo un xlsx valido`() {
        val vacio = DatosExportacion(categorias, habitos, emptyList(), hoy)
        val salida = ByteArrayOutputStream()
        ExportadorExcel(vacio, EsquemaExportacion.EXTENDIDO, HojaExportable.PREDETERMINADAS)
            .escribeEn(salida)

        val libro = XlsxLector.lee(salida.toByteArray().inputStream())
        assertNotNull(libro.hoja("Registros"))
        // Solo el encabezado.
        assertEquals(1, libro.hoja("Registros")!!.filas.size)
    }

    /** Saca una entrada del .xlsx tal cual, sin interpretarla. */
    private fun parteDelZip(libro: ByteArray, ruta: String): String =
        java.util.zip.ZipInputStream(libro.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == ruta }
                ?: error("El libro no trae $ruta")
            String(zip.readBytes(), Charsets.UTF_8)
        }

    @Test
    fun `los nombres de hoja no pasan del tope de Excel`() {
        val libro = XlsxLector.lee(exporta(EsquemaExportacion.EXTENDIDO).inputStream())
        libro.hojas.forEach {
            assertTrue("Nombre de hoja demasiado largo: ${it.nombre}", it.nombre.length <= 31)
        }
    }
}
