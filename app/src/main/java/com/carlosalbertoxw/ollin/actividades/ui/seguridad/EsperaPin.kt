package com.carlosalbertoxw.ollin.actividades.ui.seguridad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import kotlinx.coroutines.delay

/**
 * Segundos que faltan para poder volver a probar el PIN, o cero si se puede ya.
 *
 * La cuenta atras arranca cuando la pantalla aparece y no en el momento del
 * fallo. Suena mas laxo y es justo al reves: matar la app para escaparse de la
 * espera la reinicia entera, porque el contador de fallos si esta en disco. No
 * hay reloj que guardar, y por lo tanto tampoco hay reloj que enganar
 * cambiando la hora del telefono ni reiniciando.
 *
 * La usan la pantalla de bloqueo y el dialogo de ajustes que pide el PIN actual
 * antes de cambiarlo o quitarlo. Las dos son puertas al mismo sitio: dejar una
 * sin freno equivaldria a no tener ninguno.
 */
@Composable
fun segundosDeEsperaPin(fallos: Int): Int {
    var restantes by remember(fallos) { mutableIntStateOf(ClavePin.esperaSegundos(fallos)) }
    LaunchedEffect(fallos) {
        while (restantes > 0) {
            delay(1_000)
            restantes--
        }
    }
    return restantes
}

/** "Espera 1:05" / "Espera 20 s", para el boton mientras corre el castigo. */
fun textoDeEspera(segundos: Int): String = when {
    segundos >= 60 -> "Espera %d:%02d".format(segundos / 60, segundos % 60)
    else -> "Espera $segundos s"
}
