package com.carlosalbertoxw.ollin.actividades.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.excel.ResultadoImportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.Severidad
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.ui.components.AyudaDePantalla
import com.carlosalbertoxw.ollin.actividades.ui.components.Marco
import com.carlosalbertoxw.ollin.actividades.ui.components.SeccionTitulo
import com.carlosalbertoxw.ollin.actividades.ui.components.Tutorial
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin

private const val MIME_XLSX =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Crea el archivo destino proponiendo Descargas como punto de partida, en vez
 * de abrir donde haya quedado la vez anterior.
 *
 * Es solo una sugerencia: si esa carpeta no existe, el selector la ignora y
 * abre donde pueda. Cuando el destino elegido no admite crear el archivo, quien
 * avisa es el propio selector con su "Error al guardar el documento"; Ollin no
 * recibe ningun uri y no puede distinguir ese caso de una cancelacion.
 */
private class CreaLibro : ActivityResultContracts.CreateDocument(MIME_XLSX) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Download"
                )
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivoPantalla(contenedor: Contenedor, alCerrar: () -> Unit) {
    val vm = recuerdaVm("archivo") { ArchivoVm(contenedor.repositorio, contenedor.ajustes) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()
    val estado by vm.estado.collectAsStateWithLifecycle()
    val total by vm.totalActividades.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val abrir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importa) }

    val crear = rememberLauncherForActivityResult(CreaLibro()) { uri ->
        uri?.let(vm::exporta)
    }

    val avisaSalida = contenedor.controlBloqueo::esperaVueltaDelSistema

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archivo") },
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
                .padding(16.dp, 16.dp, 16.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AyudaDePantalla(contenedor, Tutorial.ARCHIVO)

            Text(
                "Ollin guarda tu bitácora cifrada en el teléfono y usa el .xlsx como " +
                    "formato de intercambio: lo lees, lo escribes, y sigue siendo tuyo.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            when (val e = estado) {
                is EstadoArchivo.Trabajando -> Marco {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(e.mensaje)
                    }
                }

                is EstadoArchivo.Fallo -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No se pudo completar", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(e.mensaje, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::limpia) { Text("Entendido") }
                    }
                }

                is EstadoArchivo.Exportado -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Libro generado", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${e.hojas} pestañas con ${e.actividades} actividades. " +
                                "Las hojas de resumen llevan fórmulas vivas: se recalculan al abrir.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = vm::limpia) { Text("Listo") }
                    }
                }

                is EstadoArchivo.Importado -> ResumenImportacion(e.resultado, vm::limpia)

                EstadoArchivo.Reposo -> Unit
            }

            // ----------------------------------------------------------- importar
            SeccionTitulo("Importar")
            Text(
                "Lee un .xlsx y aprovecha las pestañas de Categorías, Hábitos, Diccionarios " +
                    "y Registros que traiga. Reconoce los encabezados sin importar acentos ni " +
                    "mayúsculas: para la bitácora, con una columna de fecha y otra de título " +
                    "ya es suficiente.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            Marco {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InterruptorConNota(
                        titulo = "Reemplazar todo",
                        detalle = if (ajustes.reemplazarAlImportar)
                            "Se borran las $total actividades actuales y se cargan las del archivo."
                        else "Las actividades del archivo se agregan a las que ya tienes.",
                        valor = ajustes.reemplazarAlImportar,
                        alCambiar = vm::cambiaReemplazar
                    )
                    InterruptorConNota(
                        titulo = "Crear lo que falte",
                        detalle = "Da de alta las categorías y hábitos que el archivo mencione " +
                            "y todavía no existan aquí.",
                        valor = ajustes.creaFaltantesAlImportar,
                        alCambiar = vm::cambiaCreaFaltantes
                    )
                }
            }

            Button(
                onClick = {
                    lanza(vm, "abrir", avisaSalida) {
                        abrir.launch(arrayOf(MIME_XLSX, "application/octet-stream", "*/*"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Upload, contentDescription = null)
                Text("  Elegir archivo .xlsx")
            }

            // ----------------------------------------------------------- exportar
            SeccionTitulo("Exportar")

            Text("Columnas de la hoja Registros", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                EsquemaExportacion.entries.forEachIndexed { i, esquema ->
                    SegmentedButton(
                        selected = ajustes.esquema == esquema,
                        onClick = { vm.cambiaEsquema(esquema) },
                        shape = SegmentedButtonDefaults.itemShape(i, EsquemaExportacion.entries.size)
                    ) { Text(esquema.etiqueta) }
                }
            }
            Text(
                ajustes.esquema.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            Text("Pestañas a incluir", style = MaterialTheme.typography.labelLarge)

            HojaExportable.entries.forEach { hoja ->
                val activa = hoja in HojaExportable.normaliza(ajustes.hojas)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = activa,
                        onCheckedChange = { vm.alternaHoja(hoja) },
                        enabled = !hoja.obligatoria
                    )
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            hoja.titulo + if (hoja.obligatoria) "  (siempre)" else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            hoja.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.cambiaHojasPreset(HojaExportable.MINIMA) },
                    modifier = Modifier.weight(1f)
                ) { Text("Solo datos") }
                OutlinedButton(
                    onClick = { vm.cambiaHojasPreset(HojaExportable.PREDETERMINADAS) },
                    modifier = Modifier.weight(1f)
                ) { Text("Libro completo") }
            }

            Button(
                onClick = {
                    lanza(vm, "guardar", avisaSalida) { crear.launch(vm.nombreSugerido()) }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = total > 0
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("  Exportar ${HojaExportable.normaliza(ajustes.hojas).size} pestañas")
            }

            if (total == 0) {
                Text(
                    "Todavía no hay actividades que exportar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }
        }
    }
}

/**
 * Abrir el selector de archivos del sistema es lo unico de esta pantalla que
 * corre fuera de una corrutina protegida: pasa en el hilo principal, asi que lo
 * que falle ahi se lleva la app entera. Se atrapa ancho a proposito: el caso
 * conocido es que el telefono no traiga DocumentsUI (ROMs recortadas, perfiles
 * de trabajo restringidos), pero cualquier otro fallo del sistema al abrir el
 * selector merece un mensaje, no un cierre en seco.
 */
private inline fun lanza(
    vm: ArchivoVm,
    accion: String,
    avisaSalida: () -> Unit,
    bloque: () -> Unit
) {
    try {
        // El selector manda Ollin al fondo. Sin avisar, el candado se cerraria
        // en cuanto se abriera y elegir un .xlsx te expulsaria a medio camino.
        avisaSalida()
        bloque()
    } catch (e: ActivityNotFoundException) {
        vm.avisa(
            "Este teléfono no tiene una aplicación de archivos con la que $accion el .xlsx. " +
                "Instala un gestor de archivos o activa el de Android."
        )
    } catch (e: Exception) {
        vm.avisa(
            "No se pudo abrir el selector de archivos para $accion el .xlsx " +
                "(${e.javaClass.simpleName}). Revisa que tu gestor de archivos esté activo."
        )
    }
}

@Composable
private fun InterruptorConNota(
    titulo: String,
    detalle: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = LocalColoresOllin.current.textoTenue
            )
        }
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}

