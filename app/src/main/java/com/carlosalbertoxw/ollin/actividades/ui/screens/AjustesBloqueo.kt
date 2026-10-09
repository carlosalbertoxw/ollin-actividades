/*
 * El candado de Ajustes: elegir modo, confirmar la llave puesta y crear un PIN.
 * Vive aparte de AjustesPantalla.kt por tamano, no por capa: es la misma pantalla.
 */
package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.pedirCredencialDelSistema
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.segundosDeEsperaPin
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.telefonoAsegurado
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.textoDeEspera
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import kotlinx.coroutines.launch

/**
 * El candado de la app.
 *
 * Cambiar o quitar el bloqueo exige la llave que hay puesta ahora. Sin eso,
 * quien encuentre la app abierta la desprotege en dos toques y el candado solo
 * estorba a su dueno.
 */
@Composable
internal fun SeccionBloqueo(
    ajustes: Ajustes,
    alQuitar: () -> Unit,
    alUsarSistema: () -> Unit,
    alUsarPin: (String) -> Unit,
    /** Por donde pasa el PIN actual que se pide antes de cambiarlo o quitarlo. */
    bloqueo: ControlBloqueo,
    alSalirAlSistema: () -> Unit
) {
    val contexto = LocalContext.current
    val actividad = LocalActivity.current as? FragmentActivity
    val colores = LocalColoresOllin.current
    val modoActual = ajustes.modoBloqueo

    var pidiendoPinNuevo by remember { mutableStateOf(false) }
    var pidiendoPinActual by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    // Lo que se hara en cuanto confirmes que eres tu.
    var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

    val estaAsegurado = remember(contexto) { telefonoAsegurado(contexto) }

    Text("Bloqueo", style = MaterialTheme.typography.titleMedium)
    Text(
        "Ollin pide la llave al abrirse, y al volver de un viaje al selector de " +
            "archivos que haya tardado mas de un minuto.",
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )
    Spacer(Modifier.height(10.dp))

    // Sin actividad no hay donde montar el dialogo del sistema, asi que tampoco
    // hay forma de confirmar quien eres: mejor no ofrecer el candado.
    if (actividad == null) {
        Text(
            "El bloqueo no está disponible en esta pantalla.",
            style = MaterialTheme.typography.bodySmall,
            color = colores.textoTenue
        )
        return
    }

    val confirmaConSistema = pedirCredencialDelSistema(
        actividad = actividad,
        titulo = "Confirma que eres tú",
        alLograr = {
            pendiente?.invoke()
            pendiente = null
        },
        alFallar = {
            pendiente = null
            aviso = it
        },
        alSalirAlSistema = alSalirAlSistema
    )

    fun conConfirmacion(accion: () -> Unit) {
        aviso = null
        when (modoActual) {
            ModoBloqueo.NINGUNO -> accion()

            ModoBloqueo.SISTEMA -> {
                pendiente = accion
                confirmaConSistema()
            }

            ModoBloqueo.PIN -> {
                pendiente = accion
                pidiendoPinActual = true
            }
        }
    }

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ModoBloqueo.entries.forEachIndexed { i, modo ->
            SegmentedButton(
                selected = modoActual == modo,
                onClick = {
                    aviso = null
                    when (modo) {
                        ModoBloqueo.NINGUNO -> conConfirmacion(alQuitar)

                        ModoBloqueo.SISTEMA ->
                            if (estaAsegurado) {
                                conConfirmacion(alUsarSistema)
                            } else {
                                aviso = "Tu teléfono no tiene patrón, PIN ni contraseña. " +
                                    "Configúralo en los ajustes de Android y vuelve aquí."
                            }

                        ModoBloqueo.PIN -> conConfirmacion { pidiendoPinNuevo = true }
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(i, ModoBloqueo.entries.size),
                icon = {}
            ) {
                Text(
                    modo.etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    aviso?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(Modifier.height(8.dp))
    Text(
        when (modoActual) {
            ModoBloqueo.NINGUNO -> "Cualquiera que tome tu teléfono desbloqueado puede abrir Ollin."

            ModoBloqueo.SISTEMA ->
                "Se usa el patrón, PIN o huella con que desbloqueas el teléfono. " +
                    "Ollin no guarda ningún secreto."

            ModoBloqueo.PIN ->
                "Se usa un PIN solo de Ollin. Si lo olvidas no hay forma de " +
                    "recuperarlo: tendrías que reinstalar la app y perderías los datos."
        },
        style = MaterialTheme.typography.bodySmall,
        color = colores.textoTenue
    )

    if (modoActual == ModoBloqueo.PIN) {
        TextButton(
            onClick = { conConfirmacion { pidiendoPinNuevo = true } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cambiar el PIN") }
    }

    if (pidiendoPinActual) {
        DialogoPinActual(
            bloqueo = bloqueo,
            alConfirmar = {
                pidiendoPinActual = false
                pendiente?.invoke()
                pendiente = null
            },
            alCancelar = {
                pidiendoPinActual = false
                pendiente = null
            }
        )
    }

    if (pidiendoPinNuevo) {
        DialogoNuevoPin(
            alGuardar = { pin ->
                alUsarPin(pin)
                pidiendoPinNuevo = false
            },
            alCancelar = { pidiendoPinNuevo = false }
        )
    }
}

@Composable
private fun DialogoPinActual(
    bloqueo: ControlBloqueo,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    val espera = segundosDeEsperaPin(bloqueo)

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Confirma tu PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Escribe el PIN que tienes puesto para poder cambiarlo o quitarlo.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(ClavePin.LARGO_MAXIMO) },
                    label = { Text("PIN actual") },
                    singleLine = true,
                    isError = error != null,
                    enabled = espera == 0,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (espera > 0) {
                    Text(
                        "Demasiados intentos fallidos. Vuelve a probar en un momento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalColoresOllin.current.textoTenue
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length >= ClavePin.LARGO_MINIMO && !verificando && espera == 0,
                onClick = {
                    verificando = true
                    error = null
                    ambito.launch {
                        // `verificando` se suelta al final, con el intento ya
                        // resuelto: el mismo freno que la pantalla de bloqueo,
                        // con la misma cuenta de fallos y la misma espera.
                        try {
                            when (bloqueo.intentaPin(pin)) {
                                ControlBloqueo.IntentoDePin.Correcto -> alConfirmar()

                                ControlBloqueo.IntentoDePin.Incorrecto -> {
                                    error = "PIN incorrecto"
                                    pin = ""
                                }

                                is ControlBloqueo.IntentoDePin.EnEspera -> Unit
                            }
                        } finally {
                            verificando = false
                        }
                    }
                }
            ) {
                Text(
                    when {
                        espera > 0 -> textoDeEspera(espera)
                        verificando -> "Comprobando…"
                        else -> "Confirmar"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun DialogoNuevoPin(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }

    val corto = pin.length < ClavePin.LARGO_MINIMO
    val distintos = confirmacion.isNotEmpty() && pin != confirmacion

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("PIN de Ollin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Mínimo ${ClavePin.LARGO_MINIMO} dígitos. No se guarda tal cual: " +
                        "de él solo queda una huella de la que no se puede volver atrás.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(ClavePin.LARGO_MAXIMO) },
                    label = { Text("PIN nuevo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmacion,
                    onValueChange = {
                        confirmacion = it.filter(Char::isDigit).take(ClavePin.LARGO_MAXIMO)
                    },
                    label = { Text("Repítelo") },
                    singleLine = true,
                    isError = distintos,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (distintos) {
                    Text(
                        "Los dos PIN no coinciden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alGuardar(pin) },
                enabled = !corto && pin == confirmacion
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = alCancelar) { Text("Cancelar") }
        }
    )
}
