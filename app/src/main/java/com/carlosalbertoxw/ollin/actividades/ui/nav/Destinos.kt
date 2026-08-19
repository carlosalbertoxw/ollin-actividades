package com.carlosalbertoxw.ollin.actividades.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

/** Las cuatro pestañas de abajo. Todo lo demas cuelga de ellas. */
enum class Destino(
    val ruta: String,
    val titulo: String,
    val icono: ImageVector
) {
    HOY("hoy", "Hoy", Icons.Filled.Today),
    ACTIVIDADES("actividades", "Registro", Icons.AutoMirrored.Filled.ListAlt),
    HABITOS("habitos", "Habitos", Icons.Filled.Repeat),
    ANALITICA("analitica", "Analítica", Icons.Filled.Insights)
}

object Rutas {
    /**
     * Los tres argumentos son opcionales y se excluyen entre si: `id` abre una
     * actividad que ya existe, `habito` y `dia` estrenan una rellenada con la
     * plantilla de un habito, y sin ninguno se captura en blanco.
     */
    const val CAPTURA = "captura?id={id}&habito={habito}&dia={dia}"
    const val CATEGORIAS = "categorias"
    const val AJUSTES = "ajustes"
    const val ARCHIVO = "archivo"
    const val ACERCA_DE = "acerca"

    /** Un dia epoch valido puede ser negativo, asi que el centinela va aparte. */
    const val SIN_DIA = Long.MIN_VALUE

    /** Sin id captura una actividad nueva; con id abre la que ya existe. */
    fun captura(id: Long? = null): String = if (id == null) "captura" else "captura?id=$id"

    /**
     * Captura nueva ya rellenada desde un habito, para marcarlo revisando antes
     * los minutos y la hora. El dia viaja porque la pantalla de hoy sabe mirar
     * otras fechas, y marcar ahi tiene que registrar en la que se esta viendo.
     */
    fun capturaDeHabito(habitoId: Long, dia: LocalDate): String =
        "captura?habito=$habitoId&dia=${dia.toEpochDay()}"
}
