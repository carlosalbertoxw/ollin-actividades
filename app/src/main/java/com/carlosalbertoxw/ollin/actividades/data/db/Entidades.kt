package com.carlosalbertoxw.ollin.actividades.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.DiasSemana
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Frecuencia
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.model.Unidad
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Las entidades de Room son tambien el modelo de dominio. Con un solo modulo,
 * duplicarlas en otra capa solo agrega mapeo sin ganancia.
 */

@Entity(
    tableName = "categoria",
    indices = [Index(value = ["nombre"], unique = true), Index("ambito")]
)
data class Categoria(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    /** Decide en que analitica suma y que icono se le pone. */
    val ambito: Ambito,
    val colorHex: String? = null,
    val archivada: Boolean = false,
    val orden: Int = 0
)

/**
 * El registro. Es la unica tabla que crece con el uso diario.
 *
 * Sobre las marcas de tiempo: [inicio] y [fin] son instantes UTC, [dia] es el
 * dia local del inicio y [duracionMinutos] es el resultado ya calculado. Se
 * guardan los tres aunque uno se derive de los otros: agrupar por dia local en
 * SQL exigiria aplicar el huso en cada consulta, y recalcular la duracion en
 * cada suma impide usar un indice. Quien escribe paga una vez lo que quien lee
 * pagaria siempre.
 */