@Composable
private fun ResumenImportacion(resultado: ResultadoImportacion, alCerrar: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (resultado.huboProblemas) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                // Un libro puede traer solo catalogos: contar renglones de una
                // bitacora que no venia diria "0 de 0" y sonaria a que fallo.
                if (resultado.soloCatalogos) "Catálogos actualizados"
                else "Importadas ${resultado.importadas} de ${resultado.filasLeidas} renglones",
                style = MaterialTheme.typography.titleSmall
            )

            if (resultado.hojasLeidas.isNotEmpty()) {
                Text("· Pestañas leidas: ${resultado.hojasLeidas.joinToString()}")
            }
            if (resultado.categoriasCreadas.isNotEmpty()) {
                Text("· Categorías nuevas: ${resultado.categoriasCreadas.joinToString()}")
            }
            if (resultado.categoriasActualizadas > 0) {
                Text("· ${resultado.categoriasActualizadas} categorías actualizadas")
            }
            if (resultado.habitosCreados.isNotEmpty()) {
                Text("· Hábitos nuevos: ${resultado.habitosCreados.joinToString()}")
            }
            if (resultado.habitosActualizados > 0) {
                Text("· ${resultado.habitosActualizados} hábitos actualizados")
            }
            if (resultado.sinCategoria > 0) {
                Text("· ${resultado.sinCategoria} actividades sin categoría")
            }
            if (resultado.omitidas > 0) {
                Text("· ${resultado.omitidas} renglones omitidos por venir incompletos")
            }

            resultado.diagnosticos
                .filter { it.severidad == Severidad.ERROR }
                .take(3)
                .forEach {
                    Text(
                        "· ${it.mensaje}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            TextButton(onClick = alCerrar) { Text("Listo") }
        }
    }
}
