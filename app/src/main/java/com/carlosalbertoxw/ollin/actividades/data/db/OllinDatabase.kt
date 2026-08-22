package com.carlosalbertoxw.ollin.actividades.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carlosalbertoxw.ollin.actividades.data.seguridad.LlaveBase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Version 2: los habitos ganan hora de recordatorio.
 *
 * Ya hay bitacoras reales en telefonos, asi que a partir de aqui cada cambio de
 * entidad sube la version y trae su [androidx.room.migration.Migration]. No hay
 * `fallbackToDestructiveMigration` en ningun lado: la base va cifrada con una
 * llave del Keystore que no se respalda, asi que borrarla y empezar de cero no
 * es un inconveniente, es perder la bitacora entera.
 */
@Database(
    entities = [
        Categoria::class,
        Habito::class,
        Actividad::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Convertidores::class)
abstract class OllinDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun habitoDao(): HabitoDao
    abstract fun actividadDao(): ActividadDao

    companion object {
        private const val NOMBRE = "ollin_actividades.db"

        /**
         * Añade la hora del recordatorio del habito.
         *
         * Nullable y sin valor por omision: un habito que ya existia no
         * empieza a avisar solo porque la app se actualice. Avisar es una
         * decision de quien lo creo, no un efecto de instalar una version.
         */
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habito ADD COLUMN horaRecordatorio INTEGER")
            }
        }

        @Volatile private var instancia: OllinDatabase? = null

        /**
         * La base va cifrada con AES-256 (SQLCipher). La frase la guarda
         * [LlaveBase] envuelta en el Keystore, asi que el archivo .db no sirve
         * de nada fuera de este telefono: copiarlo por adb o sacarlo de un
         * respaldo no revela un solo registro.
         *
         * No hay camino sin cifrar. Si SQLCipher no arranca, la app no abre: es
         * preferible a que una bitacora personal quede en claro sin avisar.
         */
        fun obten(contexto: Context): OllinDatabase =
            instancia ?: synchronized(this) {
                instancia ?: construye(contexto.applicationContext).also { instancia = it }
            }

        private fun construye(app: Context): OllinDatabase {
            System.loadLibrary("sqlcipher")
            val frase = LlaveBase.frase(app)

            return Room.databaseBuilder(app, OllinDatabase::class.java, NOMBRE)
                .openHelperFactory(SupportOpenHelperFactory(frase.toByteArray(Charsets.UTF_8)))
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRACION_1_2)
                .build()
        }
    }
}
