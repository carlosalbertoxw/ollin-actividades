package com.carlosalbertoxw.ollin.actividades.data.seguridad

import android.os.SystemClock
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decide cuando Ollin esta cerrada con llave.
 *
 * Vive en el contenedor y no en un ViewModel porque debe sobrevivir a que la
 * actividad se recree: si el estado se perdiera al girar el telefono, girarlo
 * seria la forma de saltarse el candado.
 *
 * Todo PIN que se teclea en la app pasa por [intentaPin], tambien el que pide
 * Ajustes antes de cambiarlo o quitarlo. Si cada pantalla lo comprobara por su
 * cuenta, la que se olvidara del freno seria el atajo para adivinarlo.
 */
class ControlBloqueo(
    private val ajustes: AjustesRepositorio,
    /** Sella las huellas del PIN. En la app es [LlaveDelPin]; las pruebas pasan uno propio. */
    private val sello: ClavePin.Sello = LlaveDelPin
) {

    /** En que quedo un intento de PIN. */
    sealed interface IntentoDePin {
        data object Correcto : IntentoDePin
        data object Incorrecto : IntentoDePin

        /** Ni se comprobo: todavia corre la espera de los fallos anteriores. */
        data class EnEspera(val segundos: Int) : IntentoDePin
    }

    /**
     * `Main` a secas y no `Main.immediate`.
     *
     * Con `immediate`, construir el control desde el hilo principal ejecuta el
     * colector de preferencias **dentro del constructor**, sin llegar a
     * suspender, siempre que el DataStore pueda servir de cache. Cuando eso
     * pasa y no hay candado puesto, `bloqueado` ya sale en falso antes de que
     * el constructor retorne, y "arranca bloqueada" —lo que evita ensenar la
     * bitacora en el parpadeo previo a saber si hay llave— deja de ser una
     * garantia para pasar a depender de si la cache estaba caliente.
     */
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
     * Instante del reloj monotono hasta el que no se admite otro intento de PIN.
     * Las pantallas que piden el PIN leen de aqui la cuenta atras.
     */
    private val _esperaHasta = MutableStateFlow(0L)
    val esperaHasta: StateFlow<Long> = _esperaHasta.asStateFlow()

    /**
     * Un intento a la vez. Sin esto, dos toques seguidos leerian los dos que no
     * hay espera antes de que el primero registrara su fallo.
     */
    private val turno = Mutex()

    /**
     * Cierto mientras Ollin espera que el sistema le devuelva algo —el selector
     * de archivos, el dialogo de credencial— y por eso su marcha al fondo no
     * cuenta como salir de la app.
     */
    private var vueltaEsperada = false

    init {
        ambito.launch {
            var primeraLectura = true
            ajustes.ajustes.collect { preferencias ->
                modo = preferencias.modoBloqueo
                if (modo == ModoBloqueo.NINGUNO) _bloqueado.value = false

                // La espera vive en el reloj monotono, que no se guarda: al
                // arrancar se vuelve a cobrar entera la que tocan los fallos que
                // si estan en disco. Asi cerrar la app no regala un intento, y no
                // hay hora guardada que enganar cambiando la del telefono. Solo
                // en la primera lectura: las siguientes son el eco de los fallos
                // que [intentaPin] ya cobro.
                if (primeraLectura) {
                    primeraLectura = false
                    val pendiente = ClavePin.esperaSegundos(preferencias.pinFallos)
                    if (pendiente > 0) _esperaHasta.value = ahora() + pendiente * 1_000L
                }
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

    // ------------------------------------------------------- intentos de PIN

    /** Segundos que faltan para poder volver a intentar. Cero si ya se puede. */
    fun segundosDeEspera(): Int {
        val falta = _esperaHasta.value - ahora()
        return if (falta <= 0) 0 else ((falta + 999) / 1_000).toInt()
    }

    /**
     * Comprueba [pin] contra la huella guardada, respetando la espera.
     *
     * El fallo se apunta en disco antes de contestar: si el proceso muere justo
     * despues, lo que no puede perderse es la cuenta. Acertar contra una huella
     * de antes del sello la vuelve a guardar sellada, sin pedirle nada a nadie;
     * si guardarla falla se deja como estaba y se reintenta en el siguiente
     * acierto.
     */
    suspend fun intentaPin(pin: String): IntentoDePin = turno.withLock {
        val espera = segundosDeEspera()
        if (espera > 0) return@withLock IntentoDePin.EnEspera(espera)

        val actuales = ajustes.ajustes.first()
        val sal = actuales.pinSal
        when (ClavePin.verifica(pin, actuales.pinHash, sal, sello)) {
            ClavePin.Verificacion.INCORRECTO -> {
                ajustes.sumaFalloPin()
                val segundos = ClavePin.esperaSegundos(actuales.pinFallos + 1)
                _esperaHasta.value = if (segundos > 0) ahora() + segundos * 1_000L else 0L
                return@withLock IntentoDePin.Incorrecto
            }

            ClavePin.Verificacion.CORRECTO_SIN_SELLAR -> runCatching {
                // La sal no es nula: sin ella no se acierta.
                ajustes.guardaHuellaPin(ClavePin.huella(pin, sal!!, sello), sal)
            }

            ClavePin.Verificacion.CORRECTO -> Unit
        }
        ajustes.limpiaFallosPin()
        _esperaHasta.value = 0L
        desbloquea()
        IntentoDePin.Correcto
    }

    /** La huella y la sal de un PIN nuevo, ya selladas, listas para guardar. */
    suspend fun huellaNueva(pin: String): Pair<String, String> {
        val sal = ClavePin.nuevaSal()
        return ClavePin.huella(pin, sal, sello) to sal
    }

    private fun ahora(): Long = SystemClock.elapsedRealtime()

    companion object {
        /** Lo que se le concede a un viaje de ida y vuelta al sistema. */
        const val GRACIA_MILLIS = 60_000L
    }
}
