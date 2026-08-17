package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.data.repo.ActividadesRepositorio
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriasVm(private val repo: ActividadesRepositorio) : ViewModel() {

    val categorias: StateFlow<List<Categoria>> = repo.observaTodasLasCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(categoria: Categoria) {
        viewModelScope.launch { repo.guardaCategoria(categoria) }
    }

    fun elimina(categoria: Categoria) {
        viewModelScope.launch { repo.eliminaCategoria(categoria) }
    }
}
