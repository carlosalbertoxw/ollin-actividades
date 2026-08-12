package mx.ollin.actividades.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import mx.ollin.actividades.data.seguridad.LlaveBase
import mx.ollin.actividades.data.seguridad.MigraCifrado
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
         * Llegan las cadencias periodicas: cada tantos dias y cada tantos meses.
         * Son tres columnas nuevas en `habito` y ninguna toca lo que ya habia,
         * asi que basta agregarlas con su valor por omision.
         *
         * `ancla` admite nulos a proposito: para los habitos que ya existian
         * significa "cuenta desde el dia en que te di de alta", que es lo que
         * resuelve [Habito.anclaEfectiva] sin tener que inventarles una fecha.
         */
        internal val DE_1_A_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `habito` ADD COLUMN `intervaloDias` INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE `habito` ADD COLUMN `intervaloMeses` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `habito` ADD COLUMN `ancla` INTEGER")
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
            // Antes de que Room toque el archivo: las instalaciones anteriores
            // al cifrado tienen la base en claro y hay que convertirla.
            MigraCifrado.migraSiHaceFalta(app, NOMBRE, frase)

            return Room.databaseBuilder(app, OllinDatabase::class.java, NOMBRE)
                .openHelperFactory(SupportOpenHelperFactory(frase.toByteArray(Charsets.UTF_8)))
                // Las claves foraneas evitan que quede una actividad apuntando a
                // una categoria borrada.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(DE_1_A_2)
                .build()
        }
    }
}
