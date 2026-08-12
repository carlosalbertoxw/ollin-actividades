# Exportación e importación en Excel

La pantalla **Ajustes → Archivo** genera un `.xlsx` con toda la bitácora y lo vuelve a leer. Es el respaldo real de la app: la base cifrada no se puede restaurar en otro teléfono.

Todo el paquete `data/excel/` es propio, **sin dependencias externas**. Apache POI pesa del orden de 15 MB en Android, mete decenas de miles de métodos y obliga a desugaring; aquí el formato producido está bajo control, así que un escritor de ~400 líneas es más pequeño, arranca más rápido y no sorprende.

## El libro que sale

### Hojas

Se eligen desde la pantalla de Archivo ([`HojaExportable`](../app/src/main/java/mx/ollin/actividades/data/excel/CatalogoHojas.kt)):

| Hoja | Contenido |
|---|---|
| **Registros** | Toda la bitácora, una actividad por renglón. Obligatoria: es la fuente de las demás |
| **Por día** | Minutos y sesiones de cada día con actividad |
| **Por categoría** | Tiempo por categoría y qué tajada del total representa |
| **Hábitos** | Cadencia, racha actual y mejor racha |
| **Categorías** | El catálogo con su ámbito y color, para reordenarlo fuera del teléfono |
| **Diccionarios** | Listas de categorías, ámbitos, estados, unidades y hábitos; alimentan los desplegables de Registros |

El preajuste "solo datos" (`HojaExportable.MINIMA`) deja Registros, Categorías, Hábitos y Diccionarios: lo que se puede volver a importar.

Las pestañas salen en orden de lectura natural: primero el resumen, luego el detalle y los catálogos al final.

### Esquemas de columna

[`EsquemaExportacion`](../app/src/main/java/mx/ollin/actividades/data/excel/CatalogoHojas.kt) decide el ancho de la hoja Registros:

- **Extendido** — `Fecha, Titulo, Categoria, Ambito, Estado, Inicio, Fin, Minutos, Cantidad, Unidad, Habito, Notas`. Conserva todo.
- **Compacto** — `Fecha, Titulo, Categoria, Estado, Minutos`.

Las fórmulas de las hojas de análisis resuelven sus referencias de columna a partir del esquema elegido, así que ambos modos producen un libro consistente.

### Fórmulas vivas, no tablas dinámicas

Las hojas de análisis llevan fórmulas reales (`SUMIFS`, `COUNTIFS`) apuntando a Registros, más el valor ya calculado como caché. La hoja se ve bien al abrirla en cualquier visor y sigue viva si editas un renglón: cambias unos minutos y los totales se mueven solos.

Por qué no dinámicas: exigen refresco manual y hasta entonces muestran números viejos; las fórmulas se comportan igual en Excel, WPS, LibreOffice y Sheets, y permiten exportar solo algunas pestañas sin dejar cachés huérfanos. El libro se marca con `fullCalcOnLoad`.

El libro incluye además anchos de columna, panel congelado en el encabezado, autofiltro, validaciones de lista contra Diccionarios y un `ListObject` (tabla de Excel) sobre Registros.

## Cómo está hecho

