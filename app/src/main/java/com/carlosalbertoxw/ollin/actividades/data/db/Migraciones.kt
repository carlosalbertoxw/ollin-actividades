package com.carlosalbertoxw.ollin.actividades.data.db

import androidx.room.migration.Migration

/**
 * La version del esquema y el camino para llegar hasta ella.
 *
 * Vive aparte de [OllinDatabase] para que la version y sus migraciones no se
 * puedan tocar en archivos distintos. Subir un numero sin escribir el paso que
 * lo acompana es el error que deja la app sin abrir en el telefono de alguien,
 * y aqui las dos cosas estan a tres lineas de distancia.
 *
 * **Ya hay bitacoras publicadas.** Desde la 1.0.0, borrar la base para
 * empezar de cero no es un inconveniente: la llave vive en el Keystore y no se
 * respalda, asi que lo que se pierde es la bitacora entera y no hay de donde
 * recuperarla. Por eso no existe `fallbackToDestructiveMigration` en ningun
 * lado y no debe existir nunca.
 *
 * Para agregar una version, ver docs/modelo-de-datos.md. El resumen:
 *
 * 1. Cambiar las entidades.
 * 2. Subir [VERSION].
 * 3. Escribir la `Migration` y sumarla a [TODAS], en orden.
 * 4. Compilar para que KSP escriba `app/schemas/N.json`, y versionarlo.
 *
 * `EsquemaTest` y `MigracionesTest` cazan los tres primeros descuidos posibles:
 * una version sin migracion, un esquema sin versionar y una migracion cuyo SQL
 * no deja la base como Room la espera.
 */
object Migraciones {

    /** La version del esquema. Es la que declara [OllinDatabase]. */
    const val VERSION = 1

    /**
     * Los pasos, en orden y sin huecos, de la version 1 a [VERSION].
     *
     * Vacia mientras el esquema siga en 1: no hay nada que migrar cuando solo
     * existe la primera version.
     */
    val TODAS: Array<Migration> = arrayOf()
}
