package com.carlosalbertoxw.ollin.actividades.domain.usecase

import com.carlosalbertoxw.ollin.actividades.data.db.Habito
import com.carlosalbertoxw.ollin.actividades.domain.model.ModoCiclo
import java.time.LocalDate

/**
 * Cuando toca un habito periodico.
 *
 * Vive aparte de la entidad porque dejo de ser una funcion de la plantilla. Con
 * [ModoCiclo.DESDE_ULTIMO] el calendario depende de **lo que se cumplio**: la
 * fecha siguiente se cuenta desde el ultimo cumplimiento, no desde el ancla, y
 * eso es historia que `Habito` no tiene ni debe tener. `Habito.tocaHoy` sigue
 * sirviendo para las cadencias pegadas al calendario semanal, que si son puras.
 *
 * Los dos modos comparten el resto de la maquinaria: las ocurrencias salen de
 * aqui en los dos casos, y quien pregunta no tiene que saber cual es cual.
 */
object CalendarioHabito {

    /**
     * Tope de repeticiones que se recorren. Evita recorrer el calendario entero
     * cuando un ancla quedo muy atras.
     */
    private const val LIMITE_OCURRENCIAS = 600

    /**
     * Las fechas en que toco, del ancla a [hasta], ambas incluidas.
     *
     * En [ModoCiclo.CALENDARIO] son `ancla + n × intervalo`, siempre calculadas
     * desde el ancla y no encadenando saltos: `plusMonths` recorta al ultimo dia
     * del mes, y un habito anclado al 31 que cae en el 28 de febrero debe volver
     * al 31 en marzo, no quedarse en el 28 para siempre.
     *
     * En [ModoCiclo.DESDE_ULTIMO] cada fecha nace del cumplimiento anterior. La
     * lista **se corta en la primera ocurrencia sin cumplir**, y tiene que ser
     * asi: mientras eso siga pendiente no hay desde donde contar la siguiente.
     * Un habito cada quince dias que lleva dos meses sin hacerse tiene una sola
     * ocurrencia vencida, no cuatro.
     */
    fun ocurrencias(
        habito: Habito,
        cumplidos: Set<LocalDate>,
        hasta: LocalDate
    ): List<LocalDate> {
        val ancla = habito.anclaEfectiva()
        if (ancla.isAfter(hasta)) return emptyList()

        val lista = mutableListOf<LocalDate>()
        var fecha = ancla

        while (!fecha.isAfter(hasta) && lista.size < LIMITE_OCURRENCIAS) {
            lista += fecha
            fecha = siguienteTras(habito, ancla, lista.size, fecha, cumplidos) ?: break
        }
        return lista
    }

    /**
     * La ocurrencia que sigue a la que empieza en [actual], o nula si todavia
     * no se puede saber.
     */
    private fun siguienteTras(
        habito: Habito,
        ancla: LocalDate,
        yaContadas: Int,
        actual: LocalDate,
        cumplidos: Set<LocalDate>
    ): LocalDate? = when (habito.modoCiclo) {
        ModoCiclo.CALENDARIO -> habito.ocurrencia(ancla, yaContadas.toLong())

        ModoCiclo.DESDE_ULTIMO -> cumplidoDesde(cumplidos, actual)
            ?.let { habito.ocurrencia(it, 1) }
    }

    /** El primer cumplimiento en [desde] o despues. Nulo si aun no lo hay. */
    private fun cumplidoDesde(cumplidos: Set<LocalDate>, desde: LocalDate): LocalDate? =
        cumplidos.filter { !it.isBefore(desde) }.minOrNull()

    /**
     * Los dias, dentro de la ventana, en que **toca exactamente**.
     *
     * Es lo que usan los recordatorios, y a proposito no es [pendienteEl]. Un
     * habito vencido esta pendiente todos los dias hasta que se haga, y avisar
     * todos esos dias convierte un olvido en una campana diaria: quien se
     * retrasa una semana con algo trimestral recibe siete avisos identicos y
     * acaba apagando los recordatorios enteros, que es exactamente lo que la
     * app intenta no provocar. Vencido se **ve** en la pantalla de Hoy y en la
     * lista de habitos; se **avisa** solo el dia que toca.
     *
     * Contando desde el ultimo cumplimiento hay como mucho una fecha futura
     * —las siguientes dependen de cuando se cumpla esta—, y eso tambien es lo
     * correcto: no se puede programar una alarma para una fecha que aun no
     * existe.
     */
    fun fechasEn(
        habito: Habito,
        cumplidos: Set<LocalDate>,
        desde: LocalDate,
        hasta: LocalDate
    ): Set<LocalDate> = if (habito.frecuencia.esPeriodica) {
        ocurrencias(habito, cumplidos, hasta).filterTo(mutableSetOf()) { !it.isBefore(desde) }
    } else {
        generateSequence(desde) { it.plusDays(1) }
            .takeWhile { !it.isAfter(hasta) }
            .filterTo(mutableSetOf()) { habito.tocaHoy(it) }
    }

    /**
     * Si el habito esta pendiente ese dia: toco ese dia, o toco antes y sigue
     * sin cumplirse.
     *
     * Esto es lo que ven las pantallas. Los recordatorios usan [fechasEn], que
     * solo marca el dia exacto: ver algo vencido es util, que suene todos los
     * dias no.
     *
     * Lo segundo es la diferencia con `Habito.tocaHoy`, que solo era cierto el
     * dia exacto. Con eso, un habito cada tres meses que se pasaba un dia no
     * volvia a asomar por la pantalla de Hoy en tres meses: no se fallaba, se
     * perdia de vista, que es peor porque ni siquiera se sabe.
     *
     * La ventana de lo vencido termina donde empieza la siguiente ocurrencia.
     * En [ModoCiclo.CALENDARIO] eso llega solo; en [ModoCiclo.DESDE_ULTIMO] no
     * hay siguiente mientras esto no se haga, asi que se queda pendiente hasta
     * que se haga, que es exactamente lo que se pidio de ese modo.
     *
     * Las cadencias no periodicas se preguntan a la entidad: un habito diario
     * que no se hizo ayer no esta "vencido" hoy, esta fallado y ya, y arrastrar
     * eso a la pantalla de Hoy la llenaria de deudas que nadie puede pagar.
     */
    fun pendienteEl(habito: Habito, cumplidos: Set<LocalDate>, dia: LocalDate): Boolean {
        if (!habito.frecuencia.esPeriodica) return habito.tocaHoy(dia)

        val ultima = ocurrencias(habito, cumplidos, dia).lastOrNull() ?: return false
        return cumplidos.none { !it.isBefore(ultima) && !it.isAfter(dia) }
    }

    /**
     * La ocurrencia vigente ese dia, o nula si el habito aun no empieza.
     *
     * Sirve para decir *desde cuando* esta vencido algo, que es lo que hace que
     * "vencido" signifique algo distinto de "toca".
     */
    fun ocurrenciaVigente(
        habito: Habito,
        cumplidos: Set<LocalDate>,
        dia: LocalDate
    ): LocalDate? = ocurrencias(habito, cumplidos, dia).lastOrNull()
}
