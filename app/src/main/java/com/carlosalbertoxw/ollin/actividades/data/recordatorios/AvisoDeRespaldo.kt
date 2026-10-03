package com.carlosalbertoxw.ollin.actividades.data.recordatorios

import android.os.SystemClock
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant

/**
 * El aviso de respaldo dentro de la app, arriba de Hoy.
 *
 * La notificacion semanal se pierde entre las demas, y una vez descartada ya
 * no vuelve hasta la semana siguiente. Este aviso sale cada vez que se abre la
 * app mientras toque respaldar, y se va solo en cuanto se exporta.
 *
 * Se puede quitar con su boton, pero solo por esta vez: "ahora no" no es
 * "nunca". Vuelve la siguiente vez que se abra la app, que aqui significa
 * arrancarla de cero o regresar despues de haber estado fuera mas de un
 * minuto. Es la misma gracia del candado, y por la misma razon: exportar o
 * importar abre el selector de archivos del sistema, que manda la app al
 * fondo, y volver de ahi no es abrirla otra vez.
 *
 * Quien no quiere el recordatorio lo apaga en Ajustes, y entonces no sale ni la
 * notificacion ni esto.
 *
 * Vive en el contenedor y no en el ViewModel de Hoy, porque tiene que
 * sobrevivir a que se gire el telefono o se cambie de pestaña: si no, quitarlo
 * duraria hasta el siguiente giro.
 */
class AvisoDeRespaldo(
    private val reloj: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val _descartado = MutableStateFlow(false)
    val descartado: StateFlow<Boolean> = _descartado.asStateFlow()

    private var salidaEnMillis: Long? = null

    fun descarta() {
        _descartado.value = true
    }

    fun alIrAlFondo() {
        salidaEnMillis = reloj()
    }

    fun alVolverAlFrente() {
        val salida = salidaEnMillis ?: return
        salidaEnMillis = null
        if (reloj() - salida >= AUSENCIA_MILLIS) _descartado.value = false
    }

    companion object {
        /** Cuanto hay que estar fuera para que volver cuente como abrir la app. */
        const val AUSENCIA_MILLIS = ControlBloqueo.GRACIA_MILLIS

        /**
         * El titulo del aviso, o nulo si no toca enseñarlo.
         *
         * Las reglas son las de la notificacion --el interruptor de Ajustes, el
         * plazo de [CoordinadorRecordatorios.PLAZO_RESPALDO_DIAS] y que haya
         * algo en la bitacora-- para que los dos avisos nunca se contradigan,
         * con una diferencia: el plazo cuenta solo desde el ultimo respaldo (o
         * el primer arranque), no desde la ultima notificacion. Esa marca
         * existe para no repetir la notificacion cada dia; aqui no hay nada que
         * repetir, y si contara, el aviso se apagaria justo cuando sale la
         * notificacion que el usuario descarto sin exportar.
         */
        fun texto(ajustes: Ajustes, hayBitacora: Boolean, descartado: Boolean, ahora: Instant): String? {
            if (descartado || !ajustes.avisaRespaldo || !hayBitacora) return null
            if (ajustes.respaldoDesde <= 0L) return null
            val vence = Instant.ofEpochMilli(ajustes.respaldoDesde)
                .plus(Duration.ofDays(CoordinadorRecordatorios.PLAZO_RESPALDO_DIAS))
            if (ahora.isBefore(vence)) return null
            return CoordinadorRecordatorios.tituloDelRespaldo(ajustes.ultimoRespaldo, ahora)
        }
    }
}
