# Ollin Actividades

[![Pruebas](https://github.com/carlosalbertoxw/ollin-actividades/actions/workflows/pruebas.yml/badge.svg)](https://github.com/carlosalbertoxw/ollin-actividades/actions/workflows/pruebas.yml)
[![Versión](https://img.shields.io/github/v/release/carlosalbertoxw/ollin-actividades?label=versi%C3%B3n)](https://github.com/carlosalbertoxw/ollin-actividades/releases/latest)
[![Licencia](https://img.shields.io/github/license/carlosalbertoxw/ollin-actividades?label=licencia)](https://github.com/carlosalbertoxw/ollin-actividades/blob/main/LICENSE)

**Tu tiempo, anotado.**

App Android de bitácora personal: cronometra o captura a mano lo que haces, lleva hábitos
con la cadencia que quieras, y te dice en qué se te fue la semana. *Ollin* es "movimiento"
en náhuatl, y es el glifo del calendario mexica que representa el cambio — que es
exactamente lo que registra una bitácora de tu tiempo.

**[Descargar el APK →](https://carlosalbertoxw.com/ollin-actividades/)**

No está en Google Play: el APK se instala a mano, y el sitio explica cómo. La app
comprueba una vez al día si salió una versión nueva y lo dice en *Acerca de*; se puede
apagar en Ajustes.

Cada release lleva su `checksums.txt` con la huella del APK, calculada sobre el archivo
que se descarga. Comprobarla antes de instalar es una línea:

```bash
sha256sum -c checksums.txt
```

### Comprobar que el APK es el bueno

La huella del archivo dice que no se corrompió en el camino, pero viaja por el mismo
camino que el APK: quien pudiera cambiar uno cambiaría el otro. Por eso cuenta más la
firma. Todas las versiones van firmadas con la misma llave, y esta es la huella de su
certificado; está aquí —en el repositorio, no solo en el sitio— para que se pueda comparar
por un camino distinto del que trajo el archivo:

SHA-256 del certificado de firma: `ef04928146061db10bfc915544e7384550c68ab1f4fcef0f95925d9b1d2f8f23`

```bash
apksigner verify --print-certs ollin-actividades-x.y.z.apk
```

La línea `certificate SHA-256 digest` tiene que decir exactamente eso. Si no coincide, no
lo instales. En el teléfono, sin computadora, lo comprueba también
[AppVerifier](https://github.com/soupslurpr/AppVerifier). El flujo de publicación se niega
a publicar un APK firmado con otra llave.

Y para comprobar además que el APK salió del flujo de publicación de este repositorio —de
qué commit y con qué workflow—, cada release lleva una atestación de procedencia:

```bash
gh attestation verify ollin-actividades-x.y.z.apk --repo carlosalbertoxw/ollin-actividades
```

[Ollin Finanzas](https://github.com/carlosalbertoxw/ollin-finanzas) es la app hermana —un
libro de finanzas personales— y comparte estas convenciones: la versión sale del
`CHANGELOG`, la firma de variables de entorno, y los mismos cuatro flujos de publicación.
Lo que se aprende manteniendo una se aplica a la otra.

---

## Qué hace

| | |
|---|---|
| **Cronómetro o captura a mano** | Mide una actividad mientras pasa, o anótala después con los minutos que recuerdes. Las dos cuentan igual. Solo puede haber un cronómetro corriendo: arrancar otro cierra el anterior, así que el día nunca suma más horas de las que tiene. |
| **Hábitos con la cadencia real** | Diarios, en días elegidos de la semana, tantas veces por semana, o cada tantos días o meses contando desde un ancla. No todo lo que se repite cabe en una semana, y el ancla viaja en el respaldo para que restaurarlo no le corra el calendario a nadie. |
| **Si lo haces tarde, tú decides** | Un hábito periódico puede contar por fechas fijas —la renta del día 1 sigue siendo el 1— o desde el último cumplimiento —cambiar el filtro cada quince días vuelve a contar quince desde que lo cambiaste—. Y lo que tocó y no se hizo se queda a la vista con su fecha, en vez de desaparecer hasta la siguiente vuelta: se anuncia como vencido y sube al principio de la lista, con lo que lleva más esperando arriba. Lo que hoy no toca dice cuándo vuelve a tocar, que es lo único que uno quiere saber de algo trimestral. |
| **Rachas que no castigan de más** | El día en curso es de cortesía: un hábito sin marcar a las nueve de la mañana está pendiente, no fallado. Los días que no toca se saltan sin penalizar, así que uno de lunes a viernes sobrevive al fin de semana. En los periódicos la racha cuenta repeticiones, y marcar con un día de retraso no la rompe. |
| **Marcar abre la captura** | La paloma no escribe a ciegas: abre el formulario ya lleno desde la plantilla del hábito, para corregir la duración real o la hora antes de que entre en la analítica. Guardar sin tocar nada deja exactamente el mismo registro. |
| **Analítica sobre lo completado** | Minutos por día, por categoría y por ámbito en ventanas de 7, 30 o 90 días. Lo pendiente no infla ninguna cifra: solo suma lo que cerraste. |
| **Recordatorios de lo que falta** | Un aviso por cada hábito que toque y todavía no hayas cumplido, a la hora que le pongas, y por cada tarea pendiente a su hora de inicio. Se calculan al vuelo y se arma una sola alarma, la del más próximo: una tabla de avisos habría que invalidarla en seis sitios y el primero que se olvidara dejaría fantasmas. |
| **Bajo llave si quieres** | Con la credencial del teléfono —patrón, PIN, huella— o con un PIN propio de Ollin, con espera creciente ante los intentos fallidos y `FLAG_SECURE` mientras hay candado puesto. |
| **Importar y exportar .xlsx** | Tu respaldo es un libro de Excel que tú decides dónde guardar. Y como es el único que hay —la base va cifrada con una llave que no se restaura en otro teléfono—, Ollin te lo recuerda cada semana si no has exportado —con una notificación y con un aviso arriba de Hoy cada vez que la abres, hasta que exportes—, y otra vez cuando encuentra una versión nueva, que es justo antes de instalar algo encima. El libro no va cifrado: tiene que abrirse en Excel. |

Los instantes se guardan en **UTC** y el día local **aparte**, en su propia columna. Un
instante es un punto en la línea del tiempo y no debe moverse al viajar; "cuánto trabajé
el martes" es una pregunta del calendario de quien la hace. Agrupar por día local en SQL
exigiría aplicar el huso en cada consulta, y recalcular la duración en cada suma impide
usar un índice: quien escribe paga una vez lo que quien lee pagaría siempre.

---

## Import / export

- **Importar** lee un `.xlsx` y reconoce los encabezados sin importar acentos ni
  mayúsculas. Lee primero los catálogos y después los registros, porque la hoja de
  registros nombra sus categorías y hábitos por texto y solo puede enlazarlos con los que
  ya existen. Toda la escritura va en **una sola transacción**: con "Reemplazar todo", un
  fallo a media inserción dejaría la tabla vacía y nada con que repoblarla.
- **Exportar** es configurable en dos ejes:
  - **Esquema**: `Extendido` (agrega Ámbito, Inicio, Fin, Cantidad, Unidad, Hábito y
    Notas) o `Compacto` (las cinco columnas esenciales).
  - **Pestañas**: eliges cuáles de las seis generar. `Registros` siempre va, porque es la
    fuente de las fórmulas de todas las demás.

Pestañas disponibles: `Registros`, `Por día`, `Por categoría`, `Hábitos`, `Categorías`,
`Diccionarios`.

### Por qué fórmulas y no tablas dinámicas

Las hojas de análisis salen con `SUMIFS` y `COUNTIFS` vivos en vez de dinámicas. Una
dinámica exige refresco manual, se comporta distinto según la suite, y deja cachés
huérfanos si exportas solo algunas pestañas. Las fórmulas se recalculan solas y funcionan
igual en Excel, WPS Office, LibreOffice y Google Sheets. Cada celda lleva además el valor
ya calculado, así que la hoja se ve bien incluso en visores que no recalculan.

---

## Arquitectura

Un solo módulo, Kotlin + Jetpack Compose (Material 3).

```
app/src/main/java/com/carlosalbertoxw/ollin/actividades/
├── data/
│   ├── db/              Room: entidades, DAOs, proyecciones, migraciones, catálogo semilla
│   ├── excel/           Lector y escritor de .xlsx propios, exportador e importador
│   ├── actualizaciones/ Si hay una versión nueva publicada
│   ├── recordatorios/   Qué toca avisar, la alarma del sistema y las notificaciones
│   ├── prefs/           Preferencias en DataStore
│   ├── repo/            ActividadesRepositorio: toda la escritura pasa por aquí
│   └── seguridad/       Llave de la base, PIN, control de bloqueo
├── di/                  Contenedor de dependencias, a mano
├── domain/
│   ├── model/           Enums, utilidades de tiempo y días de la semana
│   └── usecase/         Calendario de los hábitos y cálculo de rachas
└── ui/                  Compose: pantallas y sus ViewModels, navegación, tema, componentes
```

Y fuera de la app:

```
.github/workflows/   Pruebas, release firmado y publicación del sitio
web/                 El sitio de descarga (Vite), publicado en GitHub Pages
CHANGELOG.md         La versión y las notas, en un solo lugar
```

Cada pantalla recibe sus dependencias concretas —el repositorio, los ajustes—, no el
contenedor entero. Así un ViewModel declara de qué depende y se puede construir en una
prueba o en un `@Preview` sin levantar la base cifrada.

Decisiones que no son las de default, y por qué:

- **XLSX escrito a mano** sobre `java.util.zip` + SAX. Apache POI en Android pesa ~15 MB,
  mete decenas de miles de métodos y obliga a desugaring; aquí el formato producido está
  bajo control y el escritor completo cabe en ~400 líneas.
- **Sin Hilt.** Con un módulo y media docena de objetos compartidos, un contenedor a mano
  (`di/Contenedor.kt`) se lee de arriba a abajo y no cuesta tiempo de compilación.
- **Las entidades de Room son el modelo de dominio.** Duplicarlas en otra capa sería mapeo
  sin ganancia a esta escala.
- **Las rachas se calculan en Kotlin, no en SQL.** Dependen de qué días toca cada hábito, y
  eso vive en su plantilla: SQLite tendría que reconstruir el calendario.
- **Sin `fallbackToDestructiveMigration`, nunca.** La base va cifrada con una llave del
  Keystore que no se respalda: borrarla no es un inconveniente, es perder la bitácora
  entera. Si falta un paso, Room se niega a abrir, y eso se arregla con una actualización.
- **La alarma exacta no se da por concedida.** Desde Android 12 se autoriza a mano; sin
  ella el aviso sale aproximado en vez de no salir. Uno con minutos de retraso sigue
  sirviendo, uno que no llega no.
- **`androidx.fragment` declarado a mano.** `biometric:1.1.0` arrastra `fragment:1.2.5`,
  anterior a la API de ActivityResult: su `FragmentActivity` rechaza los request codes de
  más de 16 bits que genera `activity:1.10.1`, y **cualquier** selector de archivos revienta
  al abrirse. Quitar esa línea de `libs.versions.toml` vuelve a romper importar y exportar.

---

## Documentación

- [Arquitectura](docs/arquitectura.md) — capas, flujo de datos, navegación, arranque y bloqueo, y por qué no hay framework de inyección.
- [Modelo de datos](docs/modelo-de-datos.md) — las tres tablas, invariantes de las marcas de tiempo, proyecciones y cómo se migra el esquema.
- [Hábitos y rachas](docs/rachas.md) — cadencias soportadas y cómo se cuenta cada tipo de racha.
- [Recordatorios](docs/recordatorios.md) — qué avisa, cómo se programan las alarmas y qué permisos hacen falta.
- [Excel](docs/excel.md) — formato del libro exportado, hojas, fórmulas y reglas de importación.
- [Seguridad y privacidad](docs/seguridad.md) — cifrado de la base, Keystore, PIN, bloqueo y respaldos.
- [Actualizaciones](docs/actualizaciones.md) — qué se consulta, qué no sale del teléfono y el contrato del `version.json`.
- [Desarrollo](docs/desarrollo.md) — entorno, comandos, pruebas, integración continua y convenciones del código.
- [Publicación](docs/publicacion.md) — versionado, firma, release automatizado y cómo se entera la app de una versión nueva.
- [El sitio](docs/sitio.md) — la página de descarga con Vite, GitHub Pages y de dónde salen sus datos.
- [Registro de cambios](CHANGELOG.md) — qué trae cada versión. De aquí salen la versión del APK y las notas de cada lanzamiento.

---

## Privacidad

Nada de lo que registras sale del teléfono. No hay cuentas, ni servidor, ni analítica. La
base va cifrada con **AES-256** (SQLCipher) y la frase se guarda envuelta con una llave del
Keystore, así que el archivo `.db` no dice nada fuera de ese dispositivo. Por lo mismo el
respaldo automático del sistema está **desactivado** para la base: una llave del Keystore
no se puede restaurar, así que la copia llegaría ilegible. Tu respaldo es la exportación a
`.xlsx`, que tú decides dónde guardar.

La única llamada a internet es preguntarle al sitio del proyecto, una vez al día, si hay
una versión más nueva: un `GET` a un archivo estático que no manda ningún dato tuyo, ni
siquiera qué versión traes — la comparación ocurre en el teléfono. Se apaga en *Ajustes*, y
apagada la app no toca la red en ningún momento. Nunca descarga ni instala nada por su
cuenta: cuando hay versión nueva, *Acerca de* enseña un botón que abre el sitio.

---

## Compilar

Requiere **JDK 17 o superior** y el Android SDK 37 instalado. Hasta la 1.2.1 hacía falta
justo un JDK 21, porque Kotlin 2.1.20 moría con un mensaje que era solo el número de
versión (`* What went wrong: 26.0.1`); desde Kotlin 2.4 y Gradle 9.8 eso quedó atrás. Ver
[el JDK de Gradle](docs/desarrollo.md#el-jdk-de-gradle).

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Las pruebas unitarias corren en la JVM con Robolectric, sin emulador. Las de interfaz y las
de migración necesitan un teléfono o un emulador conectado:

```bash
./gradlew :app:connectedDebugAndroidTest
```

La ruta del SDK va en `local.properties`, que no se versiona:

```
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
```

La versión sale de `CHANGELOG.md`, no del `build.gradle.kts`: se lee el primer encabezado
`## [x.y.z]` y de ahí salen el `versionName` y el `versionCode`. Publicar una versión es
renombrar la sección `[Sin publicar]` y poner el tag `vX.Y.Z`; de ahí en adelante lo hacen
los flujos de GitHub Actions. Ver [Publicación](docs/publicacion.md).

El sitio es un proyecto aparte y no pasa por Gradle:

```bash
cd web && npm install && npm run dev
```

- `minSdk` 26 · `targetSdk` 36 · `compileSdk` 37 · Kotlin 2.4.20 · AGP 9.4.1 · Gradle 9.8.0

Entorno, comandos y convenciones con más detalle en [Desarrollo](docs/desarrollo.md).

---

## Licencia

[MIT](LICENSE) © 2026 Carlos Alberto.

Puedes usar, copiar, modificar y redistribuir el código, incluso con fines comerciales,
mientras conserves el aviso de copyright. La app se entrega sin garantía de ningún tipo.
Las bibliotecas de terceros que viajan en el APK tienen sus propias licencias; están en
*Acerca de → Licencias de terceros*.
