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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.carlosalbertoxw.ollin.actividades.data.prefs.Ajustes
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
        "Esta es tu pantalla del dia",
        "Escribe que estas haciendo y pulsa Iniciar para cronometrarlo. Si ya lo " +
            "hiciste y no lo cronometraste, usa el boton Registrar de abajo y captura " +
            "los minutos a mano."
    ),
    REGISTRO(
        "registro",
        "Todo lo que has anotado",
        "Busca por titulo o nota y acota con los filtros de arriba: periodo, estado " +
            "y ambito se combinan entre si. Pulsa cualquier renglon para corregirlo."
    ),
    HABITOS(
        "habitos",
        "Lo que quieres repetir",
        "Un habito puede tocar todos los dias, ciertos dias de la semana, un numero " +
            "de veces por semana, o cada tantos dias o meses. Al marcarlo se guarda " +
            "como una actividad mas, asi que suma en la analitica."
    ),
    ANALITICA(
        "analitica",
        "En que se te va el tiempo",
        "Solo cuenta lo completado. Cambia la ventana con los chips de arriba; las " +
            "barras son minutos por dia y abajo esta el reparto por categoria."
    ),
    ARCHIVO(
        "archivo",
        "Tu bitacora, fuera del telefono",
        "Exportar genera un .xlsx con formulas vivas que puedes abrir en Excel o " +
            "Sheets. Importar lee ese mismo archivo, o cualquier hoja que tenga una " +
            "columna de fecha y otra de titulo."
    ),
    CATEGORIAS(
        "categorias",
        "Como se agrupa tu tiempo",
        "Cada categoria pertenece a un ambito, y el ambito es lo que suma en las " +
            "metas de la pantalla de hoy. Archiva las que no uses: no pierdes su historial."
    )
}

class TutorialVm(contenedor: Contenedor) : ViewModel() {

    private val prefs = contenedor.ajustes

    val ajustes: StateFlow<Ajustes?> = prefs.ajustes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun oculta(tutorial: Tutorial) {
        viewModelScope.launch { prefs.ocultaTutorial(tutorial.clave) }
    }
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
    val vm = recuerdaVm("tutorial") { TutorialVm(contenedor) }
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
