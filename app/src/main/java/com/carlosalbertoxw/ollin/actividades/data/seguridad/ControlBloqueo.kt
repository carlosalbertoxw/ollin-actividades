package com.carlosalbertoxw.ollin.actividades.data.seguridad

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo

/**
 * Decide cuando Ollin esta cerrada con llave.
 *
 * Vive en el contenedor y no en un ViewModel porque debe sobrevivir a que la
 * actividad se recree: si el estado se perdiera al girar el telefono, girarlo
 * seria la forma de saltarse el candado.
 */
class ControlBloqueo(ajustes: AjustesRepositorio) {

    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Arranca bloqueada a proposito. Todavia no se sabe si hay candado puesto,
     * y equivocarse hacia el lado cerrado solo cuesta un parpadeo; hacia el
     * lado abierto ensena tu bitacora a quien no debia.
     */
    private val _bloqueado = MutableStateFlow(true)
    val bloqueado: StateFlow<Boolean> = _bloqueado.asStateFlow()

    private var modo = ModoBloqueo.NINGUNO
    private var salidaEnMillis: Long? = null

    /**
     * Cierto mientras Ollin espera que el sistema le devuelva algo —el selector
     * de archivos, el dialogo de credencial— y por eso su marcha al fondo no
     * cuenta como salir de la app.
     */
    private var vueltaEsperada = false

    init {
        ambito.launch {
            ajustes.ajustes.collect { preferencias ->
                modo = preferencias.modoBloqueo
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false
            }
        }
    }

    fun desbloquea() {
        _bloqueado.value = false
        salidaEnMillis = null
        vueltaEsperada = false
    }

    /**
     * Avisa de que lo siguiente que va a mandar Ollin al fondo es un dialogo del
     * sistema del que se espera volver: el selector de archivos al importar o
     * exportar, o la peticion de credencial. Solo esas salidas tienen gracia.
     */
    fun esperaVueltaDelSistema() {
        vueltaEsperada = true
    }

    fun alIrAlFondo() {
        if (!_bloqueado.value) salidaEnMillis = SystemClock.elapsedRealtime()
    }

    /**
     * Se usa el reloj monotono y no la hora del sistema: cambiar la hora del
     * telefono no debe poder alargar la gracia.
     *
     * Sin una vuelta esperada la gracia es cero y Ollin se cierra en cuanto
     * sale al fondo, que es justo el caso que el candado quiere cubrir: pulsar
     * Inicio y pasarle el telefono a alguien. El minuto existe solo porque
     * elegir un .xlsx en el selector del sistema te expulsaria de la app a
     * medio camino, y ese permiso se pide expresamente y se gasta al usarlo.
     */
    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        val gracia = if (vueltaEsperada) GRACIA_MILLIS else 0L
        vueltaEsperada = false
        if (modo == ModoBloqueo.NINGUNO) return
        if (SystemClock.elapsedRealtime() - salida >= gracia) _bloqueado.value = true
    }

    companion object {
        /** Lo que se le concede a un viaje de ida y vuelta al sistema. */
        const val GRACIA_MILLIS = 60_000L
    }
}
