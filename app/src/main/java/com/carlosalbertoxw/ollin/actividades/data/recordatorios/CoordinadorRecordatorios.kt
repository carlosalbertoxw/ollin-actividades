package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.content.Context
import com.carlosalbertoxw.ollin.actividades.OllinApp
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
        if (!preferencias.recordatorios) {
            AlarmaRecordatorios.cancela(contexto)
            return
        }
        val discreto = preferencias.modoBloqueo != ModoBloqueo.NINGUNO

        val ahora = Tiempo.ahora()
        val desde = ahora.minus(Duration.ofHours(PlanificadorRecordatorios.RESCATE_HORAS))
        val hasta = ahora.plus(Duration.ofDays(PlanificadorRecordatorios.HORIZONTE_DIAS))

        val avisos = planificador.entre(desde, hasta)
        val (vencidos, futuros) = avisos.partition { !it.cuando.isAfter(ahora) }

        vencidos.forEach { Notificaciones.avisa(contexto, it, discreto) }

        val siguiente = futuros.firstOrNull()?.cuando
        if (siguiente != null) AlarmaRecordatorios.programa(contexto, siguiente)
        else AlarmaRecordatorios.cancela(contexto)
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
                ajustes.ajustes.map { it.recordatorios }.distinctUntilChanged()
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

        fun de(contexto: Context): CoordinadorRecordatorios =
            (contexto.applicationContext as OllinApp).contenedor.recordatorios

        /** Un alcance suelto para lo que dispara un BroadcastReceiver. */
        fun enSegundoPlano(contexto: Context, bloque: suspend () -> Unit) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { bloque() }
        }
    }
}
