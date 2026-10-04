package com.carlosalbertoxw.ollin.actividades.data.excel

import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Una celda tal como venia en el archivo, sin interpretar todavia. */
data class CeldaLeida(val texto: String? = null, val numero: Double? = null) {
    val estaVacia: Boolean get() = texto.isNullOrBlank() && numero == null

    /** Texto para comparar contra catalogos. Un numero entero sale sin ".0". */
    fun comoTexto(): String? = when {
        texto != null -> texto

        numero != null ->
            if (numero == numero.toLong().toDouble()) {
                numero.toLong().toString()
            } else {
                numero.toString()
            }

        else -> null
    }
}

data class HojaLeida(val nombre: String, val filas: List<List<CeldaLeida>>)

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

    /** Lo que pueden sumar descomprimidas todas las partes XML del libro. */
    internal const val LIMITE_BYTES = 64L * 1024 * 1024
    private const val BYTES_BUFFER = 64 * 1024

    /**
     * Partes XML que se aceptan. Un libro real trae una veintena; un zip con
     * cientos de miles de partes vacias no pesa nada y aun asi llena el mapa.
     */
    private const val LIMITE_PARTES = 2_000

    /**
     * Los topes de una hoja de Excel: 1 048 576 filas y 16 384 columnas (XFD).
     *
     * El lector rellena los huecos que el archivo se salta, asi que el numero
     * de fila y la letra de columna deciden cuanta memoria se pide, no los
     * bytes que ocupa el archivo. Un `<row r="2000000000">` en un libro de un
     * kilobyte pedia dos mil millones de renglones. Nada escrito por una hoja
     * de calculo pasa de estos numeros, asi que lo que los pasa no es un libro.
     */
    internal const val MAXIMO_FILAS = 1_048_576
    internal const val MAXIMO_COLUMNAS = 16_384

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
            throw ArchivoInvalido(
                "El archivo no parece un libro de Excel (.xlsx). " +
                    "Si es .xls antiguo, guárdalo primero como .xlsx."
            )
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
        var leidas = 0
        try {
            ZipInputStream(entrada.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val nombre = entry.name.removePrefix("/")
                    // Solo interesan las partes XML del libro.
                    if (!nombre.endsWith(".xml") && !nombre.endsWith(".rels")) {
                        zip.closeEntry()
                        continue
                    }
                    if (++leidas > LIMITE_PARTES) throw demasiadoGrande()
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
            if (disponible < 0) throw demasiadoGrande()
            salida.write(buffer, 0, leidos)
        }
        return salida.toByteArray()
    }

    private fun demasiadoGrande() =
        ArchivoInvalido("El archivo es demasiado grande para procesarse en el teléfono.")

    private fun fueraDeLaHoja() = ArchivoInvalido(
        "El archivo trae celdas más allá del tamaño máximo de una hoja de Excel. " +
            "Vuelve a guardarlo como .xlsx desde tu hoja de cálculo."
    )

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
     * reconoce y algunas lanzan al pedirlas. En Android no las reconoce
     * **ninguna**: su SAXParserFactory esta hecha sobre Expat y solo admite las
     * de namespaces. El [EntityResolver] vacio cubre las entidades externas,
     * pero no las internas, que se declaran y se expanden dentro del propio
     * archivo. Por eso la defensa de verdad es [rechazaDoctype]: sin DOCTYPE no
     * hay entidades que declarar, y la comprobacion se hace sobre los bytes,
     * igual en el telefono que en la JVM.
     */
    private fun parsea(bytes: ByteArray, handler: DefaultHandler) {
        rechazaDoctype(bytes)
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
        try {
            lector.parse(InputSource(ByteArrayInputStream(bytes)))
        } catch (e: SAXException) {
            // Lo que lanza un handler --los topes de filas y columnas-- llega
            // envuelto: el parser solo deja salir SAXException. Se desenvuelve
            // para que el mensaje escrito para la persona no se pierda.
            (e.exception as? ArchivoInvalido)?.let { throw it }
            throw e
        }
    }

    /**
     * Recorre el prologo --lo que va antes del elemento raiz-- y aborta si
     * encuentra un DOCTYPE. Solo mira ahi: mas adelante un `<` literal viaja
     * escapado como `&lt;`, asi que la secuencia no puede aparecer en el texto
     * de una celda y buscarla en todo el archivo daria falsos positivos.
     *
     * Se lee con el juego de caracteres del archivo y no byte a byte: un XML en
     * UTF-16 intercala ceros entre las letras, y comparar bytes dejaria pasar
     * su DOCTYPE sin verlo.
     */
    private fun rechazaDoctype(bytes: ByteArray) {
        val lector = InputStreamReader(ByteArrayInputStream(bytes), codificacion(bytes)).buffered()
        while (true) {
            val c = lector.read()
            if (c < 0) return
            if (c.toChar().isWhitespace() || c == 0xFEFF) continue
            if (c.toChar() != '<') return
            lector.mark(16)
            val bufer = CharArray(8)
            val siguiente = bufer.concatToString(0, lector.read(bufer, 0, 8).coerceAtLeast(0))
            when {
                // Declaracion XML o instruccion de proceso: <? ... ?>
                siguiente.startsWith("?") -> {
                    lector.reset()
                    lector.read()
                    if (!saltaHasta(lector, "?>")) return
                }

                // Comentario: <!-- ... -->
                siguiente.startsWith("!--") -> {
                    lector.reset()
                    repeat(3) { lector.read() }
                    if (!saltaHasta(lector, "-->")) return
                }

                siguiente.startsWith("!DOCTYPE", ignoreCase = true) -> throw ArchivoInvalido(
                    "El archivo declara un DOCTYPE, que Ollin Actividades no acepta. " +
                        "Vuelve a guardarlo como .xlsx desde tu hoja de cálculo."
                )

                // Cualquier otra cosa ya es el elemento raiz: el prologo acabo.
                else -> return
            }
        }
    }

    /** Consume hasta justo despues de [cierre]; falso si el archivo se acaba antes. */
    private fun saltaHasta(lector: java.io.Reader, cierre: String): Boolean {
        // Una ventana con los ultimos caracteres leidos, y no un contador de
        // coincidencias: con "-->" un contador que se reinicia se pierde el
        // cierre de "--->", porque el tercer guion ya era el principio.
        val ventana = StringBuilder()
        while (true) {
            val c = lector.read()
            if (c < 0) return false
            ventana.append(c.toChar())
            if (ventana.length > cierre.length) ventana.deleteCharAt(0)
            if (ventana.contentEquals(cierre)) return true
        }
    }

    /**
     * El juego de caracteres por la marca de orden de bytes, o por como viene
     * escrito el primer `<` si no la trae. Sin ninguna de las dos, UTF-8, que es
     * lo que escriben todas las hojas de calculo.
     */
    private fun codificacion(bytes: ByteArray): java.nio.charset.Charset {
        fun b(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            b(0) == 0xFF && b(1) == 0xFE -> Charsets.UTF_16LE
            b(0) == 0xFE && b(1) == 0xFF -> Charsets.UTF_16BE
            b(0) == 0x3C && b(1) == 0x00 -> Charsets.UTF_16LE
            b(0) == 0x00 && b(1) == 0x3C -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
    }

    private fun leeSharedStrings(bytes: ByteArray): List<String> {
        val resultado = mutableListOf<String>()
        val actual = StringBuilder()
        var dentroDeSi = false
        var capturando = false

        parsea(
            bytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    local: String?,
                    qName: String,
                    attrs: Attributes?
                ) {
                    when (qName) {
                        "si" -> {
                            dentroDeSi = true
                            actual.setLength(0)
                        }

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

                        "si" -> {
                            resultado += actual.toString()
                            dentroDeSi = false
                        }
                    }
                }
            }
        )
        return resultado
    }

    private fun leeRelaciones(bytes: ByteArray): Map<String, String> {
        val mapa = HashMap<String, String>()
        parsea(
            bytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    local: String?,
                    qName: String,
                    attrs: Attributes?
                ) {
                    if (qName == "Relationship" && attrs != null) {
                        val id = attrs.getValue("Id") ?: return
                        val target = attrs.getValue("Target") ?: return
                        mapa[id] = target
                    }
                }
            }
        )
        return mapa
    }

    /** Devuelve pares (nombre de hoja, rId) en el orden del libro. */
    private fun leeDefinicionHojas(bytes: ByteArray): List<Pair<String, String>> {
        val lista = mutableListOf<Pair<String, String>>()
        parsea(
            bytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    local: String?,
                    qName: String,
                    attrs: Attributes?
                ) {
                    if (qName == "sheet" && attrs != null) {
                        val nombre = attrs.getValue("name") ?: return
                        val rid = attrs.getValue("r:id") ?: attrs.getValue("id") ?: return
                        lista += nombre to rid
                    }
                }
            }
        )
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
            if (filas.size == numeroFilaActual - 1) {
                filas.add(fila)
            } else {
                filas[numeroFilaActual - 1] = fila
            }
        }

        parsea(
            bytes,
            object : DefaultHandler() {
                override fun startElement(
                    uri: String?,
                    local: String?,
                    qName: String,
                    attrs: Attributes?
                ) {
                    when (qName) {
                        "row" -> {
                            filaActual = HashMap()
                            maxColFila = 0
                            numeroFilaActual =
                                attrs?.getValue("r")?.toIntOrNull() ?: (filas.size + 1)
                            if (numeroFilaActual > MAXIMO_FILAS) throw fueraDeLaHoja()
                        }

                        "c" -> {
                            val ref = attrs?.getValue("r")
                            columnaCelda = if (ref != null) {
                                Ooxml.indiceColumna(Ooxml.partesReferencia(ref).first)
                            } else {
                                columnaCelda + 1
                            }
                            if (columnaCelda > MAXIMO_COLUMNAS) throw fueraDeLaHoja()
                            if (columnaCelda > maxColFila) maxColFila = columnaCelda
                            tipoCelda = attrs?.getValue("t")
                            valor.setLength(0)
                        }

                        "f" -> dentroDeFormula = true

                        "v" -> if (!dentroDeFormula) {
                            capturandoValor = true
                            valor.setLength(0)
                        }

                        "t" -> if (!dentroDeFormula) {
                            capturandoValor = true
                        }
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
                                    "s" -> CeldaLeida(
                                        texto = crudo.toIntOrNull()?.let { cadenas.getOrNull(it) }
                                    )

                                    "inlineStr", "str" -> CeldaLeida(texto = crudo)

                                    "b" -> CeldaLeida(
                                        texto = if (crudo == "1") "VERDADERO" else "FALSO"
                                    )

                                    "e" -> CeldaLeida(texto = crudo)

                                    // #REF!, #VALUE!, etc.
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
            }
        )

        return filas
    }
}
