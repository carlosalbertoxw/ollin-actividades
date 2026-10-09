package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ControlBloqueo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesVm(private val repo: AjustesRepositorio, private val bloqueo: ControlBloqueo) :
    ViewModel() {

    val ajustes: StateFlow<Ajustes> = repo.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    fun tema(oscuro: Boolean?) = viewModelScope.launch { repo.guardaTema(oscuro) }
    fun colorDinamico(valor: Boolean) = viewModelScope.launch { repo.guardaColorDinamico(valor) }
    fun metaTrabajo(minutos: Int) = viewModelScope.launch { repo.guardaMetaTrabajo(minutos) }
    fun metaFisico(minutos: Int) = viewModelScope.launch { repo.guardaMetaFisico(minutos) }
    fun duracionRapida(minutos: Int) = viewModelScope.launch { repo.guardaDuracionRapida(minutos) }
    fun completadasEnHoy(valor: Boolean) =
        viewModelScope.launch { repo.guardaMuestraCompletadas(valor) }

    fun recordatorios(valor: Boolean) = viewModelScope.launch { repo.guardaRecordatorios(valor) }

    fun buscarActualizaciones(valor: Boolean) =
        viewModelScope.launch { repo.guardaBuscarActualizaciones(valor) }

    fun avisaRespaldo(valor: Boolean) = viewModelScope.launch { repo.guardaAvisaRespaldo(valor) }

    fun tutoriales(valor: Boolean) = viewModelScope.launch { repo.guardaMuestraTutoriales(valor) }

    fun reiniciaTutoriales() = viewModelScope.launch { repo.reiniciaTutoriales() }

    fun quitaBloqueo() = viewModelScope.launch { repo.quitaBloqueo() }

    fun usaBloqueoDelSistema() = viewModelScope.launch { repo.activaBloqueoSistema() }

    /** La huella sale ya sellada con el Keystore; ver [ControlBloqueo.huellaNueva]. */
    fun usaBloqueoConPin(pin: String) = viewModelScope.launch {
        val (hash, sal) = bloqueo.huellaNueva(pin)
        repo.activaBloqueoPin(hash = hash, sal = sal)
    }
}
