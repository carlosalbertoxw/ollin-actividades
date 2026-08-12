package mx.ollin.actividades.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import java.time.LocalDate

/**
 * Nota sobre fechas: `dia` es dia epoch y `inicio`/`fin` son milisegundos
 * epoch, asi que los rangos se comparan con enteros y usan indice. No hace
 * falta ninguna funcion de fecha de SQLite.
 *
 * Nota sobre minutos: en las sumas se usa COALESCE porque una actividad
 * pendiente todavia no tiene duracion, y sumar nulos vaciaria el total.
 */

@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categoria WHERE archivada = 0 ORDER BY orden, nombre")
    fun observaActivas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria ORDER BY archivada, orden, nombre")
    fun observaTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categoria ORDER BY archivada, orden, nombre")
    suspend fun todas(): List<Categoria>

    @Query("SELECT * FROM categoria WHERE id = :id")
    suspend fun porId(id: Long): Categoria?

    @Query("SELECT COUNT(*) FROM categoria")
    suspend fun cuenta(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserta(categoria: Categoria): Long

    @Update
    suspend fun actualiza(categoria: Categoria)

    @Delete
    suspend fun elimina(categoria: Categoria)
}

@Dao
interface HabitoDao {

    @Query("SELECT * FROM habito WHERE activo = 1 ORDER BY orden, nombre")
    fun observaActivos(): Flow<List<Habito>>

    @Query("SELECT * FROM habito ORDER BY activo DESC, orden, nombre")
    fun observaTodos(): Flow<List<Habito>>

    @Query("SELECT * FROM habito ORDER BY activo DESC, orden, nombre")
    suspend fun todos(): List<Habito>

    @Query("SELECT * FROM habito WHERE id = :id")
    suspend fun porId(id: Long): Habito?

    @Query("SELECT COUNT(*) FROM habito")
    suspend fun cuenta(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserta(habito: Habito): Long

    @Update
    suspend fun actualiza(habito: Habito)

    @Delete
    suspend fun elimina(habito: Habito)
}

@Dao
interface ActividadDao {

    /**
     * La lista con filtros. Cada filtro se anula solo cuando llega nulo, asi
     * que una sola consulta cubre todas las combinaciones de la pantalla sin
     * armar SQL a mano.
     */
    @Query(
        """
        SELECT a.*,
               c.nombre  AS categoriaNombre,
               c.colorHex AS categoriaColor,
               c.ambito  AS categoriaAmbito,
               h.nombre  AS habitoNombre
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        LEFT JOIN habito h ON h.id = a.habitoId
        WHERE (:categoriaId IS NULL OR a.categoriaId = :categoriaId)
          AND (:estado IS NULL OR a.estado = :estado)
          AND (:ambito IS NULL OR c.ambito = :ambito)
          AND (:desde IS NULL OR a.dia >= :desde)
          AND (:hasta IS NULL OR a.dia <= :hasta)
          AND (
                :texto = ''
                OR a.titulo LIKE '%' || :texto || '%'
                OR IFNULL(a.notas, '') LIKE '%' || :texto || '%'
              )
        ORDER BY a.inicio DESC
        LIMIT :limite
        """
    )
    fun observaDetalladas(
        texto: String = "",
        estado: EstadoActividad? = null,
        ambito: Ambito? = null,
        categoriaId: Long? = null,
        desde: LocalDate? = null,
        hasta: LocalDate? = null,
        limite: Int = 500
    ): Flow<List<ActividadDetallada>>

    @Query(
        """
        SELECT a.*,
               c.nombre  AS categoriaNombre,
               c.colorHex AS categoriaColor,
               c.ambito  AS categoriaAmbito,
               h.nombre  AS habitoNombre
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        LEFT JOIN habito h ON h.id = a.habitoId
        WHERE a.dia = :dia
        ORDER BY a.inicio DESC
        """
    )
    fun observaDelDia(dia: LocalDate): Flow<List<ActividadDetallada>>

    /**
     * La actividad que corre ahora mismo. Solo puede haber una: al arrancar
     * otra, el repositorio cierra la anterior.
     */
    @Query(
        """
        SELECT a.*,
               c.nombre  AS categoriaNombre,
               c.colorHex AS categoriaColor,
               c.ambito  AS categoriaAmbito,
               h.nombre  AS habitoNombre
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        LEFT JOIN habito h ON h.id = a.habitoId
        WHERE a.estado = 'EN_CURSO'
        ORDER BY a.inicio DESC
        LIMIT 1
        """
    )
    fun observaEnCurso(): Flow<ActividadDetallada?>

    /** Lo pendiente que ya deberia haber empezado o empieza hoy. */
    @Query(
        """
        SELECT a.*,
               c.nombre  AS categoriaNombre,
               c.colorHex AS categoriaColor,
               c.ambito  AS categoriaAmbito,
               h.nombre  AS habitoNombre
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        LEFT JOIN habito h ON h.id = a.habitoId
        WHERE a.estado = 'PENDIENTE' AND a.dia <= :hasta
        ORDER BY a.inicio
        LIMIT :limite
        """
    )
    fun observaPendientes(hasta: LocalDate, limite: Int = 50): Flow<List<ActividadDetallada>>

    @Query("SELECT * FROM actividad WHERE id = :id")
    suspend fun porId(id: Long): Actividad?

    /** Toda la bitacora, para exportarla. Ordenar aqui evita hacerlo en memoria. */
    @Query("SELECT * FROM actividad ORDER BY dia, inicio, id")
    suspend fun todas(): List<Actividad>

