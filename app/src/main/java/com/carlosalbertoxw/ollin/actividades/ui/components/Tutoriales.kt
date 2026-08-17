package com.carlosalbertoxw.ollin.actividades.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlosalbertoxw.ollin.actividades.di.Contenedor
import com.carlosalbertoxw.ollin.actividades.ui.recuerdaVm
import com.carlosalbertoxw.ollin.actividades.ui.theme.LocalColoresOllin

/**
 * Las tarjetas de ayuda que abren cada pantalla.
 *
 * La clave se guarda en preferencias, asi que renombrarla haria reaparecer una
 * tarjeta que alguien ya habia descartado. Los textos si se pueden cambiar.
 */
enum class Tutorial(
    val clave: String,
    val titulo: String,
    val texto: String
) {
    HOY(
        "hoy",
        "Esta es tu pantalla del día",
        "Escribe qué estás haciendo y pulsa Iniciar para cronometrarlo. Si ya lo " +
            "hiciste y no lo cronometraste, usa el botón Registrar de abajo y captura " +
            "los minutos a mano."
    ),
    REGISTRO(
        "registro",
        "Todo lo que has anotado",
        "Busca por titulo o nota y acota con los filtros de arriba: periodo, estado " +
            "y ámbito se combinan entre sí. Pulsa cualquier renglón para corregirlo."
    ),
    HABITOS(
        "habitos",
        "Lo que quieres repetir",
        "Un hábito puede tocar todos los días, ciertos días de la semana, un número " +
            "de veces por semana, o cada tantos días o meses. Al marcarlo se guarda " +
            "como una actividad más, así que suma en la analítica."
    ),
    ANALITICA(
        "analitica",
        "En qué se te va el tiempo",
        "Solo cuenta lo completado. Cambia la ventana con los chips de arriba; las " +
            "barras son minutos por día y abajo está el reparto por categoría."
    ),
    ARCHIVO(
        "archivo",
        "Tu bitácora, fuera del teléfono",
        "Exportar genera un .xlsx con formulas vivas que puedes abrir en Excel o " +
            "Sheets. Importar lee ese mismo archivo, o cualquier hoja que tenga una " +
            "columna de fecha y otra de título."
    ),
    CATEGORIAS(
        "categorias",
        "Cómo se agrupa tu tiempo",
        "Cada categoría pertenece a un ámbito, y el ámbito es lo que suma en las " +
            "metas de la pantalla de hoy. Archiva las que no uses: no pierdes su historial."
    )
}

/**
 * Pinta la tarjeta de ayuda de una pantalla, si toca.
 *
 * Se resuelve sola contra las preferencias en vez de pedirle el estado a la
 * pantalla que la hospeda: son seis pantallas y ninguna tiene por que cargar
 * con la contabilidad de que tarjetas ya se descartaron. El ViewModel va con
 * clave fija, asi que las seis comparten una sola instancia.
 */
@Composable
fun AyudaDePantalla(
    contenedor: Contenedor,
    tutorial: Tutorial,
    modifier: Modifier = Modifier
) {
    val vm = recuerdaVm("tutorial") { TutorialVm(contenedor.ajustes) }
    val ajustes by vm.ajustes.collectAsStateWithLifecycle()

    // Mientras no se leen las preferencias no se pinta nada: aparecer y
    // desaparecer a los dos cuadros es peor que tardar un parpadeo.
    val visible = ajustes?.let {
        it.muestraTutoriales && tutorial.clave !in it.tutorialesOcultos
    } ?: false

    AnimatedVisibility(
        visible = visible,
        exit = shrinkVertically() + fadeOut(),
        enter = fadeIn(),
        modifier = modifier
    ) {
        TarjetaAyuda(tutorial) { vm.oculta(tutorial) }
    }
}

@Composable
private fun TarjetaAyuda(tutorial: Tutorial, alOcultar: () -> Unit) {
    val colores = LocalColoresOllin.current

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 14.dp)) {
            Icon(
                Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tutorial.titulo,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    tutorial.texto,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Se puede volver a mostrar desde Ajustes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = colores.textoTenue
                )
            }
            IconButton(onClick = alOcultar, modifier = Modifier.align(Alignment.Top)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Ocultar esta ayuda",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
