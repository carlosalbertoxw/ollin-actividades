package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDao
import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.data.db.HabitoDao
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.domain.usecase.CalendarioHabito
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate

/** Un aviso concreto: que recordar y a que hora. */
data class Recordatorio(
    val clase: Clase,
    /** El id del habito o de la actividad, segun la clase. */
    val id: Long,
    val titulo: String,
    val detalle: String,
    val cuando: Instant
) {
    enum class Clase { HABITO, TAREA }

    /**
     * Id de la notificacion en el sistema.
     *
     * Lleva la clase dentro porque el habito 3 y la tarea 3 son cosas
     * distintas y con el id a secas una taparia a la otra. Es estable entre
     * procesos —`String.hashCode` esta especificado en Java— asi que volver a
     * avisar de lo mismo reemplaza la notificacion en vez de apilar otra.
     */
    val idNotificacion: Int get() = "${clase.name}:$id".hashCode()
}

/**
 * Decide que hay que recordar y cuando.
 *
 * Se calcula al vuelo en vez de guardar una tabla de avisos programados: lo que
 * toca depende del calendario del habito y de lo que ya se cumplio hoy, y
 * ambas cosas cambian sin avisar —al marcar, al editar la cadencia, al cruzar
 * la medianoche—. Una tabla habria que invalidarla en todos esos momentos y el
 * primero que se olvidara dejaria avisos fantasma.
 */
class PlanificadorRecordatorios(
    private val habitos: HabitoDao,
    private val actividades: ActividadDao
) {

    /**
     * Emite cada vez que cambia algo de lo que depende el plan.
     *
     * Las dos tablas y no solo la bitacora: cumplir un habito escribe en
     * `actividad`, pero moverle la hora o la cadencia escribe en `habito`, y
     * ese es justo el cambio que dejaria la alarma apuntando a la hora vieja.
     *
     * Se observa el conteo porque Room reemite ante cualquier escritura de la
     * tabla, valga o no lo mismo el numero. El valor sobra; interesa el aviso.
     */
    val cambios: Flow<Unit> =
        combine(habitos.observaConteo(), actividades.observaConteo()) { _, _ -> Unit }

    /**
     * Los avisos que caen dentro de la ventana, del mas proximo al mas lejano.
     *
     * [desde] inclusive y [hasta] exclusive. Se pide una ventana y no "el
     * siguiente" porque quien llama necesita las dos cosas: lo ya vencido para
     * notificarlo y lo venidero para programar la alarma, y las dos salen del
     * mismo recorrido.
     */
    suspend fun entre(desde: Instant, hasta: Instant): List<Recordatorio> =
        (deHabitos(desde, hasta) + deTareas(desde, hasta)).sortedBy { it.cuando }

    private suspend fun deHabitos(desde: Instant, hasta: Instant): List<Recordatorio> {
        val conHora = habitos.todos().filter { it.activo && it.horaRecordatorio != null }
        if (conHora.isEmpty()) return emptyList()

        val primerDia = Tiempo.dia(desde)
        val ultimoDia = Tiempo.dia(hasta)

        // La historia entera de una vez, y no una consulta por dia y por habito.
        // Con las cadencias que cuentan desde el ultimo cumplimiento el
        // calendario **depende** de lo cumplido, asi que ya no basta con
        // preguntar si hoy esta hecho: hay que saber cuando se hizo la vez
        // anterior para saber si hoy toca siquiera.
        val historia = actividades.cumplimientosDesde(primerDia.minusDays(VENTANA_HISTORIA))
            .groupBy { it.habitoId }

        val avisos = mutableListOf<Recordatorio>()

        conHora.forEach { habito ->
            val meta = habito.metaDiaria.coerceAtLeast(1)
            val porDia = historia[habito.id].orEmpty().associate { it.dia to it.veces }
            val cumplidos = porDia.filterValues { it >= meta }.keys

            // Los dias en que toca exactamente, no los que esta pendiente: un
            // habito vencido lo esta todos los dias hasta que se haga, y avisar
            // cada uno seria una campana diaria por un solo olvido.
            val fechas = CalendarioHabito.fechasEn(habito, cumplidos, primerDia, ultimoDia)

            fechas.forEach { dia ->
                val momento = Tiempo.instante(dia.atTime(habito.horaRecordatorio))
                if (momento >= desde && momento < hasta && (porDia[dia] ?: 0) < meta) {
                    avisos += aviso(habito, momento)
                }
            }
        }
        return avisos
    }

    /**
     * Un habito que ya se cumplio ese dia no avisa. Avisar de lo hecho es la
     * forma mas rapida de que alguien apague los recordatorios enteros; se
     * comprueba arriba, contra la misma historia que sirve para el calendario.
     */
    private fun aviso(habito: Habito, momento: Instant) = Recordatorio(
        clase = Recordatorio.Clase.HABITO,
        id = habito.id,
        titulo = habito.nombre,
        detalle = habito.minutosSugeridos
            ?.let { "Te toca hoy · ${it} min" }
            ?: "Te toca hoy",
        cuando = momento
    )

    /**
     * Las tareas avisan a su hora de inicio, que es la que ya tenian: una
     * actividad pendiente es justamente algo agendado para un momento.
     */
    private suspend fun deTareas(desde: Instant, hasta: Instant): List<Recordatorio> =
        actividades.pendientesEntre(desde, hasta.minusMillis(1)).map { tarea ->
            Recordatorio(
                clase = Recordatorio.Clase.TAREA,
                id = tarea.id,
                titulo = tarea.titulo,
                detalle = "Lo tenías agendado para las ${Tiempo.hora(tarea.inicio)}",
                cuando = tarea.inicio
            )
        }

    companion object {
        /**
         * Cuanto se mira hacia adelante al buscar el siguiente aviso.
         *
         * Un habito "cada tres meses" puede no tocar en mucho tiempo, y sin un
         * horizonte generoso su alarma no se programaria nunca. Con uno acotado
         * el recorrido sigue siendo barato: son unos cientos de dias por habito
         * con hora puesta, y solo cuando algo cambia.
         */
        const val HORIZONTE_DIAS = 120L

        /**
         * Cuanto hacia atras se rescata un aviso que no llego a sonar.
         *
         * El telefono apagado, una alarma diferida en doze o una actualizacion
         * de la app dejan huecos. Avisar de algo de esta manana todavia sirve;
         * de anteayer, no: seria ruido por algo que ya no se puede hacer.
         */
        const val RESCATE_HORAS = 6L

        /**
         * Cuanta historia de cumplimientos se lee para armar el calendario.
         *
         * Con las cadencias que cuentan desde el ultimo cumplimiento hace falta
         * saber cuando fue esa ultima vez, y un habito "cada tres meses" puede
         * tenerla lejos. Un ano cubre de sobra cualquier intervalo razonable sin
         * traerse la bitacora entera en cada replanificacion.
         */
        const val VENTANA_HISTORIA = 400L
    }
}
