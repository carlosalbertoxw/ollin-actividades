package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.excel.CeldaLeida
import com.carlosalbertoxw.ollin.actividades.data.excel.XlsxLector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Lo que un `.xlsx` hecho a proposito no puede conseguir: cerrar la app o
 * agotarle la memoria.
 *
 * El lector no puede confiar en que el parser de la plataforma sepa prohibir el
 * DOCTYPE: en Android no sabe, porque su SAXParserFactory esta hecha sobre Expat
 * y solo reconoce las banderas de namespaces. Estas pruebas corren en la JVM,
 * donde el parser si las reconoce, asi que no pueden demostrar el
 * comportamiento en el telefono. Lo que si fijan es que el rechazo **no
 * dependa** de esas banderas: se hace leyendo el prologo a mano, y eso se
 * comporta igual en las dos plataformas.
 */
class XlsxLectorSeguridadTest {

    /** Libro minimo pero valido, con una hoja y una celda de texto. */
    private fun libro(
        prologoDeHoja: String = "",
        filas: String = """<row r="1"><c r="A1" t="inlineStr"><is><t>Hola</t></is></c></row>""",
        hoja: ((String) -> ByteArray)? = null
    ): ByteArray {
        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { zip ->
            fun parte(ruta: String, contenido: ByteArray) {
                zip.putNextEntry(ZipEntry(ruta))
                zip.write(contenido)
                zip.closeEntry()
            }
            parte(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <workbook><sheets><sheet name="Registros" sheetId="1" r:id="rId1"/></sheets></workbook>"""
                    .toByteArray()
            )
            parte(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                   <Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>"""
                    .toByteArray()
            )
            val texto = """<?xml version="1.0" encoding="UTF-8"?>$prologoDeHoja
                   <worksheet><sheetData>$filas</sheetData></worksheet>"""
            parte("xl/worksheets/sheet1.xml", hoja?.invoke(texto) ?: texto.toByteArray())
        }
        return salida.toByteArray()
    }

    private fun rechaza(bytes: ByteArray, motivo: String): XlsxLector.ArchivoInvalido {
        try {
            XlsxLector.lee(ByteArrayInputStream(bytes))
        } catch (e: XlsxLector.ArchivoInvalido) {
            return e
        }
        fail(motivo)
        error("inalcanzable")
    }

    @Test
    fun `un libro normal se lee`() {
        val leido = XlsxLector.lee(ByteArrayInputStream(libro()))

        assertEquals(listOf("Registros"), leido.hojas.map { it.nombre })
        assertEquals("Hola", leido.hoja("Registros")!!.filas[0][0].comoTexto())
    }

    /**
     * La bomba de entidades: sin DOCTYPE no hay entidades que expandir, y por eso
     * el rechazo va antes de que el parser toque el archivo.
     */
    @Test
    fun `un libro con DOCTYPE se rechaza`() {
        val e = rechaza(
            libro("""<!DOCTYPE foo [<!ENTITY a "AAAAAAAAAA"><!ENTITY b "&a;&a;&a;&a;&a;">]>"""),
            "Un libro con DOCTYPE no debe leerse"
        )

        assertTrue(
            "El mensaje debe decirle a la persona que hacer, no citar clases internas: ${e.message}",
            e.message!!.contains("DOCTYPE") && e.message!!.contains("hoja de cálculo")
        )
    }

    /** `<!doctype` en minusculas es igual de valido para XML, y hay que atajarlo. */
    @Test
    fun `el DOCTYPE se detecta sin importar mayusculas`() {
        val e = rechaza(libro("<!doctype foo>"), "Un doctype en minusculas tampoco debe leerse")

        assertTrue(e.message!!.contains("DOCTYPE"))
    }

    /**
     * En UTF-16 cada letra lleva un cero al lado, y una comparacion byte a byte
     * no veria el DOCTYPE. El parser si lo leeria.
     */
    @Test
    fun `el DOCTYPE se detecta tambien en UTF-16`() {
        val e = rechaza(
            libro("<!DOCTYPE foo>") { texto ->
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
                    texto.replace("UTF-8", "UTF-16").toByteArray(Charsets.UTF_16LE)
            },
            "Un DOCTYPE en UTF-16 no debe colarse"
        )

        assertTrue(e.message!!.contains("DOCTYPE"))
    }

    /** Un comentario en el prologo es legal y no debe confundirse con un DOCTYPE. */
    @Test
    fun `un comentario antes de la raiz no estorba`() {
        val leido = XlsxLector.lee(
            ByteArrayInputStream(libro("<!-- generado por otra suite, menciona <!DOCTYPE -->"))
        )

        assertEquals("Hola", leido.hoja("Registros")!!.filas[0][0].comoTexto())
    }

