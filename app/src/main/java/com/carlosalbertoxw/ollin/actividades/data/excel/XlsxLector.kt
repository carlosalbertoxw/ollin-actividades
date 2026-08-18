package com.carlosalbertoxw.ollin.actividades.data.excel

import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Una celda tal como venia en el archivo, sin interpretar todavia. */
data class CeldaLeida(
    val texto: String? = null,
    val numero: Double? = null
) {
    val estaVacia: Boolean get() = texto.isNullOrBlank() && numero == null

    /** Texto para comparar contra catalogos. Un numero entero sale sin ".0". */
    fun comoTexto(): String? = when {
        texto != null -> texto
        numero != null ->
            if (numero == numero.toLong().toDouble()) numero.toLong().toString()
            else numero.toString()
        else -> null
    }
}

data class HojaLeida(
    val nombre: String,
    val filas: List<List<CeldaLeida>>
)

data class LibroLeido(val hojas: List<HojaLeida>) {
    fun hoja(nombre: String): HojaLeida? =
        hojas.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }
}

/**
 * Lector de .xlsx basado en SAX del JDK, sin dependencias.
 *
 * El paquete se carga completo en memoria porque sharedStrings.xml puede venir
 * despues de las hojas dentro del zip y hace falta resolverlo antes. Para una
 * bitacora personal (decenas de miles de renglones como mucho) el costo es
 * irrelevante y evita necesitar acceso aleatorio al archivo.
 */
object XlsxLector {

    private const val LIMITE_BYTES = 64L * 1024 * 1024
    private const val BYTES_BUFFER = 64 * 1024

