package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Ambito
import com.carlosalbertoxw.ollin.actividades.domain.model.EstadoActividad
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.components.AyudaDePantalla
import com.carlosalbertoxw.ollin.actividades.ui.components.EstadoVacio
import com.carlosalbertoxw.ollin.actividades.ui.components.RenglonActividad
import com.carlosalbertoxw.ollin.actividades.ui.components.Tutorial
import com.carlosalbertoxw.ollin.actividades.ui.components.iconoDe
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin

@Composable
fun ActividadesPantalla(
    contenedor: Contenedor,
    alAbrirActividad: (Long) -> Unit
) {
    val vm = recuerdaVm("actividades") { ActividadesVm(contenedor.repositorio) }
    val filtro by vm.filtro.collectAsStateWithLifecycle()
    val actividades by vm.actividades.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    val totalMinutos = actividades.sumOf { it.actividad.duracionMinutos ?: 0 }
    val porDia = actividades.groupBy { it.actividad.dia }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp)) {
            AyudaDePantalla(contenedor, Tutorial.REGISTRO, Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = filtro.texto,
                onValueChange = { texto -> vm.actualiza { it.copy(texto = texto) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por título o nota") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            LazyRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(Rango.entries.toList()) { rango ->
                    FilterChip(
                        selected = filtro.rango == rango,
                        onClick = { vm.actualiza { it.copy(rango = rango) } },
                        label = { Text(rango.etiqueta) }
                    )
                }
            }

            LazyRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EstadoActividad.entries.toList()) { estado ->
                    FilterChip(
                        selected = filtro.estado == estado,
                        onClick = {
                            vm.actualiza { it.copy(estado = if (it.estado == estado) null else estado) }
                        },
                        label = { Text(estado.etiqueta) }
                    )
                }
                items(Ambito.entries.toList()) { ambito ->
                    FilterChip(
                        selected = filtro.ambito == ambito,
                        onClick = {
                            vm.actualiza { it.copy(ambito = if (it.ambito == ambito) null else ambito) }
                        },
                        label = { Text(ambito.etiqueta) },
                        leadingIcon = {
                            Icon(
                                iconoDe(ambito),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${actividades.size} registros",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Text(
                    "Suma: ${Tiempo.duracion(totalMinutos)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }
        }

        if (actividades.isEmpty()) {
            EstadoVacio(
                icono = Icons.AutoMirrored.Filled.ListAlt,
                titulo = "Nada por aquí",
                detalle = "Ajusta el filtro o registra una actividad con el botón de abajo.",
                modifier = Modifier.fillMaxWidth()
            )
            return
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            porDia.keys.sortedDescending().forEach { dia ->
                val delDia = porDia[dia].orEmpty()
                item(key = "encabezado-$dia") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            Tiempo.diaRelativo(dia),
                            style = MaterialTheme.typography.labelLarge,
                            color = colores.textoTenue
                        )
                        Text(
                            Tiempo.duracion(delDia.sumOf { it.actividad.duracionMinutos ?: 0 }),
                            style = MaterialTheme.typography.labelLarge,
                            color = colores.textoTenue
                        )
                    }
                }
                items(delDia, key = { it.actividad.id }) { detalle ->
                    RenglonActividad(
                        detalle = detalle,
                        alPulsar = { alAbrirActividad(detalle.actividad.id) }
                    )
                    HorizontalDivider(color = colores.trazoSuave)
                }
            }
        }
    }
}
