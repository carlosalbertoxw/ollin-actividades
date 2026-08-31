package com.carlosalbertoxw.ollin.actividades.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlosalbertoxw.ollin.actividades.data.db.Migraciones
import com.carlosalbertoxw.ollin.actividades.data.db.OllinDatabase
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

    private companion object {
        /** Un nombre de prueba: la base de verdad no se toca. */
        const val NOMBRE = "migraciones_en_prueba.db"
    }
}
