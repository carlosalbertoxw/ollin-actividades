package com.carlosalbertoxw.ollin.actividades

import com.carlosalbertoxw.ollin.actividades.data.db.Migraciones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El guardian barato del esquema.
 *
 * Corre en la JVM, sin emulador y en milisegundos, asi que entra en cada
 * compilacion y en cada pull request. No ejecuta una sola linea de SQL: lo que
 * comprueba es que la contabilidad cuadre —tantas versiones, tantos esquemas
 * versionados, tantas migraciones encadenadas—, que es donde se cometen los
 * descuidos que dejan la app sin abrir.
 *
 * Lo caro —que el SQL de cada migracion deje la base exactamente como Room la
 * espera— lo comprueba `MigracionesTest` con MigrationTestHelper, que si
 * necesita un telefono o un emulador.
 */
class EsquemaTest {

    /**
     * Room exporta los esquemas a `app/schemas/`. Las pruebas unitarias corren
     * con el modulo como directorio de trabajo, pero se acepta tambien la raiz
     * del proyecto para que la prueba no dependa de esa costumbre.
     */
    private val carpeta: File = listOf(
        File("schemas/$BASE"),
        File("app/schemas/$BASE")
    ).firstOrNull { it.isDirectory }
        ?: error("No encuentro los esquemas exportados. ¿Compilaste con KSP?")

    private val versionesEnDisco: List<Int>
        get() = carpeta.listFiles { archivo -> archivo.extension == "json" }
            .orEmpty()
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()

    /**
     * La prueba que faltaba el dia que la version bajo de 2 a 1.
     *
     * Room guarda la version dentro del archivo de la base, asi que en cuanto
     * un APK con la version N se instala en un telefono —el de quien
     * desarrolla incluido—, ese telefono queda con una base marcada con N para
     * siempre. Bajar la version despues convierte cada una de esas
     * instalaciones en un downgrade, y Room se niega a abrir una base mas nueva
     * que la app: se cierra al arrancar, sin diálogo, y solo en los telefonos
     * que venian de antes.
     *
     * Aquella vez el razonamiento fue "no hay nada publicado, no hay nada que
     * migrar". Era cierto y aun asi insuficiente: publicado y instalado no son
     * lo mismo. Por eso el numero no vive solo en un comentario que se puede
     * borrar al refactorizar —se borro—, sino aqui, donde bajarlo rompe la
     * compilación de las pruebas antes de llegar a un telefono.
     *
     * **Al subir la version, sube tambien este numero.**
     */
    @Test
    fun `la version del esquema nunca retrocede`() {
        assertTrue(
            "Migraciones.VERSION es ${Migraciones.VERSION} y la $USADA_MAS_ALTA ya estuvo " +
                "instalada en algun telefono. Bajarla convierte esas instalaciones en un " +
                "downgrade y Room se niega a abrir: la app se cierra al arrancar. Un numero " +
                "de version usado esta quemado, aunque no llegara a publicarse.",
            Migraciones.VERSION >= USADA_MAS_ALTA
        )
    }