    /**
     * La zip bomb: unos KB comprimidos que se expanden a mas que el limite
     * dentro de **una sola** parte. Tiene que salir como archivo invalido, con
     * mensaje legible, y no como un `OutOfMemoryError`.
     */
    @Test
    fun `una parte que se expande por encima del limite se rechaza`() {
        val bomba = ByteArrayOutputStream()
        ZipOutputStream(bomba).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val ceros = ByteArray(1024 * 1024)
            repeat((XlsxLector.LIMITE_BYTES / ceros.size).toInt() + 1) { zip.write(ceros) }
            zip.closeEntry()
        }
        assertTrue(
            "La bomba tiene que caber en poco: es lo que la hace peligrosa",
            bomba.size() < 1024 * 1024
        )

        val e = rechaza(bomba.toByteArray(), "Una parte mas grande que el limite no debe leerse")

        assertTrue(e.message!!.contains("demasiado grande"))
    }

    /** Cientos de miles de partes vacias no pesan nada, pero llenan el mapa. */
    @Test
    fun `un zip con miles de partes se rechaza`() {
        val muchas = ByteArrayOutputStream()
        ZipOutputStream(muchas).use { zip ->
            repeat(2_001) { i ->
                zip.putNextEntry(ZipEntry("xl/vacia$i.xml"))
                zip.closeEntry()
            }
        }

        val e = rechaza(muchas.toByteArray(), "Un zip con miles de partes no debe leerse")

        assertTrue(e.message!!.contains("demasiado grande"))
    }

    /**
     * El lector rellena las filas que el archivo se salta: un numero de fila
     * enorme en un archivo de un kilobyte pedia miles de millones de renglones.
     */
    @Test
    fun `una fila mas alla del tope de Excel se rechaza`() {
        val e = rechaza(
            libro(
                filas = """<row r="2000000000">""" +
                    """<c r="A2000000000" t="inlineStr"><is><t>x</t></is></c></row>"""
            ),
            "Una fila fuera de la hoja no debe leerse"
        )

        assertTrue(e.message!!.contains("tamaño máximo"))
    }

    /** Lo mismo con las columnas, que ademas desbordaban el entero al convertirlas. */
    @Test
    fun `una columna mas alla de XFD se rechaza`() {
        val e = rechaza(
            libro(
                filas = """<row r="1"><c r="ZZZZZZZZ1" t="inlineStr"><is><t>x</t></is></c></row>"""
            ),
            "Una columna fuera de la hoja no debe leerse"
        )

        assertTrue(e.message!!.contains("tamaño máximo"))
    }

    /**
     * Cada eje cabe en su tope, pero su producto no: una celda vacia en XFD
     * obliga a rellenar 16 384 columnas, y diez mil filas asi pedian 164 millones
     * de casillas desde un archivo que comprimido no llega a unas decenas de KB.
     */
    @Test
    fun `muchas filas que llegan hasta XFD se rechazan antes de agotar la memoria`() {
        val filas = (1..10_000).joinToString("") { n -> """<row r="$n"><c r="XFD$n"/></row>""" }
        val bytes = libro(filas = filas)
        assertTrue(
            "El archivo tiene que ser pequeno: es lo que lo hace peligroso",
            bytes.size < 64 * 1024
        )

        val e = rechaza(bytes, "Un libro que pide mas celdas que el tope no debe leerse")

        assertTrue(e.message!!.contains("demasiado grande"))
    }

    /** Las casillas de relleno son la misma: no cuestan un objeto cada una. */
    @Test
    fun `los huecos comparten una sola celda vacia`() {
        val leido = XlsxLector.lee(
            ByteArrayInputStream(
                libro(filas = """<row r="1"><c r="D1" t="inlineStr"><is><t>x</t></is></c></row>""")
            )
        )

        val fila = leido.hoja("Registros")!!.filas[0]
        assertEquals(4, fila.size)
        assertTrue(fila.take(3).all { it === CeldaLeida.VACIA })
    }

    /** La ultima fila y la ultima columna de Excel siguen siendo legales. */
    @Test
    fun `la ultima celda de una hoja de Excel se lee`() {
        val leido = XlsxLector.lee(
            ByteArrayInputStream(
                libro(
                    filas = """<row r="3"><c r="XFD3" t="inlineStr"><is><t>fin</t></is></c></row>"""
                )
            )
        )

        val filas = leido.hoja("Registros")!!.filas
        assertEquals(3, filas.size)
        assertEquals(XlsxLector.MAXIMO_COLUMNAS, filas[2].size)
        assertEquals("fin", filas[2].last().comoTexto())
    }
}
