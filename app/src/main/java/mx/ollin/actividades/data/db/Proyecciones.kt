package mx.ollin.actividades.data.db

import androidx.room.Embedded
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad
import java.time.LocalDate

/**
 * Lo que devuelven las consultas con join o con agregacion. Son solo de
 * lectura: nadie las inserta, nadie las modifica.
 */

/** Una actividad con el nombre de su categoria y de su habito ya resueltos. */
data class ActividadDetallada(
    @Embedded val actividad: Actividad,
    val categoriaNombre: String?,
    val categoriaColor: String?,
    val categoriaAmbito: Ambito?,
    val habitoNombre: String?
)

data class TotalCategoria(
    val categoriaId: Long?,
    val nombre: String?,
    val colorHex: String?,
    val ambito: Ambito?,
    val minutos: Int,
    val conteo: Int
)

data class TotalDia(
    val dia: LocalDate,
    val minutos: Int,
    val conteo: Int
)

data class TotalAmbito(
    val ambito: Ambito?,
    val minutos: Int,
    val conteo: Int
)

data class ConteoEstado(
    val estado: EstadoActividad,
    val conteo: Int
)

/** Un dia en el que se cumplio un habito, con cuantas veces. Alimenta la racha. */
data class CumplimientoDia(
    val dia: LocalDate,
    val veces: Int,
    val minutos: Int
)

/** Cumplimiento de un habito en un dia. Con esto se arma la racha de todos. */
data class CumplimientoHabito(
    val habitoId: Long,
    val dia: LocalDate,
    val veces: Int,
    val minutos: Int
)
