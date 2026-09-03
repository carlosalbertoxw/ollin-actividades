package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.ModoBloqueo
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.pedirCredencialDelSistema
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.segundosDeEsperaPin
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.textoDeEspera
import com.carlosalbertoxw.ollin.actividades.ui.seguridad.telefonoAsegurado
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Notificaciones
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.AlarmaRecordatorios
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.os.Build
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.Manifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesPantalla(
    contenedor: Contenedor,
    alAbrirCategorias: () -> Unit,
    alAbrirArchivo: () -> Unit,
    alAbrirAcercaDe: () -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("ajustes") { AjustesVm(contenedor.ajustes) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current
    val contexto = LocalContext.current

    // Encender los avisos y conceder el permiso son la misma intencion, asi que
    // van en el mismo gesto: si el sistema lo niega, el interruptor no se
    // enciende, porque quedaria prometiendo algo que no puede cumplir.
    // Que interruptor se encendera cuando vuelva la respuesta del permiso. Los
    // dos que notifican lo piden igual, y el launcher es uno solo.
    var alConcederPermiso by remember { mutableStateOf<(Boolean) -> Unit>({}) }

    val permisoNotificaciones = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> alConcederPermiso(concedido) }

    val pidePermisoNotificaciones = { encender: (Boolean) -> Unit ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Notificaciones.sePuedeAvisar(contexto)
        ) {
            alConcederPermiso = encender
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            encender(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Apariencia", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ajustes.temaOscuro == null,
                    onClick = { vm.tema(null) },
                    label = { Text("Sistema") }
                )
                FilterChip(
                    selected = ajustes.temaOscuro == false,
                    onClick = { vm.tema(false) },
                    label = { Text("Claro") }
                )
                FilterChip(
                    selected = ajustes.temaOscuro == true,
                    onClick = { vm.tema(true) },
                    label = { Text("Oscuro") }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Color del sistema", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Usa la paleta del fondo de pantalla en vez de la de Ollin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(checked = ajustes.colorDinamico, onCheckedChange = vm::colorDinamico)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(20.dp))

            Text("Metas del día", style = MaterialTheme.typography.titleMedium)
            Text(
                "La referencia de las barras de la pantalla de hoy. No es una calificación.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoMinutos(
                    etiqueta = "Trabajo",
                    minutos = ajustes.metaTrabajoMinutos,
                    alCambiar = vm::metaTrabajo,
                    modifier = Modifier.weight(1f)
                )
                CampoMinutos(
                    etiqueta = "Movimiento",
                    minutos = ajustes.metaFisicoMinutos,
                    alCambiar = vm::metaFisico,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            CampoMinutos(
                etiqueta = "Duración al marcar sin cronómetro",
                minutos = ajustes.duracionRapidaMinutos,
                alCambiar = vm::duracionRapida,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar lo completado en Hoy", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Apagado, la pantalla de hoy solo enseña lo que falta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.muestraCompletadasEnHoy,
                    onCheckedChange = vm::completadasEnHoy
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Un aviso por cada hábito que toque y no hayas cumplido, a la hora " +
                            "que le pusiste, y por cada tarea pendiente a su hora de inicio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.recordatorios,
                    onCheckedChange = { quiere ->
                        // El permiso se pide solo al encenderlo: preguntarlo al
                        // abrir Ajustes seria pedir algo que quiza nadie quiere.
                        if (quiere) pidePermisoNotificaciones(vm::recordatorios)
                        else vm.recordatorios(false)
                    }
                )
            }

            if (ajustes.recordatorios) {
                RecordatoriosEnRiesgo(contexto)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Recordarme respaldar", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Cada semana, si no has exportado. Tu bitácora vive cifrada con una " +
                            "llave que no se puede restaurar en otro teléfono: el .xlsx es el " +
                            "único respaldo que hay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.avisaRespaldo,
                    onCheckedChange = { quiere ->
                        if (quiere) pidePermisoNotificaciones(vm::avisaRespaldo)
                        else vm.avisaRespaldo(false)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Avisarme de versiones nuevas", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Una vez al día Ollin pregunta al sitio si salió una versión más " +
                            "nueva y te lo enseña en Acerca de. La pregunta no lleva nada " +
                            "tuyo dentro y nunca se descarga ni se instala nada sola.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(
                    checked = ajustes.buscarActualizaciones,
                    onCheckedChange = vm::buscarActualizaciones
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Ayuda", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mostrar tutoriales", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Las tarjetas de ayuda que abren cada pantalla. Cada una se puede " +
                            "cerrar por su cuenta; este interruptor las apaga todas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
                Switch(checked = ajustes.muestraTutoriales, onCheckedChange = vm::tutoriales)
            }

            // Solo cuando hay algo que restaurar: un boton que no haria nada
            // ocupa el mismo espacio y obliga a preguntarse para que sirve.
            if (!ajustes.muestraTutoriales || ajustes.tutorialesOcultos.isNotEmpty()) {
                TextButton(
                    onClick = { vm.reiniciaTutoriales() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Volver a mostrar todos los tutoriales") }
            }

            ListItem(
                headlineContent = { Text("Categorías") },
                supportingContent = { Text("Renombrar, cambiar de ámbito o archivar") },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = alAbrirCategorias)
            )

            ListItem(
                headlineContent = { Text("Archivo") },
                supportingContent = { Text("Exportar e importar tu bitácora en Excel (.xlsx)") },
                leadingContent = {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = alAbrirArchivo)
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(20.dp))

            SeccionBloqueo(
                ajustes = ajustes,
                alQuitar = { vm.quitaBloqueo() },
                alUsarSistema = { vm.usaBloqueoDelSistema() },
                alUsarPin = { vm.usaBloqueoConPin(it) },
                alFallarPin = { vm.sumaFalloPin() },
                alAcertarPin = { vm.limpiaFallosPin() },
                alSalirAlSistema = contenedor.controlBloqueo::esperaVueltaDelSistema
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Acerca de Ollin") },
                supportingContent = { Text("Versión, qué hace y qué pasa con tus datos") },
                leadingContent = {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = alAbrirAcercaDe)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * El candado de la app.
 *
 * Cambiar o quitar el bloqueo exige la llave que hay puesta ahora. Sin eso,
 * quien encuentre la app abierta la desprotege en dos toques y el candado solo
 * estorba a su dueno.
 */
@Composable
private fun SeccionBloqueo(
    ajustes: Ajustes,
    alQuitar: () -> Unit,
    alUsarSistema: () -> Unit,
    alUsarPin: (String) -> Unit,
    alFallarPin: () -> Unit,
    alAcertarPin: () -> Unit,
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
        alLograr = { pendiente?.invoke(); pendiente = null },
        alFallar = { pendiente = null; aviso = it },
        alSalirAlSistema = alSalirAlSistema
    )

    fun conConfirmacion(accion: () -> Unit) {
        aviso = null
        when (modoActual) {
            ModoBloqueo.NINGUNO -> accion()
            ModoBloqueo.SISTEMA -> { pendiente = accion; confirmaConSistema() }
            ModoBloqueo.PIN -> { pendiente = accion; pidiendoPinActual = true }
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
                            if (estaAsegurado) conConfirmacion(alUsarSistema)
                            else aviso = "Tu teléfono no tiene patrón, PIN ni contraseña. " +
                                "Configúralo en los ajustes de Android y vuelve aquí."
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
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(8.dp))
    Text(
        when (modoActual) {
            ModoBloqueo.NINGUNO -> "Cualquiera que tome tu teléfono desbloqueado puede abrir Ollin."
            ModoBloqueo.SISTEMA -> "Se usa el patrón, PIN o huella con que desbloqueas el teléfono. " +
                "Ollin no guarda ningún secreto."
            ModoBloqueo.PIN -> "Se usa un PIN solo de Ollin. Si lo olvidas no hay forma de " +
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
            ajustes = ajustes,
            alFallar = alFallarPin,
            alAcertar = alAcertarPin,
            alConfirmar = {
                pidiendoPinActual = false
                pendiente?.invoke()
                pendiente = null
            },
            alCancelar = { pidiendoPinActual = false; pendiente = null }
        )
    }

    if (pidiendoPinNuevo) {
        DialogoNuevoPin(
            alGuardar = { pin -> alUsarPin(pin); pidiendoPinNuevo = false },
            alCancelar = { pidiendoPinNuevo = false }
        )
    }
}

@Composable
private fun DialogoPinActual(
    ajustes: Ajustes,
    alFallar: () -> Unit,
    alAcertar: () -> Unit,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verificando by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    val espera = segundosDeEsperaPin(ajustes.pinFallos)

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
                        val correcto = ClavePin.coincide(pin, ajustes.pinHash, ajustes.pinSal)
                        verificando = false
                        if (correcto) {
                            alAcertar()
                            alConfirmar()
                        } else {
                            alFallar()
                            error = "PIN incorrecto"
                            pin = ""
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

@Composable
private fun CampoMinutos(
    etiqueta: String,
    minutos: Int,
    alCambiar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = minutos.toString(),
        onValueChange = { texto ->
            alCambiar(texto.filter(Char::isDigit).take(4).toIntOrNull() ?: 0)
        },
        modifier = modifier,
        label = { Text(etiqueta) },
        supportingText = { Text(Tiempo.duracion(minutos)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

/**
 * Avisa de las dos cosas que pueden dejar un recordatorio sin sonar.
 *
 * Se ensena solo cuando pasa, y no como texto fijo: una advertencia permanente
 * sobre algo que casi siempre esta bien se deja de leer a la tercera vez.
 */
@Composable
private fun RecordatoriosEnRiesgo(contexto: Context) {
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
