package com.carlosalbertoxw.ollin.actividades.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.BuildConfig
import com.carlosalbertoxw.ollin.actividades.R
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.domain.model.Tiempo
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin
import java.time.Instant

/**
 * Que es Ollin y con que reglas trabaja.
 *
 * Se cuentan las decisiones que el usuario no puede deducir desde la interfaz
 * —que nada sale del telefono, que la base va cifrada, como se cuenta una
 * racha— porque son justo las que determinan si le conviene confiarle su
 * bitacora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcercaDePantalla(contenedor: Contenedor, alCerrar: () -> Unit) {
    val vm = recuerdaVm("acerca") {
        AcercaDeVm(
            repo = contenedor.ajustes,
            comprobador = contenedor.actualizaciones,
            instalada = BuildConfig.VERSION_NAME
        )
    }
    val version by vm.estado.collectAsStateWithLifecycle()
    val colores = LocalColoresOllin.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
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
                .padding(24.dp, 8.dp, 24.dp, 40.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_ollin_glyph),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colores.textoTenue
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Versión ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colores.textoTenue
                )
            }

            Spacer(Modifier.height(24.dp))
            SeccionVersion(version, vm::compruebaAhora)

            Spacer(Modifier.height(24.dp))

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Que quiere decir Ollin", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Ollin es \"movimiento\" en nahuatl, y es el nombre del glifo del " +
                            "calendario mexica que representa el cambio. Una bitacora no es " +
                            "mas que el registro de tus movimientos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colores.textoTenue
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Que hace", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Rasgo(
                Icons.Filled.Timer,
                "Cronometra o captura a mano",
                "Puedes medir lo que haces mientras pasa, o anotarlo despues con los " +
                    "minutos que recuerdes. Las dos cosas cuentan igual."
            )
            Rasgo(
                Icons.Filled.Repeat,
                "Habitos con la cadencia que quieras",
                "Todos los dias, ciertos dias de la semana, un numero de veces por " +
                    "semana, o cada tantos dias o meses."
            )
            Rasgo(
                Icons.Filled.Insights,
                "Analitica sobre lo completado",
                "Solo suma lo que cerraste. Lo pendiente no infla ninguna cifra."
            )
            Rasgo(
                Icons.Filled.FolderOpen,
                "Tus datos salen en Excel",
                "La exportacion a .xlsx lleva formulas vivas y se puede volver a importar."
            )
            Rasgo(
                Icons.Filled.Lock,
                "Cerrada con llave si tu quieres",
                "Con el patron o huella del telefono, o con un PIN propio de Ollin."
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(20.dp))

            Text("Tus datos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tu bitácora no sale de este teléfono: no hay cuenta, no hay nube y no " +
                    "hay publicidad. Vive aquí en una base cifrada con AES-256, y la llave " +
                    "no sale del Keystore de Android, así que el archivo no dice nada " +
                    "aunque alguien lo saque por USB.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Lo único que Ollin le pide a la red es preguntar una vez al día si hay " +
                    "una versión más nueva publicada. Esa pregunta no lleva nada tuyo " +
                    "dentro —ni siquiera qué versión tienes: la comparación se hace aquí—, " +
                    "y se apaga en Ajustes. Ollin nunca descarga ni instala nada sola.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Por lo mismo el respaldo automatico del sistema esta desactivado para la " +
                    "base: una llave del Keystore no se puede restaurar en otro telefono y " +
                    "la copia llegaria ilegible. Tu respaldo es la exportacion a .xlsx, que " +
                    "decides tu donde guardar.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = colores.trazoSuave)
            Spacer(Modifier.height(20.dp))

            Text("Como se cuenta una racha", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "El dia de hoy nunca rompe una racha mientras no termine: un habito sin " +
                    "marcar a las nueve de la manana esta pendiente, no fallado. Los dias " +
                    "que el habito no toca se saltan sin penalizar, asi que uno de lunes a " +
                    "viernes sobrevive al fin de semana. En los habitos periodicos la racha " +
                    "cuenta repeticiones, y marcar con un dia de retraso no la rompe.",
                style = MaterialTheme.typography.bodyMedium,
                color = colores.textoTenue
            )

            Spacer(Modifier.height(28.dp))
            Text(
                "Hecho en Mexico, sin prisa.",
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
private fun SeccionVersion(estado: AcercaDeVm.Estado, alComprobar: () -> Unit) {
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
    else -> "Última búsqueda: " +
        Tiempo.fechaHora(Instant.ofEpochMilli(estado.ultimaComprobacion))
}

@Composable
private fun Rasgo(icono: ImageVector, titulo: String, detalle: String) {
    val colores = LocalColoresOllin.current

    Row(Modifier.padding(bottom = 16.dp)) {
        Icon(
            icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                detalle,
                style = MaterialTheme.typography.bodySmall,
                color = colores.textoTenue
            )
        }
    }
}
