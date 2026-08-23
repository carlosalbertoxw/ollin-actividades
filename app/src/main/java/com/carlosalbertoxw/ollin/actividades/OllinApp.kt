package com.carlosalbertoxw.ollin.actividades

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Notificaciones
import com.carlosalbertoxw.ollin.actividades.di.Contenedor

class OllinApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        alcance.launch {
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
        }
    }
}
