package com.carlosalbertoxw.ollin.actividades.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.excel.EsquemaExportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.HojaExportable
import com.carlosalbertoxw.ollin.actividades.data.excel.OpcionesImportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.ResultadoImportacion
import com.carlosalbertoxw.ollin.actividades.data.excel.XlsxLector
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface EstadoArchivo {
    data object Reposo : EstadoArchivo
    data class Trabajando(val mensaje: String) : EstadoArchivo
    data class Importado(val resultado: ResultadoImportacion) : EstadoArchivo
    data class Exportado(val hojas: Int, val actividades: Int) : EstadoArchivo
    data class Fallo(val mensaje: String) : EstadoArchivo
}

class ArchivoVm(
    private val repo: ActividadesRepositorio,
    private val prefs: AjustesRepositorio
) : ViewModel() {

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
        _estado.value = EstadoArchivo.Trabajando("Leyendo el archivo…")
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
        _estado.value = EstadoArchivo.Trabajando("Generando el libro…")
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
                "espacio libre y que la ubicación siga disponible."
            is OutOfMemoryError -> "El libro es demasiado grande para la memoria del teléfono. " +
                "Exporta menos pestañas desde \"Solo datos\"."
            else -> "No se pudo $accion. Inténtalo de nuevo."
        }
    }

    fun limpia() { _estado.value = EstadoArchivo.Reposo }

    fun avisa(mensaje: String) { _estado.value = EstadoArchivo.Fallo(mensaje) }

    fun nombreSugerido(): String = "Actividades-${LocalDate.now()}.xlsx"
}
