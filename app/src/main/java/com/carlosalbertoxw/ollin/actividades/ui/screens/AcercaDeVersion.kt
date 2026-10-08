/*
 * La tarjeta de version de Acerca de: si hay una nueva, sus notas y el boton
 * que abre la descarga. Aparte de AcercaDePantalla.kt por tamano.
 */
package com.carlosalbertoxw.ollin.actividades.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.Instant

/**
 * La version instalada y si hay una mas nueva.
 *
 * Ollin se instala fuera de la tienda, asi que esta tarjeta es el unico sitio
 * donde alguien puede enterarse de que salio una correccion. Por eso ensena
 * siempre la version puesta —aunque no haya novedad— y no solo cuando hay algo
 * que anunciar: una tarjeta que aparece y desaparece no se aprende a mirar.
 *
 * El boton **abre el navegador**, no descarga. Instalar un APK exige un permiso
 * que convertiria a Ollin en un canal de entrega de software, y eso es mucho
 * mas de lo que hace falta para avisar de una version.
 */
@Composable
internal fun SeccionVersion(estado: AcercaDeVm.Estado, alComprobar: () -> Unit) {
    val colores = LocalColoresOllin.current
    val navegador = LocalUriHandler.current
    val url = estado.urlDeDescarga

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (estado.hayVersionNueva) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdateAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (estado.hayVersionNueva) "Hay una versión nueva" else "Estás al día",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        if (estado.hayVersionNueva) {
                            "Tienes la ${estado.instalada} y ya salió la ${estado.disponible}."
                        } else {
                            "Versión instalada ${estado.instalada}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colores.textoTenue
                    )
                }
            }

            // Las notas solo cuando hay algo que decidir: leer el resumen de la
            // version que ya tienes puesta no ayuda a nadie a hacer nada.
            if (estado.hayVersionNueva && !estado.notas.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    estado.notas,
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue
                )
            }

            if (estado.hayVersionNueva && url != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { navegador.openUri(url) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ver la versión ${estado.disponible}") }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    estado.aviso ?: textoDeLaUltimaComprobacion(estado),
                    style = MaterialTheme.typography.bodySmall,
                    color = colores.textoTenue,
                    modifier = Modifier.weight(1f)
                )
                if (estado.consultando) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = alComprobar) { Text("Buscar ahora") }
                }
            }
        }
    }
}

/**
 * Cuando se preguntó por última vez.
 *
 * Se dice aunque no haya novedad, porque es lo que distingue "no hay version
 * nueva" de "llevo un mes sin poder preguntar". Sin esta linea, una app sin red
 * desde hace semanas se ve exactamente igual que una al dia.
 */
private fun textoDeLaUltimaComprobacion(estado: AcercaDeVm.Estado): String = when {
    !estado.activa -> "La búsqueda de actualizaciones está apagada en Ajustes."

    estado.ultimaComprobacion <= 0L -> "Todavía no se ha buscado."

    else ->
        "Última búsqueda: " +
            Tiempo.fechaHora(Instant.ofEpochMilli(estado.ultimaComprobacion))
}
