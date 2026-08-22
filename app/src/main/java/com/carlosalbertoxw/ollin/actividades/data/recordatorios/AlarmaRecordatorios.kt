package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/**
 * Programa el despertador del sistema para el proximo aviso.
 *
 * **Una sola alarma viva a la vez**, la del recordatorio mas cercano. Al sonar
 * se vuelve a planificar y se arma la siguiente. La alternativa —una alarma por
 * recordatorio— obliga a llevar la cuenta de cuales estan puestas para poder
 * cancelarlas cuando un habito cambia de hora o se cumple, y esa contabilidad
 * es justo donde aparecen los avisos fantasma.
 */
object AlarmaRecordatorios {

    private const val CODIGO = 4001
    const val ACCION = "com.carlosalbertoxw.ollin.actividades.RECORDATORIO"

    fun programa(contexto: Context, cuando: Instant) {
        val gestor = contexto.getSystemService(AlarmManager::class.java) ?: return
        val disparo = cuando.toEpochMilli()

        // Exacta si el sistema lo permite; si no, aproximada en vez de nada.
        // Desde Android 12 la alarma exacta es un permiso que hay que conceder
        // a mano, y una app de bitacora no puede darlo por hecho. Un aviso con
        // unos minutos de retraso sigue sirviendo; uno que no llega, no.
        if (puedeSerExacta(gestor)) {
            runCatching {
                gestor.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, intento(contexto))
            }.onFailure { gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, intento(contexto)) }
        } else {
            gestor.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, disparo, intento(contexto))
        }
    }

    fun cancela(contexto: Context) {
        contexto.getSystemService(AlarmManager::class.java)?.cancel(intento(contexto))
    }

    fun puedeSerExacta(gestor: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || gestor.canScheduleExactAlarms()

    fun puedeSerExacta(contexto: Context): Boolean =
        contexto.getSystemService(AlarmManager::class.java)?.let(::puedeSerExacta) ?: false

    private fun intento(contexto: Context): PendingIntent = PendingIntent.getBroadcast(
        contexto,
        CODIGO,
        Intent(contexto, ReceptorRecordatorios::class.java).setAction(ACCION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
