package com.carlosalbertoxw.ollin.actividades.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.recordatorios.Notificaciones
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesPantalla(
    contenedor: Contenedor,
    alAbrirCategorias: () -> Unit,
    alAbrirArchivo: () -> Unit,
    alAbrirAcercaDe: () -> Unit,
    alCerrar: () -> Unit
) {
    val vm = recuerdaVm("ajustes") { AjustesVm(contenedor.ajustes, contenedor.controlBloqueo) }
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
                    Text(
                        "Mostrar lo completado en Hoy",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        if (quiere) {
                            pidePermisoNotificaciones(vm::recordatorios)
                        } else {
                            vm.recordatorios(false)
                        }
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
                        if (quiere) {
                            pidePermisoNotificaciones(vm::avisaRespaldo)
                        } else {
                            vm.avisaRespaldo(false)
                        }
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
                    Text(
                        "Avisarme de versiones nuevas",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                bloqueo = contenedor.controlBloqueo,
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
