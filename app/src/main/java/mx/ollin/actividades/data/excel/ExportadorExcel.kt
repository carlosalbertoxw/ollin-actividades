package mx.ollin.actividades.data.excel

import mx.ollin.actividades.data.db.Actividad
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.Habito
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import mx.ollin.actividades.domain.model.Tiempo
import mx.ollin.actividades.domain.model.Unidad
import java.io.OutputStream

/**
 * Convierte la bitacora de Ollin en un libro de Excel.
 *
 * Las hojas de analisis llevan formulas reales (SUMIFS, COUNTIFS) apuntando a
 * Registros, ademas del valor ya calculado como cache. Asi la hoja se ve
 * correcta al abrirla en cualquier visor y sigue viva si editas un renglon a
 * mano: cambias unos minutos y los totales se mueven solos.
 */
class ExportadorExcel(
    private val datos: DatosExportacion,
    private val esquema: EsquemaExportacion,
    seleccion: Set<HojaExportable>
) {

    private val hojasElegidas = HojaExportable.normaliza(seleccion)

    // Referencias a columnas de Registros, resueltas segun el esquema.
    private val colFecha = Ooxml.letraColumna(esquema.columnaFecha)
    private val colCategoria = Ooxml.letraColumna(esquema.columnaCategoria)
    private val colEstado = Ooxml.letraColumna(esquema.columnaEstado)
    private val colMinutos = Ooxml.letraColumna(esquema.columnaMinutos)

    /** Nunca menor que 2: un rango "A2:A1" es invalido aunque no haya datos. */
    private val ultimaFila = maxOf(datos.ultimaFilaRegistros, 2)

    private val completado = EstadoActividad.COMPLETADO.etiqueta

    fun escribeEn(salida: OutputStream) {
        val hojas = buildList {
            // El orden de las pestañas sigue el de lectura natural: primero el
            // resumen, luego el detalle, y los catalogos al final.
            if (HojaExportable.POR_DIA in hojasElegidas) add(hojaPorDia())
            if (HojaExportable.POR_CATEGORIA in hojasElegidas) add(hojaPorCategoria())
            if (HojaExportable.HABITOS in hojasElegidas) add(hojaHabitos())
            add(hojaRegistros())
            if (HojaExportable.CATEGORIAS in hojasElegidas) add(hojaCategorias())
            if (HojaExportable.DICCIONARIOS in hojasElegidas) add(hojaDiccionarios())
        }
        XlsxEscritor(hojas).escribeEn(salida)
    }

    // ------------------------------------------------------------ Registros

    private fun hojaRegistros(): Hoja {
        val columnas = esquema.columnas
        val filas = mutableListOf<List<Celda>>()
        filas += columnas.map { Celda.Texto(it, Estilo.ENCABEZADO) }

        datos.actividadesOrdenadas.forEach { a ->
            filas += when (esquema) {
                EsquemaExportacion.EXTENDIDO -> filaExtendida(a)
                EsquemaExportacion.COMPACTO -> filaCompacta(a)
            }
        }

        return Hoja(
            nombre = "Registros",
            filas = filas,
            anchos = anchosRegistros(),
            congelarTrasFila = 1,
            validaciones = if (HojaExportable.DICCIONARIOS in hojasElegidas) validaciones()
            else emptyList(),
            // Tabla de Excel de verdad: el filtro y el formato crecen solos al
            // agregar renglones a mano.
            tabla = TablaExcel(
                nombre = "tblRegistros",
                rango = "A1:${Ooxml.letraColumna(columnas.size)}$ultimaFila",
                encabezados = columnas
            )
        )
    }

    private fun filaExtendida(a: Actividad): List<Celda> = listOf(
        Celda.Fecha(a.dia),
        Celda.Texto(a.titulo),
        Celda.Texto(datos.nombreCategoria(a.categoriaId)),
        Celda.Texto(datos.etiquetaAmbito(a.categoriaId)),
        Celda.Texto(a.estado.etiqueta),
        Celda.Hora(Tiempo.local(a.inicio).toLocalTime()),
        a.fin?.let { Celda.Hora(Tiempo.local(it).toLocalTime()) } ?: Celda.Vacia,
        Celda.Numero((a.duracionMinutos ?: 0).toDouble(), Estilo.ENTERO),
        a.cantidad?.let { Celda.Numero(it, Estilo.DECIMAL) } ?: Celda.Vacia,
        Celda.Texto(if (a.unidad == Unidad.NINGUNA) "" else a.unidad.etiqueta),
        Celda.Texto(datos.nombreHabito(a.habitoId)),
        Celda.Texto(a.notas.orEmpty())
    )

    private fun filaCompacta(a: Actividad): List<Celda> = listOf(
        Celda.Fecha(a.dia),
        Celda.Texto(a.titulo),
        Celda.Texto(datos.nombreCategoria(a.categoriaId)),
        Celda.Texto(a.estado.etiqueta),
        Celda.Numero((a.duracionMinutos ?: 0).toDouble(), Estilo.ENTERO)
    )

    private fun anchosRegistros(): List<AnchoColumna> = when (esquema) {
        EsquemaExportacion.EXTENDIDO -> listOf(
            AnchoColumna(1, 12.0), AnchoColumna(2, 30.0), AnchoColumna(3, 22.0),
            AnchoColumna(4, 16.0), AnchoColumna(5, 13.0), AnchoColumna(6, 9.0),
            AnchoColumna(7, 9.0), AnchoColumna(8, 10.0), AnchoColumna(9, 11.0),
            AnchoColumna(10, 14.0), AnchoColumna(11, 22.0), AnchoColumna(12, 34.0)
        )
        EsquemaExportacion.COMPACTO -> listOf(
            AnchoColumna(1, 12.0), AnchoColumna(2, 32.0), AnchoColumna(3, 22.0),
            AnchoColumna(4, 13.0), AnchoColumna(5, 10.0)
        )
    }

    /**
     * Desplegables sobre las columnas de catalogo. El rango llega mas abajo que
     * el ultimo dato para que los renglones que agregues a mano tambien los
     * traigan.
     */
    private fun validaciones(): List<ValidacionLista> {
        val fin = maxOf(ultimaFila, 2_000)
        val lista = mutableListOf(
            ValidacionLista(
                "${colCategoria}2:$colCategoria$fin",
                Ooxml.refHoja("Diccionarios", "\$A\$2:\$A\$${datos.categorias.size + 1}")
            ),
            ValidacionLista(
                "${colEstado}2:$colEstado$fin",
                Ooxml.refHoja("Diccionarios", "\$C\$2:\$C\$${EstadoActividad.entries.size + 1}")
            )
        )
        if (esquema == EsquemaExportacion.EXTENDIDO) {
            val colAmbito = Ooxml.letraColumna(esquema.indiceDe("Ambito"))
            val colUnidad = Ooxml.letraColumna(esquema.indiceDe("Unidad"))
            lista += ValidacionLista(
                "${colAmbito}2:$colAmbito$fin",
                Ooxml.refHoja("Diccionarios", "\$B\$2:\$B\$${Ambito.entries.size + 1}")
            )
            lista += ValidacionLista(
                "${colUnidad}2:$colUnidad$fin",
                Ooxml.refHoja("Diccionarios", "\$D\$2:\$D\$${Unidad.entries.size + 1}")
            )
        }
        return lista
    }

    // -------------------------------------------------------------- Por dia

    private fun hojaPorDia(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += listOf(Celda.Texto("Tiempo por dia", Estilo.TITULO))
        filas += listOf(
            Celda.Texto(
                "Solo cuenta lo completado: lo pendiente todavia no ocurrio.",
                Estilo.TENUE
            )
        )
        filas += listOf(Celda.Vacia)
        filas += listOf("Dia", "Minutos", "Horas", "Sesiones")
            .map { Celda.Texto(it, Estilo.ENCABEZADO) }

        val primeraFilaDatos = filas.size + 1
        datos.diasConRegistro.forEach { dia ->
            val n = filas.size + 1
            val minutos = datos.minutosDelDia(dia)
            filas += listOf(
                Celda.Fecha(dia),
                Celda.Formula(
                    sumaMinutos("\$A$n"),
                    cache = minutos.toDouble(),
                    estilo = Estilo.ENTERO
                ),
                Celda.Formula("B$n/60", cache = minutos / 60.0, estilo = Estilo.DECIMAL),
                Celda.Formula(
                    cuentaSesiones("\$A$n"),
                    cache = datos.sesionesDelDia(dia).toDouble(),
                    estilo = Estilo.ENTERO
                )
            )
        }

        if (datos.diasConRegistro.isNotEmpty()) {
            val ultima = filas.size
            filas += listOf(
                Celda.Texto("Total", Estilo.SUBTITULO),
                Celda.Formula(
                    "SUM(B$primeraFilaDatos:B$ultima)",
                    cache = datos.minutosTotales.toDouble(),
                    estilo = Estilo.ENTERO_TOTAL
                ),
                Celda.Formula(
                    "SUM(C$primeraFilaDatos:C$ultima)",
                    cache = datos.minutosTotales / 60.0,
                    // Con formato entero el total de horas se veria redondeado.
                    estilo = Estilo.DECIMAL
                ),
                Celda.Formula(
                    "SUM(D$primeraFilaDatos:D$ultima)",
                    cache = datos.completadas.size.toDouble(),
                    estilo = Estilo.ENTERO_TOTAL
                )
            )
        }

        return Hoja(
            nombre = "Por dia",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 14.0), AnchoColumna(2, 12.0),
                AnchoColumna(3, 10.0), AnchoColumna(4, 12.0)
            ),
            congelarTrasFila = 4
        )
    }

    // -------------------------------------------------------- Por categoria

    private fun hojaPorCategoria(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += listOf(Celda.Texto("Tiempo por categoria", Estilo.TITULO))
        filas += listOf(
            Celda.Texto(
                "El ultimo renglon junta lo que quedo sin categoria.",
                Estilo.TENUE
            )
        )
        filas += listOf(Celda.Vacia)
        filas += listOf("Categoria", "Ambito", "Minutos", "Horas", "Sesiones", "% del total")
            .map { Celda.Texto(it, Estilo.ENCABEZADO) }

        val primeraFilaDatos = filas.size + 1
        val total = datos.minutosTotales.coerceAtLeast(1)

        // Solo las categorias que aparecen: una lista con veinte ceros no dice
        // nada y esconde las cuatro que si importan.
        datos.categorias
            .map { it to datos.minutosDeCategoria(it.id) }
            .filter { (_, minutos) -> minutos > 0 }
            .sortedByDescending { (_, minutos) -> minutos }
            .forEach { (categoria, minutos) -> filas += filaCategoria(categoria, minutos, filas.size + 1, total) }

        val sueltas = datos.minutosDeCategoria(null)
        if (sueltas > 0) filas += filaCategoria(null, sueltas, filas.size + 1, total)

        if (filas.size >= primeraFilaDatos) {
            val ultima = filas.size
            filas += listOf(
                Celda.Texto("Total", Estilo.SUBTITULO),
                Celda.Vacia,
                Celda.Formula(
                    "SUM(C$primeraFilaDatos:C$ultima)",
                    cache = datos.minutosTotales.toDouble(),
                    estilo = Estilo.ENTERO_TOTAL
                ),
                Celda.Formula(
                    "SUM(D$primeraFilaDatos:D$ultima)",
                    cache = datos.minutosTotales / 60.0,
                    estilo = Estilo.DECIMAL
                ),
                Celda.Formula(
                    "SUM(E$primeraFilaDatos:E$ultima)",
                    cache = datos.completadas.size.toDouble(),
                    estilo = Estilo.ENTERO_TOTAL
                ),
                Celda.Formula(
                    "SUM(F$primeraFilaDatos:F$ultima)",
                    cache = 1.0,
                    estilo = Estilo.PORCENTAJE_TOTAL
                )
            )
        }

        return Hoja(
            nombre = "Por categoria",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 26.0), AnchoColumna(2, 18.0), AnchoColumna(3, 12.0),
                AnchoColumna(4, 10.0), AnchoColumna(5, 12.0), AnchoColumna(6, 12.0)
            ),
            congelarTrasFila = 4
        )
    }

    private fun filaCategoria(
        categoria: Categoria?,
        minutos: Int,
        fila: Int,
        total: Int
    ): List<Celda> {
        // Lo que no tiene categoria sale con la celda vacia en Registros, y a una
        // celda vacia no la encuentra el nombre del renglon: hay que preguntar
        // literalmente por el vacio. Sin esto la fila mostraria cero en cuanto
        // Excel recalculara, que es de entrada por `fullCalcOnLoad`.
        val criterio = if (categoria != null) "\$A$fila" else "\"\""

        return listOf(
        Celda.Texto(categoria?.nombre ?: "(sin categoria)"),
        Celda.Texto(categoria?.ambito?.etiqueta ?: ""),
        Celda.Formula(
            sumaMinutos(criterio, columna = colCategoria),
            cache = minutos.toDouble(),
            estilo = Estilo.ENTERO
        ),
        Celda.Formula("C$fila/60", cache = minutos / 60.0, estilo = Estilo.DECIMAL),
        Celda.Formula(
            cuentaSesiones(criterio, columna = colCategoria),
            cache = datos.sesionesDeCategoria(categoria?.id).toDouble(),
            estilo = Estilo.ENTERO
        ),
        Celda.Formula(
            "IFERROR(C$fila/${totalMinutosFormula()},0)",
            cache = minutos.toDouble() / total,
            estilo = Estilo.PORCENTAJE
        )
        )
    }

    /**
     * El porcentaje se saca contra el total de Registros y no contra la suma de
     * la propia hoja: asi sigue siendo correcto aunque borres renglones de aqui.
     */
    private fun totalMinutosFormula(): String =
        "SUMIFS(${rango(colMinutos)},${rango(colEstado)},\"$completado\")"

    // ------------------------------------------------------------- Habitos

    private fun hojaHabitos(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += listOf(Celda.Texto("Habitos", Estilo.TITULO))
        filas += listOf(
            Celda.Texto(
                "La racha se calcula como en la app: los dias que el habito no toca " +
                    "no la rompen, y el dia de hoy es de cortesia hasta que termine.",
                Estilo.TENUE
            )
        )
        filas += listOf(Celda.Vacia)
        filas += listOf(
            "Habito", "Categoria", "Cadencia", "Meta diaria", "Minutos sugeridos",
            "Activo", "Cumplimientos", "Racha actual", "Mejor racha", "Unidad de racha", "Notas"
        ).map { Celda.Texto(it, Estilo.ENCABEZADO) }

        datos.habitos.forEach { habito ->
            val racha = datos.racha(habito)
            filas += listOf(
                Celda.Texto(habito.nombre),
                Celda.Texto(datos.nombreCategoria(habito.categoriaId)),
                Celda.Texto(habito.cadencia()),
                Celda.Numero(habito.metaDiaria.toDouble(), Estilo.ENTERO),
                habito.minutosSugeridos?.let { Celda.Numero(it.toDouble(), Estilo.ENTERO) }
                    ?: Celda.Vacia,
                Celda.Booleano(habito.activo),
                Celda.Numero(datos.cumplimientos(habito).toDouble(), Estilo.ENTERO),
                Celda.Numero(racha.actual.toDouble(), Estilo.ENTERO),
                Celda.Numero(racha.mejor.toDouble(), Estilo.ENTERO),
                Celda.Texto(racha.unidad),
                Celda.Texto(habito.notas.orEmpty())
            )
        }

        return Hoja(
            nombre = "Habitos",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 26.0), AnchoColumna(2, 22.0), AnchoColumna(3, 22.0),
                AnchoColumna(4, 12.0), AnchoColumna(5, 16.0), AnchoColumna(6, 9.0),
                AnchoColumna(7, 14.0), AnchoColumna(8, 13.0), AnchoColumna(9, 13.0),
                AnchoColumna(10, 15.0), AnchoColumna(11, 30.0)
            ),
            congelarTrasFila = 4
        )
    }

    // ---------------------------------------------------------- Categorias

    private fun hojaCategorias(): Hoja {
        val filas = mutableListOf<List<Celda>>()
        filas += listOf("Categoria", "Ambito", "Color", "Archivada", "Orden")
            .map { Celda.Texto(it, Estilo.ENCABEZADO) }

        datos.categorias.forEach { categoria ->
            filas += listOf(
                Celda.Texto(categoria.nombre),
                Celda.Texto(categoria.ambito.etiqueta),
                Celda.Texto(categoria.colorHex.orEmpty()),
                Celda.Booleano(categoria.archivada),
                Celda.Numero(categoria.orden.toDouble(), Estilo.ENTERO)
            )
        }

        return Hoja(
            nombre = "Categorias",
            filas = filas,
            anchos = listOf(
                AnchoColumna(1, 26.0), AnchoColumna(2, 18.0),
                AnchoColumna(3, 12.0), AnchoColumna(4, 12.0), AnchoColumna(5, 9.0)
            ),
            congelarTrasFila = 1,
            autoFiltro = "A1:E${maxOf(filas.size, 2)}"
        )
    }

    // --------------------------------------------------------- Diccionarios

    /**
     * Una columna por catalogo. Las validaciones de Registros apuntan aqui, asi
     * que el orden de las columnas —A categorias, B ambitos, C estados, D
     * unidades, E habitos— no se puede cambiar sin tocar [validaciones].
     */
    private fun hojaDiccionarios(): Hoja {
        val columnas = listOf(
            datos.categorias.map { it.nombre },
            Ambito.entries.map { it.etiqueta },
            EstadoActividad.entries.map { it.etiqueta },
            Unidad.entries.map { it.etiqueta },
            datos.habitos.map { it.nombre }
        )

        val filas = mutableListOf<List<Celda>>()
        filas += listOf("Categorias", "Ambitos", "Estados", "Unidades", "Habitos")
            .map { Celda.Texto(it, Estilo.ENCABEZADO) }

        val alto = columnas.maxOfOrNull { it.size } ?: 0
        repeat(alto) { i ->
            filas += columnas.map { lista ->
                lista.getOrNull(i)?.let { Celda.Texto(it) } ?: Celda.Vacia
            }
        }

        return Hoja(
            nombre = "Diccionarios",
            filas = filas,
            anchos = (1..5).map { AnchoColumna(it, 22.0) },
            congelarTrasFila = 1
        )
    }

    // -------------------------------------------------------------- comunes

    /** Rango absoluto de una columna de Registros, de la fila 2 a la ultima. */
    private fun rango(columna: String): String =
        Ooxml.refHoja("Registros", "\$$columna\$2:\$$columna\$$ultimaFila")

    /**
     * Suma de minutos de lo completado que coincide con [criterio]. Por omision
     * cruza contra la fecha, que es como agrupa la hoja Por dia.
     */
    private fun sumaMinutos(criterio: String, columna: String = colFecha): String =
        "SUMIFS(${rango(colMinutos)},${rango(columna)},$criterio," +
            "${rango(colEstado)},\"$completado\")"

    private fun cuentaSesiones(criterio: String, columna: String = colFecha): String =
        "COUNTIFS(${rango(columna)},$criterio,${rango(colEstado)},\"$completado\")"
}
