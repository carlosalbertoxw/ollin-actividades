package com.carlosalbertoxw.ollin.actividades.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** Las cuatro pestañas de abajo. Todo lo demas cuelga de ellas. */
enum class Destino(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
) {
    HOY("hoy", "Hoy", Icons.Filled.Today),
    ACTIVIDADES("actividades", "Registro", Icons.AutoMirrored.Filled.ListAlt),
    HABITOS("habitos", "Habitos", Icons.Filled.Repeat),
    ANALITICA("analitica", "Analitica", Icons.Filled.Insights)
}

object Rutas {
    const val CAPTURA_CON_ID = "captura?id={id}"
    const val CATEGORIAS = "categorias"
    const val AJUSTES = "ajustes"
    const val ARCHIVO = "archivo"
    const val ACERCA_DE = "acerca"

    /** Sin id captura una actividad nueva; con id abre la que ya existe. */
    fun captura(id: Long? = null): String = if (id == null) "captura" else "captura?id=$id"
}