    @Query("SELECT * FROM actividad WHERE estado = 'EN_CURSO'")
    suspend fun enCurso(): List<Actividad>

    @Query("SELECT COUNT(*) FROM actividad")
    suspend fun cuenta(): Int

    @Query("SELECT COUNT(*) FROM actividad")
    fun observaConteo(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserta(actividad: Actividad): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertaTodas(actividades: List<Actividad>)

    @Update
    suspend fun actualiza(actividad: Actividad)

    @Delete
    suspend fun elimina(actividad: Actividad)

    /** Solo la usa la importacion que reemplaza. Nada en la interfaz la alcanza. */
    @Query("DELETE FROM actividad")
    suspend fun eliminaTodas()

    // ---- Analitica ----

    @Query(
        """
        SELECT a.categoriaId AS categoriaId,
               c.nombre      AS nombre,
               c.colorHex    AS colorHex,
               c.ambito      AS ambito,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos,
               COUNT(a.id)   AS conteo
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        WHERE a.dia BETWEEN :desde AND :hasta AND a.estado = 'COMPLETADO'
        GROUP BY a.categoriaId
        ORDER BY minutos DESC
        """
    )
    fun observaTotalPorCategoria(desde: LocalDate, hasta: LocalDate): Flow<List<TotalCategoria>>

    @Query(
        """
        SELECT a.dia AS dia,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos,
               COUNT(a.id) AS conteo
        FROM actividad a
        WHERE a.dia BETWEEN :desde AND :hasta AND a.estado = 'COMPLETADO'
        GROUP BY a.dia
        ORDER BY a.dia
        """
    )
    fun observaTotalPorDia(desde: LocalDate, hasta: LocalDate): Flow<List<TotalDia>>

    @Query(
        """
        SELECT c.ambito AS ambito,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos,
               COUNT(a.id) AS conteo
        FROM actividad a
        LEFT JOIN categoria c ON c.id = a.categoriaId
        WHERE a.dia BETWEEN :desde AND :hasta AND a.estado = 'COMPLETADO'
        GROUP BY c.ambito
        ORDER BY minutos DESC
        """
    )
    fun observaTotalPorAmbito(desde: LocalDate, hasta: LocalDate): Flow<List<TotalAmbito>>

    @Query(
        """
        SELECT a.estado AS estado, COUNT(a.id) AS conteo
        FROM actividad a
        WHERE a.dia BETWEEN :desde AND :hasta
        GROUP BY a.estado
        """
    )
    fun observaConteoPorEstado(desde: LocalDate, hasta: LocalDate): Flow<List<ConteoEstado>>

    /** Suma de la medida (km, repeticiones) de una unidad en un rango. */
    @Query(
        """
        SELECT COALESCE(SUM(a.cantidad), 0.0)
        FROM actividad a
        WHERE a.dia BETWEEN :desde AND :hasta
          AND a.estado = 'COMPLETADO'
          AND a.unidad = :unidad
        """
    )
    fun observaSumaDeUnidad(unidad: String, desde: LocalDate, hasta: LocalDate): Flow<Double>

    // ---- Habitos ----

    /**
     * Dias en que se cumplio un habito, del mas reciente al mas viejo. El
     * calculo de la racha se hace en Kotlin: necesita saber que dias tocaba el
     * habito, y eso vive en la plantilla, no en la tabla de registros.
     */
    @Query(
        """
        SELECT a.dia AS dia,
               COUNT(a.id) AS veces,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos
        FROM actividad a
        WHERE a.habitoId = :habitoId AND a.estado = 'COMPLETADO'
        GROUP BY a.dia
        ORDER BY a.dia DESC
        LIMIT :limite
        """
    )
    fun observaCumplimientos(habitoId: Long, limite: Int = 400): Flow<List<CumplimientoDia>>

    /**
     * Cumplimientos de todos los habitos por dia. Una sola consulta alimenta
     * los checks de hoy y las rachas de la pantalla de habitos; pedirlas habito
     * por habito serviria la misma tabla N veces.
     */
    @Query(
        """
        SELECT a.habitoId AS habitoId,
               a.dia AS dia,
               COUNT(a.id) AS veces,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos
        FROM actividad a
        WHERE a.habitoId IS NOT NULL
          AND a.estado = 'COMPLETADO'
          AND a.dia >= :desde
        GROUP BY a.habitoId, a.dia
        ORDER BY a.dia DESC
        """
    )
    fun observaCumplimientosDesde(desde: LocalDate): Flow<List<CumplimientoHabito>>

    @Query(
        """
        SELECT a.dia AS dia,
               COUNT(a.id) AS veces,
               CAST(COALESCE(SUM(a.duracionMinutos), 0) AS INTEGER) AS minutos
        FROM actividad a
        WHERE a.habitoId IS NOT NULL
          AND a.estado = 'COMPLETADO'
          AND a.dia BETWEEN :desde AND :hasta
        GROUP BY a.dia
        ORDER BY a.dia
        """
    )
    fun observaCumplimientoGlobal(desde: LocalDate, hasta: LocalDate): Flow<List<CumplimientoDia>>

    /** La ultima vez que se registro este habito, para poder deshacer el check. */
    @Query(
        """
        SELECT * FROM actividad
        WHERE habitoId = :habitoId AND dia = :dia AND estado = 'COMPLETADO'
        ORDER BY inicio DESC
        LIMIT 1
        """
    )
    suspend fun ultimoCumplimiento(habitoId: Long, dia: LocalDate): Actividad?
}
