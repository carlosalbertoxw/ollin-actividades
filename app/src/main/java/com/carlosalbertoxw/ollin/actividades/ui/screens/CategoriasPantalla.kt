package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.ui.components.AyudaDePantalla
import com.carlosalbertoxw.ollin.actividades.ui.components.Punto
import com.carlosalbertoxw.ollin.actividades.ui.components.Tutorial
import com.carlosalbertoxw.ollin.actividades.ui.components.iconoDe
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.actividades.ui.theme.colorDeCategoria

/** Colores que puede tomar una categoria. Una paleta cerrada evita el arcoiris. */
private val PALETA = listOf(
    "#3D6DB5", "#5B87C9", "#2F9E6E", "#48B183",
    "#E9A13B", "#F0B563", "#C4453F", "#D46B66",
    "#8E6FC9", "#4FA5B5", "#8C8C8C", "#B07A22"
)

class CategoriasVm(contenedor: Contenedor) : ViewModel() {

    private val repo = contenedor.repositorio

    val categorias: StateFlow<List<Categoria>> = repo.observaTodasLasCategorias()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun guarda(categoria: Categoria) {
        viewModelScope.launch { repo.guardaCategoria(categoria) }
    }

    fun elimina(categoria: Categoria) {
        viewModelScope.launch { repo.eliminaCategoria(categoria) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasPantalla(contenedor: Contenedor, alCerrar: () -> Unit) {
    val vm = recuerdaVm("categorias") { CategoriasVm(contenedor) }
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    var editando by remember { mutableStateOf<Categoria?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorias") },
                navigationIcon = {
                    IconButton(onClick = alCerrar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editando = Categoria(
                        nombre = "",
                        ambito = Ambito.TRABAJO,
                        colorHex = PALETA.first(),
                        orden = categorias.size
                    )
                },
                // La descripcion va en el icono: el boton extendido borra la
                // semantica de su contenido. Ver la misma nota en OllinRaiz.
                icon = { Icon(Icons.Filled.Add, contentDescription = "Nueva categoria") },
                text = { Text("Nueva") }
            )
        }
    ) { relleno ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(relleno),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item(key = "ayuda") {
                AyudaDePantalla(
                    contenedor,
                    Tutorial.CATEGORIAS,
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            Ambito.entries.forEach { ambito ->
                val delAmbito = categorias.filter { it.ambito == ambito }
                if (delAmbito.isEmpty()) return@forEach

                item(key = "ambito-$ambito") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            iconoDe(ambito),
                            contentDescription = null,
                            tint = colores.de(ambito),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ambito.etiqueta,
                            style = MaterialTheme.typography.labelLarge,
                            color = colores.textoTenue
                        )
                    }
                }

                items(delAmbito, key = { it.id }) { categoria ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editando = categoria }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Punto(colorDeCategoria(categoria.colorHex, colores.de(ambito)), 12)
                        Spacer(Modifier.width(12.dp))
                        Text(categoria.nombre, Modifier.weight(1f))
                        if (categoria.archivada) {
                            Text(
                                "Archivada",
                                style = MaterialTheme.typography.labelSmall,
                                color = colores.textoTenue
                            )
                        }
                    }
                    HorizontalDivider(color = colores.trazoSuave)
                }
            }
        }
    }

    editando?.let { categoria ->
        DialogoCategoria(
            inicial = categoria,
            alGuardar = {
                vm.guarda(it)
                editando = null
            },
            alEliminar = if (categoria.id == 0L) null else {
                {
                    vm.elimina(categoria)
                    editando = null
                }
            },
            alCerrar = { editando = null }
        )
    }
}

@Composable
private fun DialogoCategoria(
    inicial: Categoria,
    alGuardar: (Categoria) -> Unit,
    alEliminar: (() -> Unit)?,
    alCerrar: () -> Unit
) {
    var nombre by remember { mutableStateOf(inicial.nombre) }
    var ambito by remember { mutableStateOf(inicial.ambito) }
    var color by remember { mutableStateOf(inicial.colorHex ?: PALETA.first()) }
    var archivada by remember { mutableStateOf(inicial.archivada) }
    val colores = LocalColoresOllin.current

    AlertDialog(
        onDismissRequest = alCerrar,
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        inicial.copy(
                            nombre = nombre.trim(),
                            ambito = ambito,
                            colorHex = color,
                            archivada = archivada
                        )
                    )
                },
                enabled = nombre.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (alEliminar != null) {
                    IconButton(onClick = alEliminar) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = alCerrar) { Text("Cancelar") }
            }
        },
        title = { Text(if (inicial.id == 0L) "Nueva categoria" else "Editar categoria") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text("Ambito", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ambito.entries.forEach { opcion ->
                        FilterChip(
                            selected = ambito == opcion,
                            onClick = { ambito = opcion },
                            label = { Text(opcion.etiqueta) },
                            leadingIcon = {
                                Icon(
                                    iconoDe(opcion),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge, color = colores.textoTenue)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETA.chunked(6).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { hex ->
                                val elegido = hex == color
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(colorDeCategoria(hex, Color.Gray))
                                        .border(
                                            width = if (elegido) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { color = hex }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Archivada", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Deja de ofrecerse al capturar, pero el historial se conserva.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colores.textoTenue
                        )
                    }
                    Switch(checked = archivada, onCheckedChange = { archivada = it })
                }
            }
        }
    )
}