| Archivo | Papel |
|---|---|
| [`ModeloHoja.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/ModeloHoja.kt) | `Celda` (texto, número, fecha, hora, booleano, fórmula), `Hoja`, anchos, validaciones, tablas y los índices de estilo |
| [`Ooxml.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/Ooxml.kt) | Seriales de fecha, letras de columna, escape de XML, saneo de nombres de hoja |
| [`XlsxEscritor.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/XlsxEscritor.kt) | Serializa el paquete OOXML completo dentro de un ZIP |
| [`XlsxLector.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/XlsxLector.kt) | Lee un `.xlsx` con el SAX del JDK |
| [`ExportadorExcel.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/ExportadorExcel.kt) | Arma las hojas a partir de `DatosExportacion` |
| [`ImportadorExcel.kt`](../app/src/main/java/mx/ollin/actividades/data/excel/ImportadorExcel.kt) | Vuelca un libro en la bitácora |

Los índices de estilo de `Estilo` deben coincidir en orden exacto con `cellXfs` en `XlsxEscritor.estilosXml()`.

Excel cuenta los días desde el 30/12/1899 (desplazamiento 25 569) y la hora es la fracción decimal del serial: 12:00 es 0.5.

El lector carga el paquete completo en memoria porque `sharedStrings.xml` puede venir después de las hojas dentro del ZIP; para una bitácora personal el costo es irrelevante y evita necesitar acceso aleatorio. Hay un tope de 64 MB por archivo.

## Importación

`Ajustes → Archivo → Importar` abre el selector del sistema. La app también acepta "Abrir con Ollin" desde un gestor de archivos gracias al `intent-filter` de tipo `spreadsheetml.sheet`.

### Qué hoja se lee

La primera que traiga, al menos, una columna de **fecha** y otra de **título**. Se prefiere la llamada `Registros`. Admite libros que no salieron de aquí.

### Reconocimiento de columnas

Los encabezados se comparan sin acentos ni mayúsculas y con sinónimos:

| Campo | Alias reconocidos |
|---|---|
| fecha | fecha, dia, date, day |
| título | titulo, actividad, tarea, nombre, descripcion, concepto, title |
| categoría | categoria, category, rubro |
| ámbito | ambito, area, tipo |
| estado | estado, status |
| inicio / fin | inicio, hora de inicio, comienzo, start / fin, termino, end |
| minutos | minutos, duracion, tiempo, min |
| cantidad / unidad | cantidad, medida, valor / unidad, unit |
| hábito | habito, habit |
| notas | notas, nota, comentario, observaciones |

Las fechas se aceptan como serial de Excel o como texto en ISO, `dd/MM/yyyy`, `d/M/yyyy`, `MM/dd/yyyy`, `yyyy/MM/dd` y `dd-MM-yyyy`. Las horas, como fracción de día o como `HH:mm`.

### Reglas de relleno

Lo que no venga se completa con una regla explícita en vez de rechazarse: una importación que falla por una columna ausente obliga a editar el archivo a mano justo cuando menos se quiere.

- **Sin hora** → mediodía. Es la que menos miente cuando ya nadie recuerda la hora, y evita que un registro se corra de día al cambiar de huso.
- **Sin estado** → lo del pasado se da por completado (es una bitácora, no una agenda) y lo del futuro sin minutos, por pendiente.
- **Minutos y hora de fin en conflicto** → mandan los minutos capturados; solo si faltan se deducen del reloj. Un fin anterior al inicio se interpreta como cruce de medianoche.
- **Completado** → siempre queda con duración. **Pendiente** y **en curso** nunca la llevan: sumarían en la analítica sin haber ocurrido.
- **Sin fecha o sin título** → el renglón se omite y se reporta con su número de fila.

### Opciones

- **Reemplazar** (por omisión) vacía la bitácora antes de importar; si se apaga, agrega.
- **Crear faltantes** (por omisión) da de alta las categorías y hábitos que el archivo mencione y no existan. El ámbito de una categoría nueva sale del primer renglón que la use, o `PERSONAL` si el archivo no lo trae.

### Resultado

`ResultadoImportacion` devuelve filas leídas, importadas, omitidas, categorías y hábitos creados, cuántas quedaron sin categoría, y una lista de diagnósticos con severidad `INFO`, `AVISO` o `ERROR`.

Los fallos se traducen a mensajes accionables; la excepción cruda se manda a logcat sin datos del usuario.

## Pruebas

- [`ExcelRoundTripTest`](../app/src/test/java/mx/ollin/actividades/ExcelRoundTripTest.kt) — el escritor y el lector reales, sin Android de por medio.
- [`ImportadorTest`](../app/src/test/java/mx/ollin/actividades/ImportadorTest.kt) — lo que sale por la exportación vuelve a entrar por la importación y queda igual, contra una base en memoria.
