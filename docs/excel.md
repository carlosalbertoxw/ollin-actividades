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

`Ajustes → Archivo → Importar` abre el selector de documentos del sistema. Es el único camino: la app no se declara como manejadora de `.xlsx`, así que no aparece en el "Abrir con" de un gestor de archivos.

### Qué pestañas se leen

Las cuatro que Ollin sabe reconocer, **cada una si viene en el archivo** y en este orden:

| Pestaña | Qué entra | Qué se ignora |
|---|---|---|
| **Categorias** | Nombre, ámbito, color, si está archivada y orden | — |
| **Habitos** | Nombre, categoría, cadencia, meta diaria, minutos sugeridos, activo y notas | Cumplimientos, racha actual, mejor racha y unidad de racha |
| **Diccionarios** | Las columnas *Categorias* y *Habitos*, solo para dar de alta lo que falte | Ámbitos, estados y unidades: son enumeraciones fijas de la app |
| **Registros** | La bitácora | — |

El orden no es casual. `Registros` nombra sus categorías y hábitos por texto y solo puede enlazarlos con los que ya existen; leyendo antes los catálogos, un registro cae en la categoría con su color y su ámbito de verdad en lugar de en una recién inventada como `PERSONAL`. `Diccionarios` va al final y solo rellena: sus columnas son nombres sueltos, sin ámbito ni cadencia, así que cualquier otra hoja sabe más.

Las pestañas se buscan **por nombre**, sin acentos ni mayúsculas —`Categorías` y `categorias` son la misma—. La de `Registros` es la excepción: si no está, se usa la primera hoja que traiga al menos una columna de **fecha** y otra de **título**, para admitir libros que no salieron de aquí.

Un libro que solo trae catálogos es legítimo: sirve para reordenar las categorías o retocar los hábitos desde la computadora. En ese caso la bitácora **no se toca aunque esté marcado "Reemplazar todo"**, porque no hay nada con qué reemplazarla.

### Cómo se emparejan los catálogos

Por nombre normalizado (sin acentos ni mayúsculas). Lo que ya existe se actualiza; lo que no, se crea si está activo "Crear lo que falte".

**El nombre nunca se reescribe.** Es la llave con la que se emparejan las dos listas y además lleva índice único: adoptar la ortografía del archivo podría chocar contra otra fila y tumbar la importación entera. De una categoría que ya existe se actualizan su ámbito, su color, su orden y si está archivada —que es justamente para lo que sirve poder reordenar el catálogo fuera del teléfono—; de un hábito, su categoría, cadencia, meta, minutos sugeridos, si está activo y sus notas.

Los hábitos nuevos que no traen columna de orden lo toman de su posición en la hoja: es el orden que alguien acomodó al editarla.

### La cadencia, de ida y de vuelta

La columna *Cadencia* se escribe en español (`Habito.cadencia()`) y se vuelve a interpretar al importar:

| En la hoja | Frecuencia |
|---|---|
| `Todos los dias` | `DIARIA` |
| `Entre semana`, `Fin de semana`, `Ningun dia`, `L X V` | `DIAS_ELEGIDOS` |
| `4 dias por semana` | `SEMANAL`, meta 4 |
| `Cada semana`, `Cada quincena`, `Cada 3 dias` | `CADA_DIAS` con 7, 15 y 3 |
| `Cada mes`, `Cada trimestre`, `Cada semestre`, `Cada ano`, `Cada 2 meses` | `CADA_MESES` con 1, 3, 6, 12 y 2 |

También se aceptan el nombre crudo de la `Frecuencia` y su etiqueta, para un archivo escrito a mano. Lo que no se entiende deja el hábito con la cadencia que tenía y se reporta como aviso con su número de fila.

Un detalle asumido: `CADA_DIAS` con intervalo 1 y `DIAS_ELEGIDOS` con los siete días marcados se escriben ambos como "Todos los dias" y vuelven como `DIARIA`. Se comportan igual y es la que la pantalla enseña más limpia.

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
