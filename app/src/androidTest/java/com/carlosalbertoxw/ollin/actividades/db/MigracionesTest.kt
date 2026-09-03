package com.carlosalbertoxw.ollin.actividades.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.actividades.data.db.Migraciones
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Corre las migraciones de verdad contra los esquemas exportados.
 *
 * `EsquemaTest` comprueba que la contabilidad cuadre; esta comprueba que el SQL
 * funcione. Son cosas distintas: una migracion puede existir, estar bien
 * encadenada y aun asi dejar una columna con el tipo equivocado o sin su
 * indice, y entonces Room se niega a abrir la base **en el telefono de quien
 * actualizo**, que es el peor sitio para enterarse.
 *
 * MigrationTestHelper compara la base resultante contra el `N.json` que KSP
 * exporto de las entidades, asi que valida el resultado, no la intencion.
 *
 * Necesita un telefono o un emulador: la validacion usa el SQLite del sistema.
 * Va sin cifrar a proposito —el helper abre con el SQLite normal, no con
 * SQLCipher—, y da igual: el cifrado envuelve el archivo entero y las
 * migraciones ven el mismo esquema con llave o sin ella.
 *
 * **Al agregar una migracion**, se agrega aqui una prueba propia que ademas
 * escriba un renglon antes de migrar y lo vuelva a leer despues. La cadena de
 * abajo demuestra que el esquema llega entero; solo un dato real demuestra que
 * la bitacora sobrevive al viaje.
 */
@RunWith(AndroidJUnit4::class)
class MigracionesTest {

    @get:Rule
    val ayudante = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OllinDatabase::class.java
    )

    /**
     * De la primera version a la actual, de un tiron.
     *
     * Es el camino que recorre quien instalo Ollin el primer dia y actualiza
     * hoy, saltandose todas las versiones intermedias. Con el esquema todavia
     * en 1 esta prueba comprueba lo unico que hay que comprobar: que `1.json`
     * describe una base que Room acepta como suya.
     */
    @Test
    fun laBaseMigraDeLaPrimeraVersionALaActual() {
        ayudante.createDatabase(NOMBRE, 1).close()

        ayudante.runMigrationsAndValidate(
            NOMBRE,
            Migraciones.VERSION,
            /* validateDroppedTables = */ true,
            *Migraciones.TODAS
        ).close()
    }

    /**
     * Y version por version, que es el camino de quien actualiza siempre.
     *
     * Se prueba aparte porque no es el mismo recorrido: encadenar los pasos de
     * uno en uno puede fallar donde el salto largo no falla, cuando una
     * migracion deshace algo que la siguiente da por hecho.
     */
    @Test
    fun cadaSaltoIntermedioDejaLaBaseValida() {
        for (version in 1 until Migraciones.VERSION) {
            ayudante.createDatabase(NOMBRE, version).close()
            ayudante.runMigrationsAndValidate(
                NOMBRE,
                version + 1,
                true,
                *Migraciones.TODAS
            ).close()
        }
    }

    /**
     * La 2 a la 3, con un habito dentro.
     *
     * Es la prueba que el comentario de esta clase pide para cada migracion, y
     * la unica que demuestra lo que de verdad importa: que la bitacora
     * sobrevive al viaje. La cadena de arriba solo dice que el esquema llega
     * entero, y un `ALTER TABLE` puede dejar el esquema perfecto y aun asi
     * perder o corromper lo que habia.
     *
     * Se comprueba tambien el valor por omision de la columna nueva: un habito
     * escrito por la version 2 no eligio modo de ciclo, y tiene que salir de la
     * migracion con el de siempre. Si saliera con el otro, actualizar le
     * cambiaria el calendario a gente que no pidio nada.
     */
    @Test
    fun laMigracionDe2A3ConservaLosHabitos() {
        ayudante.createDatabase(NOMBRE, 2).use { base ->
            base.execSQL(
                """
                INSERT INTO habito (nombre, frecuencia, metaDiaria, metaSemanal, diasSemana,
                                    intervaloDias, intervaloMeses, activo, orden, creadoEn)
                VALUES ('Cambiar el filtro', 'CADA_DIAS', 1, 5, 127, 15, 1, 1, 0, 0)
                """.trimIndent()
            )
        }

        ayudante.runMigrationsAndValidate(NOMBRE, 3, true, *Migraciones.TODAS).use { base ->
            base.query("SELECT nombre, intervaloDias, modoCiclo FROM habito").use { fila ->
                assertTrue("El habito no sobrevivio a la migracion", fila.moveToFirst())
                assertEquals("Cambiar el filtro", fila.getString(0))
                assertEquals(15, fila.getInt(1))
                assertEquals(
                    "Un habito de antes tiene que seguir contando como antes",
                    "CALENDARIO",
                    fila.getString(2)
                )
            }
        }
    }

    private companion object {
        /** Un nombre de prueba: la base de verdad no se toca. */
        const val NOMBRE = "migraciones_en_prueba.db"
    }
}
