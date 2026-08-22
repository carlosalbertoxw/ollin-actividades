package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Despierta cuando suena la alarma y cuando el telefono termina de arrancar.
 *
 * El arranque hace falta porque el sistema borra todas las alarmas al apagarse:
 * sin esto, reiniciar el telefono dejaria a Ollin muda hasta que alguien la
 * abriera.
 */
class ReceptorRecordatorios : BroadcastReceiver() {

    override fun onReceive(contexto: Context, intent: Intent) {
        val app = contexto.applicationContext
        // Se toma antes de saltar al hilo de fondo: pasado onReceive, el
        // sistema puede matar el proceso, y goAsync() es lo que lo retiene.
        val pendiente = goAsync()
        CoordinadorRecordatorios.enSegundoPlano(app) {
            withContext(Dispatchers.IO) { CoordinadorRecordatorios.de(app).despacha() }
            pendiente.finish()
        }
    }
}
