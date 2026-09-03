package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.content.Context
import com.carlosalbertoxw.ollin.actividades.OllinApp
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Une las tres piezas: mira que toca, avisa de lo vencido y arma la alarma de
 * lo siguiente.
 *
 * Es el unico punto por el que se replanifica, y se le llama desde todos los
 * sitios donde el plan puede haber quedado viejo: al abrir la app, al cambiar
 * un habito o una tarea, al sonar la alarma y al arrancar el telefono.
 */
class CoordinadorRecordatorios(
    private val contexto: Context,
    private val planificador: PlanificadorRecordatorios,
    private val ajustes: AjustesRepositorio
) {

    /**
     * Notifica lo que ya vencio y programa la alarma del siguiente.
     *
     * Lo vencido se rescata mirando unas horas hacia atras: el telefono apagado
     * o una alarma diferida en doze dejan huecos, y un habito de esta manana
     * todavia se puede cumplir. Mas atras no, que seria ruido.
     */
    suspend fun despacha() {
        val preferencias = ajustes.ajustes.first()
        val discreto = preferencias.modoBloqueo != ModoBloqueo.NINGUNO

        val ahora = Tiempo.ahora()
        val desde = ahora.minus(Duration.ofHours(PlanificadorRecordatorios.RESCATE_HORAS))
        val hasta = ahora.plus(Duration.ofDays(PlanificadorRecordatorios.HORIZONTE_DIAS))

        // Habitos y tareas van bajo el interruptor maestro. El respaldo no, y
        // es deliberado: son dos cosas distintas. Apagar los avisos de habitos
        // es decir "no me persigas con lo que me propuse"; el del respaldo es
        // lo unico que se interpone entre un telefono perdido y una bitacora
        // que no se puede recuperar de ningun lado, porque la llave vive en el
        // Keystore y no viaja. Tiene su propio interruptor.
        val deLaBitacora =
            if (preferencias.recordatorios) planificador.entre(desde, hasta) else emptyList()

        val avisos = (deLaBitacora + listOfNotNull(respaldoPendiente(preferencias, ahora, hasta)))
            .sortedBy { it.cuando }

        val (vencidos, futuros) = avisos.partition { !it.cuando.isAfter(ahora) }

        vencidos.forEach { aviso ->
            Notificaciones.avisa(contexto, aviso, discreto)
            // La marca se pone al avisar y no al respaldar: si no, un aviso
            // desatendido se repetiria en cada replanificacion, o sea varias
            // veces al dia.
            if (aviso.clase == Recordatorio.Clase.RESPALDO) ajustes.marcaAvisoDeRespaldo()
        }

        val siguiente = futuros.firstOrNull()?.cuando
        if (siguiente != null) AlarmaRecordatorios.programa(contexto, siguiente)
        else AlarmaRecordatorios.cancela(contexto)
    }

    /**
     * El recordatorio de respaldar, si toca y si hay algo que respaldar.
     *
     * El plazo cuenta desde el ultimo respaldo, o desde el ultimo aviso si
     * nadie le hizo caso, o desde el primer arranque si no hay ninguna de las
     * dos cosas. Ese ultimo caso es el que estrena el plazo: se guarda la fecha
     * en vez de avisar de inmediato, porque estrenar la app y recibir a los dos
     * minutos "respalda tu bitacora" no tiene ningun sentido.
     *
     * `internal` para poder probar el calendario del aviso sin levantar
     * AlarmManager ni el canal de notificaciones, que es sistema operativo.
     */
    internal suspend fun respaldoPendiente(
        preferencias: Ajustes,
        ahora: Instant,
        hasta: Instant
    ): Recordatorio? {
        if (!preferencias.avisaRespaldo) return null

        val ancla = maxOf(preferencias.respaldoDesde, preferencias.ultimoAvisoRespaldo)
        if (ancla == 0L) {
            ajustes.estrenaPlazoDeRespaldo(ahora.toEpochMilli())
            return null
        }

        if (!planificador.hayBitacora()) return null

        val vence = Instant.ofEpochMilli(ancla).plus(Duration.ofDays(PLAZO_RESPALDO_DIAS))
        // Vencido se avisa ya, sin la ventana de rescate de los habitos: este
        // no depende de una hora concreta, asi que llegar tarde no lo caduca.
        val cuando = if (vence.isBefore(ahora)) ahora else vence
        if (cuando.isAfter(hasta)) return null

        return Recordatorio(
            clase = Recordatorio.Clase.RESPALDO,
            id = 0,
            titulo = tituloDelRespaldo(preferencias.ultimoRespaldo, ahora),
            detalle = "Exporta a Excel desde Ajustes → Archivo. Es el único respaldo que hay.",
            cuando = cuando
        )
    }

    /**
     * El titulo lleva la cuenta de dias, no una formula.
     *
     * "Acuérdate de respaldar" deja de leerse a la tercera semana: no dice nada
     * que quien lo ve no sepa ya. "Tu último respaldo es de hace 21 días" si
     * mueve, porque pone delante el numero que uno no tenia en la cabeza. Va en
     * el titulo y no en el detalle porque es lo unico que se ve sin desplegar
     * la notificacion.
     *
     * Y quien no ha respaldado nunca no puede leer "hace N dias" de algo que no
     * existe, asi que ese caso tiene su propia frase.
     */
    private fun tituloDelRespaldo(ultimoRespaldo: Long, ahora: Instant): String {
        if (ultimoRespaldo <= 0L) return "Todavía no has respaldado tu bitácora"

        val dias = Duration.between(Instant.ofEpochMilli(ultimoRespaldo), ahora).toDays()
        return when {
            dias <= 0L -> "Tu último respaldo es de hoy"
            dias == 1L -> "Tu último respaldo es de ayer"
            else -> "Tu último respaldo es de hace $dias días"
        }
    }

    /**
     * Avisa de que hay version nueva, una sola vez por version.
     *
     * Va aqui y no en el comprobador porque es una notificacion, y el
     * comprobador no sabe de notificaciones: pregunta, compara y devuelve.
     *
     * El texto habla de respaldar y no solo de actualizar porque ese es el
     * momento en que mas importa: instalar un APK encima es justo cuando a
     * alguien le puede pasar algo con sus datos, y es el unico aviso que llega
     * **antes** de que sea tarde.
     */
    suspend fun avisaDeVersionNueva(version: String) {
        val preferencias = ajustes.ajustes.first()
        if (preferencias.versionAvisada == version) return

        Notificaciones.avisa(
            contexto,
            Recordatorio(
                clase = Recordatorio.Clase.VERSION,
                id = 0,
                titulo = "Hay una versión nueva de Ollin",
                detalle = "La $version ya está publicada. Respalda a Excel antes de instalarla.",
                cuando = Tiempo.ahora()
            ),
            discreto = preferencias.modoBloqueo != ModoBloqueo.NINGUNO
        )
        ajustes.marcaVersionAvisada(version)
    }

    /**
     * Replanifica sola cada vez que cambia algo que pueda mover un aviso.
     *
     * Observar es preferible a llamar a mano desde cada escritura: los sitios
     * que tocan habitos y actividades son muchos —captura, importacion,
     * deshacer, editar la cadencia— y el primero que se olvidara dejaria la
     * alarma apuntando a algo que ya no toca.
     *
     * Se llama una sola vez, al arrancar la aplicacion. El `combine` emite de
     * entrada, asi que abrir la app tambien replanifica: hace falta, porque la
     * alarma siguiente solo se arma dentro de [despacha].
     */
    fun vigila(alcance: CoroutineScope) {
        alcance.launch {
            combine(
                planificador.cambios,
                ajustes.ajustes
                    .map { it.recordatorios to it.avisaRespaldo }
                    .distinctUntilChanged()
            ) { _, _ -> Unit }
                .collectLatest {
                    // Un respiro antes de recalcular: una importacion escribe
                    // cientos de filas seguidas y no tiene sentido replanificar
                    // con cada una.
                    delay(REPOSO_MS)
                    runCatching { despacha() }
                }
        }
    }

    companion object {
        private const val REPOSO_MS = 700L

        /**
         * Cada cuanto se recuerda respaldar. Una semana: lo bastante seguido
         * para que lo perdido quepa en la cabeza, y lo bastante espaciado para
         * que no se vuelva ruido de fondo.
         */
        const val PLAZO_RESPALDO_DIAS = 7L

        fun de(contexto: Context): CoordinadorRecordatorios =
            (contexto.applicationContext as OllinApp).contenedor.recordatorios

        /** Un alcance suelto para lo que dispara un BroadcastReceiver. */
        fun enSegundoPlano(contexto: Context, bloque: suspend () -> Unit) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { bloque() }
        }
    }
}
