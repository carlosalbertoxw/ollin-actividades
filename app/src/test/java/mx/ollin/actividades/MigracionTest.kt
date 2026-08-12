package mx.ollin.actividades

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import mx.ollin.actividades.data.db.OllinDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La migracion a la version 2 corre sobre bases que ya tienen datos de alguien.
 * Si se equivoca, Room no abre y la bitacora queda inaccesible, asi que se
 * prueba contra el esquema real de la version 1.
 *
 * Se monta el SQLite de la plataforma a mano en vez de Room: aqui interesa el
 * SQL de la migracion, no el cifrado ni las entidades.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigracionTest {

    /** El `habito` tal como lo dejaba la version 1, copiado de schemas/1.json. */
    private val habitoV1 = """
        CREATE TABLE IF NOT EXISTS `habito` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `nombre` TEXT NOT NULL,
            `categoriaId` INTEGER,
            `frecuencia` TEXT NOT NULL,
            `metaDiaria` INTEGER NOT NULL,
            `metaSemanal` INTEGER NOT NULL,
            `diasSemana` INTEGER NOT NULL,
            `minutosSugeridos` INTEGER,
            `activo` INTEGER NOT NULL,
            `orden` INTEGER NOT NULL,
            `notas` TEXT,
            `creadoEn` INTEGER NOT NULL
        )
    """.trimIndent()

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun abre() {
        val contexto = ApplicationProvider.getApplicationContext<Application>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(contexto)
            // Sin nombre: la base vive en memoria y muere con la prueba.
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL(habitoV1)
                override fun onUpgrade(db: SupportSQLiteDatabase, viejo: Int, nuevo: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun cierra() = helper.close()

    private fun insertaHabitoV1(nombre: String) {
        db.execSQL(
            """
            INSERT INTO habito
                (nombre, categoriaId, frecuencia, metaDiaria, metaSemanal, diasSemana,
                 minutosSugeridos, activo, orden, notas, creadoEn)
            VALUES (?, NULL, 'DIARIA', 1, 5, 127, 20, 1, 0, NULL, 1754179200000)
            """.trimIndent(),
            arrayOf(nombre)
        )
    }

    private fun columnas(): Map<String, String> = buildMap {
        db.query("PRAGMA table_info(`habito`)").use { c ->
            val nombre = c.getColumnIndexOrThrow("name")
            val tipo = c.getColumnIndexOrThrow("type")
            while (c.moveToNext()) put(c.getString(nombre), c.getString(tipo))
        }
    }

    @Test
    fun `la migracion agrega las tres columnas de cadencia periodica`() {
        insertaHabitoV1("Leer")
        OllinDatabase.DE_1_A_2.migrate(db)

        val columnas = columnas()
        assertEquals("INTEGER", columnas["intervaloDias"])
        assertEquals("INTEGER", columnas["intervaloMeses"])
        assertEquals("INTEGER", columnas["ancla"])
    }

    @Test
    fun `los habitos que ya existian conservan sus datos y estrenan valores por omision`() {
        insertaHabitoV1("Leer")
        OllinDatabase.DE_1_A_2.migrate(db)

        db.query("SELECT nombre, frecuencia, minutosSugeridos, intervaloDias, intervaloMeses, ancla FROM habito")
            .use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Leer", c.getString(0))
                assertEquals("DIARIA", c.getString(1))
                assertEquals(20, c.getInt(2))
                // Los valores por omision del ALTER, iguales a los de la entidad.
                assertEquals(15, c.getInt(3))
                assertEquals(1, c.getInt(4))
                // Nulo a proposito: significa "cuenta desde que te di de alta".
                assertTrue(c.isNull(5))
                assertEquals(1, c.count)
            }
    }

    @Test
    fun `un habito insertado despues de migrar puede fijar su ancla`() {
        OllinDatabase.DE_1_A_2.migrate(db)
        db.execSQL(
            """
            INSERT INTO habito
                (nombre, categoriaId, frecuencia, metaDiaria, metaSemanal, diasSemana,
                 intervaloDias, intervaloMeses, ancla, minutosSugeridos, activo, orden, notas, creadoEn)
            VALUES ('Regar', NULL, 'CADA_DIAS', 1, 5, 127, 15, 1, 20669, NULL, 1, 0, NULL, 1754179200000)
            """.trimIndent()
        )

        db.query("SELECT frecuencia, intervaloDias, ancla FROM habito WHERE nombre = 'Regar'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("CADA_DIAS", c.getString(0))
            assertEquals(15, c.getInt(1))
            // El ancla se guarda como dia epoch, igual que el resto de las fechas.
            assertEquals(20669, c.getInt(2))
        }
    }

    @Test
    fun `la migracion no toca las demas tablas`() {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categoria` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL)")
        db.execSQL("INSERT INTO categoria (nombre) VALUES ('Lectura')")

        OllinDatabase.DE_1_A_2.migrate(db)

        db.query("SELECT nombre FROM categoria").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Lectura", c.getString(0))
        }
        assertNull(columnas()["columnaInventada"])
    }
}
