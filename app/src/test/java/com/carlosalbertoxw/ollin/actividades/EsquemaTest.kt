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
    }
}
