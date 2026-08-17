package com.carlosalbertoxw.ollin.actividades.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
import com.carlosalbertoxw.ollin.actividades.data.prefs.AjustesRepositorio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TutorialVm(private val prefs: AjustesRepositorio) : ViewModel() {

    val ajustes: StateFlow<Ajustes?> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun oculta(tutorial: Tutorial) {
        viewModelScope.launch { prefs.ocultaTutorial(tutorial.clave) }
    }
}
