package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import com.carlosalbertoxw.ollin.actividades.data.seguridad.ClavePin
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesVm(private val repo: AjustesRepositorio) : ViewModel() {

    val ajustes: StateFlow<Ajustes> = repo.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Ajustes())

    fun tema(oscuro: Boolean?) = viewModelScope.launch { repo.guardaTema(oscuro) }
    fun colorDinamico(valor: Boolean) = viewModelScope.launch { repo.guardaColorDinamico(valor) }
    fun metaTrabajo(minutos: Int) = viewModelScope.launch { repo.guardaMetaTrabajo(minutos) }
    fun metaFisico(minutos: Int) = viewModelScope.launch { repo.guardaMetaFisico(minutos) }
    fun duracionRapida(minutos: Int) = viewModelScope.launch { repo.guardaDuracionRapida(minutos) }
    fun completadasEnHoy(valor: Boolean) = viewModelScope.launch { repo.guardaMuestraCompletadas(valor) }

    fun recordatorios(valor: Boolean) = viewModelScope.launch { repo.guardaRecordatorios(valor) }

    fun tutoriales(valor: Boolean) = viewModelScope.launch { repo.guardaMuestraTutoriales(valor) }

    fun reiniciaTutoriales() = viewModelScope.launch { repo.reiniciaTutoriales() }

    fun quitaBloqueo() = viewModelScope.launch { repo.quitaBloqueo() }

    fun usaBloqueoDelSistema() = viewModelScope.launch { repo.activaBloqueoSistema() }

    fun usaBloqueoConPin(pin: String) = viewModelScope.launch {
        val sal = ClavePin.nuevaSal()
        repo.activaBloqueoPin(hash = ClavePin.deriva(pin, sal), sal = sal)
    }

    /**
     * El contador de fallos del PIN es el mismo que el de la pantalla de
     * bloqueo. Este dialogo tambien abre la puerta —desde aqui se quita el
     * candado—, y llevarle una cuenta aparte seria dejar una entrada sin freno.
     */
    fun sumaFalloPin() = viewModelScope.launch { repo.sumaFalloPin() }

    fun limpiaFallosPin() = viewModelScope.launch { repo.limpiaFallosPin() }
}