    /**
     * La otra forma del mismo error, que la prueba de arriba no ve.
     *
     * No hace falta *bajar* la version para romper un telefono: basta con
     * cambiarle la forma a una version que ya salio. Si la 2 gana una columna,
     * el telefono que tiene una base marcada como 2 no corre ninguna migracion
     * —ya esta en la version que la app pide— y Room valida contra un esquema
     * que no corresponde. Se cierra al arrancar, igual que antes.
     *
     * La tentacion aparece sola: «esa version todavia no se ha subido, la
     * aprovecho». Subir no es lo que la quema; instalarla si, y en desarrollo
     * se instala mucho antes de subir nada.
     *
     * El `identityHash` es la huella que Room calcula de las entidades y guarda
     * dentro de la base. Fijarlo aqui para las versiones que ya salieron
     * convierte «cambiaste algo de una version publicada» en una prueba roja.
     */
    @Test
    fun `las versiones que ya salieron no cambian de forma`() {
        HUELLAS_CONGELADAS.forEach { (version, huella) ->
            val contenido = File(carpeta, "$version.json").readText()
            assertTrue(
                "El esquema $version cambió de forma. Su identityHash debería seguir " +
                    "siendo $huella: es el que quedó grabado en las bases que ya están " +
                    "instaladas con esa versión. Si el cambio hace falta, no se edita " +
                    "esta versión: se estrena la siguiente con su migración.",
                contenido.contains(""""identityHash": "$huella"""")
            )
        }
    }

    @Test
    fun `cada version del esquema esta versionada en el repositorio`() {
        assertEquals(
            "Falta el .json de alguna version, o sobra el de una que no existe. " +
                "KSP los escribe al compilar; hay que agregarlos a git.",
            (1..Migraciones.VERSION).toList(),
            versionesEnDisco
        )
    }

    @Test
    fun `el json de cada version declara su propio numero`() {
        versionesEnDisco.forEach { version ->
            val contenido = File(carpeta, "$version.json").readText()
            assertTrue(
                "$version.json no declara \"version\": $version. " +
                    "Seguramente se copio a mano en vez de dejar que KSP lo escribiera.",
                Regex(""""version"\s*:\s*$version\b""").containsMatchIn(contenido)
            )
        }
    }

    /**
     * El descuido que esta prueba existe para cazar: subir [Migraciones.VERSION]
     * y olvidar la migracion. Room no protesta al compilar —lo descubre al
     * abrir la base en el telefono de alguien, cuando ya es tarde—.
     */
    @Test
    fun `las migraciones encadenan de la 1 a la version actual sin huecos`() {
        val pasos = Migraciones.TODAS.map { it.startVersion to it.endVersion }
        val esperados = (1 until Migraciones.VERSION).map { it to it + 1 }

        assertEquals(
            "Cada version nueva necesita su Migration de la anterior. " +
                "Faltan o sobran pasos en Migraciones.TODAS.",
            esperados,
            pasos
        )
    }

    @Test
    fun `ninguna migracion salta versiones ni va hacia atras`() {
        Migraciones.TODAS.forEach { paso ->
            assertEquals(
                "La migracion ${paso.startVersion}->${paso.endVersion} salta versiones. " +
                    "Un paso por version: asi cada salto se puede probar por separado.",
                paso.startVersion + 1,
                paso.endVersion
            )
        }
    }

    private companion object {
        const val BASE = "com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase"

        /**
         * La version mas alta que alguna vez salio de este repositorio dentro
         * de un APK, publicado o no.
         *
         * No se deriva de nada a proposito: si se calculara desde los archivos
         * de `app/schemas/`, borrar uno —que es justo lo que se hizo aquella
         * vez— haria bajar el listón junto con la version y la prueba pasaria
         * tan campante. Es un numero escrito a mano porque tiene que doler
         * cambiarlo.
         */
        const val USADA_MAS_ALTA = 3

        /**
         * La huella de cada version que ya salio en un APK, copiada de su
         * `N.json` el dia que se publico. Al estrenar una version, se agrega su
         * huella aqui **cuando se etiqueta**, no antes: mientras nadie la haya
         * instalado todavia se puede corregir.
         *
         * La 1 y la 2 comparten huella porque describen el mismo esquema: la 2
         * agrego `horaRecordatorio`, y al replegar todo a la 1 esa columna
         * acabo dentro del `CREATE TABLE` inicial.
         */
        val HUELLAS_CONGELADAS = mapOf(
            1 to "cc3cd97f94296b5baabe7ee78f790407",
            2 to "cc3cd97f94296b5baabe7ee78f790407",
            // La 3 se congela al etiquetar la 1.1.0, que es la que la publica.
            3 to "527478060b25204de00c336cbde3fd44"
        )
    }
}
