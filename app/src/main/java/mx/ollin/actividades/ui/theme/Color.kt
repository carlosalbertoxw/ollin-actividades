package mx.ollin.actividades.ui.theme

import androidx.compose.ui.graphics.Color
import mx.ollin.actividades.domain.model.Ambito
import mx.ollin.actividades.domain.model.EstadoActividad

/**
 * Paleta de Ollin.
 *
 * Los pigmentos vienen de la tradicion mexicana y aqui cada uno carga un
 * ambito: anil para el trabajo, jade para el cuerpo, cempasuchil para los
 * habitos, grana para lo personal. El fondo es obsidiana, que deja respirar a
 * los cuatro.
 */
object Pigmento {
    val Obsidiana = Color(0xFF12181B)
    val ObsidianaSuave = Color(0xFF1A2226)
    val ObsidianaClara = Color(0xFF242E33)

    val Jade = Color(0xFF2F9E6E)
    val JadeClaro = Color(0xFF6FCFA1)
    val JadeTenue = Color(0xFF163828)

    val Grana = Color(0xFFC4453F)
    val GranaClaro = Color(0xFFF08A83)
    val GranaTenue = Color(0xFF3B1917)

    val Cempasuchil = Color(0xFFE9A13B)
    val CempasuchilClaro = Color(0xFFF5C57E)
    val CempasuchilTenue = Color(0xFF3A2A12)

    val Anil = Color(0xFF3D6DB5)
    val AnilClaro = Color(0xFF8FB2E3)
    val AnilTenue = Color(0xFF16243A)

    val Papel = Color(0xFFF7F5F0)
    val PapelSombra = Color(0xFFE7E3DA)
    val Tinta = Color(0xFF1A1A18)
    val TintaSuave = Color(0xFF5B615E)
}

/** Colores semanticos, para no repartir decisiones de color por las pantallas. */
data class ColoresOllin(
    val trabajo: Color,
    val fisico: Color,
    val habito: Color,
    val personal: Color,
    val enCurso: Color,
    val completado: Color,
    val pendiente: Color,
    val superficieElevada: Color,
    val textoTenue: Color,
    val trazoSuave: Color
) {
    fun de(ambito: Ambito?): Color = when (ambito) {
        Ambito.TRABAJO -> trabajo
        Ambito.FISICO -> fisico
        Ambito.HABITO -> habito
        Ambito.PERSONAL -> personal
        null -> textoTenue
    }

    fun de(estado: EstadoActividad): Color = when (estado) {
        EstadoActividad.PENDIENTE -> pendiente
        EstadoActividad.EN_CURSO -> enCurso
        EstadoActividad.COMPLETADO -> completado
    }
}

val ColoresOscuros = ColoresOllin(
    trabajo = Pigmento.AnilClaro,
    fisico = Pigmento.JadeClaro,
    habito = Pigmento.CempasuchilClaro,
    personal = Pigmento.GranaClaro,
    enCurso = Pigmento.Cempasuchil,
    completado = Pigmento.JadeClaro,
    pendiente = Color(0xFF9AA5A1),
    superficieElevada = Pigmento.ObsidianaClara,
    textoTenue = Color(0xFF9AA5A1),
    trazoSuave = Color(0xFF2E3A3F)
)

val ColoresClaros = ColoresOllin(
    trabajo = Pigmento.Anil,
    fisico = Pigmento.Jade,
    habito = Color(0xFFB07A22),
    personal = Pigmento.Grana,
    enCurso = Color(0xFFB07A22),
    completado = Pigmento.Jade,
    pendiente = Pigmento.TintaSuave,
    superficieElevada = Color(0xFFFFFFFF),
    textoTenue = Pigmento.TintaSuave,
    trazoSuave = Pigmento.PapelSombra
)

/**
 * Convierte el color guardado de una categoria. Si el texto no es un hex
 * valido se cae al color del ambito, que siempre existe: una categoria sin
 * color no debe pintar un hueco negro en la lista.
 */
fun colorDeCategoria(hex: String?, respaldo: Color): Color {
    if (hex.isNullOrBlank()) return respaldo
    val limpio = hex.removePrefix("#")
    val valor = limpio.toLongOrNull(16) ?: return respaldo
    return when (limpio.length) {
        6 -> Color(valor or 0xFF000000L)
        8 -> Color(valor)
        else -> respaldo
    }
}
