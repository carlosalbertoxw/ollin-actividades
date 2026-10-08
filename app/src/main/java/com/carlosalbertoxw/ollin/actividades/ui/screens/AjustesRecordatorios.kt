/*
 * El aviso de Ajustes cuando el sistema no deja sonar los recordatorios, y los
 * atajos a los ajustes de Android que lo arreglan.
 */
package com.carlosalbertoxw.ollin.actividades.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.AlarmaRecordatorios
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Notificaciones
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin

/**
 * Avisa de las dos cosas que pueden dejar un recordatorio sin sonar.
 *
 * Se ensena solo cuando pasa, y no como texto fijo: una advertencia permanente
 * sobre algo que casi siempre esta bien se deja de leer a la tercera vez.
 */
@Composable
internal fun RecordatoriosEnRiesgo(contexto: Context) {
    val colores = LocalColoresOllin.current

    // El estado se relee al volver al frente: los dos permisos se conceden en
    // los ajustes del sistema, o sea saliendo de Ollin y regresando.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    var puedeAvisar by remember { mutableStateOf(Notificaciones.sePuedeAvisar(contexto)) }
    var exactas by remember { mutableStateOf(AlarmaRecordatorios.puedeSerExacta(contexto)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        puedeAvisar = Notificaciones.sePuedeAvisar(contexto)
        exactas = AlarmaRecordatorios.puedeSerExacta(contexto)
    }

    if (!puedeAvisar) {
        Text(
            "Las notificaciones de Ollin están apagadas en los ajustes del teléfono, " +
                "así que no vas a ver ningún aviso.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = { contexto.abreAjustesDeLaApp() }) {
            Text("Abrir los ajustes de notificaciones")
        }
    }

    if (!exactas) {
        Text(
            "Sin permiso de alarmas exactas los avisos pueden llegar con unos minutos " +
                "de retraso, sobre todo con la pantalla apagada.",
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoTenue
        )
        TextButton(onClick = { contexto.abrePermisoDeAlarmas() }) {
            Text("Permitir alarmas exactas")
        }
    }
}
private fun Context.abreAjustesDeLaApp() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
private fun Context.abrePermisoDeAlarmas() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
