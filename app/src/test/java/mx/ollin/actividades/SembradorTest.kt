package mx.ollin.actividades

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import mx.ollin.actividades.data.db.Categoria
import mx.ollin.actividades.data.db.OllinDatabase
import mx.ollin.actividades.data.db.Sembrador
import mx.ollin.actividades.data.db.Semilla
import mx.ollin.actividades.domain.model.Ambito
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El catalogo inicial corre en cada arranque. Si dejara de ser idempotente,
 * abrir la app dos veces duplicaria las dieciseis categorias.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SembradorTest {

    private lateinit var db: OllinDatabase
    private lateinit var sembrador: Sembrador

    @Before
    fun abre() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OllinDatabase::class.java
        ).allowMainThreadQueries().build()
        sembrador = Sembrador(db.categoriaDao())
    }

    @After
    fun cierra() = db.close()

    @Test
    fun `la primera vez deja el catalogo completo y en orden`() = runTest {
        sembrador.sembrarSiHaceFalta()

        val catalogo = db.categoriaDao().todas()
        assertEquals(Semilla.CATEGORIAS.size, catalogo.size)
        assertEquals(Semilla.CATEGORIAS.map { it.nombre }.toSet(), catalogo.map { it.nombre }.toSet())
        // El orden es el de la plantilla: agrupa por ambito y eso se ve al elegir.
        assertEquals(Semilla.CATEGORIAS.first().nombre, catalogo.minByOrNull { it.orden }!!.nombre)
    }

    @Test
    fun `los cuatro ambitos quedan representados`() = runTest {
        sembrador.sembrarSiHaceFalta()

        val ambitos = db.categoriaDao().todas().map { it.ambito }.toSet()
        assertEquals(Ambito.entries.toSet(), ambitos)
    }

    @Test
    fun `todas nacen con color, para que la analitica no pinte huecos`() = runTest {
        sembrador.sembrarSiHaceFalta()

        assertTrue(db.categoriaDao().todas().all { !it.colorHex.isNullOrBlank() })
    }

    @Test
    fun `sembrar dos veces no duplica nada`() = runTest {
        sembrador.sembrarSiHaceFalta()
        sembrador.sembrarSiHaceFalta()

        assertEquals(Semilla.CATEGORIAS.size, db.categoriaDao().cuenta())
    }

    /**
     * Quien ya borro casi todo el catalogo no quiere que se lo devuelvan al
     * abrir la app: basta con que quede una categoria para no volver a sembrar.
     */
    @Test
    fun `con una sola categoria propia ya no siembra`() = runTest {
        db.categoriaDao().inserta(Categoria(nombre = "La mia", ambito = Ambito.PERSONAL))

        sembrador.sembrarSiHaceFalta()

        assertEquals(1, db.categoriaDao().cuenta())
        assertEquals("La mia", db.categoriaDao().todas().single().nombre)
    }
}
