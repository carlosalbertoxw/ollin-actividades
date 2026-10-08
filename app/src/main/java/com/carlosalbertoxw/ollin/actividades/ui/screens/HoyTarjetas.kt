/*
 * Las tarjetas de Hoy que no son la lista de habitos: el cronometro en curso,
 * la meta del dia y el aviso de respaldo. Aparte de HoyPantalla.kt por tamano.
 */
package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosalbertoxw.ollin.actividades.data.db.Actividad
import com.carlosalbertoxw.ollin.actividades.data.db.ActividadDetallada
import com.carlosalbertoxw.ollin.actividades.data.db.Categoria
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.components.BarraAvance
import com.carlosalbertoxw.ollin.actividades.ui.components.Punto
import com.carlosalbertoxw.ollin.actividades.ui.components.iconoDe
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import com.carlosalbertoxw.ollin.actividades.ui.theme.colorDeCategoria
import java.time.Instant

/**
 * El corazon de la pantalla: o esta corriendo algo, y entonces manda el
 * cronometro, o no hay nada, y entonces manda el campo para arrancar. Nunca
 * las dos cosas: dos llamados a la accion se anulan entre si.
 */
@Composable
internal fun TarjetaCronometro(
    enCurso: ActividadDetallada?,
    ahora: () -> Instant,
    categorias: List<Categoria>,
    categoriaRapida: Long?,
    alElegirCategoria: (Long) -> Unit,
    alIniciar: (String) -> Unit,
    alDetener: () -> Unit,
    alAbrir: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    // Sobrevive a girar el telefono: perder lo que ibas escribiendo por voltear
    // la mano es la clase de tropiezo que hace que no vuelvas a anotar nada.
    var titulo by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            if (enCurso != null) {
                val actividad = enCurso.actividad
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punto(
                        colorDeCategoria(
                            enCurso.categoriaColor,
                            colores.de(enCurso.categoriaAmbito)
                        ),
                        10
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        enCurso.categoriaNombre ?: "Sin categoría",
                        style = MaterialTheme.typography.labelMedium,
                        color = colores.textoTenue
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(actividad.titulo, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Cronometro(actividad, ahora)
                Text(
                    "Desde las ${Tiempo.hora(actividad.inicio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = alDetener) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Detener")
                    }
                    TextButton(onClick = alAbrir) { Text("Editar") }
                }
            } else {
                Text("Qué estás haciendo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Reunión de diseño, correr 5 km…") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categorias, key = { it.id }) { categoria ->
                        FilterChip(
                            selected = categoria.id == categoriaRapida,
                            onClick = { alElegirCategoria(categoria.id) },
                            label = { Text(categoria.nombre) },
                            leadingIcon = {
                                Icon(
                                    iconoDe(categoria.ambito),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(
                    onClick = {
                        alIniciar(titulo)
                        titulo = ""
                    },
                    enabled = titulo.isNotBlank()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Iniciar")
                }
            }
        }
    }
}

/**
 * Lo unico que cambia cada segundo.
 *
 * Vive aparte y lee el reloj dentro de su propio cuerpo para que la
 * recomposicion del tick no salga de estas cuatro lineas: si el reloj se leyera
 * en la tarjeta, cada segundo se volverian a componer el titulo, la categoria y
 * los dos botones.
 */
@Composable
private fun Cronometro(actividad: Actividad, ahora: () -> Instant) {
    Text(
        Tiempo.cronometro(actividad.segundosVividos(ahora())),
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        color = LocalColoresOllin.current.enCurso
    )
}

@Composable
internal fun MetaDelDia(
    etiqueta: String,
    minutos: Int,
    meta: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colores = LocalColoresOllin.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Punto(color, 8)
                Spacer(Modifier.width(8.dp))
                Text(
                    etiqueta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoTenue
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(Tiempo.duracion(minutos), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            BarraAvance(
                avance = if (meta <= 0) 0.0 else minutos.toDouble() / meta,
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (meta > 0) "Meta ${Tiempo.duracion(meta)}" else "Sin meta",
                style = MaterialTheme.typography.labelSmall,
                color = colores.textoTenue
            )
        }
    }
}

/**
 * La tarjeta del aviso de respaldo. Toda ella lleva a Archivo, igual que la
 * notificacion; la cruz la quita hasta la siguiente vez que se abra la app.
 */
@Composable
internal fun AvisoRespaldo(
    titulo: String,
    alExportar: () -> Unit,
    alQuitar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier.fillMaxWidth().clickable(onClick = alExportar),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.SaveAlt, contentDescription = null)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Exporta a Excel: es el único respaldo que hay de tu bitácora.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = alExportar, contentPadding = PaddingValues(0.dp)) {
                    Text("Exportar ahora")
                }
            }
            IconButton(onClick = alQuitar, modifier = Modifier.align(Alignment.Top)) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar el aviso")
            }
        }
    }
}