    /** Todo lo que permitiria a un XML de fuera hacer algo mas que describir celdas. */
    private val BANDERAS_CERRADAS = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
    )

    class ArchivoInvalido(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa)

    fun lee(entrada: InputStream): LibroLeido {
        val partes = descomprime(entrada)

        if (!partes.containsKey("xl/workbook.xml")) {
            throw ArchivoInvalido("El archivo no parece un libro de Excel (.xlsx). Si es .xls antiguo, guárdalo primero como .xlsx.")
        }

        val cadenas = partes["xl/sharedStrings.xml"]?.let(::leeSharedStrings) ?: emptyList()
        val relaciones = partes["xl/_rels/workbook.xml.rels"]?.let(::leeRelaciones) ?: emptyMap()
        val definiciones = leeDefinicionHojas(partes.getValue("xl/workbook.xml"))

        val hojas = definiciones.mapNotNull { (nombre, rid) ->
            val destino = relaciones[rid] ?: return@mapNotNull null
            val ruta = normalizaRuta(destino)
            val bytes = partes[ruta] ?: return@mapNotNull null
            HojaLeida(nombre, leeFilas(bytes, cadenas))
        }

        if (hojas.isEmpty()) throw ArchivoInvalido("El libro no tiene hojas legibles.")
        return LibroLeido(hojas)
    }

    // ------------------------------------------------------------------ zip

    private fun descomprime(entrada: InputStream): Map<String, ByteArray> {
        val partes = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(entrada.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val nombre = entry.name.removePrefix("/")
                    // Solo interesan las partes XML del libro.
                    if (!nombre.endsWith(".xml") && !nombre.endsWith(".rels")) {
                        zip.closeEntry(); continue
                    }
                    val bytes = leeAcotado(zip, LIMITE_BYTES - total)
                    total += bytes.size
                    partes[nombre] = bytes
                    zip.closeEntry()
                }
            }
        } catch (e: ArchivoInvalido) {
            throw e
        } catch (e: Exception) {
            // La causa se conserva para el diagnostico, pero no viaja en el
            // texto: el mensaje crudo habla de rutas y clases internas.
            throw ArchivoInvalido(
                "No se pudo abrir el archivo. Puede estar dañado o protegido con contraseña.",
                e
            )
        }
        return partes
    }

    /**
     * Lee una entrada del zip sin pasarse de [restante] bytes.
     *
     * No se usa `readBytes()` porque descomprime la entrada entera antes de que
     * nadie pueda mirar cuanto ocupa: un .xlsx de cien kilobytes con una hoja
     * de relacion 1000:1 —un archivo corrupto, o uno hecho a proposito— agota
     * la memoria del telefono antes de llegar a la comprobacion. El tope se
     * aplica mientras se lee, asi que lo peor que pasa es un mensaje.
     */
    private fun leeAcotado(zip: ZipInputStream, restante: Long): ByteArray {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(BYTES_BUFFER)
        var disponible = restante
        while (true) {
            val leidos = zip.read(buffer)
            if (leidos <= 0) break
            disponible -= leidos
            if (disponible < 0) {
                throw ArchivoInvalido(
                    "El archivo es demasiado grande para procesarse en el teléfono."
                )
            }
            salida.write(buffer, 0, leidos)
        }
        return salida.toByteArray()
    }

    private fun normalizaRuta(destino: String): String {
        val limpio = destino.removePrefix("/")
        return if (limpio.startsWith("xl/")) limpio else "xl/$limpio"
    }

    // ------------------------------------------------------------- parseo

    /**
     * Parsea una parte del libro con las entidades externas cerradas.
     *
     * Un .xlsx es un zip de XML que llega de fuera, y el XML admite declarar
     * entidades que el parser resuelve solo: unas leen archivos del telefono y
     * los dejan caer dentro de una celda, otras se expanden en cascada hasta
     * agotar la memoria. Aqui no hace falta ninguna —las hojas de calculo no
     * declaran DTD— asi que se apagan todas.
     *
     * Las banderas van en `runCatching` porque no toda implementacion las
     * reconoce y algunas lanzan al pedirlas; el [EntityResolver] vacio es el
     * cinturon que no depende de que ninguna este disponible: aunque el parser
     * decida resolver una entidad, lo que recibe es la cadena vacia.
     */
    private fun parsea(bytes: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // Cada blindaje va suelto y tolerado: ninguno puede tumbar una
            // importacion por no estar disponible. setXIncludeAware es el caso
            // real —el SAXParserFactory de Android no lo implementa y la clase
            // base lanza UnsupportedOperationException— y no se veia en las
            // pruebas porque en la JVM lo sirve Xerces, que si lo soporta.
            runCatching { isXIncludeAware = false }
            BANDERAS_CERRADAS.forEach { (bandera, valor) ->
                runCatching { setFeature(bandera, valor) }
            }
        }
        val lector = factory.newSAXParser().xmlReader
        lector.contentHandler = handler
        lector.errorHandler = handler
        lector.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
        lector.parse(InputSource(ByteArrayInputStream(bytes)))
    }

    private fun leeSharedStrings(bytes: ByteArray): List<String> {
        val resultado = mutableListOf<String>()
        val actual = StringBuilder()
        var dentroDeSi = false
        var capturando = false

        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                when (qName) {
                    "si" -> { dentroDeSi = true; actual.setLength(0) }
                    "t" -> if (dentroDeSi) capturando = true
                    // <rPh> lleva la lectura fonetica japonesa; no es contenido.
                    "rPh" -> capturando = false
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capturando) actual.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, local: String?, qName: String) {
                when (qName) {
                    "t" -> capturando = false
                    "si" -> { resultado += actual.toString(); dentroDeSi = false }
                }
            }
        })
        return resultado
    }

    private fun leeRelaciones(bytes: ByteArray): Map<String, String> {
        val mapa = HashMap<String, String>()
        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                if (qName == "Relationship" && attrs != null) {
                    val id = attrs.getValue("Id") ?: return
                    val target = attrs.getValue("Target") ?: return
                    mapa[id] = target
                }
            }
        })
        return mapa
    }

    /** Devuelve pares (nombre de hoja, rId) en el orden del libro. */
    private fun leeDefinicionHojas(bytes: ByteArray): List<Pair<String, String>> {
        val lista = mutableListOf<Pair<String, String>>()
        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                if (qName == "sheet" && attrs != null) {
                    val nombre = attrs.getValue("name") ?: return
                    val rid = attrs.getValue("r:id") ?: attrs.getValue("id") ?: return
                    lista += nombre to rid
                }
            }
        })
        return lista
    }

    private fun leeFilas(bytes: ByteArray, cadenas: List<String>): List<List<CeldaLeida>> {
        val filas = mutableListOf<List<CeldaLeida>>()
        var filaActual = HashMap<Int, CeldaLeida>()
        var numeroFilaActual = 0
        var maxColFila = 0

        var columnaCelda = 0
        var tipoCelda: String? = null
        val valor = StringBuilder()
        var capturandoValor = false
        var dentroDeFormula = false

        fun cierraFila() {
            if (numeroFilaActual <= 0) return
            // Rellena los huecos que el archivo omite y las filas salteadas.
            while (filas.size < numeroFilaActual - 1) filas.add(emptyList())
            val fila = (1..maxColFila).map { filaActual[it] ?: CeldaLeida() }
            if (filas.size == numeroFilaActual - 1) filas.add(fila) else filas[numeroFilaActual - 1] = fila
        }

        parsea(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, local: String?, qName: String, attrs: Attributes?) {
                when (qName) {
                    "row" -> {
                        filaActual = HashMap()
                        maxColFila = 0
                        numeroFilaActual = attrs?.getValue("r")?.toIntOrNull() ?: (filas.size + 1)
                    }
                    "c" -> {
                        val ref = attrs?.getValue("r")
                        columnaCelda = if (ref != null) Ooxml.indiceColumna(Ooxml.partesReferencia(ref).first)
                        else columnaCelda + 1
                        if (columnaCelda > maxColFila) maxColFila = columnaCelda
                        tipoCelda = attrs?.getValue("t")
                        valor.setLength(0)
                    }
                    "f" -> dentroDeFormula = true
                    "v" -> if (!dentroDeFormula) { capturandoValor = true; valor.setLength(0) }
                    "t" -> if (!dentroDeFormula) { capturandoValor = true }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (capturandoValor) valor.appendRange(ch, start, start + length)
            }

            override fun endElement(uri: String?, local: String?, qName: String) {
                when (qName) {
                    "f" -> dentroDeFormula = false
                    "v", "t" -> capturandoValor = false
                    "c" -> {
                        val crudo = valor.toString()
                        if (crudo.isNotEmpty()) {
                            val celda = when (tipoCelda) {
                                "s" -> CeldaLeida(texto = crudo.toIntOrNull()?.let { cadenas.getOrNull(it) })
                                "inlineStr", "str" -> CeldaLeida(texto = crudo)
                                "b" -> CeldaLeida(texto = if (crudo == "1") "VERDADERO" else "FALSO")
                                "e" -> CeldaLeida(texto = crudo) // #REF!, #VALUE!, etc.
                                else -> crudo.toDoubleOrNull()
                                    ?.let { CeldaLeida(numero = it) }
                                    ?: CeldaLeida(texto = crudo)
                            }
                            if (!celda.estaVacia) filaActual[columnaCelda] = celda
                        }
                        valor.setLength(0)
                        tipoCelda = null
                    }
                    "row" -> cierraFila()
                }
            }
        })

        return filas
    }
}
