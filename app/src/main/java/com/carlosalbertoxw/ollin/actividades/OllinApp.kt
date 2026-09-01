package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Notificaciones
import com.carlosalbertoxw.ollin.actividades.di.Contenedor

class OllinApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _arranqueFallido = MutableStateFlow<Throwable?>(null)

    /**
     * Lo que impidio arrancar, o nulo si todo fue bien.
     *
     * Lo observa [MainActivity] para ensenar una pantalla que lo explique. Sin
     * esto, una excepcion aqui salia del `launch`, llegaba al manejador por
     * defecto del hilo y mataba el proceso: la app se cerraba nada mas abrirse,
     * sin dialogo, sin mensaje y sin nada que contarle a nadie.
     */
    val arranqueFallido: StateFlow<Throwable?> = _arranqueFallido.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)

        alcance.launch {
            try {
                // Se toca la base a proposito antes que nada. Abrirla carga la
                // biblioteca nativa de SQLCipher y desenvuelve la frase del
                // Keystore, y el `by lazy` del contenedor es sincronizado: quien
                // llegue primero paga la espera y los demas se quedan esperandolo.
                // Sin esta linea, ese primero podia ser un ViewModel en el hilo
                // principal, y entonces el arranque daba un tiron —en un telefono
                // lento, el suficiente para que Android se queje—.
                contenedor.baseDeDatos
                contenedor.sembrador.sembrarSiHaceFalta()

                // El canal se crea al arrancar y no al primer aviso: asi los
                // ajustes de notificaciones del sistema ensenan "Recordatorios"
                // desde el principio, y no solo despues de que suene el primero.
                Notificaciones.creaCanal(this@OllinApp)

                // Aqui se enciende el motor. La alarma del siguiente aviso se
                // programa dentro de despacha(), y despacha() solo corre si algo
                // observa: sin esta linea no hay primera alarma, el receptor no
                // despierta nunca y no suena nada por muy encendido que este el
                // interruptor de Ajustes.
                contenedor.recordatorios.vigila(alcance)
            } catch (cancelacion: CancellationException) {
                // No es un fallo: es que el alcance se cerro. Se deja pasar,
                // porque atraparla rompe la cancelacion de las corrutinas.
                throw cancelacion
            } catch (fallo: Throwable) {
                // Throwable y no Exception: si SQLCipher no carga, lo que llega
                // es un UnsatisfiedLinkError, y ese es justo el caso en que la
                // app no puede hacer nada y tiene que decirlo bien.
                Log.e(BITACORA, "El arranque no pudo completarse", fallo)
                _arranqueFallido.value = fallo
                return@launch
            }

            // Y de paso se mira si hay version nueva. Va al final y envuelto:
            // es lo unico del arranque que depende de la red, y quedarse sin
            // señal no puede impedir que la app abra. El propio comprobador
            // decide si toca —una vez al dia— y si el interruptor lo permite.
            runCatching { contenedor.actualizaciones.compruebaSiToca() }
        }
    }

    companion object {
        const val BITACORA = "OllinActividades"
    }
}
