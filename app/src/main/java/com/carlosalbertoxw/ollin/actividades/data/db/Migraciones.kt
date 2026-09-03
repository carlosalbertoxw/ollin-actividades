package com.carlosalbertoxw.ollin.actividades.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * La version del esquema y el camino para llegar hasta ella.
 *
 * Vive aparte de [OllinDatabase] para que la version y sus migraciones no se
 * puedan tocar en archivos distintos. Subir un numero sin escribir el paso que
 * lo acompana es el error que deja la app sin abrir en el telefono de alguien,
 * y aqui las dos cosas estan a tres lineas de distancia.
 *
 * **Un numero de version usado esta quemado, aunque no llegara a publicarse.**
 * Room guarda la version dentro del archivo de la base, asi que en cuanto un
 * APK con la version N se instala en un telefono —el de quien desarrolla
 * incluye—, ese telefono tiene una base marcada con N para siempre. Bajar la
 * version despues no la "libera": convierte cada una de esas instalaciones en
 * un downgrade, y Room se niega a abrir una base mas nueva que la app. Esto ya
 * paso aqui una vez, ver el historial de abajo.
 *
 * **Ya hay bitacoras publicadas.** Desde la 1.0.0, borrar la base para empezar
 * de cero no es un inconveniente: la llave vive en el Keystore y no se
 * respalda, asi que lo que se pierde es la bitacora entera y no hay de donde
 * recuperarla. Por eso no existe `fallbackToDestructiveMigration` en ningun
 * lado —ni su variante `OnDowngrade`— y no debe existir nunca.
 *
 * Para agregar una version, ver docs/modelo-de-datos.md. El resumen:
 *
 * 1. Cambiar las entidades.
 * 2. Subir [VERSION].
 * 3. Escribir la `Migration` y sumarla a [TODAS], en orden.
 * 4. Compilar para que KSP escriba `app/schemas/N.json`, y versionarlo.
 *
 * `EsquemaTest` y `MigracionesTest` cazan los cuatro descuidos posibles: una
 * version sin migracion, un esquema sin versionar, una version que retrocede,
 * y una migracion cuyo SQL no deja la base como Room la espera.
 */
object Migraciones {

    /**
     * La version del esquema. Es la que declara [OllinDatabase].
     *
     * Historial, porque el numero no cuenta toda la verdad:
     *
     * | Version | Que la uso |
     * |---|---|
     * | 1 | El primer esquema, y el de las publicadas 1.0.0 y 1.0.1 |
     * | 2 | `habito.horaRecordatorio`, en compilaciones de desarrollo; luego **la misma forma** que la 1 |
     * | 3 | `habito.modoCiclo`: desde donde recuenta una cadencia periodica |
     *
     * La 2 nacio agregando `horaRecordatorio` sobre una 1 que no la tenia. Al
     * preparar la primera publicacion se replegó todo a la version 1 —con la
     * columna ya dentro del `CREATE TABLE` inicial— porque no habia nada
     * publicado que migrar. El razonamiento tenia un agujero: no habia nada
     * *publicado*, pero si habia teléfonos de desarrollo con una base marcada
     * como 2, y para esos la 1.0.0 era un downgrade. Se cerraban al abrirse.
     *
     * De ahi que hoy la version vuelva a ser 2 y [SIN_CAMBIOS_1_2] no haga
     * nada: las dos describen exactamente el mismo esquema —mismo
     * `identityHash`— y lo unico que hace falta es que el numero avance en vez
     * de retroceder.
     */
    const val VERSION = 3

    /**
     * De la 1 a la 2 no hay nada que hacer, y aun asi tiene que existir.
     *
     * Las dos versiones describen el mismo esquema: la 1 de las publicadas
     * 1.0.0 y 1.0.1 ya trae `horaRecordatorio` dentro del `CREATE TABLE`. Pero
     * Room exige un paso declarado para cada salto, y sin este las bases de
     * quienes instalaron esas dos versiones no abririan.
     *
     * Una migracion vacia es rara y por eso se explica: no esta arreglando el
     * esquema, esta reconciliando dos numeraciones que se cruzaron. Es la
     * cicatriz de haber bajado la version una vez, y sale mas barata que
     * pedirle a nadie que desinstale y pierda su bitacora.
     */
    val SIN_CAMBIOS_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    /**
     * Agrega `habito.modoCiclo`: si una cadencia periodica recuenta desde su
     * fecha fija o desde el ultimo cumplimiento.
     *
     * `NOT NULL DEFAULT 'CALENDARIO'`, y el mismo valor por omision esta
     * declarado en la entidad con `@ColumnInfo`. Las dos cosas tienen que
     * decir lo mismo: Room compara la tabla real contra la que generaria de
     * las entidades, y una diferencia en el `DEFAULT` es motivo suficiente
     * para que se niegue a abrir.
     *
     * CALENDARIO es lo que hacian todos los habitos hasta ahora, asi que
     * quien actualiza no ve moverse ni una fecha. Contar desde el ultimo
     * cumplimiento cambia cuando toca lo siguiente, y eso solo debe pasar si
     * alguien lo pide, no por instalar una version.
     */
    val MODO_DE_CICLO_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE habito ADD COLUMN modoCiclo TEXT NOT NULL DEFAULT 'CALENDARIO'"
            )
        }
    }

    /** Los pasos, en orden y sin huecos, de la version 1 a [VERSION]. */
    val TODAS: Array<Migration> = arrayOf(SIN_CAMBIOS_1_2, MODO_DE_CICLO_2_3)
}
