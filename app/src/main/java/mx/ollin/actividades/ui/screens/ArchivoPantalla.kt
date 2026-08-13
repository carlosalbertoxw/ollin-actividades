package mx.ollin.actividades.ui.screens

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ollin.actividades.data.excel.EsquemaExportacion
import mx.ollin.actividades.data.excel.HojaExportable
import mx.ollin.actividades.data.excel.OpcionesImportacion
import mx.ollin.actividades.data.excel.ResultadoImportacion
import mx.ollin.actividades.data.excel.Severidad
import mx.ollin.actividades.data.excel.XlsxLector
import mx.ollin.actividades.data.prefs.Ajustes
import mx.ollin.actividades.di.Contenedor
import mx.ollin.actividades.ui.components.AyudaDePantalla
import mx.ollin.actividades.ui.components.Marco
import mx.ollin.actividades.ui.components.SeccionTitulo
import mx.ollin.actividades.ui.components.Tutorial
import mx.ollin.actividades.ui.recuerdaVm
import mx.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.LocalDate

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

sealed interface EstadoArchivo {
    data object Reposo : EstadoArchivo
    data class Trabajando(val mensaje: String) : EstadoArchivo
    data class Importado(val resultado: ResultadoImportacion) : EstadoArchivo
    data class Exportado(val hojas: Int, val actividades: Int) : EstadoArchivo
    data class Fallo(val mensaje: String) : EstadoArchivo
}

class ArchivoVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio
    private val prefs = contenedor.ajustes

    val ajustes: StateFlow<Ajustes> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    val totalActividades: StateFlow<Int> = repo.observaConteoActividades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _estado = MutableStateFlow<EstadoArchivo>(EstadoArchivo.Reposo)
    val estado: StateFlow<EstadoArchivo> = _estado

    fun cambiaEsquema(esquema: EsquemaExportacion) {
        viewModelScope.launch { prefs.guardaEsquema(esquema) }
    }

    fun alternaHoja(hoja: HojaExportable) {
        if (hoja.obligatoria) return
        viewModelScope.launch {
            val actuales = ajustes.value.hojas
            prefs.guardaHojas(if (hoja in actuales) actuales - hoja else actuales + hoja)
        }
    }

    fun cambiaHojasPreset(hojas: Set<HojaExportable>) {
        viewModelScope.launch { prefs.guardaHojas(hojas) }
    }

    fun cambiaReemplazar(valor: Boolean) {
        viewModelScope.launch { prefs.guardaReemplazar(valor) }
    }

    fun cambiaCreaFaltantes(valor: Boolean) {
        viewModelScope.launch { prefs.guardaCreaFaltantes(valor) }
    }

    fun importa(uri: Uri) {
        _estado.value = EstadoArchivo.Trabajando("Leyendo el archivo...")
        viewModelScope.launch {
            val a = ajustes.value
            runCatching {
                repo.importa(
                    uri,
                    OpcionesImportacion(
                        creaFaltantes = a.creaFaltantesAlImportar,
                        reemplazarTodo = a.reemplazarAlImportar
                    )
                )
            }.fold(
                onSuccess = { _estado.value = EstadoArchivo.Importado(it) },
                onFailure = { _estado.value = EstadoArchivo.Fallo(explica(it, "importar")) }
            )
        }
    }

    fun exporta(uri: Uri) {
        _estado.value = EstadoArchivo.Trabajando("Generando el libro...")
        viewModelScope.launch {
            val a = ajustes.value
            runCatching { repo.exporta(uri, a.esquema, a.hojas) }.fold(
                onSuccess = {
                    _estado.value = EstadoArchivo.Exportado(
                        hojas = HojaExportable.normaliza(a.hojas).size,
                        actividades = totalActividades.value
                    )
                },
                onFailure = { _estado.value = EstadoArchivo.Fallo(explica(it, "exportar")) }
            )
        }
    }

    /**
     * Traduce el fallo a algo accionable. El mensaje crudo de una excepcion
     * habla de rutas internas, clases y consultas: al usuario no le sirve de
     * nada y de paso le ensena como esta hecha la app por dentro.
     */
    private fun explica(fallo: Throwable, accion: String): String {
        // El mensaje que ve el usuario oculta los internos a proposito, asi que
        // el fallo real se manda a logcat: sin esto, un error de exportacion no
        // deja rastro de que lo causo. No lleva ningun dato del usuario.
        android.util.Log.w("Ollin", "Fallo al $accion", fallo)
        return when (fallo) {
            // Los suyos si estan escritos para leerse; el resto no.
            is XlsxLector.ArchivoInvalido -> fallo.message ?: "El archivo no se pudo leer."
            is SecurityException -> "Ya no hay permiso sobre ese archivo. Vuelve a elegirlo."
            is java.io.IOException -> "No se pudo leer o escribir el archivo. Revisa que haya " +
                "espacio libre y que la ubicacion siga disponible."
            is OutOfMemoryError -> "El libro es demasiado grande para la memoria del telefono. " +
                "Exporta menos pestanas desde \"Solo datos\"."
            else -> "No se pudo $accion. Intenta de nuevo."
        }
    }

    fun limpia() { _estado.value = EstadoArchivo.Reposo }

    fun avisa(mensaje: String) { _estado.value = EstadoArchivo.Fallo(mensaje) }

    fun nombreSugerido(): String = "Actividades-${LocalDate.now()}.xlsx"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivoPantalla(contenedor: Contenedor, alCerrar: () -> Unit) {
    val vm = recuerdaVm("archivo") { ArchivoVm(contenedor) }
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
                "Ollin guarda tu bitacora cifrada en el telefono y usa el .xlsx como " +
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
                                "Las hojas de resumen llevan formulas vivas: se recalculan al abrir.",
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
                "Lee un .xlsx y aprovecha las pestañas de Categorias, Habitos, Diccionarios " +
                    "y Registros que traiga. Reconoce los encabezados sin importar acentos ni " +
                    "mayusculas: para la bitacora, con una columna de fecha y otra de titulo " +
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
                        detalle = "Da de alta las categorias y habitos que el archivo mencione " +
                            "y todavia no existan aqui.",
                        valor = ajustes.creaFaltantesAlImportar,
                        alCambiar = vm::cambiaCreaFaltantes
                    )
                }
            }

            Button(
                onClick = {
                    lanza(vm, "abrir") {
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
                onClick = { lanza(vm, "guardar") { crear.launch(vm.nombreSugerido()) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = total > 0
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("  Exportar ${HojaExportable.normaliza(ajustes.hojas).size} pestañas")
            }

            if (total == 0) {
                Text(
                    "Todavia no hay actividades que exportar.",
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
private inline fun lanza(vm: ArchivoVm, accion: String, bloque: () -> Unit) {
    try {
        bloque()
    } catch (e: ActivityNotFoundException) {
        vm.avisa(
            "Este telefono no tiene una aplicacion de archivos con la que $accion el .xlsx. " +
                "Instala un gestor de archivos o activa el de Android."
        )
    } catch (e: Exception) {
        vm.avisa(
            "No se pudo abrir el selector de archivos para $accion el .xlsx " +
                "(${e.javaClass.simpleName}). Revisa que tu gestor de archivos este activo."
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
                if (resultado.soloCatalogos) "Catalogos actualizados"
                else "Importadas ${resultado.importadas} de ${resultado.filasLeidas} renglones",
                style = MaterialTheme.typography.titleSmall
            )

            if (resultado.hojasLeidas.isNotEmpty()) {
                Text("· Pestañas leidas: ${resultado.hojasLeidas.joinToString()}")
            }
            if (resultado.categoriasCreadas.isNotEmpty()) {
                Text("· Categorias nuevas: ${resultado.categoriasCreadas.joinToString()}")
            }
            if (resultado.categoriasActualizadas > 0) {
                Text("· ${resultado.categoriasActualizadas} categorias actualizadas")
            }
            if (resultado.habitosCreados.isNotEmpty()) {
                Text("· Habitos nuevos: ${resultado.habitosCreados.joinToString()}")
            }
            if (resultado.habitosActualizados > 0) {
                Text("· ${resultado.habitosActualizados} habitos actualizados")
            }
            if (resultado.sinCategoria > 0) {
                Text("· ${resultado.sinCategoria} actividades sin categoria")
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
