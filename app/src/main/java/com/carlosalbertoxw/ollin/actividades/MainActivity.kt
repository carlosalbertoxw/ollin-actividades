package com.carlosalbertoxw.ollin.actividades

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.ui.OllinRaiz
import com.carlosalbertoxw.ollin.actividades.ui.screens.BloqueoPantalla
import com.carlosalbertoxw.ollin.actividades.ui.theme.TemaOllin

/**
 * FragmentActivity y no ComponentActivity: el dialogo de huella y credencial
 * del sistema se monta sobre el gestor de fragmentos de la actividad.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as OllinApp
        val contenedor = app.contenedor
        val bloqueo = contenedor.controlBloqueo

        setContent {
            // null mientras no se leen las preferencias del disco. Distinguirlo
            // de "sin bloqueo" evita ensenar un candado a quien no lo puso, y de
            // paso quita el parpadeo de tema al abrir.
            val preferencias by contenedor.ajustes.ajustes
                .collectAsStateWithLifecycle(initialValue = null)
            val bloqueado by bloqueo.bloqueado.collectAsStateWithLifecycle()
            val fallo by app.arranqueFallido.collectAsStateWithLifecycle()

            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { bloqueo.alIrAlFondo() }
            LifecycleEventEffect(Lifecycle.Event.ON_START) { bloqueo.alVolverAlFrente() }

            // Con candado puesto se marca la ventana como segura: ni capturas de
            // pantalla ni miniatura en la vista de apps recientes, que es donde
            // el sistema deja tu bitacora a la vista de cualquiera. Mientras no
            // se sabe se asume que si: quitarlo de mas es peor que ponerlo.
            val protegerVentana = preferencias?.modoBloqueo?.let { it != ModoBloqueo.NINGUNO } ?: true
            LaunchedEffect(protegerVentana) {
                if (protegerVentana) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            TemaOllin(
                oscuro = preferencias?.temaOscuro ?: isSystemInDarkTheme(),
                colorDinamico = preferencias?.colorDinamico ?: false
            ) {
                val ajustes = preferencias
                val arranque = fallo
                when {
                    // Antes que nada, incluido el candado: si la base no abrio
                    // no hay bitacora que proteger, y esta pantalla no ensena
                    // ni un dato. Quedarse en el telon seria dejar a alguien
                    // mirando un fondo liso sin saber que ya no va a pasar nada.
                    arranque != null -> ArranqueFallido(arranque)

                    ajustes == null -> Telon()

                    bloqueado && ajustes.modoBloqueo != ModoBloqueo.NINGUNO -> BloqueoPantalla(
                        actividad = this,
                        ajustes = ajustes,
                        preferencias = contenedor.ajustes,
                        alDesbloquear = bloqueo::desbloquea
                    )

                    // No hay candado puesto, pero el control aun no lo sabe.
                    bloqueado -> Telon()

                    else -> OllinRaiz(contenedor)
                }
            }
        }
    }
}

/** Fondo liso mientras se decide si hay candado. Nunca muestra datos. */
@Composable
private fun Telon() {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
}

/**
 * Cuando la app no pudo arrancar.
 *
 * Lo normal en Ollin es que los mensajes escondan lo interno: el texto crudo de
 * una excepcion habla de rutas y clases, no le sirve a nadie y de paso cuenta
 * como esta hecha la app. Aqui la balanza se inclina al otro lado. La app no va
 * a funcionar, no hay ninguna otra pantalla desde la que enterarse de nada, y
 * sin una linea que copiar no hay forma de reportarlo ni de distinguir "se me
 * corrompio la base" de "esta version no abre en mi telefono". Se ensena el
 * tipo y el mensaje, que es una linea; el detalle completo va a logcat.
 */
@Composable
private fun ArranqueFallido(fallo: Throwable) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ollin no pudo abrir tu bitácora",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "La base de datos no se pudo abrir, así que la app se detuvo antes de " +
                    "tocar nada. Tus datos siguen donde estaban: no se ha borrado ni " +
                    "modificado nada.",
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Text(
                "Si acabas de instalar una versión encima de otra, lo más probable es " +
                    "que vengan de compilaciones distintas. Reinstalar desde el sitio " +
                    "suele resolverlo; desinstalar borra la bitácora, así que exporta " +
                    "antes a Excel si puedes abrir la versión anterior.",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Text(
                "${fallo::class.java.simpleName}: ${fallo.message.orEmpty().take(200)}",
                Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