@Entity(
    tableName = "actividad",
    indices = [
        Index("dia"),
        Index("inicio"),
        Index("estado"),
        Index("categoriaId"),
        Index("habitoId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Habito::class,
            parentColumns = ["id"],
            childColumns = ["habitoId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Actividad(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** La accion concreta: "Reunion de diseno", "Correr 5 km". */
    val titulo: String,
    val categoriaId: Long? = null,
    val estado: EstadoActividad = EstadoActividad.PENDIENTE,
    /** Cuando empezo. Si esta pendiente, cuando se planea empezar. */
    val inicio: Instant,
    /** Nulo mientras corre o mientras solo esta agendada. */
    val fin: Instant? = null,
    /** Dia local de [inicio]. Derivado, nunca se captura. */
    val dia: LocalDate,
    /**
     * Minutos de esfuerzo. Se llena al cerrar la actividad, pero admite
     * captura a mano: quien registra una carrera de ayer sabe que duro 40
     * minutos aunque ya no pueda cronometrarla.
     */
    val duracionMinutos: Int? = null,
    /** Medida opcional del esfuerzo que el reloj no ve. */
    val cantidad: Double? = null,
    val unidad: Unidad = Unidad.NINGUNA,
    /** Si viene de un habito, aqui esta el vinculo que alimenta la racha. */
    val habitoId: Long? = null,
    val notas: String? = null,
    val creadoEn: Long = System.currentTimeMillis(),
    val actualizadoEn: Long = System.currentTimeMillis()
) {

    /**
     * Minutos que lleva la actividad en este momento. Mientras corre se cuenta
     * contra el reloj; ya cerrada devuelve lo guardado.
     */
    fun minutosVividos(ahora: Instant = Tiempo.ahora()): Int = when {
        duracionMinutos != null -> duracionMinutos
        estado == EstadoActividad.EN_CURSO ->
            ((ahora.toEpochMilli() - inicio.toEpochMilli()) / 60_000L)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        else -> 0
    }

    /** Segundos transcurridos, para el cronometro de la pantalla de hoy. */
    fun segundosVividos(ahora: Instant = Tiempo.ahora()): Long =
        ((ahora.toEpochMilli() - inicio.toEpochMilli()) / 1000L).coerceAtLeast(0L)
}

/**
 * La plantilla de un habito. No guarda cumplimientos: esos son actividades
 * completadas que apuntan aqui. Asi la racha y el historial salen de la misma
 * tabla que todo lo demas, y un habito cumplido se puede cronometrar igual que
 * cualquier otra actividad.
 */
@Entity(
    tableName = "habito",
    indices = [Index(value = ["nombre"], unique = true), Index("activo"), Index("categoriaId")],
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Habito(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val categoriaId: Long? = null,
    val frecuencia: Frecuencia = Frecuencia.DIARIA,
    /** Cuantas veces al dia cuenta como cumplido. Casi siempre una. */
    val metaDiaria: Int = 1,
    /** Solo aplica a [Frecuencia.SEMANAL]: cuantos dias de la semana. */
    val metaSemanal: Int = 5,
    /** Mapa de bits de dias, ver [DiasSemana]. Solo aplica a DIAS_ELEGIDOS. */
    val diasSemana: Int = DiasSemana.TODOS,
    /** Cada cuantos dias vuelve a tocar. Solo aplica a [Frecuencia.CADA_DIAS]. */
    val intervaloDias: Int = 15,
    /** Cada cuantos meses vuelve a tocar. Solo aplica a [Frecuencia.CADA_MESES]. */
    val intervaloMeses: Int = 1,
    /**
     * Dia desde el que se cuenta el ciclo de las frecuencias periodicas. Nulo
     * significa el dia en que se creo el habito, que es lo que casi siempre se
     * quiere: se empieza a contar cuando lo diste de alta.
     */
    val ancla: LocalDate? = null,
    /** Duracion sugerida al marcarlo desde la pantalla de hoy. */
    val minutosSugeridos: Int? = null,
    val activo: Boolean = true,
    val orden: Int = 0,
    val notas: String? = null,
    val creadoEn: Long = System.currentTimeMillis()
) {

    /** El ancla efectiva: la elegida, o el dia en que nacio el habito. */
    fun anclaEfectiva(): LocalDate = ancla ?: Tiempo.dia(Instant.ofEpochMilli(creadoEn))

    /**
     * La fecha de la enesima repeticion contada desde [desde].
     *
     * Siempre se calcula desde el ancla y no encadenando saltos, porque
     * `plusMonths` recorta al ultimo dia del mes: un habito anclado al 31
     * cae en el 28 de febrero, pero el de marzo debe volver al 31, no
     * quedarse en el 28 para siempre.
     */
    fun ocurrencia(desde: LocalDate, indice: Long): LocalDate = when (frecuencia) {
        Frecuencia.CADA_MESES -> desde.plusMonths(indice * intervaloMeses.coerceAtLeast(1))
        else -> desde.plusDays(indice * intervaloDias.coerceAtLeast(1))
    }

    /**
     * Como se lee la cadencia. Vive en la entidad y no en la pantalla porque la
     * lista de habitos y la exportacion a Excel la escriben igual, y dos copias
     * de esta frase acabarian contradiciendose.
     */
    fun cadencia(): String = when (frecuencia) {
        Frecuencia.DIARIA -> "Todos los dias"
        Frecuencia.DIAS_ELEGIDOS -> DiasSemana.etiqueta(diasSemana)
        Frecuencia.SEMANAL -> "$metaSemanal dias por semana"
        Frecuencia.CADA_DIAS -> when (val n = intervaloDias.coerceAtLeast(1)) {
            1 -> "Todos los dias"
            7 -> "Cada semana"
            // Quince dias es la cadencia mas pedida y tiene nombre propio.
            15 -> "Cada quincena"
            else -> "Cada $n dias"
        }
        Frecuencia.CADA_MESES -> when (val n = intervaloMeses.coerceAtLeast(1)) {
            1 -> "Cada mes"
            2 -> "Cada 2 meses"
            3 -> "Cada trimestre"
            6 -> "Cada semestre"
            12 -> "Cada ano"
            else -> "Cada $n meses"
        }
    }

    /** Cierto si el habito toca este dia. Un habito semanal toca todos. */
    fun tocaHoy(dia: LocalDate): Boolean = when (frecuencia) {
        Frecuencia.DIARIA -> true
        Frecuencia.SEMANAL -> true
        Frecuencia.DIAS_ELEGIDOS -> DiasSemana.contiene(diasSemana, dia.dayOfWeek)

        Frecuencia.CADA_DIAS -> {
            val inicio = anclaEfectiva()
            !dia.isBefore(inicio) &&
                ChronoUnit.DAYS.between(inicio, dia) % intervaloDias.coerceAtLeast(1) == 0L
        }

        Frecuencia.CADA_MESES -> {
            val inicio = anclaEfectiva()
            if (dia.isBefore(inicio)) {
                false
            } else {
                // Se pregunta por la ocurrencia y no por el dia del mes: asi el
                // recorte de los meses cortos se decide en un solo lugar.
                val paso = intervaloMeses.coerceAtLeast(1)
                val meses = ChronoUnit.MONTHS.between(
                    inicio.withDayOfMonth(1),
                    dia.withDayOfMonth(1)
                )
                meses % paso == 0L && ocurrencia(inicio, meses / paso) == dia
            }
        }
    }
}
