package mx.ollin.actividades

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import mx.ollin.actividades.di.Contenedor

class OllinApp : Application() {

    lateinit var contenedor: Contenedor
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        contenedor = Contenedor(this)
        alcance.launch { contenedor.sembrador.sembrarSiHaceFalta() }
    }
}
