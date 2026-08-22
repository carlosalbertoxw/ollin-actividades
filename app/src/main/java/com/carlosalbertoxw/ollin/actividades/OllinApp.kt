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
        }
    }
}
