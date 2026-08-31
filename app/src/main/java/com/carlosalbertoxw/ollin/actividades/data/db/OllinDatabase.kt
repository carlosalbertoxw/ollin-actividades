package com.carlosalbertoxw.ollin.actividades.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carlosalbertoxw.ollin.actividades.data.seguridad.LlaveBase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * La base. Tres tablas, cifrada, y una sola instancia por proceso.
 *
 * La version y el camino para llegar a ella viven en [Migraciones], no aqui:
 * asi no se puede subir un numero en un archivo y olvidar el paso que lo
 * acompana en otro.
 */
@Database(
    entities = [
        Categoria::class,
        Habito::class,
        Actividad::class
    ],
    version = Migraciones.VERSION,
    exportSchema = true
)
@TypeConverters(Convertidores::class)
abstract class OllinDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun habitoDao(): HabitoDao
    abstract fun actividadDao(): ActividadDao

    companion object {
        private const val NOMBRE = "ollin_actividades.db"

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
                // Sin `fallbackToDestructiveMigration`, a proposito: si falta
                // un paso, Room se niega a abrir en vez de borrar la bitacora.
                // Un arranque que falla se arregla; una bitacora borrada no.
                .addMigrations(*Migraciones.TODAS)
                .build()
        }
    }
}
