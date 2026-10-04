package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carlosalbertoxw.ollin.actividades.MainActivity
import com.carlosalbertoxw.ollin.actividades.R

/**
 * El canal y el envio de los avisos.
 *
 * Un canal y no dos —habitos y tareas juntos— porque quien quiera silenciar
 * "los recordatorios de Ollin" los quiere silenciar todos, y partirlo obligaria
 * a apagar dos cosas para conseguir una.
 */
object Notificaciones {

    const val CANAL = "recordatorios"

    fun creaCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(
            CANAL,
            "Recordatorios",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de los hábitos y las tareas que tienes pendientes."
        }
        contexto.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    /** Cierto si el sistema nos deja avisar. Desde Android 13 es un permiso. */
    fun sePuedeAvisar(contexto: Context): Boolean {
        val permitido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitido && NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    }

    /**
     * Publica un aviso. Si el permiso no esta, no hace nada y no revienta: el
     * planificador corre en segundo plano y no tiene a quien preguntarle.
     */
    // El permiso se comprueba en sePuedeAvisar(), justo arriba, pero Lint no
    // sigue la llamada hasta ahi. Y aunque la siguiera no bastaria: entre la
    // comprobacion y el envio el permiso puede revocarse, que es lo que cubre
    // el runCatching del final.
    @SuppressLint("MissingPermission")
    fun avisa(contexto: Context, recordatorio: Recordatorio, discreto: Boolean) {
        if (!sePuedeAvisar(contexto)) return
        creaCanal(contexto)

        val abrir = PendingIntent.getActivity(
            contexto,
            recordatorio.idNotificacion,
            Intent(contexto, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Lo que ensena la pantalla de bloqueo cuando la persona eligio en
        // Android "ocultar contenido sensible": sin version publica, el sistema
        // pondria un "contenido oculto" sin decir de que app. Con el ajuste de
        // fabrica ("mostrar todo") Android ignora esta version y ensena el aviso
        // completo; por eso, con candado puesto, el aviso ya viene sin nombres
        // desde [textoVisible].
        val publico = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_recordatorio)
            .setContentTitle(contexto.getString(R.string.app_name))
            .setContentText("Tienes un aviso pendiente.")
            .build()

        val (titulo, detalle) = textoVisible(recordatorio, discreto)
        val aviso = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(R.drawable.ic_recordatorio)
            .setContentTitle(titulo)
            .setContentText(detalle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Con candado puesto, el contenido no se ensena en la pantalla de
            // bloqueo. Seria incoherente marcar la ventana con FLAG_SECURE para
            // que la bitacora no salga ni en las apps recientes y a la vez
            // anunciar "Terapia, te toca hoy" a quien mire el telefono encima
            // de la mesa. Se ve que hay un aviso de Ollin, no de que.
            .setVisibility(
                if (discreto) {
                    NotificationCompat.VISIBILITY_PRIVATE
                } else {
                    NotificationCompat.VISIBILITY_PUBLIC
                }
            )
            .setPublicVersion(publico)
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()

        // El try no sobra: entre la comprobacion y el envio el permiso puede
        // haberse revocado, y eso lanza SecurityException.
        runCatching {
            NotificationManagerCompat.from(contexto).notify(recordatorio.idNotificacion, aviso)
        }
    }

    /**
     * Titulo y texto con que sale el aviso.
     *
     * [discreto] va encendido cuando la app tiene candado. `VISIBILITY_PRIVATE`
     * no basta para callar el contenido: Android solo lo oculta en la pantalla
     * de bloqueo si la persona eligio "ocultar contenido sensible", que no es
     * lo que viene de fabrica, y la cortina se puede bajar sin desbloquear.
     * Quien le puso candado a su bitacora no deberia encontrarse "Terapia" en
     * la pantalla del telefono, asi que el aviso dice que hay algo pendiente y
     * nada mas.
     *
     * El respaldo y la version nueva salen igual con candado: no dicen nada de
     * lo que hay en la bitacora.
     */
    fun textoVisible(recordatorio: Recordatorio, discreto: Boolean): Pair<String, String> {
        if (!discreto) return recordatorio.titulo to recordatorio.detalle
        return when (recordatorio.clase) {
            Recordatorio.Clase.HABITO ->
                "Tienes un hábito pendiente" to "Abre Ollin Actividades para verlo."

            Recordatorio.Clase.TAREA ->
                "Tienes una tarea por empezar" to "Abre Ollin Actividades para verla."

            Recordatorio.Clase.RESPALDO, Recordatorio.Clase.VERSION ->
                recordatorio.titulo to recordatorio.detalle
        }
    }
}
